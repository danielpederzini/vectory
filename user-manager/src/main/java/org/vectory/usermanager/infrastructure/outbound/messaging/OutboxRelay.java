package org.vectory.usermanager.infrastructure.outbound.messaging;

public interface OutboxRelay {

    void publishPending();
}
