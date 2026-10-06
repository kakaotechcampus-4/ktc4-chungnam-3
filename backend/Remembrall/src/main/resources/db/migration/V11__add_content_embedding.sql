CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE content
    ADD COLUMN embedding vector(768),
    ADD COLUMN embedding_model VARCHAR(100);
