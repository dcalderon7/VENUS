-- Migración para mensajería de chat en tiempo real en Watch Party

CREATE TABLE IF NOT EXISTS watch_party_messages (
    id BIGSERIAL PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES watch_party_rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_watch_party_messages_room ON watch_party_messages(room_id, created_at ASC);
