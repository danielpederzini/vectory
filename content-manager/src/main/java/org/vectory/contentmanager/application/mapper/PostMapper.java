package org.vectory.contentmanager.application.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.vectory.contentmanager.application.event.PostCreatedEvent;
import org.vectory.contentmanager.application.event.PostMediaEvent;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostMediaCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostMediaResponseDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostResponseDto;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostMedia;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PostMapper {

    public static PostEntity toEntity(PostCreationRequestDto request, UUID id, Instant creationInstant) {
        return PostEntity.builder()
                .id(id)
                .authorId(request.authorId())
                .text(request.text())
                .creationInstant(creationInstant)
                .media(toPostMedia(request.media()))
                .build();
    }

    public static PostResponseDto toResponseDto(PostEntity entity) {
        return PostResponseDto.builder()
                .id(entity.getId())
                .authorId(entity.getAuthorId())
                .text(entity.getText())
                .creationInstant(entity.getCreationInstant())
                .media(toMediaResponseDto(entity.getMedia()))
                .build();
    }

    public static PostCreatedEvent toCreatedEvent(PostEntity entity) {
        return PostCreatedEvent.builder()
                .postId(entity.getId())
                .authorId(entity.getAuthorId())
                .text(entity.getText())
                .media(toMediaEvent(entity.getMedia()))
                .creationInstant(entity.getCreationInstant())
                .build();
    }

    private static PostMedia toPostMedia(PostMediaCreationRequestDto request) {
        if (request == null || request.objectKey() == null) {
            return null;
        }

        return PostMedia.builder()
                .objectKey(request.objectKey())
                .mediaType(request.mediaType())
                .build();
    }

    private static PostMediaResponseDto toMediaResponseDto(PostMedia media) {
        if (media == null || media.getObjectKey() == null) {
            return null;
        }

        return PostMediaResponseDto.builder()
                .objectKey(media.getObjectKey())
                .mediaType(media.getMediaType())
                .build();
    }

    private static PostMediaEvent toMediaEvent(PostMedia media) {
        if (media == null || media.getObjectKey() == null) {
            return null;
        }

        return PostMediaEvent.builder()
                .objectKey(media.getObjectKey())
                .mediaType(media.getMediaType())
                .build();
    }
}
