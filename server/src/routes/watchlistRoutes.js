const express = require('express');
const router = express.Router();
const WatchlistController = require('../controllers/watchlistController');
const authMiddleware = require('../middleware/authMiddleware');

router.get('/', authMiddleware, WatchlistController.getWatchlist);
router.post('/', authMiddleware, WatchlistController.addToWatchlist);
router.delete('/:id', authMiddleware, WatchlistController.removeFromWatchlist);

module.exports = router;
