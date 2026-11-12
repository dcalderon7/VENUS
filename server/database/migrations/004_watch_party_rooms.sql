-- Migración para la funcionalidad Watch Party autoritativa en VENUS

CREATE TABLE IF NOT EXISTS watch_party_rooms (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    room_code VARCHAR(20) NOT NULL UNIQUE,
    host_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_id UUID REFERENCES media_files(id) ON DELETE SET NULL,
    is_playing BOOLEAN NOT NULL DEFAULT FALSE,
    current_position_ms BIGINT NOT NULL DEFAULT 0,
    last_updated_timestamp BIGINT NOT NULL DEFAULT 0,
    host_only_control BOOLEAN NOT NULL DEFAULT TRUE,
    passcode_hash VARCHAR(255) DEFAULT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS watch_party_participants (
    id BIGSERIAL PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES watch_party_rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL DEFAULT 'guest',
    allowed_control BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_room_user UNIQUE (room_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_watch_party_rooms_code ON watch_party_rooms(room_code);
CREATE INDEX IF NOT EXISTS idx_watch_party_rooms_host ON watch_party_rooms(host_id);
CREATE INDEX IF NOT EXISTS idx_watch_party_participants_room ON watch_party_participants(room_id);
CREATE INDEX IF NOT EXISTS idx_watch_party_participants_user ON watch_party_participants(user_id);
