CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar VARCHAR(500),
    role VARCHAR(50) DEFAULT 'collaborator',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS watchlist (
    id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    tmdb_id VARCHAR(50) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_watchlist UNIQUE (user_id, tmdb_id)
);

CREATE TABLE IF NOT EXISTS media_files (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tmdb_id VARCHAR(50) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    file_format VARCHAR(50) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    upload_status VARCHAR(50) DEFAULT 'completed',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS watch_history (
    id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    media_id UUID REFERENCES media_files(id) ON DELETE CASCADE,
    tmdb_id VARCHAR(50) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    playback_position BIGINT DEFAULT 0,
    duration BIGINT DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_media_history UNIQUE (user_id, media_id)
);

CREATE INDEX IF NOT EXISTS idx_watchlist_user_id ON watchlist(user_id);
CREATE INDEX IF NOT EXISTS idx_history_user_id ON watch_history(user_id);
CREATE INDEX IF NOT EXISTS idx_history_media_id ON watch_history(media_id);
CREATE INDEX IF NOT EXISTS idx_media_tmdb_id ON media_files(tmdb_id);
