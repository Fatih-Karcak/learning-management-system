package com.lms.enrollmentservice.mapper;

import com.lms.enrollmentservice.domain.entity.Enrollment;
import com.lms.enrollmentservice.dto.response.EnrollmentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EnrollmentMapper {
    EnrollmentResponse toResponse(Enrollment enrollment);
}