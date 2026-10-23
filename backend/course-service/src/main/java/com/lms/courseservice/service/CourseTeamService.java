package com.lms.courseservice.service;

import com.lms.courseservice.client.InstructorDirectory;
import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.repository.CourseRepository;
import com.lms.courseservice.security.AuthenticatedUser;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CourseTeamService {

    private final CourseRepository courses;
    private final CourseAuthoringAccess access;
    private final InstructorDirectory directory;
    private final JdbcTemplate jdbc;

    public record Invite(@NotBlank @Email @Size(max = 254) String email) {}

    public record Reply(boolean accept) {}

    public record Team(
        UUID ownerId,
        String ownerName,
        boolean canManage,
        List<Map<String, Object>> members
    ) {}

    private Course course(UUID id) {
        return courses
            .findById(id)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND)
            );
    }

    private Course managed(UUID id, AuthenticatedUser user) {
        jdbc.queryForList("select id from courses where id=? for update", id);
        var course = course(id);
        if (
            !user.isAdmin() && !course.getInstructorId().equals(user.id())
        ) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Ekibi yalnızca kurs sahibi yönetebilir"
        );
        return course;
    }

    @Transactional(readOnly = true)
    public Team get(UUID id, AuthenticatedUser user) {
        var c = course(id);
        if (
            !user.isAdmin() && !access.canEdit(c, user.id())
        ) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return new Team(
            c.getInstructorId(),
            c.getInstructorName(),
            user.isAdmin() || c.getInstructorId().equals(user.id()),
            jdbc.queryForList(
                "select user_id,display_name,status,invited_at,accepted_at from course_collaborators where course_id=? order by invited_at",
                id
            )
        );
    }

    @Transactional
    public Team invite(UUID id, Invite input, AuthenticatedUser user) {
        var c = managed(id, user);
        var target = directory.find(
            input.email().trim().toLowerCase(Locale.ROOT)
        );
        if (
            target.id().equals(c.getInstructorId())
        ) throw new IllegalArgumentException("Kurs sahibi zaten ekipte");
        var count = jdbc.queryForObject(
            "select count(*) from course_collaborators where course_id=?",
            Integer.class,
            id
        );
        if (count != null && count >= 20) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Bir kursta en fazla 20 ortak eğitmen olabilir"
        );
        jdbc.update(
            "insert into course_collaborators(course_id,user_id,display_name,status,invited_by) values(?,?,?,'INVITED',?) on conflict(course_id,user_id) do nothing",
            id,
            target.id(),
            target.displayName(),
            user.id()
        );
        return get(id, user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> invitations(AuthenticatedUser user) {
        return jdbc.queryForList(
            "select m.course_id,c.title,c.instructor_name,m.invited_at from course_collaborators m join courses c on c.id=m.course_id where m.user_id=? and m.status='INVITED' order by m.invited_at desc",
            user.id()
        );
    }

    @Transactional
    public void reply(UUID id, Reply input, AuthenticatedUser user) {
        jdbc.queryForList("select id from courses where id=? for update", id);
        int changed = input.accept()
            ? jdbc.update(
                  "update course_collaborators set status='ACCEPTED',accepted_at=now() where course_id=? and user_id=? and status='INVITED'",
                  id,
                  user.id()
              )
            : jdbc.update(
                  "delete from course_collaborators where course_id=? and user_id=? and status='INVITED'",
                  id,
                  user.id()
              );
        if (changed != 1) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Bekleyen davet bulunamadı"
        );
    }

    @Transactional
    public void remove(UUID id, UUID member, AuthenticatedUser user) {
        if (!member.equals(user.id())) managed(id, user);
        else jdbc.queryForList(
            "select id from courses where id=? for update",
            id
        );
        jdbc.update(
            "delete from course_collaborators where course_id=? and user_id=?",
            id,
            member
        );
    }
}
