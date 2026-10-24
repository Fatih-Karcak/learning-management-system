package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.client.UserClient;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OrganizationService {
  private final JdbcTemplate jdbc;
  private final UserClient users;
  private final LearningManagementService learning;
  private final CourseCatalogService courses;
  private final OutboxService events;

  public record Name(@NotBlank @Size(max = 200) String name) {}

  public record Members(
      @NotNull @Size(min = 1, max = 200) List<@Email @NotBlank @Size(max = 254) String> emails) {}

  public record Assignment(@NotNull UUID courseId, Instant dueAt) {}

  private Map<String, Object> owner(UUID org, UUID user, boolean admin, boolean lock) {
    var rows =
        jdbc.queryForList(
            "select * from training_organizations where id=?" + (lock ? " for update" : ""), org);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
    if (!admin && !user.equals(rows.getFirst().get("owner_id")))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You do not manage this organization");
    return rows.getFirst();
  }

  private UUID group(UUID group, UUID user, boolean admin, boolean lock) {
    var rows =
        jdbc.queryForList(
            "select organization_id from training_groups where id=?", UUID.class, group);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
    owner(rows.getFirst(), user, admin, lock);
    return rows.getFirst();
  }

  public Map<String, Object> organizations(UUID user, boolean admin, int page) {
    if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid page");
    var content =
        admin
            ? jdbc.queryForList(
                "select * from training_organizations order by created_at desc limit 12 offset ?",
                page * 12)
            : jdbc.queryForList(
                "select * from training_organizations where owner_id=? order by created_at desc"
                    + " limit 12 offset ?",
                user,
                page * 12);
    long total =
        admin
            ? jdbc.queryForObject("select count(*) from training_organizations", Long.class)
            : jdbc.queryForObject(
                "select count(*) from training_organizations where owner_id=?", Long.class, user);
    return Map.of("content", content, "total", total);
  }

  @Transactional
  public UUID create(UUID user, Name name) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into training_organizations(id,owner_id,name) values(?,?,?)",
        id,
        user,
        name.name());
    return id;
  }

  public Map<String, Object> detail(UUID org, UUID user, boolean admin) {
    var organization = owner(org, user, admin, false);
    return Map.of(
        "organization",
        organization,
        "groups",
        jdbc.queryForList(
            "select g.*, (select count(*) from training_group_members m where m.group_id=g.id and"
                + " status='ACTIVE') as member_count from training_groups g where organization_id=?"
                + " order by created_at",
            org));
  }

  @Transactional
  public UUID createGroup(UUID org, UUID user, boolean admin, Name input) {
    owner(org, user, admin, true);
    if (jdbc.queryForObject(
            "select count(*) from training_groups where organization_id=?", Integer.class, org)
        >= 100)
      throw new IllegalArgumentException("An organization can contain up to 100 training groups");
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into training_groups(id,organization_id,name) values(?,?,?)",
        id,
        org,
        input.name());
    return id;
  }

  public Map<String, Object> members(UUID group, UUID user, boolean admin, int page) {
    group(group, user, admin, false);
    if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid page");
    return Map.of(
        "content",
        jdbc.queryForList(
            "select * from training_group_members where group_id=? order by created_at desc limit"
                + " 50 offset ?",
            group,
            page * 50),
        "total",
        jdbc.queryForObject(
            "select count(*) from training_group_members where group_id=?", Long.class, group),
        "assignments",
        jdbc.queryForList(
            "select * from training_group_courses where group_id=? order by created_at desc",
            group));
  }

  @Transactional
  public int invite(UUID group, UUID user, boolean admin, Members input) {
    group(group, user, admin, true);
    int added = 0;
    int count =
        jdbc.queryForObject(
            "select count(*) from training_group_members where group_id=?", Integer.class, group);
    for (String email :
        new LinkedHashSet<>(
            input.emails().stream().map(value -> value.trim().toLowerCase(Locale.ROOT)).toList())) {
      com.lms.enrollmentservice.dto.response.UserInfoResult result;
      try {
        result = users.byEmail(email);
      } catch (
          org.springframework.web.reactive.function.client.WebClientResponseException.NotFound ex) {
        throw new IllegalArgumentException("No active verified account for " + email);
      }
      if (result == null
          || !result.success()
          || result.data() == null
          || !"ACTIVE".equals(result.data().status())
          || !result.data().emailVerified())
        throw new IllegalArgumentException("An active verified account is required for " + email);
      var value = result.data();
      int created =
          jdbc.update(
              "insert into training_group_members(group_id,student_id,email,display_name)"
                  + " values(?,?,?,?) on conflict(group_id,student_id) do nothing",
              group,
              value.id(),
              value.email(),
              value.firstName() + " " + value.lastName());
      added += created;
      if (count + added > 500)
        throw new IllegalArgumentException(
            "A group supports up to 500 learners including pending invitations");
      if (created == 1) {
        UUID event = UUID.randomUUID();
        events.enqueue(
            "platform-notices",
            event.toString(),
            new LearningReminderService.Notice(
                event,
                value.id(),
                null,
                "Training group invitation",
                "You have been invited to a training group. Accept to share progress for the"
                    + " courses assigned by this organization.",
                "/organizations",
                "GROUP_INVITATION",
                null));
      }
    }
    return added;
  }

  public List<Map<String, Object>> invitations(UUID user) {
    return jdbc.queryForList(
        "select m.group_id,m.status,g.name as group_name,o.name as organization_name from"
            + " training_group_members m join training_groups g on g.id=m.group_id join"
            + " training_organizations o on o.id=g.organization_id where m.student_id=? order by"
            + " m.created_at desc limit 100",
        user);
  }

  @Transactional
  public void reply(UUID group, UUID user, boolean accept) {
    var organizations =
        jdbc.queryForList(
            "select organization_id from training_groups where id=?", UUID.class, group);
    if (organizations.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
    jdbc.queryForList(
        "select id from training_organizations where id=? for update", organizations.getFirst());
    var rows =
        jdbc.queryForList(
            "select status from training_group_members where group_id=? and student_id=? for"
                + " update",
            String.class,
            group,
            user);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
    if (!"INVITED".equals(rows.getFirst()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Invitation has already been answered");
    if (accept
        && jdbc.queryForObject(
                "select count(*) from training_group_members where group_id=? and status='ACTIVE'",
                Integer.class,
                group)
            >= 500)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This group has reached its learner limit");
    jdbc.update(
        "update training_group_members set status=?,replied_at=now() where group_id=? and"
            + " student_id=?",
        accept ? "ACTIVE" : "DECLINED",
        group,
        user);
    if (accept)
      for (UUID course :
          jdbc.queryForList(
              "select course_id from training_group_courses where group_id=?", UUID.class, group))
        learning.enrollFree(course, user);
  }

  @Transactional
  public void remove(UUID group, UUID student, UUID actor, boolean admin) {
    group(group, actor, admin, true);
    jdbc.update(
        "delete from training_group_members where group_id=? and student_id=?", group, student);
    // Membership removal ends reporting access; existing course enrollment remains with the
    // learner.
  }

  @Transactional
  public void assign(UUID group, UUID actor, boolean admin, Assignment input) {
    group(group, actor, admin, true);
    var course = courses.requireCourse(input.courseId());
    var price = course.discountPrice() != null ? course.discountPrice() : course.price();
    if (jdbc.queryForObject(
            "select count(*) from training_group_courses where group_id=? and course_id<>?",
            Integer.class,
            group,
            input.courseId())
        >= 100) throw new IllegalArgumentException("A group supports up to 100 assigned courses");
    if (!"PUBLISHED".equals(course.status()) || price == null || price.signum() != 0)
      throw new IllegalArgumentException("Only published free courses can be assigned");
    var members =
        jdbc.queryForList(
            "select student_id from training_group_members where group_id=? and status='ACTIVE'"
                + " order by student_id",
            UUID.class,
            group);
    if (members.size() > 500)
      throw new IllegalArgumentException("A training group supports up to 500 active learners");
    for (UUID student : members) {
      var account = users.getUserById(student);
      if (account == null
          || !account.success()
          || account.data() == null
          || !"ACTIVE".equals(account.data().status()))
        throw new IllegalArgumentException("Remove inactive learners before assigning this course");
      learning.enrollFree(input.courseId(), student);
    }
    jdbc.update(
        "insert into training_group_courses(group_id,course_id,assigned_by,due_at) values(?,?,?,?)"
            + " on conflict(group_id,course_id) do update set"
            + " due_at=excluded.due_at,assigned_by=excluded.assigned_by",
        group,
        input.courseId(),
        actor,
        input.dueAt() == null
            ? null
            : java.time.LocalDateTime.ofInstant(input.dueAt(), java.time.ZoneOffset.UTC));
  }

  public Map<String, Object> report(UUID group, UUID user, boolean admin, int page) {
    group(group, user, admin, false);
    if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid page");
    String from =
        " from training_group_members m join training_group_courses c on c.group_id=m.group_id left"
            + " join enrollments e on e.student_id=m.student_id and e.course_id=c.course_id where"
            + " m.group_id=? and m.status='ACTIVE'";
    return Map.of(
        "content",
        jdbc.queryForList(
            "select"
                + " m.display_name,m.email,c.course_id,c.due_at,e.course_title,e.status,e.progress_percentage,e.completed_at,e.last_accessed_at,(c.due_at<now()"
                + " and coalesce(e.status,'')<>'COMPLETED') as overdue"
                + from
                + " order by m.display_name,c.course_id limit 50 offset ?",
            group,
            page * 50),
        "total",
        jdbc.queryForObject("select count(*)" + from, Long.class, group));
  }

  public static String csvCell(Object value) {
    String text = value == null ? "" : value.toString();
    if (!text.isEmpty() && "=+-@\t\r\n".indexOf(text.charAt(0)) >= 0) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public String csv(UUID group, UUID user, boolean admin) {
    StringBuilder csv =
        new StringBuilder("Learner,Email,Course,Status,Progress,Due date,Overdue\r\n");
    for (int page = 0; page < 1000; page++) {
      var report = report(group, user, admin, page);
      @SuppressWarnings("unchecked")
      var rows = (List<Map<String, Object>>) report.get("content");
      for (var row : rows)
        csv.append(
                java.util.stream.Stream.of(
                        "display_name",
                        "email",
                        "course_title",
                        "status",
                        "progress_percentage",
                        "due_at",
                        "overdue")
                    .map(key -> csvCell(row.get(key)))
                    .collect(java.util.stream.Collectors.joining(",")))
            .append("\r\n");
      if ((page + 1) * 50 >= ((Number) report.get("total")).longValue()) return csv.toString();
    }
    throw new ResponseStatusException(
        HttpStatus.PAYLOAD_TOO_LARGE, "Export a smaller training group");
  }
}
