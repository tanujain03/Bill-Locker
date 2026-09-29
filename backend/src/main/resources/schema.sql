-- Runs at startup, before Hibernate creates the tables from the entities.
-- DocumentChunk.embedding (AI document search) needs the pgvector extension.
-- Only enabled when pgvector is installed on this server: a plain
-- CREATE EXTENSION fails with SQLSTATE 0A000, which HikariCP treats as a dead
-- connection and startup aborts. Without pgvector only document_chunks is skipped.
-- The file is a single statement (spring.sql.init.separator), so ';' inside is fine.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
    END IF;
END
$$;
