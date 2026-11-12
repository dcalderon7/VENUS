const db = require('../config/db');

const WatchlistModel = {
    async getByUserId(userId) {
        const query = `
            SELECT id, tmdb_id, media_type, created_at
            FROM watchlist
            WHERE user_id = $1
            ORDER BY created_at DESC;
        `;
        const result = await db.query(query, [userId]);
        return result.rows;
    },

    async add(userId, tmdbId, mediaType) {
        const query = `
            INSERT INTO watchlist (user_id, tmdb_id, media_type)
            VALUES ($1, $2, $3)
            ON CONFLICT (user_id, tmdb_id) DO NOTHING
            RETURNING id, tmdb_id, media_type, created_at;
        `;
        const result = await db.query(query, [userId, tmdbId, mediaType]);
        return result.rows[0];
    },

    async remove(userId, tmdbId) {
        const query = `
            DELETE FROM watchlist
            WHERE user_id = $1 AND (id::text = $2 OR tmdb_id = $2)
            RETURNING id;
        `;
        const result = await db.query(query, [userId, tmdbId]);
        return result.rowCount > 0;
    }
};

module.exports = WatchlistModel;
