const crypto = require('crypto');
const bcrypt = require('bcrypt');
const nodemailer = require('nodemailer');
const path = require('path');
const db = require('../config/db');

require('dotenv').config({ path: path.join(__dirname, '../../.env') });

const SALT_ROUNDS = 10;
const MIN_PASSWORD_LENGTH = 6;
const RESET_TOKEN_TTL_MINUTES = 30;

function validationError(message) {
    const error = new Error(message);
    error.status = 400;
    return error;
}

function validateNewPassword(password, confirmation) {
    if (typeof password !== 'string' || password.length < MIN_PASSWORD_LENGTH) {
        throw validationError(`La contraseña debe tener al menos ${MIN_PASSWORD_LENGTH} caracteres.`);
    }
    if (password !== confirmation) {
        throw validationError('Las contraseñas no coinciden.');
    }
}

async function getMailer() {
    const required = ['SMTP_HOST', 'SMTP_PORT', 'SMTP_USER', 'SMTP_PASSWORD', 'MAIL_FROM', 'PUBLIC_WEB_URL'];
    const missing = required.filter((key) => !process.env[key]);
    if (missing.length > 0) {
        const error = new Error('El servicio de correo no está configurado.');
        error.status = 503;
        throw error;
    }

    const port = Number(process.env.SMTP_PORT);
    const secure = process.env.SMTP_SECURE === 'true';
    if (!Number.isInteger(port) || port < 1 || port > 65535) {
        const error = new Error('La configuración SMTP no tiene un puerto válido.');
        error.status = 503;
        throw error;
    }

    if (process.env.SMTP_HOST === 'smtp.gmail.com' && port === 587 && secure) {
        const error = new Error('Gmail SMTP en el puerto 587 requiere STARTTLS con SMTP_SECURE=false.');
        error.status = 503;
        throw error;
    }

    const transporter = nodemailer.createTransport({
        host: process.env.SMTP_HOST,
        port,
        secure,
        requireTLS: process.env.SMTP_HOST === 'smtp.gmail.com' && port === 587,
        auth: {
            user: process.env.SMTP_USER,
            pass: process.env.SMTP_PASSWORD
        }
    });

    await transporter.verify();

    return {
        transporter,
        from: process.env.MAIL_FROM,
        publicWebUrl: process.env.PUBLIC_WEB_URL.replace(/\/$/, '')
    };
}

async function changeOwnPassword(userId, currentPassword, newPassword, confirmation) {
    if (typeof currentPassword !== 'string' || currentPassword.length === 0) {
        throw validationError('La contraseña actual es obligatoria.');
    }
    validateNewPassword(newPassword, confirmation);
    const client = await db.pool.connect();

    try {
        await client.query('BEGIN');
        const result = await client.query(
            `SELECT password_hash
             FROM users
             WHERE id = $1::uuid
             FOR UPDATE;`,
            [userId]
        );

        if (result.rowCount !== 1) {
            const error = new Error('Usuario no encontrado.');
            error.status = 404;
            throw error;
        }

        const matches = await bcrypt.compare(currentPassword, result.rows[0].password_hash);
        if (!matches) {
            const error = new Error('La contraseña actual no es correcta.');
            error.status = 401;
            throw error;
        }

        const passwordHash = await bcrypt.hash(newPassword, SALT_ROUNDS);
        await client.query(
            'UPDATE users SET password_hash = $1 WHERE id = $2::uuid;',
            [passwordHash, userId]
        );
        await client.query('COMMIT');
    } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        throw error;
    } finally {
        client.release();
    }
}

