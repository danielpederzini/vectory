CREATE INDEX idx_interactions_user_post ON interactions (user_id, post_id);

CREATE INDEX idx_post_embeddings_creation ON post_embeddings (creation_instant DESC);

CREATE INDEX idx_post_embeddings_cosine ON post_embeddings
    USING hnsw (embedding vector_cosine_ops);
