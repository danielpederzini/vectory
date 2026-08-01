package org.vectory.contentmanager.application.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.vectory.contentmanager.domain.enums.PostMediaType;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostMediaCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostResponseDto;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostMedia;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostMapper")
class PostMapperTest {

    private static final UUID POST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AUTHOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CREATION_INSTANT = Instant.parse("2026-01-15T10:15:30Z");
    private static final String POST_TEXT = "hello world";
    private static final String MEDIA_URL = "https://cdn.vectory.org/media/cat.png";

    private static Stream<PostMediaCreationRequestDto> provideMediaRequestsWithoutUsableUrl() {
        return Stream.of(
                null,
                new PostMediaCreationRequestDto(PostMediaType.IMAGE, null)
        );
    }

    private static Stream<PostMedia> provideEmbeddedMediaWithoutUsableUrl() {
        return Stream.of(
                null,
                PostMedia.builder().build(),
                PostMedia.builder().mediaType(PostMediaType.IMAGE).build()
        );
    }

    private static PostEntity buildEntityWithMedia(PostMedia media) {
        return PostEntity.builder()
                .id(POST_ID)
                .authorId(AUTHOR_ID)
                .text(POST_TEXT)
                .creationInstant(CREATION_INSTANT)
                .media(media)
                .build();
    }

    @Test
    @DisplayName("maps request fields plus the supplied id and creation instant onto the entity")
    void shouldMapRequestFieldsAndSuppliedIdentityValuesOntoEntity() {
        PostCreationRequestDto request = new PostCreationRequestDto(AUTHOR_ID, POST_TEXT, null);

        PostEntity entity = PostMapper.toEntity(request, POST_ID, CREATION_INSTANT);

        assertThat(entity.getId()).isEqualTo(POST_ID);
        assertThat(entity.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(entity.getText()).isEqualTo(POST_TEXT);
        assertThat(entity.getCreationInstant()).isEqualTo(CREATION_INSTANT);
    }

    @ParameterizedTest(name = "media type {0}")
    @EnumSource(PostMediaType.class)
    @DisplayName("maps requested media onto the embedded entity media")
    void shouldMapRequestedMediaOntoEntityForEverySupportedType(PostMediaType mediaType) {
        PostMediaCreationRequestDto media = new PostMediaCreationRequestDto(mediaType, MEDIA_URL);
        PostCreationRequestDto request = new PostCreationRequestDto(AUTHOR_ID, POST_TEXT, media);

        PostEntity entity = PostMapper.toEntity(request, POST_ID, CREATION_INSTANT);

        assertThat(entity.getMedia()).isNotNull();
        assertThat(entity.getMedia().getMediaType()).isEqualTo(mediaType);
        assertThat(entity.getMedia().getMediaUrl()).isEqualTo(MEDIA_URL);
    }

    @ParameterizedTest(name = "requested media {0}")
    @MethodSource("provideMediaRequestsWithoutUsableUrl")
    @DisplayName("leaves the entity media null when the request carries no usable media")
    void shouldLeaveEntityMediaNullWhenRequestHasNoUsableMedia(PostMediaCreationRequestDto media) {
        PostCreationRequestDto request = new PostCreationRequestDto(AUTHOR_ID, POST_TEXT, media);

        PostEntity entity = PostMapper.toEntity(request, POST_ID, CREATION_INSTANT);

        assertThat(entity.getMedia()).isNull();
    }

    @Test
    @DisplayName("maps entity fields onto the response")
    void shouldMapEntityFieldsOntoResponse() {
        PostResponseDto response = PostMapper.toResponseDto(buildEntityWithMedia(null));

        assertThat(response.id()).isEqualTo(POST_ID);
        assertThat(response.authorId()).isEqualTo(AUTHOR_ID);
        assertThat(response.text()).isEqualTo(POST_TEXT);
        assertThat(response.creationInstant()).isEqualTo(CREATION_INSTANT);
    }

    @ParameterizedTest(name = "media type {0}")
    @EnumSource(PostMediaType.class)
    @DisplayName("maps embedded entity media onto the response media")
    void shouldMapEmbeddedMediaOntoResponseForEverySupportedType(PostMediaType mediaType) {
        PostMedia media = PostMedia.builder().mediaType(mediaType).mediaUrl(MEDIA_URL).build();

        PostResponseDto response = PostMapper.toResponseDto(buildEntityWithMedia(media));

        assertThat(response.media()).isNotNull();
        assertThat(response.media().mediaType()).isEqualTo(mediaType);
        assertThat(response.media().mediaUrl()).isEqualTo(MEDIA_URL);
    }

    @ParameterizedTest(name = "embedded media case {index}")
    @MethodSource("provideEmbeddedMediaWithoutUsableUrl")
    @DisplayName("leaves the response media null when the entity carries no usable media")
    void shouldLeaveResponseMediaNullWhenEntityHasNoUsableMedia(PostMedia media) {
        PostResponseDto response = PostMapper.toResponseDto(buildEntityWithMedia(media));

        assertThat(response.media()).isNull();
    }
}
