package com.lms.courseservice.dto.response;

import com.lms.courseservice.domain.enums.CourseLevel;
import com.lms.courseservice.domain.enums.CourseStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CourseResponse {
    private UUID id;
    private String title;
    private String description;
    private String shortDescription;
    private String thumbnailUrl;
    private String previewVideoUrl;
    private String reviewFeedback;
    private UUID instructorId;
    private String instructorName;
    private String categoryName;
    private CourseStatus status;
    private CourseLevel level;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private String language;
    private Integer durationMinutes;
    private List<String> requirements;
    private List<String> outcomes;
    private List<SectionResponse> sections;
    private Integer totalEnrollments;
    private Double averageRating;
    private Integer totalReviews;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
