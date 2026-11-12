const UserModel = require('../models/userModel');

const UserController = {
    async getProfile(req, res) {
        try {
            const user = await UserModel.findById(req.user.userId);
            if (!user) {
                return res.status(404).json({ error: 'Usuario no encontrado.' });
            }
            return res.status(200).json({ user });
        } catch (err) {
            console.error('Error al obtener perfil:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    },

    async updateProfile(req, res) {
        try {
            const { name, avatar } = req.body;
            const updatedUser = await UserModel.updateProfile(req.user.userId, { name, avatar });
            if (!updatedUser) {
                return res.status(404).json({ error: 'Usuario no encontrado.' });
            }
            return res.status(200).json({ message: 'Perfil actualizado exitosamente', user: updatedUser });
        } catch (err) {
            console.error('Error al actualizar perfil:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    }
};

module.exports = UserController;
