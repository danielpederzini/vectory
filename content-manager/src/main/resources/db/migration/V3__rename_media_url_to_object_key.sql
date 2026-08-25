-- Media is now stored in object storage and referenced by its object key
-- (bytes are uploaded directly by the client via presigned URLs).
ALTER TABLE posts DROP CONSTRAINT posts_content_not_empty;
ALTER TABLE posts DROP CONSTRAINT posts_media_complete;

ALTER TABLE posts RENAME COLUMN media_url TO media_object_key;
ALTER TABLE posts ALTER COLUMN media_object_key TYPE VARCHAR(1024);

ALTER TABLE posts
    ADD CONSTRAINT posts_content_not_empty CHECK (text IS NOT NULL OR media_object_key IS NOT NULL);
ALTER TABLE posts
    ADD CONSTRAINT posts_media_complete CHECK ((media_type IS NULL) = (media_object_key IS NULL));
