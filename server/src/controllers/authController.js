const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const UserModel = require('../models/userModel');
require('dotenv').config();

const generateToken = (user) => {
    return jwt.sign(
        { userId: user.id, email: user.email, username: user.username, role: user.role },
        process.env.JWT_SECRET || 'fallback_secret',
        { expiresIn: '7d' }
    );
};

const AuthController = {
    async register(req, res) {
        try {
            const allowPublicReg = process.env.ALLOW_PUBLIC_REGISTRATION === 'true';
            if (!allowPublicReg) {
                return res.status(403).json({ error: 'El registro público está deshabilitado temporalmente.' });
            }

            const { name, username, email, password } = req.body;

            if (!name || !username || !email || !password || password.length < 6) {
                return res.status(400).json({ error: 'Todos los campos son obligatorios y la contraseña debe tener al menos 6 caracteres.' });
            }

            const existingUser = await UserModel.findByEmailOrUsername(email);
            if (existingUser) {
                return res.status(409).json({ error: 'El correo electrónico o nombre de usuario ya está registrado.' });
            }

            const saltRounds = 10;
            const passwordHash = await bcrypt.hash(password, saltRounds);
            const avatar = `https://api.dicebear.com/7.x/bottts/svg?seed=${username}`;

            const newUser = await UserModel.create({
                name,
                username,
                email,
                passwordHash,
                avatar
            });

            const token = generateToken(newUser);

            return res.status(201).json({
                message: 'Usuario registrado exitosamente',
                token,
                user: newUser
            });
        } catch (err) {
            console.error('Error en registro:', err);
            return res.status(500).json({ error: 'Error interno del servidor en el registro.' });
        }
    },

    async login(req, res) {
        try {
            const { identifier, password } = req.body;

            if (!identifier || !password) {
                return res.status(400).json({ error: 'Ingrese su correo/usuario y contraseña.' });
            }

            const user = await UserModel.findByEmailOrUsername(identifier);
            if (!user) {
                return res.status(401).json({ error: 'Credenciales inválidas.' });
            }

            const isMatch = await bcrypt.compare(password, user.password_hash);
            if (!isMatch) {
                return res.status(401).json({ error: 'Credenciales inválidas.' });
            }

            const token = generateToken(user);
            const { password_hash, ...userWithoutPassword } = user;

            return res.status(200).json({
                message: 'Inicio de sesión exitoso',
                token,
                user: userWithoutPassword
            });
        } catch (err) {
            console.error('Error en login:', err);
            return res.status(500).json({ error: 'Error interno del servidor en el inicio de sesión.' });
        }
    },

    async me(req, res) {
        try {
            const user = await UserModel.findById(req.user.userId);
            if (!user) {
                return res.status(404).json({ error: 'Usuario no encontrado.' });
            }
            return res.status(200).json({ user });
        } catch (err) {
            console.error('Error al obtener usuario actual:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    }
};

module.exports = AuthController;
