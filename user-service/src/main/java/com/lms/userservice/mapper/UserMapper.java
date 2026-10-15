package com.lms.userservice.mapper;

import com.lms.userservice.domain.entity.User;
import com.lms.userservice.dto.response.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    UserResponse toResponse(User user);
}