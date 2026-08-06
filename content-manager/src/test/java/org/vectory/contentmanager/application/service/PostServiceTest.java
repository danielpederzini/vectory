package org.vectory.contentmanager.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vectory.contentmanager.application.event.PostCreatedEvent;
import org.vectory.contentmanager.domain.enums.AggregateType;
import org.vectory.contentmanager.domain.enums.PostMediaType;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostMediaCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostResponseDto;
import org.vectory.contentmanager.infrastructure.outbound.messaging.OutboxEventWriter;
import org.vectory.contentmanager.infrastructure.outbound.messaging.OutboxTopics;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostMedia;
import org.vectory.contentmanager.infrastructure.outbound.persistence.repository.PostRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostService")
class PostServiceTest {

    private static final UUID PERSISTED_POST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AUTHOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant PERSISTED_INSTANT = Instant.parse("2026-01-15T10:15:30Z");
    private static final String SUBMITTED_TEXT = "hello world";
    private static final String PERSISTED_TEXT = "text as stored";
    private static final String MEDIA_URL = "https://cdn.vectory.org/media/cat.png";
    private static final PostMediaType MEDIA_TYPE = PostMediaType.IMAGE;

    @Mock
    private PostRepository postRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @InjectMocks
    private PostService postService;

    @Captor
    private ArgumentCaptor<PostEntity> postEntityCaptor;

    @Captor
    private ArgumentCaptor<PostCreatedEvent> postCreatedEventCaptor;

    private static PostCreationRequestDto buildRequest(PostMediaCreationRequestDto media) {
        return new PostCreationRequestDto(AUTHOR_ID, SUBMITTED_TEXT, media);
    }

    private static Stream<PostMediaCreationRequestDto> provideRequestsWithoutUsableMedia() {
        return Stream.of(
                null,
                new PostMediaCreationRequestDto(MEDIA_TYPE, null)
        );
    }

    private void stubSaveToReturnItsArgument() {
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PostEntity capturePersistedEntity() {
        verify(postRepository).save(postEntityCaptor.capture());
        return postEntityCaptor.getValue();
    }

    @Test
    @DisplayName("persists a post carrying the submitted author, text and media")
    void shouldPersistPostBuiltFromRequest() {
        stubSaveToReturnItsArgument();

        postService.create(buildRequest(new PostMediaCreationRequestDto(MEDIA_TYPE, MEDIA_URL)));

        PostEntity persisted = capturePersistedEntity();
        assertThat(persisted.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(persisted.getText()).isEqualTo(SUBMITTED_TEXT);
        assertThat(persisted.getMedia().getMediaType()).isEqualTo(MEDIA_TYPE);
        assertThat(persisted.getMedia().getMediaUrl()).isEqualTo(MEDIA_URL);
    }

    @ParameterizedTest(name = "media type {0}")
    @EnumSource(PostMediaType.class)
    @DisplayName("persists a post for every supported media type")
    void shouldPersistPostForEverySupportedMediaType(PostMediaType mediaType) {
        stubSaveToReturnItsArgument();

        postService.create(buildRequest(new PostMediaCreationRequestDto(mediaType, MEDIA_URL)));

        assertThat(capturePersistedEntity().getMedia().getMediaType()).isEqualTo(mediaType);
    }

    @Test
    @DisplayName("generates an id and a creation instant for the persisted post")
    void shouldGenerateIdAndCreationInstantForPersistedPost() {
        stubSaveToReturnItsArgument();

        Instant before = Instant.now();
        postService.create(buildRequest(null));
        Instant after = Instant.now();

        PostEntity persisted = capturePersistedEntity();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getCreationInstant()).isBetween(before, after);
    }

    @Test
    @DisplayName("generates a distinct id for each created post")
    void shouldGenerateDistinctIdForEachCreatedPost() {
        stubSaveToReturnItsArgument();

        PostResponseDto first = postService.create(buildRequest(null));
        PostResponseDto second = postService.create(buildRequest(null));

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    @DisplayName("returns the response built from the persisted post rather than from the request")
    void shouldReturnResponseBuiltFromPersistedPost() {
        PostEntity persisted = PostEntity.builder()
                .id(PERSISTED_POST_ID)
                .authorId(AUTHOR_ID)
                .text(PERSISTED_TEXT)
                .creationInstant(PERSISTED_INSTANT)
                .media(PostMedia.builder().mediaType(MEDIA_TYPE).mediaUrl(MEDIA_URL).build())
                .build();
        when(postRepository.save(any(PostEntity.class))).thenReturn(persisted);

        PostResponseDto response = postService.create(buildRequest(null));

        assertThat(response.id()).isEqualTo(PERSISTED_POST_ID);
        assertThat(response.authorId()).isEqualTo(AUTHOR_ID);
        assertThat(response.text()).isEqualTo(PERSISTED_TEXT);
        assertThat(response.creationInstant()).isEqualTo(PERSISTED_INSTANT);
        assertThat(response.media().mediaUrl()).isEqualTo(MEDIA_URL);
    }

    @ParameterizedTest(name = "requested media {0}")
    @MethodSource("provideRequestsWithoutUsableMedia")
    @DisplayName("persists and returns a post without media when none is usable")
    void shouldPersistPostWithoutMediaWhenRequestHasNoUsableMedia(PostMediaCreationRequestDto media) {
        stubSaveToReturnItsArgument();

        PostResponseDto response = postService.create(buildRequest(media));

        assertThat(capturePersistedEntity().getMedia()).isNull();
        assertThat(response.media()).isNull();
    }

    @Test
    @DisplayName("writes a posts.created outbox event keyed by the persisted post id")
    void shouldWritePostCreatedOutboxEvent() {
        stubSaveToReturnItsArgument();

        PostResponseDto response = postService.create(buildRequest(new PostMediaCreationRequestDto(MEDIA_TYPE, MEDIA_URL)));

        verify(outboxEventWriter).append(
                eq(AggregateType.POST),
                eq(response.id()),
                eq(OutboxTopics.POSTS_CREATED),
                eq(response.id().toString()),
                postCreatedEventCaptor.capture()
        );
        PostCreatedEvent event = postCreatedEventCaptor.getValue();
        assertThat(event.postId()).isEqualTo(response.id());
        assertThat(event.authorId()).isEqualTo(AUTHOR_ID);
        assertThat(event.text()).isEqualTo(SUBMITTED_TEXT);
        assertThat(event.media().mediaUrl()).isEqualTo(MEDIA_URL);
        assertThat(event.creationInstant()).isEqualTo(response.creationInstant());
    }
}
