const express = require('express');
const adminMiddleware = require('../middleware/adminMiddleware');
const AdminController = require('../controllers/adminController');
const PasswordController = require('../controllers/passwordController');

const router = express.Router();

router.get('/access', adminMiddleware, (req, res) => {
    res.status(200).json({ authorized: true, role: req.user.role });
});
router.get('/summary', adminMiddleware, AdminController.summary);
router.get('/users', adminMiddleware, AdminController.users);
router.get('/users/:id', adminMiddleware, AdminController.userDetails);
router.patch('/users/:id/role', adminMiddleware, AdminController.updateUserRole);
router.post('/users/:id/password-reset', adminMiddleware, PasswordController.requestAdminReset);
router.get('/catalog', adminMiddleware, AdminController.catalog);
router.get('/media', adminMiddleware, AdminController.media);
router.patch('/media/:id/title', adminMiddleware, AdminController.updateMediaTitle);
router.delete('/media/:id', adminMiddleware, AdminController.deleteMedia);
router.get('/activity', adminMiddleware, AdminController.activity);

module.exports = router;
