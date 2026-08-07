package org.vectory.contentmanager.infrastructure.outbound.messaging;

public interface OutboxRelay {

    void publishPending();
}
