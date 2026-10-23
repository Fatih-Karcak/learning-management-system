package com.lms.courseservice.mapper;

import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.domain.entity.Lesson;
import com.lms.courseservice.domain.entity.Section;
import com.lms.courseservice.dto.response.CourseResponse;
import com.lms.courseservice.dto.response.CourseSummaryResponse;
import com.lms.courseservice.dto.response.LessonResponse;
import com.lms.courseservice.dto.response.SectionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CourseMapper {

    @Mapping(source = "category.name", target = "categoryName")
    CourseResponse toResponse(Course course);

    @Mapping(source = "category.name", target = "categoryName")
    CourseSummaryResponse toSummaryResponse(Course course);

    SectionResponse toSectionResponse(Section section);

    LessonResponse toLessonResponse(Lesson lesson);
}