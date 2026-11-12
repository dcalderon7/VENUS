const db = require('../config/db');

let schemaPromise = null;
async function ensureSchema() {
    if (!schemaPromise) {
        schemaPromise = (async () => {
            try {
                await db.query(`
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
                `);
                await db.query(`
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
                `);
                await db.query(`
                    CREATE TABLE IF NOT EXISTS watch_party_messages (
                        id BIGSERIAL PRIMARY KEY,
                        room_id UUID NOT NULL REFERENCES watch_party_rooms(id) ON DELETE CASCADE,
                        user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        message TEXT NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    );
                `);
                await db.query(`CREATE INDEX IF NOT EXISTS idx_watch_party_rooms_code ON watch_party_rooms(room_code);`);
                await db.query(`CREATE INDEX IF NOT EXISTS idx_watch_party_rooms_host ON watch_party_rooms(host_id);`);
                await db.query(`CREATE INDEX IF NOT EXISTS idx_watch_party_participants_room ON watch_party_participants(room_id);`);
                await db.query(`CREATE INDEX IF NOT EXISTS idx_watch_party_participants_user ON watch_party_participants(user_id);`);
                await db.query(`CREATE INDEX IF NOT EXISTS idx_watch_party_messages_room ON watch_party_messages(room_id, created_at ASC);`);
            } catch (e) {
                console.error('[WATCH PARTY SCHEMA ERROR]', e.message);
            }
        })();
    }
    return schemaPromise;
}

