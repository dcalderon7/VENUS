const PasswordService = require('../services/passwordService');

function sendError(res, error, message) {
    console.error(message, {
        name: error.name,
        code: error.code,
        command: error.command,
        responseCode: error.responseCode,
        response: error.response,
        message: error.message
    });
    return res.status(error.status || 500).json({
        error: error.status ? error.message : 'Error interno del servidor.'
    });
}

const PasswordController = {
    async changeOwn(req, res) {
        try {
            const { currentPassword, newPassword, confirmation } = req.body || {};
            await PasswordService.changeOwnPassword(
                req.user.userId,
                currentPassword,
                newPassword,
                confirmation
            );
            return res.status(200).json({ message: 'Contraseña actualizada correctamente.' });
        } catch (error) {
            return sendError(res, error, 'Error al cambiar contraseña:');
        }
    },

    async requestAdminReset(req, res) {
        try {
            await PasswordService.createAdminReset({
                actorId: req.user.userId,
                targetId: req.params.id
            });
            return res.status(202).json({
                message: 'Si el usuario tiene un correo válido, recibirá instrucciones de restablecimiento.'
            });
        } catch (error) {
            return sendError(res, error, 'Error al solicitar restablecimiento administrativo:');
        }
    },

    async completeReset(req, res) {
        try {
            const { token, newPassword, confirmation } = req.body || {};
            await PasswordService.completeReset(token, newPassword, confirmation);
            return res.status(200).json({ message: 'Contraseña restablecida correctamente.' });
        } catch (error) {
            return sendError(res, error, 'Error al completar restablecimiento:');
        }
    }
};

module.exports = PasswordController;
