package com.lms.courseservice.dto.response;

import com.lms.courseservice.domain.enums.CourseLevel;
import com.lms.courseservice.domain.enums.CourseStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CourseSummaryResponse {
    private UUID id;
    private String title;
    private String shortDescription;
    private String reviewFeedback;
    private String thumbnailUrl;
    private String instructorName;
    private String categoryName;
    private CourseStatus status;
    private CourseLevel level;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private Double averageRating;
    private Integer totalEnrollments;
    private Integer totalReviews;
    private String language;
}