async function createAdminReset({ actorId, targetId }) {
    if (typeof targetId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(targetId)) {
        throw validationError('El identificador del usuario no es válido.');
    }
    if (actorId === targetId) {
        throw validationError('Utiliza el cambio de contraseña propio para tu cuenta.');
    }
    const mailer = await getMailer();
    const rawToken = crypto.randomBytes(32).toString('hex');
    const tokenHash = crypto.createHash('sha256').update(rawToken).digest('hex');
    const client = await db.pool.connect();

    try {
        await client.query('BEGIN');
        const actor = await client.query(
            `SELECT id
             FROM users
             WHERE id = $1::uuid AND role = 'admin'
             FOR UPDATE;`,
            [actorId]
        );
        if (actor.rowCount !== 1) {
            const error = new Error('Permisos administrativos insuficientes.');
            error.status = 403;
            throw error;
        }

        const target = await client.query(
            `SELECT id, username, email
             FROM users
             WHERE id = $1::uuid
             FOR UPDATE;`,
            [targetId]
        );
        if (target.rowCount !== 1) {
            const error = new Error('Usuario no encontrado.');
            error.status = 404;
            throw error;
        }

        await client.query(
            `UPDATE password_reset_tokens
             SET used_at = CURRENT_TIMESTAMP
             WHERE user_id = $1::uuid AND used_at IS NULL;`,
            [targetId]
        );
        await client.query(
            `INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, created_by)
             VALUES ($1::uuid, $2, CURRENT_TIMESTAMP + ($3 * INTERVAL '1 minute'), $4::uuid);`,
            [targetId, tokenHash, RESET_TOKEN_TTL_MINUTES, actorId]
        );

        const mailInfo = await mailer.transporter.sendMail({
            from: mailer.from,
            to: target.rows[0].email,
            subject: 'Restablecimiento de contraseña de VENUS',
            text: `Solicitaste restablecer tu contraseña de VENUS. Completa el proceso en ${mailer.publicWebUrl}/reset-password#token=${rawToken}. El enlace expira en ${RESET_TOKEN_TTL_MINUTES} minutos y solo puede utilizarse una vez.`
        });
        console.info('Correo de restablecimiento aceptado por SMTP.', {
            messageId: mailInfo.messageId,
            responseCode: mailInfo.responseCode
        });

        const auditTable = await client.query(
            `SELECT to_regclass('public.admin_audit_log') AS table_name;`
        );
        if (auditTable.rows[0].table_name) {
            await client.query(
                `INSERT INTO admin_audit_log
                    (actor_user_id, target_user_id, action, previous_role, new_role)
                 VALUES ($1::uuid, $2::uuid, $3, $4, $5);`,
                [actorId, targetId, 'user.password_reset_requested', 'unchanged', 'unchanged']
            );
        }

        await client.query('COMMIT');
    } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        throw error;
    } finally {
        client.release();
    }
}

async function completeReset(token, newPassword, confirmation) {
    validateNewPassword(newPassword, confirmation);
    if (typeof token !== 'string' || !/^[a-f0-9]{64}$/.test(token)) {
        throw validationError('El enlace de restablecimiento no es válido.');
    }

    const tokenHash = crypto.createHash('sha256').update(token).digest('hex');
    const client = await db.pool.connect();

    try {
        await client.query('BEGIN');
        const result = await client.query(
            `SELECT id, user_id
             FROM password_reset_tokens
             WHERE token_hash = $1
               AND used_at IS NULL
               AND expires_at > CURRENT_TIMESTAMP
             FOR UPDATE;`,
            [tokenHash]
        );
        if (result.rowCount !== 1) {
            const error = new Error('El enlace de restablecimiento es inválido o expiró.');
            error.status = 400;
            throw error;
        }

        const passwordHash = await bcrypt.hash(newPassword, SALT_ROUNDS);
        await client.query(
            'UPDATE users SET password_hash = $1 WHERE id = $2::uuid;',
            [passwordHash, result.rows[0].user_id]
        );
        await client.query(
            'UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP WHERE id = $1;',
            [result.rows[0].id]
        );
        await client.query('COMMIT');
    } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        throw error;
    } finally {
        client.release();
    }
}

module.exports = {
    changeOwnPassword,
    completeReset,
    createAdminReset
};
