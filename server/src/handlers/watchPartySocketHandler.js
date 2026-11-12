const jwt = require('jsonwebtoken');
const WatchPartyService = require('../services/watchPartyService');
const MediaModel = require('../models/mediaModel');
require('dotenv').config();

// Mapeo en memoria de sockets activos por usuario y sala: Map<userId, Map<roomId, Set<socketId>>>
const activeUserSockets = new Map();

function trackUserSocket(userId, roomId, socketId) {
    if (!activeUserSockets.has(userId)) {
        activeUserSockets.set(userId, new Map());
    }
    const userRooms = activeUserSockets.get(userId);
    if (!userRooms.has(roomId)) {
        userRooms.set(roomId, new Set());
    }
    userRooms.get(roomId).add(socketId);
}

function untrackUserSocket(userId, roomId, socketId) {
    if (!activeUserSockets.has(userId)) return false;
    const userRooms = activeUserSockets.get(userId);
    if (!userRooms.has(roomId)) return false;

    const sockets = userRooms.get(roomId);
    sockets.delete(socketId);

    if (sockets.size === 0) {
        userRooms.delete(roomId);
        if (userRooms.size === 0) {
            activeUserSockets.delete(userId);
        }
        return true; // El usuario ya no tiene sockets activos en esta sala
    }
    return false; // El usuario todavía tiene otros sockets activos en esta sala
}

