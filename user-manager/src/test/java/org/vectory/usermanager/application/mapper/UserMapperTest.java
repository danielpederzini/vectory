package org.vectory.usermanager.application.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.vectory.usermanager.application.event.UserCreatedEvent;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.SignupRequestDto;
import org.vectory.usermanager.infrastructure.inbound.rest.dto.UserResponseDto;
import org.vectory.usermanager.infrastructure.outbound.persistence.entity.UserEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserMapper")
class UserMapperTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String USERNAME = "alice";
    private static final String EMAIL = "alice@example.com";
    private static final String RAW_PASSWORD = "s3cretpass";
    private static final String PASSWORD_HASH = "$2a$10$hashedvalue";
    private static final Instant CREATION_INSTANT = Instant.parse("2026-01-15T10:15:30Z");

    private static SignupRequestDto buildRequest() {
        return new SignupRequestDto(USERNAME, EMAIL, RAW_PASSWORD);
    }

    private static UserEntity buildEntity() {
        return UserEntity.builder()
                .id(USER_ID)
                .username(USERNAME)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .creationInstant(CREATION_INSTANT)
                .build();
    }

    @Test
    @DisplayName("maps request fields plus the supplied id, password hash and creation instant onto the entity")
    void shouldMapRequestFieldsAndSuppliedValuesOntoEntity() {
        UserEntity entity = UserMapper.toEntity(buildRequest(), USER_ID, PASSWORD_HASH, CREATION_INSTANT);

        assertThat(entity.getId()).isEqualTo(USER_ID);
        assertThat(entity.getUsername()).isEqualTo(USERNAME);
        assertThat(entity.getEmail()).isEqualTo(EMAIL);
        assertThat(entity.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(entity.getCreationInstant()).isEqualTo(CREATION_INSTANT);
    }

    @Test
    @DisplayName("never carries the raw password onto the entity")
    void shouldNotCarryRawPasswordOntoEntity() {
        UserEntity entity = UserMapper.toEntity(buildRequest(), USER_ID, PASSWORD_HASH, CREATION_INSTANT);

        assertThat(entity.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
    }

    @Test
    @DisplayName("exposes id, username, email and creation instant on the response without the password hash")
    void shouldMapEntityOntoResponse() {
        UserResponseDto response = UserMapper.toResponseDto(buildEntity());

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.username()).isEqualTo(USERNAME);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.creationInstant()).isEqualTo(CREATION_INSTANT);
    }

    @Test
    @DisplayName("maps the persisted entity onto a created event without the password hash")
    void shouldMapEntityOntoCreatedEvent() {
        UserCreatedEvent event = UserMapper.toCreatedEvent(buildEntity());

        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.username()).isEqualTo(USERNAME);
        assertThat(event.email()).isEqualTo(EMAIL);
        assertThat(event.creationInstant()).isEqualTo(CREATION_INSTANT);
    }
}
