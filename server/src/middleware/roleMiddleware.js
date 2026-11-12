const db = require('../config/db');

const roleMiddleware = (allowedRoles = ['admin', 'collaborator']) => {
    return async (req, res, next) => {
        try {
            const userId = req.user?.userId;
            if (!userId) {
                return res.status(401).json({ error: 'No autorizado.' });
            }

            const query = `SELECT role FROM users WHERE id = $1;`;
            const result = await db.query(query, [userId]);
            if (result.rows.length === 0) {
                return res.status(404).json({ error: 'Usuario no encontrado.' });
            }

            const userRole = result.rows[0].role || 'collaborator';
            if (!allowedRoles.includes(userRole)) {
                return res.status(403).json({ error: 'Permisos insuficientes para realizar esta acción.' });
            }

            req.user.role = userRole;
            next();
        } catch (err) {
            console.error('Error en roleMiddleware:', err);
            return res.status(500).json({ error: 'Error interno del servidor en verificación de permisos.' });
        }
    };
};

module.exports = roleMiddleware;
