package com.lms.enrollmentservice.domain.entity;

import com.lms.enrollmentservice.domain.enums.CompletionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lesson_progress",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"enrollment_id", "lesson_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(nullable = false)
    private UUID lessonId;

    @Column(nullable = false)
    private UUID sectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompletionStatus status;

    // Video izleme süresi (saniye)
    private Integer watchedSeconds = 0;
    private Integer totalSeconds = 0;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastWatchedAt;
}