const db = require('../config/db');

const HistoryModel = {
    async getByUserId(userId) {
        const query = `
            SELECT id, media_id, tmdb_id, media_type, playback_position, duration, updated_at
            FROM watch_history
            WHERE user_id = $1
            ORDER BY updated_at DESC;
        `;
        const result = await db.query(query, [userId]);
        return result.rows;
    },

    async upsert(userId, mediaId, tmdbId, mediaType, playbackPosition, duration = 0) {
        const query = `
            INSERT INTO watch_history (user_id, media_id, tmdb_id, media_type, playback_position, duration, updated_at)
            VALUES ($1, $2, $3, $4, $5, $6, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, media_id)
            DO UPDATE SET playback_position = EXCLUDED.playback_position,
                          duration = EXCLUDED.duration,
                          updated_at = CURRENT_TIMESTAMP
            RETURNING id, media_id, tmdb_id, media_type, playback_position, duration, updated_at;
        `;
        const result = await db.query(query, [userId, mediaId, tmdbId, mediaType, playbackPosition, duration]);
        return result.rows[0];
    }
};

module.exports = HistoryModel;
