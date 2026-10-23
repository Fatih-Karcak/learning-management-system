package com.lms.courseservice.dto.request;

import com.lms.courseservice.domain.enums.CourseLevel;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class CreateCourseRequest {

    @NotBlank(message = "Kurs başlığı boş olamaz")
    @Size(min = 10, max = 200)
    private String title;

    @jakarta.validation.constraints.Size(max = 2000)
    private String targetAudience;

    @NotBlank(message = "Açıklama boş olamaz")
    private String description;

    @NotBlank(message = "Kısa açıklama boş olamaz")
    @Size(max = 500)
    private String shortDescription;

    @NotNull(message = "Fiyat boş olamaz")
    @DecimalMin(value = "0.0")
    private BigDecimal price;

    @DecimalMin(value = "0.0")
    private BigDecimal discountPrice;

    @NotNull
    private Long categoryId;

    private CourseLevel level = CourseLevel.ALL_LEVELS;
    private String language = "Türkçe";
    private String thumbnailUrl;
    private String previewVideoUrl;
    private List<String> requirements;
    private List<String> outcomes;
}
