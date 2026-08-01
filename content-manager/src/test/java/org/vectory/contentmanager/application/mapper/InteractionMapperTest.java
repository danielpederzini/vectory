package org.vectory.contentmanager.application.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.vectory.contentmanager.domain.enums.InteractionType;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.InteractionCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.InteractionResponseDto;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.InteractionEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InteractionMapper")
class InteractionMapperTest {

    private static final UUID INTERACTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID POST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_POST_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant CREATION_INSTANT = Instant.parse("2026-01-15T10:15:30Z");
    private static final InteractionType DEFAULT_TYPE = InteractionType.LIKE;

    private static InteractionCreationRequestDto buildRequest(InteractionType type) {
        return new InteractionCreationRequestDto(POST_ID, USER_ID, type);
    }

    private static PostEntity buildPost(UUID postId) {
        return PostEntity.builder().id(postId).build();
    }

    private static InteractionEntity buildEntityWithType(InteractionType type) {
        return InteractionEntity.builder()
                .id(INTERACTION_ID)
                .post(buildPost(POST_ID))
                .userId(USER_ID)
                .type(type)
                .creationInstant(CREATION_INSTANT)
                .build();
    }

    @Test
    @DisplayName("maps request fields plus the supplied id, post and creation instant onto the entity")
    void shouldMapRequestFieldsAndSuppliedIdentityValuesOntoEntity() {
        PostEntity post = buildPost(POST_ID);

        InteractionEntity entity = InteractionMapper.toEntity(
                buildRequest(DEFAULT_TYPE), INTERACTION_ID, post, CREATION_INSTANT);

        assertThat(entity.getId()).isEqualTo(INTERACTION_ID);
        assertThat(entity.getPost()).isSameAs(post);
        assertThat(entity.getUserId()).isEqualTo(USER_ID);
        assertThat(entity.getType()).isEqualTo(DEFAULT_TYPE);
        assertThat(entity.getCreationInstant()).isEqualTo(CREATION_INSTANT);
    }

    @Test
    @DisplayName("prefers the supplied post reference over the post id carried by the request")
    void shouldPreferSuppliedPostOverPostIdCarriedByTheRequest() {
        InteractionEntity entity = InteractionMapper.toEntity(
                buildRequest(DEFAULT_TYPE), INTERACTION_ID, buildPost(OTHER_POST_ID), CREATION_INSTANT);

        assertThat(entity.getPost().getId()).isEqualTo(OTHER_POST_ID);
    }

    @ParameterizedTest(name = "type {0}")
    @EnumSource(InteractionType.class)
    @DisplayName("maps every interaction type onto the entity")
    void shouldMapEveryInteractionTypeOntoEntity(InteractionType type) {
        InteractionEntity entity = InteractionMapper.toEntity(
                buildRequest(type), INTERACTION_ID, buildPost(POST_ID), CREATION_INSTANT);

        assertThat(entity.getType()).isEqualTo(type);
    }

    @Test
    @DisplayName("flattens the associated post into a post id on the response")
    void shouldFlattenAssociatedPostIntoPostIdOnResponse() {
        InteractionResponseDto response = InteractionMapper.toResponseDto(buildEntityWithType(DEFAULT_TYPE));

        assertThat(response.id()).isEqualTo(INTERACTION_ID);
        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.type()).isEqualTo(DEFAULT_TYPE);
        assertThat(response.creationInstant()).isEqualTo(CREATION_INSTANT);
    }

    @ParameterizedTest(name = "type {0}")
    @EnumSource(InteractionType.class)
    @DisplayName("preserves every interaction type on the response")
    void shouldPreserveEveryInteractionTypeOnResponse(InteractionType type) {
        assertThat(InteractionMapper.toResponseDto(buildEntityWithType(type)).type()).isEqualTo(type);
    }
}
