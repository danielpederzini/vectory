CREATE TABLE posts (
    id                 VARCHAR(255) PRIMARY KEY,
    text               TEXT         NOT NULL,
    creation_date_time TIMESTAMP    NOT NULL,
    media_type         VARCHAR(20),
    media_url          VARCHAR(2048)
);
