const express = require('express');
const router = express.Router();
const HistoryController = require('../controllers/historyController');
const authMiddleware = require('../middleware/authMiddleware');

router.get('/', authMiddleware, HistoryController.getHistory);
router.post('/', authMiddleware, HistoryController.saveHistory);

module.exports = router;
