package org.vectory.recommendationmanager.application.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vectory.recommendationmanager.application.port.EmbeddingFactory;
import org.vectory.recommendationmanager.application.port.EmbeddingRequest;
import org.vectory.recommendationmanager.application.port.FetchedMedia;
import org.vectory.recommendationmanager.application.port.MediaFetchPort;
import org.vectory.recommendationmanager.domain.enums.PostMediaType;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.PostCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.PostMedia;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.PostEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostEmbeddingRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumePostCreatedUseCase")
class ConsumePostCreatedEventUseCaseTest {

    private static final UUID POST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AUTHOR_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant CREATION_INSTANT = Instant.parse("2026-01-15T10:15:30Z");
    private static final String OBJECT_KEY = "posts/2b0f0c8e-cat.png";
    private static final String IMAGE_CONTENT_TYPE = "image/png";
    private static final byte[] IMAGE_BYTES = {1, 2, 3, 4};
    private static final float[] EMBEDDING = {0.1f, 0.2f, 0.3f};

    @Mock
    private PostEmbeddingRepository postEmbeddingRepository;

    @Mock
    private EmbeddingFactory embeddingFactory;

    @Mock
    private MediaFetchPort mediaFetchPort;

    @InjectMocks
    private ConsumePostCreatedEventUseCase useCase;

    @Test
    @DisplayName("embeds a text-only post from its text and stores the vector keyed by post id")
    void shouldEmbedTextOnlyPostAndStore() {
        when(embeddingFactory.embed(any(EmbeddingRequest.class))).thenReturn(EMBEDDING);
        PostCreatedEvent event = new PostCreatedEvent(POST_ID, AUTHOR_ID, "great post", null, CREATION_INSTANT);

        useCase.execute(event);

        verifyNoInteractions(mediaFetchPort);
        ArgumentCaptor<EmbeddingRequest> requestCaptor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingFactory).embed(requestCaptor.capture());
        assertThat(requestCaptor.getValue().text()).isEqualTo("great post");
        assertThat(requestCaptor.getValue().hasImage()).isFalse();

        assertStoredEmbedding();
    }

    @Test
    @DisplayName("embeds an image post from its text and fetched image bytes")
    void shouldEmbedImagePostWithFetchedBytes() {
        when(mediaFetchPort.fetch(OBJECT_KEY)).thenReturn(new FetchedMedia(IMAGE_BYTES, IMAGE_CONTENT_TYPE));
        when(embeddingFactory.embed(any(EmbeddingRequest.class))).thenReturn(EMBEDDING);
        PostMedia media = new PostMedia(PostMediaType.IMAGE, OBJECT_KEY);
        PostCreatedEvent event = new PostCreatedEvent(POST_ID, AUTHOR_ID, "a cat", media, CREATION_INSTANT);

        useCase.execute(event);

        verify(mediaFetchPort).fetch(OBJECT_KEY);
        ArgumentCaptor<EmbeddingRequest> requestCaptor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingFactory).embed(requestCaptor.capture());
        EmbeddingRequest request = requestCaptor.getValue();
        assertThat(request.text()).isEqualTo("a cat");
        assertThat(request.image()).isEqualTo(IMAGE_BYTES);
        assertThat(request.imageContentType()).isEqualTo(IMAGE_CONTENT_TYPE);

        assertStoredEmbedding();
    }

    private void assertStoredEmbedding() {
        ArgumentCaptor<PostEmbeddingEntity> captor = ArgumentCaptor.forClass(PostEmbeddingEntity.class);
        verify(postEmbeddingRepository).save(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo(POST_ID);
        assertThat(captor.getValue().getEmbedding()).isEqualTo(EMBEDDING);
        assertThat(captor.getValue().getCreationInstant()).isEqualTo(CREATION_INSTANT);
    }
}
