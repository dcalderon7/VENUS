const express = require('express');
const router = express.Router();
const UserController = require('../controllers/userController');
const authMiddleware = require('../middleware/authMiddleware');
const PasswordController = require('../controllers/passwordController');

router.get('/profile', authMiddleware, UserController.getProfile);
router.put('/profile', authMiddleware, UserController.updateProfile);
router.post('/password', authMiddleware, PasswordController.changeOwn);

module.exports = router;
