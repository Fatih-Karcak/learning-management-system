package com.lms.courseservice.dto.request;

import com.lms.courseservice.domain.enums.CourseLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class UpdateCourseRequest {

    @Size(min = 10, max = 200)
    private String title;

    @jakarta.validation.constraints.Size(max = 2000)
    private String targetAudience;

    private String description;

    @Size(max = 500)
    private String shortDescription;

    @DecimalMin(value = "0.0")
    private BigDecimal price;

    @DecimalMin(value = "0.0")
    private BigDecimal discountPrice;

    private String thumbnailUrl;
    private CourseLevel level;
    private List<String> requirements;
    private List<String> outcomes;
}
