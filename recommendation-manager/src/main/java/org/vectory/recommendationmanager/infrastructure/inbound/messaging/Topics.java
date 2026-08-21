package org.vectory.recommendationmanager.infrastructure.inbound.messaging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Topics {

    public static final String POSTS_CREATED = "posts.created";
    public static final String USERS_CREATED = "users.created";
    public static final String INTERACTIONS_CREATED = "interactions.created";
}
