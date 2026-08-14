-- Creates the databases used by the two manager services.
-- Runs once on first container start via /docker-entrypoint-initdb.d.
-- The default POSTGRES_USER (postgres) owns both databases.

CREATE DATABASE content_manager;
CREATE DATABASE user_manager;
