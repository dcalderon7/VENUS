const express = require('express');
const cors = require('cors');

const authRoutes = require('./routes/authRoutes');
const userRoutes = require('./routes/userRoutes');
const watchlistRoutes = require('./routes/watchlistRoutes');
const historyRoutes = require('./routes/historyRoutes');
const mediaRoutes = require('./routes/mediaRoutes');
const adminRoutes = require('./routes/adminRoutes');

const app = express();
const configuredOrigins = (process.env.CORS_ALLOWED_ORIGINS || '')
    .split(',')
    .map((origin) => origin.trim().replace(/\/$/, ''))
    .filter(Boolean);
const localOrigins = new Set([
    'http://localhost:5173',
    'http://127.0.0.1:5173',
]);

function isAllowedOrigin(origin) {
    if (!origin || localOrigins.has(origin) || configuredOrigins.includes(origin)) {
        return true;
    }

    // Quick Tunnels receive a new, random subdomain on each start.
    return /^https:\/\/[a-z0-9-]+\.trycloudflare\.com$/i.test(origin);
}

app.set('trust proxy', 'loopback');

app.use(cors({
    origin: function (origin, callback) {
        if (isAllowedOrigin(origin)) {
            callback(null, true);
            return;
        }

        callback(new Error('Origen no permitido por CORS'));
    },
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization'],
}));
app.use(express.json());

// Rutas de la API REST
app.use('/api/auth', authRoutes);
app.use('/api/user', userRoutes);
app.use('/api/user/watchlist', watchlistRoutes);
app.use('/api/user/history', historyRoutes);
app.use('/api/media', mediaRoutes);
app.use('/api/admin', adminRoutes);

app.get('/', (req, res) => {
    res.status(200).json({ message: 'Bienvenido a la API REST de VENUS Streaming' });
});

// Manejador de rutas no encontradas
app.use((req, res, next) => {
    res.status(404).json({ error: 'Ruta no encontrada' });
});

module.exports = app;
