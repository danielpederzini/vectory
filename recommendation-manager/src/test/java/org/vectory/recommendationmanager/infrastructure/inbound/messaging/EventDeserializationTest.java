package org.vectory.recommendationmanager.infrastructure.inbound.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.vectory.recommendationmanager.domain.enums.InteractionType;
import org.vectory.recommendationmanager.domain.enums.PostMediaType;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.InteractionCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.PostCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.UserCreatedEvent;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Event JSON deserialization")
class EventDeserializationTest {

    private static final String POST_ID = "11111111-1111-1111-1111-111111111111";
    private static final String AUTHOR_ID = "55555555-5555-5555-5555-555555555555";
    private static final String USER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String INTERACTION_ID = "33333333-3333-3333-3333-333333333333";
    private static final String OBJECT_KEY = "posts/2b0f0c8e-cat.png";
    private static final Instant CREATION_INSTANT = Instant.parse("2026-01-15T10:15:30Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("parses a posts.created payload including media")
    void shouldParsePostCreated() {
        String json = """
                {
                  "postId": "%s",
                  "authorId": "%s",
                  "text": "hello",
                  "media": { "mediaType": "IMAGE", "objectKey": "%s" },
                  "creationInstant": "%s"
                }
                """.formatted(POST_ID, AUTHOR_ID, OBJECT_KEY, CREATION_INSTANT);

        PostCreatedEvent event = objectMapper.readValue(json, PostCreatedEvent.class);

        assertThat(event.postId()).isEqualTo(UUID.fromString(POST_ID));
        assertThat(event.authorId()).isEqualTo(UUID.fromString(AUTHOR_ID));
        assertThat(event.text()).isEqualTo("hello");
        assertThat(event.media().mediaType()).isEqualTo(PostMediaType.IMAGE);
        assertThat(event.media().objectKey()).isEqualTo(OBJECT_KEY);
        assertThat(event.creationInstant()).isEqualTo(CREATION_INSTANT);
    }

    @Test
    @DisplayName("parses a posts.created payload with null media")
    void shouldParsePostCreatedWithoutMedia() {
        String json = """
                {
                  "postId": "%s",
                  "authorId": "%s",
                  "text": "text only",
                  "media": null,
                  "creationInstant": "%s"
                }
                """.formatted(POST_ID, AUTHOR_ID, CREATION_INSTANT);

        PostCreatedEvent event = objectMapper.readValue(json, PostCreatedEvent.class);

        assertThat(event.media()).isNull();
        assertThat(event.text()).isEqualTo("text only");
    }

    @Test
    @DisplayName("parses a users.created payload")
    void shouldParseUserCreated() {
        String json = """
                {
                  "userId": "%s",
                  "username": "alice",
                  "email": "alice@example.com",
                  "creationInstant": "%s"
                }
                """.formatted(USER_ID, CREATION_INSTANT);

        UserCreatedEvent event = objectMapper.readValue(json, UserCreatedEvent.class);

        assertThat(event.userId()).isEqualTo(UUID.fromString(USER_ID));
        assertThat(event.username()).isEqualTo("alice");
        assertThat(event.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("parses an interactions.created payload with its type")
    void shouldParseInteractionCreated() {
        String json = """
                {
                  "interactionId": "%s",
                  "postId": "%s",
                  "userId": "%s",
                  "type": "LIKE",
                  "creationInstant": "%s"
                }
                """.formatted(INTERACTION_ID, POST_ID, USER_ID, CREATION_INSTANT);

        InteractionCreatedEvent event = objectMapper.readValue(json, InteractionCreatedEvent.class);

        assertThat(event.interactionId()).isEqualTo(UUID.fromString(INTERACTION_ID));
        assertThat(event.type()).isEqualTo(InteractionType.LIKE);
        assertThat(event.creationInstant()).isEqualTo(CREATION_INSTANT);
    }
}
