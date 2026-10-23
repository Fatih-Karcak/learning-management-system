package com.lms.courseservice.service;

import com.lms.courseservice.domain.enums.*;
import com.lms.courseservice.dto.request.CreateCourseRequest;
import com.lms.courseservice.dto.response.*;
import com.lms.courseservice.security.AuthenticatedUser;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CourseTemplateService {

    private final CourseService courses;
    private final CourseWorkspaceService workspaces;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final jakarta.persistence.EntityManager entityManager;

    public record Save(
        @NotBlank @Size(max = 120) String name,
        @Min(0) long revision
    ) {}

    public record Create(
        @NotBlank @Size(min = 10, max = 200) String title,
        @NotNull Long categoryId
    ) {}

    public record Template(String id, String name, boolean builtIn) {}

    private static final List<Template> BUILT_INS = List.of(
        new Template("starter", "Temel eğitim", true),
        new Template("workshop", "Uygulamalı atölye", true),
        new Template("assessment", "Test ve değerlendirme", true)
    );

    @Transactional(readOnly = true)
    public List<Template> list(AuthenticatedUser user) {
        var result = new ArrayList<>(BUILT_INS);
        result.addAll(
            jdbc.query(
                "select id,name from course_templates where owner_id=? order by created_at desc",
                (rs, n) ->
                    new Template(
                        rs.getString("id"),
                        rs.getString("name"),
                        false
                    ),
                user.id()
            )
        );
        return result;
    }

    @Transactional
    public Template save(UUID course, Save input, AuthenticatedUser user) {
        jdbc.queryForList(
            "select id from courses where id=? for update",
            course
        );
        var workspace = workspaces.get(course, user);
        if (
            workspace.revision() != input.revision()
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Taslak değişti; güncel halini kaydedip tekrar deneyin"
        );
        jdbc.queryForList(
            "select pg_advisory_xact_lock(hashtextextended(?,0))",
            "course-templates:" + user.id()
        );
        var count = jdbc.queryForObject(
            "select count(*) from course_templates where owner_id=?",
            Integer.class,
            user.id()
        );
        if (count != null && count >= 25) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "En fazla 25 kişisel şablon saklayabilirsiniz"
        );
        var id = UUID.randomUUID();
        jdbc.update(
            "insert into course_templates(id,owner_id,name,document) values(?,?,?,?)",
            id,
            user.id(),
            input.name().trim(),
            json.writeValueAsString(workspace.document())
        );
        return new Template(id.toString(), input.name().trim(), false);
    }

    @Transactional
    public void delete(UUID template, AuthenticatedUser user) {
        if (
            jdbc.update(
                "delete from course_templates where id=? and owner_id=?",
                template,
                user.id()
            ) != 1
        ) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Şablon bulunamadı"
        );
    }

    @Transactional
    public CourseResponse create(
        String template,
        Create input,
        AuthenticatedUser user
    ) {
        CourseWorkspaceService.Document source;
        if (BUILT_INS.stream().anyMatch(t -> t.id().equals(template))) source =
            builtIn(template);
        else {
            var rows = jdbc.queryForList(
                "select document from course_templates where id=? and owner_id=?",
                String.class,
                UUID.fromString(template),
                user.id()
            );
            if (rows.isEmpty()) throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Şablon bulunamadı"
            );
            source = json.readValue(
                rows.getFirst(),
                CourseWorkspaceService.Document.class
            );
        }
        var c = source.course();
        var request = new CreateCourseRequest();
        request.setTitle(input.title().trim());
        request.setCategoryId(input.categoryId());
        request.setDescription(
            nonblank(c.getDescription(), "Kurs açıklamasını düzenleyin.")
        );
        request.setShortDescription(
            nonblank(
                c.getShortDescription(),
                "Kursun kısa açıklamasını düzenleyin."
            )
        );
        request.setTargetAudience(c.getTargetAudience());
        request.setPrice(BigDecimal.ZERO);
        request.setLevel(
            c.getLevel() == null ? CourseLevel.ALL_LEVELS : c.getLevel()
        );
        request.setLanguage(nonblank(c.getLanguage(), "Türkçe"));
        request.setRequirements(c.getRequirements());
        request.setOutcomes(c.getOutcomes());
        var created = courses.createCourse(
            request,
            user.id(),
            user.displayName()
        );
        entityManager.flush();
        var document = remap(source, created, input.categoryId());
        workspaces.save(
            created.getId(),
            new CourseWorkspaceService.Save(0, document),
            user
        );
        return created;
    }

    private static String nonblank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    // Reuse text and question banks, never media tied to a different course or learner history.
    CourseWorkspaceService.Document remap(
        CourseWorkspaceService.Document original,
        CourseResponse created,
        Long category
    ) {
        var source = json.readValue(
            json.writeValueAsString(original),
            CourseWorkspaceService.Document.class
        );
        var ids = new HashMap<UUID, UUID>();
        var sections =
            source.course().getSections() == null
                ? new ArrayList<SectionResponse>()
                : source.course().getSections();
        for (var s : sections) {
            s.setId(UUID.randomUUID());
            for (var l : s.getLessons()) {
                var next = UUID.randomUUID();
                ids.put(l.getId(), next);
                l.setId(next);
                l.setPreviewAllowed(false);
                l.setVideoUrl(null);
                if (l.getType() == LessonType.VIDEO) l.setDurationSeconds(0);
            }
        }
        created.setSections(sections);
        var quizzes = new LinkedHashMap<UUID, AssessmentService.Quiz>();
        source.quizzes().forEach((id, q) -> {
            if (ids.containsKey(id)) quizzes.put(
                ids.get(id),
                new AssessmentService.Quiz(
                    q.passingScore(),
                    q
                        .questions()
                        .stream()
                        .map(x ->
                            new AssessmentService.Question(
                                UUID.randomUUID(),
                                x.prompt(),
                                List.copyOf(x.options()),
                                x.correctOption(),
                                x.explanation()
                            )
                        )
                        .toList(),
                    q.maxAttempts(),
                    q.timeLimitSeconds(),
                    q.questionCount(),
                    q.randomize()
                )
            );
        });
        var materials = new LinkedHashMap<
            UUID,
            List<CourseWorkspaceService.Material>
        >();
        source.materials().forEach((id, items) -> {
            if (ids.containsKey(id)) materials.put(
                ids.get(id),
                items
                    .stream()
                    .filter(x -> x.url().startsWith("https://"))
                    .toList()
            );
        });
        return new CourseWorkspaceService.Document(
            created,
            category,
            quizzes,
            materials
        );
    }

    private CourseWorkspaceService.Document builtIn(String type) {
        var c = new CourseResponse();
        c.setDescription(
            "## Kurs hakkında\nHedeflerinizi, öğrenme yolunu ve uygulamaları açıklayın."
        );
        c.setShortDescription("Bu kursun kazandıracağı becerileri açıklayın.");
        c.setRequirements(List.of("Ön koşulları düzenleyin"));
        c.setOutcomes(List.of("Öğrenme kazanımlarını düzenleyin"));
        var intro = lesson(
            "Hoş geldiniz",
            LessonType.ARTICLE,
            "## Başlamadan önce\nKursun hedeflerini ve çalışma düzenini açıklayın."
        );
        var practice = lesson(
            "Uygulama ödevi",
            LessonType.ASSIGNMENT,
            "## Görev\nBeklenen çıktıyı açıklayın.\n\n## Değerlendirme\n- Doğruluk\n- Açıklık\n- Uygulama"
        );
        var section = new SectionResponse();
        section.setId(UUID.randomUUID());
        section.setTitle("Öğrenme yolculuğu");
        section.setOrderIndex(0);
        var quizzes = new LinkedHashMap<UUID, AssessmentService.Quiz>();
        if ("assessment".equals(type)) {
            var quiz = lesson("Bilgi kontrolü", LessonType.QUIZ, "");
            quizzes.put(
                quiz.getId(),
                new AssessmentService.Quiz(
                    70,
                    List.of(
                        new AssessmentService.Question(
                            UUID.randomUUID(),
                            "Örnek soruyu kendi konunuzla değiştirin",
                            List.of("Doğru seçenek", "Diğer seçenek"),
                            0,
                            "Yanıtın açıklamasını düzenleyin"
                        )
                    )
                )
            );
            section.setLessons(new ArrayList<>(List.of(intro, quiz, practice)));
        } else if ("workshop".equals(type)) section.setLessons(
            new ArrayList<>(List.of(intro, practice))
        );
        else section.setLessons(
            new ArrayList<>(
                List.of(
                    intro,
                    lesson(
                        "İlk konu",
                        LessonType.ARTICLE,
                        "## Konu\nKavramı açıklayın ve bir örnek ekleyin."
                    ),
                    practice
                )
            )
        );
        c.setSections(new ArrayList<>(List.of(section)));
        return new CourseWorkspaceService.Document(c, 0L, quizzes, Map.of());
    }

    private LessonResponse lesson(String title, LessonType type, String text) {
        var l = new LessonResponse();
        l.setId(UUID.randomUUID());
        l.setTitle(title);
        l.setType(type);
        l.setContent(text);
        return l;
    }
}
