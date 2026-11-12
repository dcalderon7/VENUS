const AdminModel = require('../models/adminModel');
const MediaController = require('./mediaController');

const MAX_LIMIT = 100;
const DEFAULT_LIMIT = 20;
const ALLOWED_ROLES = new Set(['admin', 'collaborator', 'user']);

function parsePagination(req, res) {
    const page = Number(req.query.page || 1);
    const limit = Number(req.query.limit || DEFAULT_LIMIT);

    if (!Number.isInteger(page) || page < 1 || !Number.isInteger(limit) || limit < 1 || limit > MAX_LIMIT) {
        res.status(400).json({ error: `page debe ser un entero positivo y limit debe estar entre 1 y ${MAX_LIMIT}.` });
        return null;
    }

    return { page, limit, offset: (page - 1) * limit };
}

function optionalText(value, field, maxLength = 100) {
    if (value === undefined || value === '') return null;
    if (typeof value !== 'string' || value.length > maxLength) {
        const error = new Error(`${field} no es válido.`);
        error.status = 400;
        throw error;
    }
    return value.trim();
}

function requiredUuid(value, field) {
    if (typeof value !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) {
        const error = new Error(`${field} no es válido.`);
        error.status = 400;
        throw error;
    }
    return value;
}

function sendError(res, error, message) {
    console.error(message, error);
    return res.status(error.status || 500).json({
        error: error.status ? error.message : 'Error interno del servidor.'
    });
}

const AdminController = {
    async summary(req, res) {
        try {
            const summary = await AdminModel.getSummary();
            return res.status(200).json({ summary });
        } catch (error) {
            return sendError(res, error, 'Error al obtener resumen administrativo:');
        }
    },

    async users(req, res) {
        try {
            const pagination = parsePagination(req, res);
            if (!pagination) return;
            const search = optionalText(req.query.search, 'search');
            const role = optionalText(req.query.role, 'role', 50);
            const result = await AdminModel.listUsers({ ...pagination, search, role });
            return res.status(200).json({ ...result, page: pagination.page, limit: pagination.limit });
        } catch (error) {
            return sendError(res, error, 'Error al listar usuarios administrativos:');
        }
    },

    async userDetails(req, res) {
        try {
            const userId = requiredUuid(req.params.id, 'id');
            const user = await AdminModel.getUserDetails(userId);
            if (!user) {
                return res.status(404).json({ error: 'Usuario no encontrado.' });
            }
            return res.status(200).json({ user });
        } catch (error) {
            return sendError(res, error, 'Error al obtener detalle administrativo del usuario:');
        }
    },

    async updateUserRole(req, res) {
        try {
            const targetId = requiredUuid(req.params.id, 'id');
            const { role } = req.body || {};

            if (typeof role !== 'string' || !ALLOWED_ROLES.has(role)) {
                return res.status(400).json({
                    error: 'role debe ser uno de: admin, collaborator, user.'
                });
            }

            if (targetId === req.user.userId) {
                return res.status(400).json({ error: 'No puedes modificar tu propio rol administrativo.' });
            }

            const user = await AdminModel.updateUserRole({
                actorId: req.user.userId,
                targetId,
                role
            });

            return res.status(200).json({
                message: 'Rol actualizado correctamente.',
                user
            });
        } catch (error) {
            return sendError(res, error, 'Error al cambiar rol administrativo:');
        }
    },

    async catalog(req, res) {
        try {
            const pagination = parsePagination(req, res);
            if (!pagination) return;
            const search = optionalText(req.query.search, 'search', 50);
            const mediaType = optionalText(req.query.mediaType, 'mediaType', 20);
            const result = await AdminModel.listCatalog({ ...pagination, search, mediaType });
            return res.status(200).json({ ...result, page: pagination.page, limit: pagination.limit });
        } catch (error) {
            return sendError(res, error, 'Error al listar catálogo administrativo:');
        }
    },

    async media(req, res) {
        try {
            const pagination = parsePagination(req, res);
            if (!pagination) return;
            const search = optionalText(req.query.search, 'search', 100);
            const mediaType = optionalText(req.query.mediaType, 'mediaType', 20);
            const status = optionalText(req.query.status, 'status', 50);
            const result = await AdminModel.listMedia({ ...pagination, search, mediaType, status });
            return res.status(200).json({ ...result, page: pagination.page, limit: pagination.limit });
        } catch (error) {
            return sendError(res, error, 'Error al listar archivos administrativos:');
        }
    },

    updateMediaTitle: MediaController.updateTitle,
    deleteMedia: MediaController.deleteMedia,

    async activity(req, res) {
        try {
            const pagination = parsePagination(req, res);
            if (!pagination) return;
            const result = await AdminModel.listActivity(pagination);
            return res.status(200).json({ ...result, page: pagination.page, limit: pagination.limit });
        } catch (error) {
            return sendError(res, error, 'Error al listar actividad administrativa:');
        }
    }
};

module.exports = AdminController;
