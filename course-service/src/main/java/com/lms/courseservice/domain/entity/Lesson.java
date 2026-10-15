package com.lms.courseservice.domain.entity;

import com.lms.courseservice.domain.enums.LessonType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "lessons")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String videoUrl;
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LessonType type;

    @Column(nullable = false)
    private Integer orderIndex;

    private boolean previewAllowed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;
}