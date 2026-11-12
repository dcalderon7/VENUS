const { Pool } = require('pg');
const path = require('path');

const isTestMode = process.env.NODE_ENV === 'test' || process.env.VENUS_TEST_MODE === 'true';
const envFile = isTestMode ? '../../.env.test' : '../../.env';

// Las pruebas deben cargar una configuración separada de la base real.
require('dotenv').config({ path: path.join(__dirname, envFile) });

// Validación de variables de entorno requeridas (sin imprimir contraseñas)
const requiredEnv = ['DB_HOST', 'DB_PORT', 'DB_USER', 'DB_PASSWORD', 'DB_NAME', 'JWT_SECRET'];
const missingEnv = requiredEnv.filter(envKey => !process.env[envKey]);

if (missingEnv.length > 0) {
    console.error(`[ERROR CRÍTICO] Faltan las siguientes variables de entorno en el archivo .env de la carpeta server: ${missingEnv.join(', ')}`);
    process.exit(1);
}

if (isTestMode && !/_test$/i.test(process.env.DB_NAME)) {
    console.error('[ERROR CRÍTICO] El modo de pruebas solo puede usar una base cuyo nombre termine en "_test".');
    process.exit(1);
}

const pool = new Pool({
    host: process.env.DB_HOST,
    port: parseInt(process.env.DB_PORT, 10),
    user: process.env.DB_USER,
    password: String(process.env.DB_PASSWORD),
    database: process.env.DB_NAME,
});

pool.on('connect', () => {
    console.log('Conectado a la base de datos PostgreSQL exitosamente.');
});

pool.on('error', (err) => {
    console.error('Error inesperado en el cliente de PostgreSQL', err);
    process.exit(-1);
});

module.exports = {
    query: (text, params) => pool.query(text, params),
    pool,
};
