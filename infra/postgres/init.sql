-- Creates the databases used by the manager services.
-- Runs once on first container start via /docker-entrypoint-initdb.d.
-- The default POSTGRES_USER (postgres) owns all databases.
-- The pgvector extension itself is created by recommendation-manager's Flyway migration.

CREATE DATABASE content_manager;
CREATE DATABASE user_manager;
CREATE DATABASE recommendation_manager;
