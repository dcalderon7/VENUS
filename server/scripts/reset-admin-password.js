const bcrypt = require('bcrypt');
const readline = require('readline');
const db = require('../src/config/db');

const ACCOUNT_ID = '19320fa0-c125-40af-b5e6-0a78b7bb86a6';
const EXPECTED_USERNAME = 'dcalderon';
const EXPECTED_ROLE = 'admin';
const SALT_ROUNDS = 10;

function askHidden(question) {
    return new Promise((resolve, reject) => {
        const input = process.stdin;
        const output = process.stdout;
        let value = '';

        if (!input.isTTY || !output.isTTY || typeof input.setRawMode !== 'function') {
            reject(new Error('La contraseña debe introducirse desde una terminal interactiva.'));
            return;
        }

        const cleanup = () => {
            input.removeListener('keypress', onKeypress);
            input.setRawMode(false);
            input.pause();
            output.write('\n');
        };

        const onKeypress = (character, key) => {
            if (key && key.ctrl && key.name === 'c') {
                cleanup();
                reject(new Error('Operación cancelada.'));
                return;
            }

            if (key && (key.name === 'return' || key.name === 'enter')) {
                cleanup();
                resolve(value);
                return;
            }

            if (key && key.name === 'backspace') {
                value = value.slice(0, -1);
                return;
            }

            if (character && (!key || (!key.ctrl && !key.meta))) {
                value += character;
            }
        };

        readline.emitKeypressEvents(input);
        output.write(question);
        input.setRawMode(true);
        input.resume();
        input.on('keypress', onKeypress);
    });
}

async function resetPassword(password, confirmation) {
    if (password.length < 6) {
        throw new Error('La contraseña debe tener al menos 6 caracteres.');
    }

    if (password !== confirmation) {
        throw new Error('Las contraseñas no coinciden.');
    }

    const client = await db.pool.connect();

    try {
        await client.query('BEGIN');

        const beforeResult = await client.query(
            `SELECT id, username, email, role
             FROM users
             WHERE id = $1::uuid
             FOR UPDATE`,
            [ACCOUNT_ID],
        );

        if (beforeResult.rowCount !== 1) {
            throw new Error('La cuenta objetivo no existe o no es única.');
        }

        const accountBefore = beforeResult.rows[0];
        if (accountBefore.username !== EXPECTED_USERNAME || accountBefore.role !== EXPECTED_ROLE) {
            throw new Error('El UUID no corresponde a la cuenta administradora esperada.');
        }

        const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
        const updateResult = await client.query(
            `UPDATE users
             SET password_hash = $1
             WHERE id = $2::uuid`,
            [passwordHash, ACCOUNT_ID],
        );

        if (updateResult.rowCount !== 1) {
            throw new Error('La actualización no afectó exactamente una cuenta.');
        }

        const afterResult = await client.query(
            `SELECT id, username, email, role
             FROM users
             WHERE id = $1::uuid`,
            [ACCOUNT_ID],
        );

        if (afterResult.rowCount !== 1) {
            throw new Error('La verificación posterior no encontró exactamente una cuenta.');
        }

        const accountAfter = afterResult.rows[0];
        const identityUnchanged = ['id', 'username', 'email', 'role']
            .every((field) => accountAfter[field] === accountBefore[field]);

        if (!identityUnchanged) {
            throw new Error('La identidad de la cuenta cambió inesperadamente.');
        }

        await client.query('COMMIT');
        console.log('Contraseña actualizada correctamente para la cuenta autorizada.');
    } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        throw error;
    } finally {
        client.release();
    }
}

async function main() {
    const password = await askHidden('Nueva contraseña (entrada oculta): ');
    const confirmation = await askHidden('Confirma la nueva contraseña (entrada oculta): ');
    await resetPassword(password, confirmation);
}

main()
    .catch(() => {
        console.error('No se pudo completar el restablecimiento; no se aplicaron cambios.');
        process.exitCode = 1;
    })
    .finally(() => db.pool.end());
