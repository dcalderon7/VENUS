const db = require('../src/config/db');

const prefixes = [
    'catalog_',
    'browser_guest_',
    'browser_host_',
    'protocol_guest_',
    'protocol_host_',
    'afterrestart_',
    'eventdiag_',
    'pingdiag_',
    'diag_guest_',
    'diag_host_',
];

const selectPredicate = `
    email LIKE '%@test.invalid'
    AND username LIKE ANY($1::text[])
`;

const deletePredicate = `
    email LIKE '%@test.invalid'
    AND username LIKE ANY($2::text[])
`;

async function main() {
    if (process.argv[2] !== '--confirm') {
        throw new Error('Uso: node scripts/cleanup-watch-party-test-users.js --confirm');
    }

    const client = await db.pool.connect();
    try {
        await client.query('BEGIN');
        const patterns = prefixes.map((prefix) => `${prefix}%`);
        const matching = await client.query(
            `SELECT id FROM users WHERE ${selectPredicate} FOR UPDATE;`,
            [patterns],
        );
        const ids = matching.rows.map(({ id }) => id);

        const params = [ids];
        const watchlist = await client.query(
            'DELETE FROM watchlist WHERE user_id = ANY($1::uuid[]);',
            params,
        );
        const history = await client.query(
            'DELETE FROM watch_history WHERE user_id = ANY($1::uuid[]);',
            params,
        );
        const media = await client.query(
            'UPDATE media_files SET uploaded_by = NULL WHERE uploaded_by = ANY($1::uuid[]);',
            params,
        );
        const users = await client.query(
            `DELETE FROM users WHERE id = ANY($1::uuid[]) AND ${deletePredicate};`,
            [ids, patterns],
        );

        if (users.rowCount !== ids.length) {
            throw new Error(`Se esperaban ${ids.length} cuentas y se eliminaron ${users.rowCount}.`);
        }

        await client.query('COMMIT');
        console.log(JSON.stringify({
            database: process.env.DB_NAME,
            deleted: {
                users: users.rowCount,
                watchlist: watchlist.rowCount,
                history: history.rowCount,
            },
            detachedMedia: media.rowCount,
        }, null, 2));
    } catch (error) {
        await client.query('ROLLBACK');
        throw error;
    } finally {
        client.release();
        await db.pool.end();
    }
}

main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
});
