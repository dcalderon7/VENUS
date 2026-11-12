const jwt = require('jsonwebtoken');
require('dotenv').config();

const authMiddleware = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    let token = null;

    if (authHeader && authHeader.startsWith('Bearer ')) {
        token = authHeader.split(' ')[1];
    } else if (req.query && req.query.token) {
        token = req.query.token;
    }

    if (!token) {
        return res.status(401).json({ error: 'Acceso no autorizado. Token no proporcionado.' });
    }

    try {
        const decoded = jwt.verify(token, process.env.JWT_SECRET || 'fallback_secret');
        req.user = decoded; // { userId, email, username, role }
        next();
    } catch (err) {
        return res.status(403).json({ error: 'Token inválido o expirado.' });
    }
};

module.exports = authMiddleware;
