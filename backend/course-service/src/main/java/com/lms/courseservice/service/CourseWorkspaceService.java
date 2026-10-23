package com.lms.courseservice.service;

import com.lms.courseservice.client.ContentClient;
import com.lms.courseservice.domain.entity.*;
import com.lms.courseservice.domain.enums.*;
import com.lms.courseservice.dto.response.*;
import com.lms.courseservice.repository.*;
import com.lms.courseservice.security.AuthenticatedUser;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Draft documents never mutate the curriculum visible to learners. Approval promotes them atomically. */
@Service
@RequiredArgsConstructor
public class CourseWorkspaceService {

    private final CourseRepository courses;
    private final CategoryRepository categories;
    private final PlatformEvents events;
    private final com.lms.courseservice.mapper.CourseMapper mapper;
    private final CourseAccessPolicy access;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ContentClient content;
    private final Validator validator;
    private final CourseAuthoringAccess authors;

    public record Material(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String url,
        @NotNull @Pattern(regexp = "RESOURCE|SUBTITLE") String type,
        @Size(max = 20) String language
    ) {}

    public record Document(
        @NotNull CourseResponse course,
        @NotNull Long categoryId,
        @NotNull Map<UUID, AssessmentService.Quiz> quizzes,
        @NotNull Map<UUID, List<@Valid Material>> materials
    ) {}

    public record Save(@Min(0) long revision, @NotNull Document document) {}

    public record Workspace(
        long revision,
        String status,
        String feedback,
        Document document,
        List<String> checklist
    ) {}

    public record Decision(
        @NotNull @Pattern(regexp = "APPROVE|REJECT") String action,
        @Size(max = 2000) String reason
    ) {}

