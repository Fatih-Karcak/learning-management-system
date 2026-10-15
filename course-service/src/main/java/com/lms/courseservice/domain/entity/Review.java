package com.lms.courseservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"course_id", "student_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private UUID studentId;

    private String studentName;
    private String studentAvatarUrl;

    @Column(nullable = false)
    private Integer rating; // 1-5

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}