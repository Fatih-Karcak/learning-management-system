package com.lms.courseservice.mapper;

import com.lms.courseservice.domain.entity.Review;
import com.lms.courseservice.dto.response.ReviewResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReviewMapper {
    ReviewResponse toResponse(Review review);
}