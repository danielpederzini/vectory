CREATE TABLE users
(
    id                 UUID PRIMARY KEY,
    username           VARCHAR(255) NOT NULL,
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    creation_instant   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);
