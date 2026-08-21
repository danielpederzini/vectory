CREATE TABLE post_embeddings
(
    post_id          UUID PRIMARY KEY,
    embedding        VECTOR(1536) NOT NULL,
    creation_instant TIMESTAMPTZ  NOT NULL
);

CREATE TABLE user_embeddings
(
    user_id         UUID PRIMARY KEY,
    embedding       VECTOR(1536) NOT NULL,
    updated_instant TIMESTAMPTZ  NOT NULL
);

CREATE TABLE interactions
(
    id                 UUID PRIMARY KEY,
    user_id            UUID        NOT NULL,
    post_id            UUID        NOT NULL,
    type               VARCHAR(20) NOT NULL,
    creation_instant   TIMESTAMPTZ NOT NULL,
    processed_instant  TIMESTAMPTZ
);

CREATE INDEX idx_interactions_unprocessed
    ON interactions (creation_instant)
    WHERE processed_instant IS NULL;
