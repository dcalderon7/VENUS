-- Migración no destructiva para soporte de biblioteca y videos independientes
ALTER TABLE media_files ALTER COLUMN tmdb_id DROP NOT NULL;
ALTER TABLE media_files ADD COLUMN IF NOT EXISTS display_title VARCHAR(255);
