package com.lms.notificationservice.mapper;
import com.lms.notificationservice.domain.entity.NotificationLog;
import com.lms.notificationservice.dto.response.NotificationLogResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)

public interface NotificationLogMapper {
 NotificationLogResponse toResponse(NotificationLog log);

}
