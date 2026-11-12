const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const http = require('http');
const { Server } = require('socket.io');
const app = require('./app');
const registerWatchPartyHandlers = require('./handlers/watchPartySocketHandler');

const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST", "PUT", "DELETE"]
    }
});

// Registrar manejadores en tiempo real para Watch Party y Chat
registerWatchPartyHandlers(io);

const PORT = process.env.PORT || 5000;

server.listen(PORT, '0.0.0.0', () => {
    console.log(`Servidor VENUS ejecutándose en 0.0.0.0:${PORT} (accesible en red local)`);
});
