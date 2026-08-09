package org.vectory.usermanager.infrastructure.outbound.messaging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OutboxTopics {

    public static final String USERS_CREATED = "users.created";
}
