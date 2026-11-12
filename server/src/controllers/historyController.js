const HistoryModel = require('../models/historyModel');

const HistoryController = {
    async getHistory(req, res) {
        try {
            const history = await HistoryModel.getByUserId(req.user.userId);
            return res.status(200).json({ history });
        } catch (err) {
            console.error('Error al obtener historial:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    },

    async saveHistory(req, res) {
        try {
            const { mediaId, tmdbId, mediaType, playbackPosition, duration } = req.body;
            if (!mediaId || !tmdbId || !mediaType || playbackPosition === undefined) {
                return res.status(400).json({ error: 'mediaId, tmdbId, mediaType y playbackPosition son obligatorios.' });
            }

            const item = await HistoryModel.upsert(req.user.userId, mediaId, tmdbId, mediaType, playbackPosition, duration || 0);
            return res.status(200).json({ message: 'Historial actualizado exitosamente', item });
        } catch (err) {
            console.error('Error al guardar historial:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    }
};

module.exports = HistoryController;