const WatchPartyModel = {
    async createRoom({ roomCode, hostId, mediaId, hostOnlyControl = true, passcodeHash = null }) {
        await ensureSchema();
        const now = Date.now();
        const query = `
            INSERT INTO watch_party_rooms (
                room_code, host_id, media_id, is_playing, current_position_ms,
                last_updated_timestamp, host_only_control, passcode_hash, status
            )
            VALUES ($1, $2, $3, false, 0, $4, $5, $6, 'active')
            RETURNING *;
        `;
        const values = [roomCode, hostId, mediaId || null, now, hostOnlyControl, passcodeHash];
        const result = await db.query(query, values);
        return result.rows[0];
    },

    async findRoomById(id) {
        await ensureSchema();
        const query = `
            SELECT r.*, m.file_name, m.display_title, m.media_type, u.name as host_name
            FROM watch_party_rooms r
            LEFT JOIN media_files m ON r.media_id = m.id
            LEFT JOIN users u ON r.host_id = u.id
            WHERE r.id = $1;
        `;
        const result = await db.query(query, [id]);
        return result.rows[0] || null;
    },

    async findRoomByCode(code) {
        await ensureSchema();
        const query = `
            SELECT r.*, m.file_name, m.display_title, m.media_type, u.name as host_name
            FROM watch_party_rooms r
            LEFT JOIN media_files m ON r.media_id = m.id
            LEFT JOIN users u ON r.host_id = u.id
            WHERE UPPER(r.room_code) = UPPER($1);
        `;
        const result = await db.query(query, [code]);
        return result.rows[0] || null;
    },

    async updatePlaybackState(id, { isPlaying, currentPositionMs, lastUpdatedTimestamp }) {
        await ensureSchema();
        const query = `
            UPDATE watch_party_rooms
            SET is_playing = $1,
                current_position_ms = $2,
                last_updated_timestamp = $3,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = $4
            RETURNING *;
        `;
        const values = [isPlaying, currentPositionMs, lastUpdatedTimestamp, id];
        const result = await db.query(query, values);
        return result.rows[0];
    },

    async updateMedia(id, mediaId) {
        await ensureSchema();
        const now = Date.now();
        const query = `
            UPDATE watch_party_rooms
            SET media_id = $1,
                is_playing = false,
                current_position_ms = 0,
                last_updated_timestamp = $2,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = $3
            RETURNING *;
        `;
        const result = await db.query(query, [mediaId, now, id]);
        return result.rows[0];
    },

    async updateHost(id, newHostId) {
        await ensureSchema();
        const query = `
            UPDATE watch_party_rooms
            SET host_id = $1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = $2
            RETURNING *;
        `;
        const result = await db.query(query, [newHostId, id]);
        return result.rows[0];
    },

    async updateStatus(id, status) {
        await ensureSchema();
        const query = `
            UPDATE watch_party_rooms
            SET status = $1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = $2
            RETURNING *;
        `;
        const result = await db.query(query, [status, id]);
        return result.rows[0];
    },

    async addOrUpdateParticipant({ roomId, userId, role = 'guest', allowedControl = false }) {
        await ensureSchema();
        const query = `
            INSERT INTO watch_party_participants (room_id, user_id, role, allowed_control, joined_at, last_seen_at)
            VALUES ($1, $2, $3, $4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (room_id, user_id)
            DO UPDATE SET
                role = EXCLUDED.role,
                allowed_control = EXCLUDED.allowed_control,
                last_seen_at = CURRENT_TIMESTAMP
            RETURNING *;
        `;
        const values = [roomId, userId, role, allowedControl];
        const result = await db.query(query, values);
        return result.rows[0];
    },

    async removeParticipant(roomId, userId) {
        await ensureSchema();
        const query = `
            DELETE FROM watch_party_participants
            WHERE room_id = $1 AND user_id = $2
            RETURNING *;
        `;
        const result = await db.query(query, [roomId, userId]);
        return result.rows[0];
    },

    async getParticipants(roomId) {
        await ensureSchema();
        const query = `
            SELECT p.*, u.name, u.username, u.avatar
            FROM watch_party_participants p
            JOIN users u ON p.user_id = u.id
            WHERE p.room_id = $1
            ORDER BY p.joined_at ASC;
        `;
        const result = await db.query(query, [roomId]);
        return result.rows;
    },

    async saveChatMessage({ roomId, userId, message }) {
        await ensureSchema();
        const query = `
            INSERT INTO watch_party_messages (room_id, user_id, message)
            VALUES ($1, $2, $3)
            RETURNING id, room_id, user_id, message, created_at;
        `;
        const result = await db.query(query, [roomId, userId, message]);
        const row = result.rows[0];

        const userRes = await db.query(`SELECT name, username, avatar FROM users WHERE id = $1`, [userId]);
        const userObj = userRes.rows[0] || {};
        const roomRes = await db.query(`SELECT host_id FROM watch_party_rooms WHERE id = $1`, [roomId]);
        const isHost = roomRes.rows[0]?.host_id === userId;

        return {
            id: parseInt(row.id, 10),
            roomId: row.room_id,
            userId: row.user_id,
            name: userObj.name || 'Usuario',
            username: userObj.username || 'usuario',
            avatar: userObj.avatar || null,
            message: row.message,
            createdAt: row.created_at,
            isHost: isHost
        };
    },

    async getRecentChatMessages(roomId, limit = 50) {
        await ensureSchema();
        const query = `
            SELECT m.id, m.room_id, m.user_id, m.message, m.created_at,
                   u.name, u.username, u.avatar, r.host_id
            FROM watch_party_messages m
            JOIN users u ON m.user_id = u.id
            JOIN watch_party_rooms r ON m.room_id = r.id
            WHERE m.room_id = $1
            ORDER BY m.created_at ASC
            LIMIT $2;
        `;
        const result = await db.query(query, [roomId, limit]);
        return result.rows.map(row => ({
            id: parseInt(row.id, 10),
            roomId: row.room_id,
            userId: row.user_id,
            name: row.name,
            username: row.username,
            avatar: row.avatar,
            message: row.message,
            createdAt: row.created_at,
            isHost: row.user_id === row.host_id
        }));
    },

    async deleteRoom(id) {
        await ensureSchema();
        const query = `
            DELETE FROM watch_party_rooms
            WHERE id = $1
            RETURNING *;
        `;
        const result = await db.query(query, [id]);
        return result.rows[0];
    }
};

module.exports = WatchPartyModel;
