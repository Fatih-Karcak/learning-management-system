package com.lms.contentservice.mapper;

import com.lms.contentservice.domain.entity.MediaAsset;
import com.lms.contentservice.dto.response.MediaAssetResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MediaAssetMapper {

    // streamUrl ve thumbnailUrl imzalı (presigned) URL gerektirir,
    // bu yüzden mapper'da değil MediaService'te elle dolduruluyor.
    @Mapping(target = "streamUrl", ignore = true)
    @Mapping(target = "thumbnailUrl", ignore = true)
    MediaAssetResponse toResponse(MediaAsset asset);
}