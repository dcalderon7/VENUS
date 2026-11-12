const express = require('express');
const router = express.Router();
const rateLimit = require('express-rate-limit');
const AuthController = require('../controllers/authController');
const authMiddleware = require('../middleware/authMiddleware');
const PasswordController = require('../controllers/passwordController');

const loginLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 15,
    message: { error: 'Demasiados intentos de inicio de sesión. Por favor intente más tarde.' },
    standardHeaders: true,
    legacyHeaders: false,
});

const registerLimiter = rateLimit({
    windowMs: 60 * 60 * 1000, // 1 hour
    max: 5,
    message: { error: 'Demasiadas solicitudes de registro. Por favor intente más tarde.' },
    standardHeaders: true,
    legacyHeaders: false,
});

const passwordResetLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 5,
    message: { error: 'Demasiadas solicitudes de restablecimiento. Por favor intente más tarde.' },
    standardHeaders: true,
    legacyHeaders: false,
});

router.post('/register', registerLimiter, AuthController.register);
router.post('/login', loginLimiter, AuthController.login);
router.get('/me', authMiddleware, AuthController.me);
router.post('/password-reset/complete', passwordResetLimiter, PasswordController.completeReset);

module.exports = router;
