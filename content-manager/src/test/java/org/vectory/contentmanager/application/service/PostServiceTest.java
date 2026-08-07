package org.vectory.contentmanager.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vectory.contentmanager.application.event.PostCreatedEvent;
import org.vectory.contentmanager.application.mapper.PostMapper;
import org.vectory.contentmanager.domain.enums.AggregateType;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostResponseDto;
import org.vectory.contentmanager.infrastructure.outbound.messaging.OutboxEventWriter;
import org.vectory.contentmanager.infrastructure.outbound.messaging.OutboxTopics;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.repository.PostRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostService")
class PostServiceTest {

    private static final UUID PERSISTED_POST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AUTHOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final PostCreationRequestDto REQUEST = new PostCreationRequestDto(AUTHOR_ID, "hello world", null);
    private static final PostEntity MAPPED_ENTITY = PostEntity.builder().build();
    private static final PostEntity SAVED_ENTITY = PostEntity.builder().id(PERSISTED_POST_ID).build();
    private static final PostCreatedEvent CREATED_EVENT = PostCreatedEvent.builder().postId(PERSISTED_POST_ID).build();
    private static final PostResponseDto RESPONSE = PostResponseDto.builder().id(PERSISTED_POST_ID).build();

    @Mock
    private PostRepository postRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @InjectMocks
    private PostService postService;

    private MockedStatic<PostMapper> postMapper;

    @Captor
    private ArgumentCaptor<UUID> idCaptor;

    @Captor
    private ArgumentCaptor<Instant> instantCaptor;

    @BeforeEach
    void openMapperMock() {
        postMapper = mockStatic(PostMapper.class);
        postMapper.when(() -> PostMapper.toEntity(eq(REQUEST), any(UUID.class), any(Instant.class)))
                .thenReturn(MAPPED_ENTITY);
        postMapper.when(() -> PostMapper.toCreatedEvent(SAVED_ENTITY)).thenReturn(CREATED_EVENT);
        postMapper.when(() -> PostMapper.toResponseDto(SAVED_ENTITY)).thenReturn(RESPONSE);
        when(postRepository.save(MAPPED_ENTITY)).thenReturn(SAVED_ENTITY);
    }

    @AfterEach
    void closeMapperMock() {
        postMapper.close();
    }

    @Test
    @DisplayName("maps the request to an entity with a generated id and instant, then persists it")
    void shouldMapRequestToEntityAndPersistIt() {
        Instant before = Instant.now();
        postService.create(REQUEST);
        Instant after = Instant.now();

        postMapper.verify(() -> PostMapper.toEntity(eq(REQUEST), idCaptor.capture(), instantCaptor.capture()));
        assertThat(idCaptor.getValue()).isNotNull();
        assertThat(instantCaptor.getValue()).isBetween(before, after);
        verify(postRepository).save(MAPPED_ENTITY);
    }

    @Test
    @DisplayName("returns the response produced by the mapper from the persisted entity")
    void shouldReturnResponseFromMapper() {
        PostResponseDto response = postService.create(REQUEST);

        assertThat(response).isSameAs(RESPONSE);
        postMapper.verify(() -> PostMapper.toResponseDto(SAVED_ENTITY));
    }

    @Test
    @DisplayName("generates a distinct id for each created post")
    void shouldGenerateDistinctIdForEachCreatedPost() {
        postService.create(REQUEST);
        postService.create(REQUEST);

        postMapper.verify(() -> PostMapper.toEntity(eq(REQUEST), idCaptor.capture(), any(Instant.class)), times(2));
        assertThat(idCaptor.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("writes a posts.created outbox event mapped from the persisted entity and keyed by its id")
    void shouldWritePostCreatedOutboxEvent() {
        postService.create(REQUEST);

        postMapper.verify(() -> PostMapper.toCreatedEvent(SAVED_ENTITY));
        verify(outboxEventWriter).append(
                eq(AggregateType.POST),
                eq(PERSISTED_POST_ID),
                eq(OutboxTopics.POSTS_CREATED),
                eq(PERSISTED_POST_ID.toString()),
                eq(CREATED_EVENT)
        );
    }
}
