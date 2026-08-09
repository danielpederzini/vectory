CREATE TABLE outbox
(
    id                 UUID PRIMARY KEY,
    aggregate_type     VARCHAR(50)  NOT NULL,
    aggregate_id       UUID         NOT NULL,
    topic              VARCHAR(255) NOT NULL,
    message_key        VARCHAR(255) NOT NULL,
    payload             JSONB        NOT NULL,
    creation_instant    TIMESTAMPTZ  NOT NULL,
    publication_instant TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished
    ON outbox (creation_instant)
    WHERE publication_instant IS NULL;
