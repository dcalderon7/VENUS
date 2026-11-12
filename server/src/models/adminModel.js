const db = require('../config/db');

const adminModel = {
    async getSummary() {
        const query = `
            SELECT
                (SELECT COUNT(*) FROM users)::int AS total_users,
                (SELECT COUNT(*) FROM media_files)::int AS total_media_files,
                (SELECT COUNT(*) FROM watch_history)::int AS total_playbacks,
                (
                    SELECT COUNT(*)
                    FROM (
                        SELECT DISTINCT tmdb_id, media_type
                        FROM media_files
                    ) AS catalog
                )::int AS total_catalog_items;
        `;
        const result = await db.query(query);
        return result.rows[0];
    },

    async listUsers({ search, role, limit, offset }) {
        const values = [];
        const conditions = [];

        if (search) {
            values.push(`%${search}%`);
            conditions.push(`(name ILIKE $${values.length} OR username ILIKE $${values.length} OR email ILIKE $${values.length})`);
        }
        if (role) {
            values.push(role);
            conditions.push(`role = $${values.length}`);
        }

        const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
        const countResult = await db.query(
            `SELECT COUNT(*)::int AS total FROM users ${where};`,
            values
        );

        values.push(limit, offset);
        const result = await db.query(
            `SELECT id, name, username, email, avatar, role, created_at
             FROM users
             ${where}
             ORDER BY created_at DESC
             LIMIT $${values.length - 1} OFFSET $${values.length};`,
            values
        );

        return { items: result.rows, total: countResult.rows[0].total };
    },

    async getUserDetails(id) {
        const result = await db.query(
            `SELECT id, name, username, email, role, created_at
             FROM users
             WHERE id = $1::uuid;`,
            [id]
        );
        return result.rows[0];
    },

    async updateUserRole({ actorId, targetId, role }) {
        const client = await db.pool.connect();

        try {
            await client.query('BEGIN');

            const adminRows = await client.query(
                `SELECT id
                 FROM users
                 WHERE role = 'admin'
                 FOR UPDATE;`
            );

            const actorResult = await client.query(
                `SELECT id
                 FROM users
                 WHERE id = $1::uuid AND role = 'admin';`,
                [actorId]
            );

            if (actorResult.rowCount !== 1) {
                const error = new Error('Permisos administrativos insuficientes.');
                error.status = 403;
                throw error;
            }

            const targetResult = await client.query(
                `SELECT id, name, username, email, role, created_at
                 FROM users
                 WHERE id = $1::uuid
                 FOR UPDATE;`,
                [targetId]
            );

            if (targetResult.rowCount !== 1) {
                const error = new Error('Usuario no encontrado.');
                error.status = 404;
                throw error;
            }

            const target = targetResult.rows[0];
            if (target.id === actorId) {
                const error = new Error('No puedes modificar tu propio rol administrativo.');
                error.status = 400;
                throw error;
            }

            if (target.role === 'admin' && role !== 'admin' && adminRows.rowCount <= 1) {
                const error = new Error('No se puede degradar al último administrador.');
                error.status = 409;
                throw error;
            }

            const updatedResult = await client.query(
                `UPDATE users
                 SET role = $1
                 WHERE id = $2::uuid
                 RETURNING id, name, username, email, role, created_at;`,
                [role, targetId]
            );

            if (updatedResult.rowCount !== 1) {
                const error = new Error('El cambio de rol no afectó exactamente un usuario.');
                error.status = 409;
                throw error;
            }

            const auditTableResult = await client.query(
                `SELECT to_regclass('public.admin_audit_log') AS table_name;`
            );

            if (auditTableResult.rows[0].table_name) {
                await client.query(
                    `INSERT INTO admin_audit_log
                        (actor_user_id, target_user_id, action, previous_role, new_role)
                     VALUES ($1::uuid, $2::uuid, $3, $4, $5);`,
                    [actorId, targetId, 'user.role_changed', target.role, role]
                );
            }

            await client.query('COMMIT');
            return updatedResult.rows[0];
        } catch (error) {
            await client.query('ROLLBACK').catch(() => {});
            throw error;
        } finally {
            client.release();
        }
    },

    async listCatalog({ search, mediaType, limit, offset }) {
        const values = [];
        const conditions = [];

        if (search) {
            values.push(`%${search}%`);
            conditions.push(`tmdb_id ILIKE $${values.length}`);
        }
        if (mediaType) {
            values.push(mediaType);
            conditions.push(`media_type = $${values.length}`);
        }

        const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
        const countResult = await db.query(
            `SELECT COUNT(*)::int AS total
             FROM (SELECT DISTINCT tmdb_id, media_type FROM media_files ${where}) AS catalog;`,
            values
        );

        values.push(limit, offset);
        const result = await db.query(
            `SELECT tmdb_id, media_type, COUNT(*)::int AS file_count, MAX(created_at) AS latest_created_at
             FROM media_files
             ${where}
             GROUP BY tmdb_id, media_type
             ORDER BY latest_created_at DESC
             LIMIT $${values.length - 1} OFFSET $${values.length};`,
            values
        );

        return { items: result.rows, total: countResult.rows[0].total };
    },

    async listMedia({ search, mediaType, status, limit, offset }) {
        const values = [];
        const conditions = [];

        if (search) {
            values.push(`%${search}%`);
            conditions.push(`(tmdb_id ILIKE $${values.length} OR file_name ILIKE $${values.length})`);
        }
        if (mediaType) {
            values.push(mediaType);
            conditions.push(`media_type = $${values.length}`);
        }
        if (status) {
            values.push(status);
            conditions.push(`upload_status = $${values.length}`);
        }

        const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
        const countResult = await db.query(
            `SELECT COUNT(*)::int AS total FROM media_files ${where};`,
            values
        );

        values.push(limit, offset);
        const result = await db.query(
            `SELECT id, tmdb_id, media_type, file_name, display_title, file_size, file_format,
                    uploaded_by, upload_status, created_at
             FROM media_files
             ${where}
             ORDER BY created_at DESC
             LIMIT $${values.length - 1} OFFSET $${values.length};`,
            values
        );

        return { items: result.rows, total: countResult.rows[0].total };
    },

    async listActivity({ limit, offset }) {
        const countResult = await db.query('SELECT COUNT(*)::int AS total FROM watch_history;');
        const result = await db.query(
            `SELECT
                history.id,
                history.user_id,
                users.username,
                users.name AS user_name,
                history.tmdb_id,
                history.media_type,
                history.playback_position,
                history.updated_at
             FROM watch_history AS history
             LEFT JOIN users ON users.id = history.user_id
             ORDER BY history.updated_at DESC
             LIMIT $1 OFFSET $2;`,
            [limit, offset]
        );

        return { items: result.rows, total: countResult.rows[0].total };
    }
};

module.exports = adminModel;