function registerWatchPartyHandlers(io) {

    // Middleware de autenticación por JWT para Socket.IO
    io.use((socket, next) => {
        let token = socket.handshake.auth?.token || socket.handshake.headers?.authorization;
        if (token && token.startsWith('Bearer ')) {
            token = token.split(' ')[1];
        }
        if (!token && socket.handshake.query && socket.handshake.query.token) {
            token = socket.handshake.query.token;
        }

        if (!token) {
            return next(new Error('Acceso no autorizado. Token JWT no proporcionado.'));
        }

        try {
            const decoded = jwt.verify(token, process.env.JWT_SECRET || 'fallback_secret');
            socket.user = decoded; // { userId, email, username, role }
            next();
        } catch (err) {
            return next(new Error('Token JWT inválido o expirado.'));
        }
    });

    io.on('connection', (socket) => {
        const userId = socket.user.userId;
        const username = socket.user.username;
        const joinedRooms = new Set();

        console.log(`[VENUS WS AUTH] Cliente autenticado conectado -> User: ${username} (${userId}) | Socket: ${socket.id}`);

        socket.on('ping', (cb) => {
            if (typeof cb === 'function') cb({ timestamp: Date.now() });
            else socket.emit('pong', { timestamp: Date.now() });
        });

        // 1. Crear Sala
        socket.on('watch_party_create', async (data, ack) => {
            try {
                const { mediaId, hostOnlyControl = true, passcode = null } = data || {};
                const roomState = await WatchPartyService.createRoom({
                    hostId: userId,
                    mediaId,
                    hostOnlyControl,
                    passcode
                });

                socket.join(roomState.id);
                socket.join(roomState.roomCode);
                joinedRooms.add(roomState.id);
                trackUserSocket(userId, roomState.id, socket.id);

                socket.emit('watch_party_created', { success: true, room: roomState });
                if (typeof ack === 'function') ack({ success: true, room: roomState });
            } catch (err) {
                console.error('[WS ERROR] watch_party_create:', err.message);
                socket.emit('watch_party_error', { error: err.message });
                if (typeof ack === 'function') ack({ success: false, error: err.message });
            }
        });

        // 2. Unirse a Sala
        socket.on('watch_party_join', async (data, ack) => {
            try {
                const { roomId, roomCode, passcode } = data || {};
                const targetIdOrCode = roomId || roomCode;
                if (!targetIdOrCode) {
                    throw new Error('Se requiere un identificador o código de sala válido.');
                }

                const roomState = await WatchPartyService.joinRoom(targetIdOrCode, {
                    userId: userId,
                    passcode
                });

                socket.join(roomState.id);
                socket.join(roomState.roomCode);
                joinedRooms.add(roomState.id);
                trackUserSocket(userId, roomState.id, socket.id);

                socket.emit('watch_party_joined', { success: true, room: roomState });
                socket.to(roomState.id).emit('watch_party_user_joined', {
                    userId: userId,
                    username: username,
                    userCount: roomState.participants.length,
                    room: roomState
                });

                if (typeof ack === 'function') ack({ success: true, room: roomState });
            } catch (err) {
                console.error('[WS ERROR] watch_party_join:', err.message);
                socket.emit('watch_party_error', { error: err.message });
                if (typeof ack === 'function') ack({ success: false, error: err.message });
            }
        });

        // 3. Salir de Sala (Explícito)
        socket.on('watch_party_leave', async (data, ack) => {
            try {
                const { roomId } = data || {};
                if (!roomId) throw new Error('Identificador de sala no proporcionado.');

                socket.leave(roomId);
                joinedRooms.delete(roomId);
                const isLastSocket = untrackUserSocket(userId, roomId, socket.id);

                if (isLastSocket) {
                    const result = await WatchPartyService.leaveRoom(roomId, { userId });
                    if (result && result.roomCode) {
                        socket.to(roomId).emit('watch_party_user_left', {
                            userId: userId,
                            username: username,
                            room: result
                        });
                    }
                }

                socket.emit('watch_party_left', { success: true, roomId });
                if (typeof ack === 'function') ack({ success: true, roomId });
            } catch (err) {
                console.error('[WS ERROR] watch_party_leave:', err.message);
                socket.emit('watch_party_error', { error: err.message });
                if (typeof ack === 'function') ack({ success: false, error: err.message });
            }
        });

        // 4. Solicitar Estado Autoritativo
        socket.on('watch_party_request_state', async (data, ack) => {
            try {
                const { roomId } = data || {};
                if (!roomId) throw new Error('Identificador de sala no proporcionado.');

                const roomState = await WatchPartyService.getRoomState(roomId);
                socket.emit('watch_party_state', { success: true, room: roomState });
                if (typeof ack === 'function') ack({ success: true, room: roomState });
            } catch (err) {
                socket.emit('watch_party_error', { error: err.message });
                if (typeof ack === 'function') ack({ success: false, error: err.message });
            }
        });

        // 5. Sincronizar Reproducción (Play / Pause / Seek)
        socket.on('watch_party_sync', async (data, ack) => {
            try {
                const { roomId, isPlaying, positionMs } = data || {};
                if (!roomId) throw new Error('Identificador de sala no proporcionado.');

                const updatedState = await WatchPartyService.updatePlaybackState(roomId, {
                    isPlaying,
                    positionMs,
                    userId
                });

                io.to(updatedState.id).emit('watch_party_state', {
                    success: true,
                    room: updatedState,
                    triggeredBy: username
                });

                if (typeof ack === 'function') ack({ success: true, room: updatedState });
            } catch (err) {
                console.error('[WS ERROR] watch_party_sync:', err.message);
                socket.emit('watch_party_error', { error: err.message });
                if (typeof ack === 'function') ack({ success: false, error: err.message });
            }
        });

        // 6. Cambiar Película (Solo Anfitrión)
        socket.on('watch_party_change_media', async (data, ack) => {
            try {
                const { roomId, mediaId } = data || {};
                if (!roomId || !mediaId) throw new Error('Identificador de sala y medio son obligatorios.');

                const media = await MediaModel.findById(mediaId);
                if (!media) {
                    throw new Error('El contenido multimedia seleccionado no existe en el servidor.');
                }

                const updatedState = await WatchPartyService.changeMedia(roomId, {
                    mediaId,
                    userId
                });

                io.to(updatedState.id).emit('watch_party_media_changed', {
                    success: true,
                    room: updatedState,
                    media: media
                });

                if (typeof ack === 'function') ack({ success: true, room: updatedState });
            } catch (err) {
                console.error('[WS ERROR] watch_party_change_media:', err.message);
                socket.emit('watch_party_error', { error: err.message });
                if (typeof ack === 'function') ack({ success: false, error: err.message });
            }
        });

        // 7. Conceder Permiso de Control
        socket.on('watch_party_grant_control', async (data, ack) => {
            try {
                const { roomId, targetUserId } = data || {};
                const updatedState = await WatchPartyService.setParticipantControl(roomId, {
                    targetUserId,
                    allowedControl: true,
                    requestingUserId: userId
                });

                io.to(updatedState.id).emit('watch_party_control_updated', {
                    success: true,
                    targetUserId,
                    allowedControl: true,
                    room: updatedState
                });

                if (typeof ack === 'function') ack({ success: true, room: updatedState });
            } catch (err) {
                socket.emit('watch_party_error', { error: err.message });
                if (typeof ack === 'function') ack({ success: false, error: err.message });
            }
        });

        // 8. Revocar Permiso de Control
        socket.on('watch_party_revoke_control', async (data, ack) => {
            try {
                const { roomId, targetUserId } = data || {};
                const updatedState = await WatchPartyService.setParticipantControl(roomId, {
                    targetUserId,
                    allowedControl: false,
                    requestingUserId: userId
                });

                io.to(updatedState.id).emit('watch_party_control_updated', {
                    success: true,
                    targetUserId,
                    allowedControl: false,
                    room: updatedState
                });

                if (typeof ack === 'function') ack({ success: true, room: updatedState });
            } catch (err) {
                socket.emit('watch_party_error', { error: err.message });
                if (typeof ack === 'function') ack({ success: false, error: err.message });
            }
        });

        // 9. Enviar Mensaje de Chat
        socket.on('watch_party_chat_send', async (data, ack) => {
            try {
                const { roomId, message } = data || {};
                if (!roomId) throw new Error('Identificador de sala no proporcionado.');

                const chatMsg = await WatchPartyService.sendChatMessage(roomId, {
                    userId,
                    message
                });

                io.to(chatMsg.roomId).emit('watch_party_chat_message', chatMsg);
                if (typeof ack === 'function') ack({ success: true, message: chatMsg });
            } catch (err) {
                console.error('[WS ERROR] watch_party_chat_send:', err.message);
                socket.emit('watch_party_chat_error', { error: err.message });
                if (typeof ack === 'function') ack({ success: false, error: err.message });
            }
        });

        // 10. Consultar Historial de Chat
        socket.on('watch_party_chat_history', async (data, ack) => {
            try {
                const { roomId } = data || {};
                if (!roomId) throw new Error('Identificador de sala no proporcionado.');

                const history = await WatchPartyService.getChatHistory(roomId, userId);
                socket.emit('watch_party_chat_history', { roomId, messages: history });
                if (typeof ack === 'function') ack({ success: true, messages: history });
            } catch (err) {
                console.error('[WS ERROR] watch_party_chat_history:', err.message);
                socket.emit('watch_party_chat_error', { error: err.message });
                if (typeof ack === 'function') ack({ success: false, error: err.message });
            }
        });

        // 11. Desconexión Inesperada
        socket.on('disconnect', async () => {
            console.log(`[VENUS WS DISCONNECT] Socket desconectado -> User: ${username} | Socket: ${socket.id}`);
            for (const roomId of joinedRooms) {
                const isLastSocket = untrackUserSocket(userId, roomId, socket.id);
                if (isLastSocket) {
                    try {
                        const result = await WatchPartyService.leaveRoom(roomId, { userId });
                        if (result && result.roomCode) {
                            socket.to(roomId).emit('watch_party_user_left', {
                                userId: userId,
                                username: username,
                                room: result
                            });
                        }
                    } catch (e) {
                        console.error('[WS DISCONNECT ERROR]', e.message);
                    }
                }
            }
            joinedRooms.clear();
        });
    });
}

module.exports = registerWatchPartyHandlers;
