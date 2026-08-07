package org.vectory.contentmanager.application.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.vectory.contentmanager.application.event.InteractionCreatedEvent;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.InteractionCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.InteractionResponseDto;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.InteractionEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostEntity;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InteractionMapper {

    public static InteractionEntity toEntity(
            InteractionCreationRequestDto request,
            UUID id,
            PostEntity post,
            Instant creationInstant
    ) {
        return InteractionEntity.builder()
                .id(id)
                .post(post)
                .userId(request.userId())
                .type(request.type())
                .creationInstant(creationInstant)
                .build();
    }

    public static InteractionResponseDto toResponseDto(InteractionEntity entity) {
        return InteractionResponseDto.builder()
                .id(entity.getId())
                .postId(entity.getPost().getId())
                .userId(entity.getUserId())
                .type(entity.getType())
                .creationInstant(entity.getCreationInstant())
                .build();
    }

    public static InteractionCreatedEvent toCreatedEvent(InteractionEntity entity) {
        return InteractionCreatedEvent.builder()
                .interactionId(entity.getId())
                .postId(entity.getPost().getId())
                .userId(entity.getUserId())
                .type(entity.getType())
                .creationInstant(entity.getCreationInstant())
                .build();
    }
}
