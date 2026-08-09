package org.vectory.usermanager.application.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.vectory.usermanager.application.event.UserCreatedEvent;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.SignupRequestDto;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.UserResponseDto;
import org.vectory.usermanager.infrastructure.outbound.persistence.entity.UserEntity;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserMapper {

    public static UserEntity toEntity(
            SignupRequestDto request,
            UUID id,
            String passwordHash,
            Instant creationInstant
    ) {
        return UserEntity.builder()
                .id(id)
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordHash)
                .creationInstant(creationInstant)
                .build();
    }

    public static UserResponseDto toResponseDto(UserEntity entity) {
        return UserResponseDto.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .email(entity.getEmail())
                .creationInstant(entity.getCreationInstant())
                .build();
    }

    public static UserCreatedEvent toCreatedEvent(UserEntity entity) {
        return UserCreatedEvent.builder()
                .userId(entity.getId())
                .username(entity.getUsername())
                .email(entity.getEmail())
                .creationInstant(entity.getCreationInstant())
                .build();
    }
}
