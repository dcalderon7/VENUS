const db = require('../config/db');
const authMiddleware = require('./authMiddleware');

const adminMiddleware = [
    authMiddleware,
    async (req, res, next) => {
        try {
            const userId = req.user?.userId;
            if (!userId) {
                return res.status(401).json({ error: 'No autorizado.' });
            }

            const result = await db.query(
                'SELECT role FROM users WHERE id = $1;',
                [userId]
            );

            if (result.rows.length === 0) {
                return res.status(404).json({ error: 'Usuario no encontrado.' });
            }

            const role = result.rows[0].role;
            if (role !== 'admin') {
                return res.status(403).json({ error: 'Permisos administrativos insuficientes.' });
            }

            req.user.role = role;
            next();
        } catch (err) {
            console.error('Error en adminMiddleware:', err);
            return res.status(500).json({ error: 'Error interno en la verificación administrativa.' });
        }
    }
];

module.exports = adminMiddleware;
