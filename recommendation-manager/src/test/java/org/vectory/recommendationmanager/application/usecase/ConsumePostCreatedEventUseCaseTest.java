package org.vectory.recommendationmanager.application.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vectory.recommendationmanager.application.port.EmbeddingFactory;
import org.vectory.recommendationmanager.domain.enums.PostMediaType;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.PostCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.PostMedia;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.PostEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostEmbeddingRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumePostCreatedUseCase")
class ConsumePostCreatedEventUseCaseTest {

    private static final UUID POST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AUTHOR_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant CREATION_INSTANT = Instant.parse("2026-01-15T10:15:30Z");
    private static final String MEDIA_URL = "http://x/y.png";
    private static final float[] EMBEDDING = {0.1f, 0.2f, 0.3f};

    @Mock
    private PostEmbeddingRepository postEmbeddingRepository;

    @Mock
    private EmbeddingFactory embeddingFactory;

    @InjectMocks
    private ConsumePostCreatedEventUseCase useCase;

    static Stream<Arguments> postVariants() {
        return Stream.of(
                Arguments.of("text post", null, "great post", "great post"),
                Arguments.of("media-only post", new PostMedia(PostMediaType.IMAGE, MEDIA_URL), null, "image")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("postVariants")
    @DisplayName("embeds the post description and stores the vector keyed by post id")
    void shouldEmbedAndStore(String description, PostMedia media, String text, String expectedEmbeddingText) {
        when(embeddingFactory.embed(expectedEmbeddingText)).thenReturn(EMBEDDING);
        PostCreatedEvent event = new PostCreatedEvent(POST_ID, AUTHOR_ID, text, media, CREATION_INSTANT);

        useCase.execute(event);

        verify(embeddingFactory).embed(expectedEmbeddingText);
        ArgumentCaptor<PostEmbeddingEntity> captor = ArgumentCaptor.forClass(PostEmbeddingEntity.class);
        verify(postEmbeddingRepository).save(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo(POST_ID);
        assertThat(captor.getValue().getEmbedding()).isEqualTo(EMBEDDING);
        assertThat(captor.getValue().getCreationInstant()).isEqualTo(CREATION_INSTANT);
    }
}
