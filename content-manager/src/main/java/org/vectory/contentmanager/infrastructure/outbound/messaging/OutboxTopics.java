package org.vectory.contentmanager.infrastructure.outbound.messaging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OutboxTopics {

    public static final String POSTS_CREATED = "posts.created";
    public static final String INTERACTIONS_CREATED = "interactions.created";
}
