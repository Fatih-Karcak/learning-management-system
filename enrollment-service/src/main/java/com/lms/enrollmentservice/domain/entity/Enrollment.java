package com.lms.enrollmentservice.domain.entity;

import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "enrollments",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"student_id", "course_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private UUID courseId;

    private String courseTitle;
    private String courseThumbnailUrl;
    private String instructorName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status;

    // İlerleme bilgileri
    private Integer totalLessons = 0;
    private Integer completedLessons = 0;
    private Double progressPercentage = 0.0;

    private LocalDateTime lastAccessedAt;
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "enrollment",
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LessonProgress> lessonProgressList = new ArrayList<>();

    @OneToOne(mappedBy = "enrollment",
            cascade = CascadeType.ALL, orphanRemoval = true)
    private Certificate certificate;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime enrolledAt;
}