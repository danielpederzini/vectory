package org.vectory.contentmanager.infrastructure.outbound.persistence.entity;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.vectory.contentmanager.domain.enums.AggregateType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "outbox")
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 50, updatable = false)
    private AggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(nullable = false, updatable = false)
    private String topic;

    @Column(name = "message_key", nullable = false, updatable = false)
    private String messageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "creation_instant", nullable = false, updatable = false)
    private Instant creationInstant;

    @Column(name = "publication_instant")
    private Instant publicationInstant;
}
