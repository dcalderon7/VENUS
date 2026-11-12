const db = require('../config/db');

async function ensureTableSchema() {
    try {
        await db.query(`ALTER TABLE media_files ALTER COLUMN tmdb_id DROP NOT NULL;`);
    } catch (e) {}
    try {
        await db.query(`ALTER TABLE media_files ADD COLUMN IF NOT EXISTS display_title VARCHAR(255);`);
    } catch (e) {}
}
ensureTableSchema();

const MediaModel = {
    async create({ tmdbId, mediaType, fileName, fileSize, fileFormat, storagePath, uploadedBy, displayTitle }) {
        const isIndependent = mediaType === 'independent' || !tmdbId || String(tmdbId).trim() === '' || String(tmdbId).trim() === 'null' || String(tmdbId).trim() === 'undefined';
        const cleanTmdbId = isIndependent ? null : String(tmdbId).trim();
        const resolvedMediaType = isIndependent ? 'independent' : (mediaType || 'movie');

        if (cleanTmdbId) {
            const existing = await this.findByTmdbId(cleanTmdbId);
            if (existing) {
                const query = `
                    UPDATE media_files
                    SET media_type = $1,
                        file_name = $2,
                        file_size = $3,
                        file_format = $4,
                        storage_path = $5,
                        uploaded_by = $6,
                        upload_status = 'completed',
                        display_title = COALESCE($8, display_title)
                    WHERE tmdb_id = $7
                    RETURNING *;
                `;
                const values = [resolvedMediaType, fileName, fileSize, fileFormat, storagePath, uploadedBy, cleanTmdbId, displayTitle || null];
                const result = await db.query(query, values);
                return { updated: true, oldMedia: existing, media: result.rows[0] };
            }
        }

        const query = `
            INSERT INTO media_files (tmdb_id, media_type, file_name, file_size, file_format, storage_path, uploaded_by, upload_status, display_title)
            VALUES ($1, $2, $3, $4, $5, $6, $7, 'completed', $8)
            RETURNING *;
        `;
        const finalDisplayTitle = isIndependent ? (displayTitle ? String(displayTitle).trim() : fileName) : null;
        const values = [cleanTmdbId, resolvedMediaType, fileName, fileSize, fileFormat, storagePath, uploadedBy, finalDisplayTitle];
        const result = await db.query(query, values);
        return { updated: false, oldMedia: null, media: result.rows[0] };
    },

    async findByTmdbId(tmdbId) {
        const query = `
            SELECT * FROM media_files
            WHERE tmdb_id = $1 AND upload_status = 'completed'
            ORDER BY created_at DESC;
        `;
        const result = await db.query(query, [tmdbId]);
        return result.rows[0];
    },

    async findById(id) {
        const query = `
            SELECT * FROM media_files
            WHERE id = $1;
        `;
        const result = await db.query(query, [id]);
        return result.rows[0];
    },

    async findAll() {
        const query = `
            SELECT * FROM media_files
            WHERE upload_status = 'completed'
            ORDER BY created_at DESC;
        `;
        const result = await db.query(query);
        return result.rows;
    },

    async getLibrary() {
        const query = `
            SELECT * FROM media_files
            WHERE upload_status = 'completed'
            ORDER BY created_at DESC;
        `;
        const result = await db.query(query);
        const all = result.rows.map(row => ({
            ...row,
            file_size: row.file_size ? parseInt(row.file_size, 10) : 0
        }));

        const independent = all.filter(m => {
            const tid = m.tmdb_id;
            const mtype = m.media_type;
            return mtype === 'independent' || tid == null || String(tid).trim() === '' || String(tid).trim() === 'null' || String(tid).trim() === 'undefined';
        });
        const independentIds = new Set(independent.map(m => m.id));
        const cataloged = all.filter(m => !independentIds.has(m.id));

        return { cataloged, independent };
    },

    async updateTitle(id, displayTitle) {
        const query = `
            UPDATE media_files
            SET display_title = $1
            WHERE id = $2
            RETURNING *;
        `;
        const result = await db.query(query, [displayTitle, id]);
        return result.rows[0];
    },

    async delete(id) {
        const query = `
            DELETE FROM media_files
            WHERE id = $1
            RETURNING *;
        `;
        const result = await db.query(query, [id]);
        return result.rows[0];
    }
};

module.exports = MediaModel;
