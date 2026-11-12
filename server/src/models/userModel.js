const db = require('../config/db');

const UserModel = {
    async create({ name, username, email, passwordHash, avatar }) {
        const query = `
            INSERT INTO users (name, username, email, password_hash, avatar, role)
            VALUES ($1, $2, $3, $4, $5, 'collaborator')
            RETURNING id, name, username, email, avatar, role, created_at;
        `;
        const values = [name, username, email, passwordHash, avatar];
        const result = await db.query(query, values);
        return result.rows[0];
    },

    async findByEmailOrUsername(identifier) {
        const query = `
            SELECT * FROM users
            WHERE email = $1 OR username = $1;
        `;
        const result = await db.query(query, [identifier]);
        return result.rows[0];
    },

    async findById(id) {
        const query = `
            SELECT id, name, username, email, avatar, role, created_at
            FROM users
            WHERE id = $1;
        `;
        const result = await db.query(query, [id]);
        return result.rows[0];
    },

    async updateProfile(id, { name, avatar }) {
        const query = `
            UPDATE users
            SET name = COALESCE($2, name),
                avatar = COALESCE($3, avatar)
            WHERE id = $1
            RETURNING id, name, username, email, avatar, role, created_at;
        `;
        const result = await db.query(query, [id, name, avatar]);
        return result.rows[0];
    }
};

module.exports = UserModel;
