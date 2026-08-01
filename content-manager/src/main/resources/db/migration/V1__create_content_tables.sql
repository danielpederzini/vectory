CREATE TABLE posts
(
    id                 UUID PRIMARY KEY,
    author_id          UUID          NOT NULL,
    text               TEXT,
    creation_instant   TIMESTAMPTZ   NOT NULL,
    media_type         VARCHAR(20),
    media_url          VARCHAR(2048),
    CONSTRAINT posts_content_not_empty CHECK (text IS NOT NULL OR media_url IS NOT NULL),
    CONSTRAINT posts_media_complete CHECK ((media_type IS NULL) = (media_url IS NULL))
);

CREATE INDEX idx_posts_author_creation ON posts (author_id, creation_instant DESC);

CREATE TABLE interactions
(
    id                 UUID PRIMARY KEY,
    post_id            UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id            UUID        NOT NULL,
    type               VARCHAR(20) NOT NULL,
    creation_instant   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_interactions_post_type ON interactions (post_id, type);

CREATE INDEX idx_interactions_user_type ON interactions (user_id, type, creation_instant DESC);

CREATE UNIQUE INDEX uq_interactions_stateful
    ON interactions (post_id, user_id, type)
    WHERE type IN ('LIKE', 'SAVE');
