const WatchPartyModel = require('../models/watchPartyModel');
const bcrypt = require('bcrypt');
const crypto = require('crypto');

const WatchPartyService = {

    generateRoomCode() {
        const characters = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // Evita caracteres ambiguos (0, O, 1, I)
        let code = 'VP-';
        for (let i = 0; i < 4; i++) {
            code += characters.charAt(Math.floor(Math.random() * characters.length));
        }
        return code;
    },

    isUuid(val) {
        return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(String(val || '').trim());
    },

    async createRoom({ hostId, mediaId, hostOnlyControl = true, passcode = null }) {
        if (!hostId) {
            throw new Error('El identificador del anfitrión (hostId) es obligatorio.');
        }

        const db = require('../config/db');
        const userCheck = await db.query('SELECT id FROM users WHERE id = $1', [hostId]);
        if (!userCheck.rows[0]) {
            throw new Error('El usuario anfitrión no existe en la base de datos. Por favor inicia sesión nuevamente.');
        }

        let roomCode = this.generateRoomCode();
        let attempts = 0;
        while (attempts < 10) {
            const existing = await WatchPartyModel.findRoomByCode(roomCode);
            if (!existing) break;
            roomCode = this.generateRoomCode();
            attempts++;
        }

        let passcodeHash = null;
        if (passcode && String(passcode).trim().length > 0) {
            passcodeHash = bcrypt.hashSync(String(passcode).trim(), 10);
        }

        const room = await WatchPartyModel.createRoom({
            roomCode,
            hostId,
            mediaId,
            hostOnlyControl,
            passcodeHash
        });

        // Registrar al anfitrión como participante
        await WatchPartyModel.addOrUpdateParticipant({
            roomId: room.id,
            userId: hostId,
            role: 'host',
            allowedControl: true
        });

        return await this.getRoomState(room.id);
    },

    async getRoomState(roomIdOrCode) {
        let room = null;
        const valStr = String(roomIdOrCode || '').trim();

        if (this.isUuid(valStr)) {
            room = await WatchPartyModel.findRoomById(valStr);
        }
        if (!room) {
            room = await WatchPartyModel.findRoomByCode(valStr);
        }

        if (!room) {
            throw new Error(`La sala Watch Party '${roomIdOrCode}' no fue encontrada.`);
        }

        if (room.status === 'closed') {
            throw new Error(`La sala '${room.room_code}' ha sido cerrada.`);
        }

        const participants = await WatchPartyModel.getParticipants(room.id);
        const now = Date.now();

        let basePosition = parseInt(room.current_position_ms, 10) || 0;
        let authoritativePositionMs = basePosition;

        if (room.is_playing) {
            const lastUpdated = parseInt(room.last_updated_timestamp, 10) || now;
            const elapsed = Math.max(0, now - lastUpdated);
            authoritativePositionMs = basePosition + elapsed;
        }

        authoritativePositionMs = Math.max(0, authoritativePositionMs);

        return {
            id: room.id,
            roomCode: room.room_code,
            hostId: room.host_id,
            hostName: room.host_name || 'Anfitrión',
            mediaId: room.media_id,
            mediaTitle: room.display_title || room.file_name || null,
            isPlaying: Boolean(room.is_playing),
            currentPositionMs: basePosition,
            lastUpdatedTimestamp: parseInt(room.last_updated_timestamp, 10) || now,
            authoritativePositionMs: authoritativePositionMs,
            hostOnlyControl: Boolean(room.host_only_control),
            hasPasscode: Boolean(room.passcode_hash),
            status: room.status,
            createdAt: room.created_at,
            updatedAt: room.updated_at,
            serverTime: now,
            participants: participants.map(p => ({
                userId: p.user_id,
                name: p.name,
                username: p.username,
                avatar: p.avatar,
                role: p.role,
                allowedControl: Boolean(p.allowed_control),
                joinedAt: p.joined_at,
                lastSeenAt: p.last_seen_at
            }))
        };
    },

    async updatePlaybackState(roomIdOrCode, { isPlaying, positionMs, userId }) {
        const state = await this.getRoomState(roomIdOrCode);
        await this.validateControlPermission(state, userId);

        const newPosition = Math.max(0, parseInt(positionMs, 10) || 0);
        const now = Date.now();

        await WatchPartyModel.updatePlaybackState(state.id, {
            isPlaying: Boolean(isPlaying),
            currentPositionMs: newPosition,
            lastUpdatedTimestamp: now
        });

        return await this.getRoomState(state.id);
    },

    async updatePosition(roomIdOrCode, { positionMs, userId }) {
        const state = await this.getRoomState(roomIdOrCode);
        await this.validateControlPermission(state, userId);

        const newPosition = Math.max(0, parseInt(positionMs, 10) || 0);
        const now = Date.now();

        await WatchPartyModel.updatePlaybackState(state.id, {
            isPlaying: state.isPlaying,
            currentPositionMs: newPosition,
            lastUpdatedTimestamp: now
        });

        return await this.getRoomState(state.id);
    },

    async changeMedia(roomIdOrCode, { mediaId, userId }) {
        const state = await this.getRoomState(roomIdOrCode);
        if (state.hostId !== userId) {
            throw new Error('Solo el anfitrión de la sala puede cambiar la película seleccionada.');
        }

        await WatchPartyModel.updateMedia(state.id, mediaId);
        return await this.getRoomState(state.id);
    },

    async updateHost(roomIdOrCode, { newHostId, requestingUserId }) {
        const state = await this.getRoomState(roomIdOrCode);
        if (state.hostId !== requestingUserId) {
            throw new Error('Solo el anfitrión actual puede transferir el rol de anfitrión.');
        }

        await WatchPartyModel.updateHost(state.id, newHostId);

        // Actualizar roles de participantes
        await WatchPartyModel.addOrUpdateParticipant({
            roomId: state.id,
            userId: state.hostId,
            role: 'guest',
            allowedControl: false
        });
        await WatchPartyModel.addOrUpdateParticipant({
            roomId: state.id,
            userId: newHostId,
            role: 'host',
            allowedControl: true
        });

        return await this.getRoomState(state.id);
    },

    async setParticipantControl(roomIdOrCode, { targetUserId, allowedControl, requestingUserId }) {
        const state = await this.getRoomState(roomIdOrCode);
        if (state.hostId !== requestingUserId) {
            throw new Error('Solo el anfitrión puede administrar los permisos de control.');
        }

        const target = state.participants.find(p => p.userId === targetUserId);
        if (!target) {
            throw new Error('El usuario no es un participante activo en esta sala.');
        }

        await WatchPartyModel.addOrUpdateParticipant({
            roomId: state.id,
            userId: targetUserId,
            role: target.role,
            allowedControl: Boolean(allowedControl)
        });

        return await this.getRoomState(state.id);
    },

    async joinRoom(roomIdOrCode, { userId, passcode = null }) {
        const valStr = String(roomIdOrCode || '').trim();
        const room = this.isUuid(valStr) ? await WatchPartyModel.findRoomById(valStr) : await WatchPartyModel.findRoomByCode(valStr);
        if (!room) {
            throw new Error(`La sala '${roomIdOrCode}' no existe.`);
        }

        if (room.status === 'closed') {
            throw new Error(`La sala '${room.room_code}' ha sido cerrada.`);
        }

        if (room.passcode_hash) {
            if (!passcode || !bcrypt.compareSync(String(passcode).trim(), room.passcode_hash)) {
                throw new Error('Contraseña de sala incorrecta.');
            }
        }

        const isHost = room.host_id === userId;
        await WatchPartyModel.addOrUpdateParticipant({
            roomId: room.id,
            userId: userId,
            role: isHost ? 'host' : 'guest',
            allowedControl: isHost || !room.host_only_control
        });

        return await this.getRoomState(room.id);
    },

    async leaveRoom(roomIdOrCode, { userId }) {
        const state = await this.getRoomState(roomIdOrCode);

        await WatchPartyModel.removeParticipant(state.id, userId);
        const updatedParticipants = await WatchPartyModel.getParticipants(state.id);

        if (updatedParticipants.length === 0) {
            await WatchPartyModel.updateStatus(state.id, 'closed');
            return { message: 'Sala cerrada por falta de participantes.', roomId: state.id };
        }

        if (state.hostId === userId) {
            // Promover al participante más antiguo como nuevo anfitrión
            const newHost = updatedParticipants[0];
            await WatchPartyModel.updateHost(state.id, newHost.user_id);
            await WatchPartyModel.addOrUpdateParticipant({
                roomId: state.id,
                userId: newHost.user_id,
                role: 'host',
                allowedControl: true
            });
        }

        return await this.getRoomState(state.id);
    },

    async validateControlPermission(state, userId) {
        if (!userId) {
            throw new Error('Usuario no identificado.');
        }

        if (state.hostId === userId) {
            return true;
        }

        const participant = state.participants.find(p => p.userId === userId);
        if (!participant) {
            throw new Error('El usuario no pertenece a esta sala.');
        }

        if (participant.allowedControl) {
            return true;
        }

        if (state.hostOnlyControl) {
            throw new Error('Solo el anfitrión tiene permiso de control sobre esta sala.');
        }

        return false;
    },

    // In-memory rate limiting tracker: userId -> array of timestamps
    userMessageTimestamps: new Map(),

    async sendChatMessage(roomIdOrCode, { userId, message }) {
        if (!message || typeof message !== 'string' || message.trim().length === 0) {
            throw new Error('El mensaje no puede estar vacío.');
        }

        const trimmedMessage = message.trim();
        if (trimmedMessage.length > 500) {
            throw new Error('El mensaje excede el límite máximo de 500 caracteres.');
        }

        const state = await this.getRoomState(roomIdOrCode);
        const participant = state.participants.find(p => p.userId === userId);
        if (!participant) {
            throw new Error('Solo los participantes de la sala pueden enviar mensajes de chat.');
        }

        const now = Date.now();
        const timestamps = this.userMessageTimestamps.get(userId) || [];
        const recentTimestamps = timestamps.filter(t => now - t < 3000);
        if (recentTimestamps.length >= 5) {
            throw new Error('Has alcanzado el límite de frecuencia de chat. Por favor espera unos segundos.');
        }
        recentTimestamps.push(now);
        this.userMessageTimestamps.set(userId, recentTimestamps);

        return await WatchPartyModel.saveChatMessage({
            roomId: state.id,
            userId: userId,
            message: trimmedMessage
        });
    },

    async getChatHistory(roomIdOrCode, userId) {
        const state = await this.getRoomState(roomIdOrCode);
        const participant = state.participants.find(p => p.userId === userId);
        if (!participant) {
            throw new Error('Solo los participantes de la sala pueden consultar el historial de chat.');
        }

        return await WatchPartyModel.getRecentChatMessages(state.id, 50);
    }
};

module.exports = WatchPartyService;