    private Course owner(UUID id, AuthenticatedUser user) {
        var course = courses
            .findById(id)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Kurs bulunamadı"
                )
            );
        if (
            !user.isAdmin() &&
            !course.getInstructorId().equals(user.id()) &&
            !authors.canEdit(course, user.id())
        ) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Kurs size ait değil"
        );
        return course;
    }

    public static void requireDirectEditable(Course course) {
        if (
            course.getStatus() != CourseStatus.DRAFT
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Yayındaki veya incelemedeki kursu çalışma taslağından düzenleyin"
        );
    }

    private Course locked(UUID id, AuthenticatedUser user) {
        jdbc.queryForList("select id from courses where id=? for update", id);
        return owner(id, user);
    }

    private Document current(Course course) {
        Map<UUID, AssessmentService.Quiz> quizzes = new LinkedHashMap<>();
        Map<UUID, List<Material>> materials = new LinkedHashMap<>();
        for (var section : course.getSections())
            for (var lesson : section.getLessons()) {
                jdbc.query(
                    "select definition from lesson_quizzes where lesson_id=?",
                    rs -> {
                        quizzes.put(
                            lesson.getId(),
                            json.readValue(
                                rs.getString(1),
                                AssessmentService.Quiz.class
                            )
                        );
                    },
                    lesson.getId()
                );
                jdbc.query(
                    "select document from lesson_materials where lesson_id=?",
                    rs -> {
                        materials.put(
                            lesson.getId(),
                            Arrays.asList(
                                json.readValue(
                                    rs.getString(1),
                                    Material[].class
                                )
                            )
                        );
                    },
                    lesson.getId()
                );
            }
        return new Document(
            mapper.toResponse(course),
            course.getCategory().getId(),
            quizzes,
            materials
        );
    }

    @Transactional(readOnly = true)
    public Workspace get(UUID id, AuthenticatedUser user) {
        var course = owner(id, user);
        var rows = jdbc.queryForList(
            "select revision,status,feedback,document from course_workspaces where course_id=?",
            id
        );
        if (rows.isEmpty()) {
            var d = current(course);
            return new Workspace(0, "DRAFT", null, d, checklist(d));
        }
        var r = rows.getFirst();
        var d = json.readValue((String) r.get("document"), Document.class);
        return new Workspace(
            ((Number) r.get("revision")).longValue(),
            (String) r.get("status"),
            (String) r.get("feedback"),
            d,
            checklist(d)
        );
    }

    @Transactional
    public Workspace save(UUID id, Save input, AuthenticatedUser user) {
        var course = locked(id, user);
        var previous = get(id, user);
        if (
            previous.revision() != input.revision()
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Taslak başka bir pencerede değişti. Yenileyip değişikliklerinizi yeniden uygulayın"
        );
        if (
            "PENDING_REVIEW".equals(previous.status())
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "İnceleme sürüyor; önce başvuruyu geri çekin"
        );
        validate(input.document(), course);
        jdbc.update(
            "insert into course_workspaces(course_id,revision,document) values(?,?,?) on conflict(course_id) do update set revision=excluded.revision,document=excluded.document,status='DRAFT',feedback=null,updated_at=now()",
            id,
            input.revision() + 1,
            json.writeValueAsString(input.document())
        );
        return get(id, user);
    }

    @Transactional
    public Workspace submit(UUID id, long revision, AuthenticatedUser user) {
        requirePublisher(locked(id, user), user);
        var workspace = get(id, user);
        if (
            workspace.revision() != revision || revision == 0
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Önce güncel taslağı kaydedin"
        );
        if (
            !workspace.checklist().isEmpty()
        ) throw new IllegalArgumentException(
            String.join("; ", workspace.checklist())
        );
        validate(workspace.document(), owner(id, user));
        verifyMedia(workspace.document(), owner(id, user));
        jdbc.update(
            "update course_workspaces set status='PENDING_REVIEW',revision=revision+1,submitted_at=now(),feedback=null where course_id=?",
            id
        );
        return get(id, user);
    }

    @Transactional
    public Workspace withdraw(UUID id, AuthenticatedUser user) {
        requirePublisher(locked(id, user), user);
        jdbc.update(
            "update course_workspaces set status='DRAFT',revision=revision+1 where course_id=? and status='PENDING_REVIEW'",
            id
        );
        return get(id, user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pending(AuthenticatedUser user) {
        if (!user.isAdmin()) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN
        );
        return jdbc.queryForList(
            "select w.course_id,w.revision,w.submitted_at,c.title,c.instructor_name from course_workspaces w join courses c on c.id=w.course_id where w.status='PENDING_REVIEW' order by w.submitted_at limit 100"
        );
    }

    @Transactional
    public Workspace decide(
        UUID id,
        long revision,
        Decision decision,
        AuthenticatedUser user
    ) {
        if (!user.isAdmin()) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN
        );
        var course = locked(id, user);
        var w = get(id, user);
        if (
            !"PENDING_REVIEW".equals(w.status()) || w.revision() != revision
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "İncelenen taslak değişti"
        );
        if ("REJECT".equals(decision.action())) {
            if (
                decision.reason() == null || decision.reason().isBlank()
            ) throw new IllegalArgumentException("Düzeltme gerekçesi gerekli");
            jdbc.update(
                "update course_workspaces set status='DRAFT',feedback=?,revision=revision+1 where course_id=?",
                decision.reason(),
                id
            );
        } else {
            validate(w.document(), course);
            if (
                !checklist(w.document()).isEmpty()
            ) throw new IllegalArgumentException(
                "Yayın kontrol listesini tamamlayın"
            );
            verifyMedia(w.document(), course);
            promote(course, w.document());
            events.enqueue(
                new com.lms.courseservice.event.PlatformNotice(
                    UUID.randomUUID(),
                    course.getInstructorId(),
                    id,
                    "Kursunuz yayımlandı",
                    w.document().course().getTitle(),
                    "/instructor/" + id,
                    "COURSE_PUBLISHED",
                    null
                )
            );
            jdbc.update(
                "update course_workspaces set status='PUBLISHED',feedback=null,revision=revision+1 where course_id=?",
                id
            );
            int next = jdbc.queryForObject(
                "select coalesce(max(version_number),0)+1 from course_versions where course_id=?",
                Integer.class,
                id
            );
            jdbc.update(
                "insert into course_versions(id,course_id,version_number,document,actor_id) values(?,?,?,?,?)",
                UUID.randomUUID(),
                id,
                next,
                json.writeValueAsString(w.document()),
                user.id()
            );
        }
        jdbc.update(
            "insert into course_review_events(id,course_id,actor_id,decision,reason) values(?,?,?,?,?)",
            UUID.randomUUID(),
            id,
            user.id(),
            decision.action(),
            decision.reason()
        );
        return get(id, user);
    }

    public List<Map<String, Object>> history(UUID id, AuthenticatedUser user) {
        owner(id, user);
        return jdbc.queryForList(
            "select version_number,published_at,actor_id from course_versions where course_id=? order by version_number desc limit 100",
            id
        );
    }

    @Transactional(readOnly = true)
    public List<Material> materials(
        UUID courseId,
        UUID lessonId,
        AuthenticatedUser user
    ) {
        var c = courses
            .findById(courseId)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND)
            );
        access.requireVisible(c, user);
        if (!access.canReadContent(c, user)) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Kursa kayıt olun"
        );
        if (
            c
                .getSections()
                .stream()
                .flatMap(s -> s.getLessons().stream())
                .noneMatch(l -> l.getId().equals(lessonId))
        ) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var values = jdbc.queryForList(
            "select document from lesson_materials where lesson_id=?",
            String.class,
            lessonId
        );
        return values.isEmpty()
            ? List.of()
            : Arrays.asList(
                  json.readValue(values.getFirst(), Material[].class)
              );
    }

    private void validate(Document d, Course existing) {
        if (
            d == null ||
            d.course() == null ||
            d.quizzes() == null ||
            d.materials() == null
        ) throw new IllegalArgumentException("Taslak eksik");
        if (
            json.writeValueAsString(d).length() > 10 * 1024 * 1024
        ) throw new IllegalArgumentException("Taslak 10 MB sınırını aşıyor");
        var c = d.course();
        if (
            !existing.getId().equals(c.getId())
        ) throw new IllegalArgumentException("Kurs kimliği uyuşmuyor");
        text(c.getTitle(), 3, 200, "Kurs başlığı");
        if (
            c.getTargetAudience() != null &&
            c.getTargetAudience().length() > 2000
        ) throw new IllegalArgumentException("Hedef kitle açıklaması çok uzun");
        text(c.getDescription(), 1, 100000, "Açıklama");
        text(c.getShortDescription(), 1, 500, "Kısa açıklama");
        text(c.getLanguage(), 1, 50, "Dil");
        if (
            c.getLevel() == null ||
            c.getPrice() == null ||
            c.getPrice().signum() < 0 ||
            c.getPrice().compareTo(new java.math.BigDecimal("99999999")) > 0
        ) throw new IllegalArgumentException("Seviye veya fiyat geçersiz");
        if (
            c.getDiscountPrice() != null &&
            (c.getDiscountPrice().signum() < 0 ||
                c.getDiscountPrice().compareTo(c.getPrice()) > 0)
        ) throw new IllegalArgumentException("İndirimli fiyat geçersiz");
        if (
            !categories.existsById(d.categoryId())
        ) throw new IllegalArgumentException("Kategori bulunamadı");
        if (
            c.getSections() == null || c.getSections().size() > 100
        ) throw new IllegalArgumentException("En fazla 100 bölüm olabilir");
        var sectionIds = new HashSet<UUID>();
        var lessonIds = new HashSet<UUID>();
        for (var s : c.getSections()) {
            if (
                s.getId() == null || !sectionIds.add(s.getId())
            ) throw new IllegalArgumentException("Yinelenen bölüm");
            text(s.getTitle(), 1, 200, "Bölüm başlığı");
            if (
                s.getLessons() == null || s.getLessons().size() > 200
            ) throw new IllegalArgumentException(
                "Bölümde en fazla 200 ders olabilir"
            );
            for (var l : s.getLessons()) {
                if (
                    l.getId() == null ||
                    !lessonIds.add(l.getId()) ||
                    l.getType() == null
                ) throw new IllegalArgumentException(
                    "Ders kimliği veya türü geçersiz"
                );
                text(l.getTitle(), 1, 200, "Ders başlığı");
                if (
                    l.getContent() != null && l.getContent().length() > 100000
                ) throw new IllegalArgumentException("Ders metni çok uzun");
            }
        }
        // Published lesson identities and assessment kinds remain stable: old progress/certificates stay valid.
        if (
            existing.getPublishedAt() != null
        ) for (var s : existing.getSections())
            for (var l : s.getLessons()) {
                var next = c
                    .getSections()
                    .stream()
                    .flatMap(x -> x.getLessons().stream())
                    .filter(x -> x.getId().equals(l.getId()))
                    .findFirst();
                if (
                    next.isEmpty() || next.get().getType() != l.getType()
                ) throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Yayımlanmış ders silinemez veya türü değiştirilemez. İçeriği güncelleyebilir ya da kursu arşivleyebilirsiniz"
                );
            }
        for (var id : sectionIds) {
            var owners = jdbc.queryForList(
                "select course_id from sections where id=?",
                UUID.class,
                id
            );
            if (
                !owners.isEmpty() && !owners.getFirst().equals(existing.getId())
            ) throw new IllegalArgumentException("Bölüm başka bir kursa ait");
        }
        for (var id : lessonIds) {
            var owners = jdbc.queryForList(
                "select s.course_id from lessons l join sections s on s.id=l.section_id where l.id=?",
                UUID.class,
                id
            );
            if (
                !owners.isEmpty() && !owners.getFirst().equals(existing.getId())
            ) throw new IllegalArgumentException("Ders başka bir kursa ait");
        }
        d.quizzes().forEach((id, q) -> {
            if (
                q == null ||
                !lessonIds.contains(id) ||
                !validator.validate(q).isEmpty() ||
                (q.questionCount() != null &&
                    q.questionCount() > q.questions().size())
            ) throw new IllegalArgumentException("Test tanımı geçersiz");
            var ids = new HashSet<UUID>();
            for (var question : q.questions())
                if (
                    question.id() == null ||
                    !ids.add(question.id()) ||
                    question.correctOption() >= question.options().size()
                ) throw new IllegalArgumentException("Test soruları geçersiz");
        });
        d.materials().forEach((id, items) -> {
            if (
                !lessonIds.contains(id) || items == null || items.size() > 20
            ) throw new IllegalArgumentException("Kaynak listesi geçersiz");
            for (var item : items) {
                if (
                    !validator.validate(item).isEmpty()
                ) throw new IllegalArgumentException(
                    "Kaynak bilgileri geçersiz"
                );
                URI u;
                try {
                    u = URI.create(item.url());
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                        "Kaynak adresi geçersiz"
                    );
                }
                if (
                    !item
                        .url()
                        .matches(
                            "^/api/v1/media/resources/[0-9a-fA-F-]{36}$"
                        ) &&
                    (!"https".equals(u.getScheme()) ||
                        u.getHost() == null ||
                        u.getUserInfo() != null)
                ) throw new IllegalArgumentException(
                    "Kaynak adresi HTTPS olmalı"
                );
                if (
                    "SUBTITLE".equals(item.type()) &&
                    (item.language() == null ||
                        !item
                            .language()
                            .matches("[a-zA-Z]{2,3}(-[a-zA-Z]{2,4})?"))
                ) throw new IllegalArgumentException("Altyazı dili gerekli");
            }
        });
    }

    private void text(String s, int min, int max, String name) {
        if (
            s == null || s.trim().length() < min || s.length() > max
        ) throw new IllegalArgumentException(name + " geçersiz");
    }

    private void requirePublisher(Course course, AuthenticatedUser user) {
        if (
            !user.isAdmin() && !course.getInstructorId().equals(user.id())
        ) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Yayın başvurusunu yalnızca kurs sahibi yönetebilir"
        );
    }

    private List<String> checklist(Document d) {
        var issues = new ArrayList<String>();
        var c = d.course();
        if (
            c.getRequirements() == null ||
            c
                .getRequirements()
                .stream()
                .noneMatch(x -> x != null && !x.isBlank())
        ) issues.add("En az bir ön koşul yazın (yoksa 'Ön koşul yok')");
        if (
            c.getOutcomes() == null ||
            c
                .getOutcomes()
                .stream()
                .noneMatch(x -> x != null && !x.isBlank())
        ) issues.add("En az bir öğrenme kazanımı ekleyin");
        if (
            c.getSections() == null ||
            c
                .getSections()
                .stream()
                .allMatch(s -> s.getLessons().isEmpty())
        ) issues.add("En az bir ders ekleyin");
        if (c.getSections() != null) for (var s : c.getSections())
            for (var l : s.getLessons()) {
                if (
                    l.getType() == LessonType.QUIZ &&
                    !d.quizzes().containsKey(l.getId())
                ) issues.add(l.getTitle() + ": test soruları eksik");
                if (
                    (l.getType() == LessonType.ARTICLE ||
                        l.getType() == LessonType.ASSIGNMENT) &&
                    (l.getContent() == null || l.getContent().isBlank())
                ) issues.add(l.getTitle() + ": içerik eksik");
                if (
                    l.getType() == LessonType.VIDEO &&
                    (l.getVideoUrl() == null || l.getVideoUrl().isBlank())
                ) issues.add(l.getTitle() + ": video eksik");
            }
        return issues;
    }

    private void verifyMedia(Document d, Course course) {
        for (var items : d.materials().values())
            for (var material : items)
                if (material.url().startsWith("/api/v1/media/resources/")) {
                    var resource = content.resource(
                        UUID.fromString(
                            material
                                .url()
                                .substring(material.url().lastIndexOf('/') + 1)
                        )
                    );
                    if (
                        resource == null ||
                        !resource.success() ||
                        resource.data() == null ||
                        !course.getId().equals(resource.data().courseId()) ||
                        !"MATERIAL".equals(resource.data().purpose())
                    ) throw new IllegalArgumentException(
                        "Kaynak bu kursa ve eğitmene ait değil"
                    );
                    if (
                        "SUBTITLE".equals(material.type()) &&
                        !"text/vtt".equals(resource.data().contentType())
                    ) throw new IllegalArgumentException(
                        "Altyazı WebVTT olmalı"
                    );
                }

        for (var s : d.course().getSections())
            for (var l : s.getLessons())
                if (l.getType() == LessonType.VIDEO) {
                    var match = java.util.regex.Pattern.compile(
                        "^/api/v1/media/([0-9a-fA-F-]{36})/manifest\\.m3u8$"
                    ).matcher(l.getVideoUrl());
                    if (!match.matches()) throw new IllegalArgumentException(
                        "Videoyu medya kitaplığından seçin"
                    );
                    try {
                        var result = content.linkVideo(
                            UUID.fromString(match.group(1)),
                            new ContentClient.LinkRequest(
                                course.getId(),
                                l.getId(),
                                course.getInstructorId()
                            )
                        );
                        if (
                            result == null ||
                            !result.success() ||
                            result.data() == null ||
                            !"READY".equals(result.data().status()) ||
                            result.data().durationSeconds() == null ||
                            result.data().durationSeconds() <= 0
                        ) throw new IllegalStateException(
                            l.getTitle() + ": video henüz hazır değil"
                        );
                        l.setDurationSeconds(result.data().durationSeconds());
                    } catch (
                        org.springframework.web.client.RestClientException e
                    ) {
                        throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Video doğrulanamadı",
                            e
                        );
                    }
                }
    }

    private void promote(Course original, Document d) {
        var c = d.course();
        // JDBC preserves every existing UUID, including moved lessons, and promotes all fields in one DB transaction.
        jdbc.update(
            "update courses set title=?,description=?,short_description=?,thumbnail_url=?,preview_video_url=?,target_audience=?,category_id=?,level=?,price=?,discount_price=?,language=?,updated_at=now(),published_at=coalesce(published_at,now()),status='PUBLISHED',review_feedback=null where id=?",
            c.getTitle(),
            c.getDescription(),
            c.getShortDescription(),
            c.getThumbnailUrl(),
            c.getPreviewVideoUrl(),
            c.getTargetAudience(),
            d.categoryId(),
            c.getLevel().name(),
            c.getPrice(),
            c.getDiscountPrice(),
            c.getLanguage(),
            original.getId()
        );
        jdbc.update(
            "delete from course_requirements where course_id=?",
            original.getId()
        );
        jdbc.update(
            "delete from course_outcomes where course_id=?",
            original.getId()
        );
        if (c.getRequirements() != null) for (var r : c.getRequirements())
            jdbc.update(
                "insert into course_requirements(course_id,requirement) values(?,?)",
                original.getId(),
                r
            );
        if (c.getOutcomes() != null) for (var r : c.getOutcomes())
            jdbc.update(
                "insert into course_outcomes(course_id,outcome) values(?,?)",
                original.getId(),
                r
            );
        var sectionIds = new ArrayList<UUID>();
        var lessonIds = new ArrayList<UUID>();
        for (int si = 0; si < c.getSections().size(); si++) {
            var s = c.getSections().get(si);
            sectionIds.add(s.getId());
            jdbc.update(
                "insert into sections(id,course_id,title,description,order_index) values(?,?,?,?,?) on conflict(id) do update set title=excluded.title,description=excluded.description,order_index=excluded.order_index",
                s.getId(),
                original.getId(),
                s.getTitle(),
                s.getDescription(),
                si
            );
            for (int li = 0; li < s.getLessons().size(); li++) {
                var l = s.getLessons().get(li);
                lessonIds.add(l.getId());
                jdbc.update(
                    "insert into lessons(id,section_id,title,content,video_url,duration_seconds,type,order_index,preview_allowed) values(?,?,?,?,?,?,?,?,?) on conflict(id) do update set section_id=excluded.section_id,title=excluded.title,content=excluded.content,video_url=excluded.video_url,duration_seconds=excluded.duration_seconds,type=excluded.type,order_index=excluded.order_index,preview_allowed=excluded.preview_allowed",
                    l.getId(),
                    s.getId(),
                    l.getTitle(),
                    l.getContent(),
                    l.getVideoUrl(),
                    l.getDurationSeconds(),
                    l.getType().name(),
                    li,
                    l.isPreviewAllowed()
                );
                if (d.quizzes().containsKey(l.getId())) jdbc.update(
                    "insert into lesson_quizzes(lesson_id,definition) values(?,?) on conflict(lesson_id) do update set definition=excluded.definition,updated_at=now()",
                    l.getId(),
                    json.writeValueAsString(d.quizzes().get(l.getId()))
                );
                jdbc.update(
                    "insert into lesson_materials(lesson_id,document) values(?,?) on conflict(lesson_id) do update set document=excluded.document",
                    l.getId(),
                    json.writeValueAsString(
                        d.materials().getOrDefault(l.getId(), List.of())
                    )
                );
            }
        }
        // Only unpublished content may be removed; validation above protects all published lessons.
        for (var s : original.getSections())
            for (var l : s.getLessons())
                if (!lessonIds.contains(l.getId())) jdbc.update(
                    "delete from lessons where id=?",
                    l.getId()
                );
        for (var s : original.getSections())
            if (!sectionIds.contains(s.getId())) jdbc.update(
                "delete from sections where id=?",
                s.getId()
            );
        jdbc.update(
            "update courses set duration_minutes=(select coalesce(ceil(sum(l.duration_seconds)/60.0),0) from lessons l join sections s on s.id=l.section_id where s.course_id=?) where id=?",
            original.getId(),
            original.getId()
        );
    }
}
