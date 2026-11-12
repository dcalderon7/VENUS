const WatchlistModel = require('../models/watchlistModel');

const WatchlistController = {
    async getWatchlist(req, res) {
        try {
            const watchlist = await WatchlistModel.getByUserId(req.user.userId);
            return res.status(200).json({ watchlist });
        } catch (err) {
            console.error('Error al obtener watchlist:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    },

    async addToWatchlist(req, res) {
        try {
            const { tmdbId, mediaType } = req.body;
            if (!tmdbId || !mediaType) {
                return res.status(400).json({ error: 'tmdbId y mediaType son obligatorios.' });
            }

            const item = await WatchlistModel.add(req.user.userId, tmdbId, mediaType);
            return res.status(201).json({ message: 'Añadido a la lista exitosamente', item });
        } catch (err) {
            console.error('Error al añadir a watchlist:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    },

    async removeFromWatchlist(req, res) {
        try {
            const { id } = req.params; // tmdbId o id de tabla
            const success = await WatchlistModel.remove(req.user.userId, id);
            if (!success) {
                return res.status(404).json({ error: 'Elemento no encontrado en la lista.' });
            }
            return res.status(200).json({ message: 'Eliminado de la lista exitosamente' });
        } catch (err) {
            console.error('Error al eliminar de watchlist:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    }
};

module.exports = WatchlistController;
