const express = require('express');
const router = express.Router();
const MediaController = require('../controllers/mediaController');
const authMiddleware = require('../middleware/authMiddleware');
const roleMiddleware = require('../middleware/roleMiddleware');
const upload = require('../middleware/uploadMiddleware');

router.get('/library', authMiddleware, MediaController.getLibrary);
router.get('/check/:tmdbId', authMiddleware, MediaController.checkAvailability);
router.get('/:id/stream', authMiddleware, MediaController.streamMedia);
router.get('/', authMiddleware, MediaController.listMedia);
router.get('/:id', authMiddleware, MediaController.getMediaInfo);
router.post('/upload', authMiddleware, roleMiddleware(['admin', 'collaborator']), upload.single('file'), MediaController.uploadMedia);
router.patch('/:id/title', authMiddleware, roleMiddleware(['admin', 'collaborator']), MediaController.updateTitle);
router.delete('/:id', authMiddleware, roleMiddleware(['admin', 'collaborator']), MediaController.deleteMedia);

module.exports = router;
