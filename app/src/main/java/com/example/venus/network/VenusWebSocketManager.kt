package com.example.venus.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class WebSocketState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class WatchPartyParticipant(
    val userId: String,
    val name: String,
    val username: String,
    val avatar: String? = null,
    val role: String = "guest",
    val allowedControl: Boolean = false
)

data class WatchPartyRoomState(
    val id: String,
    val roomCode: String,
    val hostId: String,
    val hostName: String = "Anfitrión",
    val mediaId: String? = null,
    val mediaTitle: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val lastUpdatedTimestamp: Long = 0L,
    val authoritativePositionMs: Long = 0L,
    val hostOnlyControl: Boolean = true,
    val hasPasscode: Boolean = false,
    val status: String = "active",
    val serverTime: Long = System.currentTimeMillis(),
    val participants: List<WatchPartyParticipant> = emptyList()
)

data class WatchPartyChatMessage(
    val id: Long,
    val roomId: String,
    val userId: String,
    val name: String,
    val username: String,
    val avatar: String? = null,
    val message: String,
    val createdAt: String = "",
    val isHost: Boolean = false
)

sealed class WatchPartyRoomEvent {
    data class Created(val room: WatchPartyRoomState) : WatchPartyRoomEvent()
    data class Joined(val room: WatchPartyRoomState) : WatchPartyRoomEvent()
    data class UserJoined(val username: String, val room: WatchPartyRoomState) : WatchPartyRoomEvent()
    data class UserLeft(val username: String, val room: WatchPartyRoomState) : WatchPartyRoomEvent()
    data class StateUpdated(val room: WatchPartyRoomState, val triggeredBy: String? = null) : WatchPartyRoomEvent()
    data class MediaChanged(val mediaId: String, val mediaTitle: String?, val room: WatchPartyRoomState) : WatchPartyRoomEvent()
    data class ControlUpdated(val targetUserId: String, val allowedControl: Boolean, val room: WatchPartyRoomState) : WatchPartyRoomEvent()
    data class Error(val message: String) : WatchPartyRoomEvent()
}

class VenusWebSocketManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    private var socket: Socket? = null

    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    private val _currentRoomState = MutableStateFlow<WatchPartyRoomState?>(null)
    val currentRoomState: StateFlow<WatchPartyRoomState?> = _currentRoomState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<WatchPartyRoomEvent>(replay = 1, extraBufferCapacity = 64)
    val eventFlow: SharedFlow<WatchPartyRoomEvent> = _eventFlow.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    private val _chatMessageFlow = MutableSharedFlow<WatchPartyChatMessage>(replay = 1, extraBufferCapacity = 64)
    val chatMessageFlow: SharedFlow<WatchPartyChatMessage> = _chatMessageFlow.asSharedFlow()

    private val _chatHistoryFlow = MutableStateFlow<List<WatchPartyChatMessage>>(emptyList())
    val chatHistoryFlow: StateFlow<List<WatchPartyChatMessage>> = _chatHistoryFlow.asStateFlow()

    private val pendingActions = mutableListOf<() -> Unit>()

    private fun flushPendingActions() {
        synchronized(pendingActions) {
            val actions = ArrayList(pendingActions)
            pendingActions.clear()
            actions.forEach { it.invoke() }
        }
    }

    companion object {
        private const val TAG = "VenusWebSocket"

        @Volatile
        private var INSTANCE: VenusWebSocketManager? = null

        fun getInstance(context: Context): VenusWebSocketManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VenusWebSocketManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun getWebSocketUrl(): String {
        val httpUrl = ApiClient.getBaseUrl(appContext)
        return httpUrl.replace(Regex("/api/?$"), "/")
    }

    @Synchronized
    fun connect(overrideToken: String? = null) {
        if (_connectionState.value == WebSocketState.CONNECTED || _connectionState.value == WebSocketState.CONNECTING) {
            return
        }

        val prefs = appContext.getSharedPreferences("venus_secure_prefs", Context.MODE_PRIVATE)
        val token = overrideToken ?: prefs.getString("jwt_token", null)?.ifBlank { null }

        if (token.isNullOrBlank()) {
            Log.w(TAG, "No se inició conexión Socket.IO: Sesión no iniciada (jwt_token no disponible).")
            _connectionState.value = WebSocketState.DISCONNECTED
            return
        }

        _connectionState.value = WebSocketState.CONNECTING

        try {
            val options = IO.Options().apply {
                auth = mapOf("token" to token)
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 2000
                timeout = 15000
                transports = arrayOf("websocket", "polling")
            }

            val serverUrl = getWebSocketUrl()
            Log.d(TAG, "Conectando Socket.IO cliente a: $serverUrl")

            socket = IO.socket(serverUrl, options).apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "Socket.IO conectado exitosamente")
                    _connectionState.value = WebSocketState.CONNECTED
                    flushPendingActions()
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val err = if (args.isNotEmpty() && args[0] != null) args[0].toString() else "Error de conexión"
                    Log.e(TAG, "Socket.IO error de conexión: $err")
                    _connectionState.value = WebSocketState.DISCONNECTED
                    _errorFlow.tryEmit("Error de conexión al servidor: $err")
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.d(TAG, "Socket.IO desconectado")
                    _connectionState.value = WebSocketState.DISCONNECTED
                }

                // Eventos de Watch Party del servidor
                on("watch_party_created") { args -> parseAndEmitRoomState(args, isCreate = true) }
                on("watch_party_joined") { args -> parseAndEmitRoomState(args, isJoin = true) }
                on("watch_party_state") { args -> parseAndEmitRoomState(args) }
                on("watch_party_media_changed") { args -> parseAndEmitMediaChanged(args) }
                on("watch_party_control_updated") { args -> parseAndEmitRoomState(args) }

                on("watch_party_user_joined") { args ->
                    if (args.isNotEmpty()) {
                        val json = parseJsonObject(args[0])
                        val username = json.optString("username", "Usuario")
                        val roomJson = json.optJSONObject("room")
                        if (roomJson != null) {
                            val state = parseRoomStateFromJson(roomJson)
                            _currentRoomState.value = state
                            _eventFlow.tryEmit(WatchPartyRoomEvent.UserJoined(username, state))
                        }
                    }
                }

                on("watch_party_user_left") { args ->
                    if (args.isNotEmpty()) {
                        val json = parseJsonObject(args[0])
                        val username = json.optString("username", "Usuario")
                        val roomJson = json.optJSONObject("room")
                        if (roomJson != null) {
                            val state = parseRoomStateFromJson(roomJson)
                            _currentRoomState.value = state
                            _eventFlow.tryEmit(WatchPartyRoomEvent.UserLeft(username, state))
                        }
                    }
                }

                on("watch_party_chat_message") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val json = parseJsonObject(args[0])
                            val msg = parseChatMessageFromJson(json)
                            _chatMessageFlow.tryEmit(msg)
                            val currentList = _chatHistoryFlow.value.toMutableList()
                            if (currentList.none { it.id == msg.id }) {
                                currentList.add(msg)
                                _chatHistoryFlow.value = currentList
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parseando chat message", e)
                        }
                    }
                }

                on("watch_party_chat_history") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val json = parseJsonObject(args[0])
                            val msgsArray = json.optJSONArray("messages")
                            val list = mutableListOf<WatchPartyChatMessage>()
                            if (msgsArray != null) {
                                for (i in 0 until msgsArray.length()) {
                                    list.add(parseChatMessageFromJson(msgsArray.getJSONObject(i)))
                                }
                            }
                            _chatHistoryFlow.value = list
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parseando chat history", e)
                        }
                    }
                }

                on("watch_party_chat_error") { args ->
                    if (args.isNotEmpty()) {
                        val json = parseJsonObject(args[0])
                        val errMsg = json.optString("error", "Error en chat")
                        _errorFlow.tryEmit(errMsg)
                    }
                }

                on("watch_party_error") { args ->
                    if (args.isNotEmpty()) {
                        val json = parseJsonObject(args[0])
                        val errMsg = json.optString("error", "Error en servidor Watch Party")
                        _errorFlow.tryEmit(errMsg)
                        _eventFlow.tryEmit(WatchPartyRoomEvent.Error(errMsg))
                    }
                }

                connect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al inicializar Socket.IO", e)
            _connectionState.value = WebSocketState.DISCONNECTED
        }
    }

    private fun parseJsonObject(arg: Any): JSONObject {
        return if (arg is JSONObject) arg else JSONObject(arg.toString())
    }

    private fun parseChatMessageFromJson(json: JSONObject): WatchPartyChatMessage {
        return WatchPartyChatMessage(
            id = json.optLong("id", System.currentTimeMillis()),
            roomId = json.optString("roomId"),
            userId = json.optString("userId"),
            name = json.optString("name", "Usuario"),
            username = json.optString("username", "usuario"),
            avatar = if (json.isNull("avatar")) null else json.optString("avatar"),
            message = json.optString("message"),
            createdAt = json.optString("createdAt", ""),
            isHost = json.optBoolean("isHost", false)
        )
    }

    private fun parseRoomStateFromJson(json: JSONObject): WatchPartyRoomState {
        val participantsList = mutableListOf<WatchPartyParticipant>()
        val participantsArray = json.optJSONArray("participants")
        if (participantsArray != null) {
            for (i in 0 until participantsArray.length()) {
                val pObj = participantsArray.getJSONObject(i)
                participantsList.add(
                    WatchPartyParticipant(
                        userId = pObj.optString("userId"),
                        name = pObj.optString("name", "Usuario"),
                        username = pObj.optString("username", "usuario"),
                        avatar = if (pObj.isNull("avatar")) null else pObj.optString("avatar"),
                        role = pObj.optString("role", "guest"),
                        allowedControl = pObj.optBoolean("allowedControl", false)
                    )
                )
            }
        }

        val resolvedCode = when {
            json.has("roomCode") && json.optString("roomCode").isNotBlank() -> json.optString("roomCode")
            json.has("room_code") && json.optString("room_code").isNotBlank() -> json.optString("room_code")
            else -> ""
        }

        val resolvedId = when {
            json.has("id") && json.optString("id").isNotBlank() -> json.optString("id")
            json.has("roomId") && json.optString("roomId").isNotBlank() -> json.optString("roomId")
            json.has("room_id") && json.optString("room_id").isNotBlank() -> json.optString("room_id")
            else -> ""
        }

        val resolvedHostId = when {
            json.has("hostId") && json.optString("hostId").isNotBlank() -> json.optString("hostId")
            json.has("host_id") && json.optString("host_id").isNotBlank() -> json.optString("host_id")
            else -> ""
        }

        return WatchPartyRoomState(
            id = resolvedId,
            roomCode = resolvedCode,
            hostId = resolvedHostId,
            hostName = json.optString("hostName", json.optString("host_name", "Anfitrión")),
            mediaId = if (json.isNull("mediaId") && json.isNull("media_id")) null else json.optString("mediaId", json.optString("media_id")),
            mediaTitle = if (json.isNull("mediaTitle") && json.isNull("display_title")) null else json.optString("mediaTitle", json.optString("display_title")),
            isPlaying = json.optBoolean("isPlaying", json.optBoolean("is_playing", false)),
            currentPositionMs = json.optLong("currentPositionMs", json.optLong("current_position_ms", 0L)),
            lastUpdatedTimestamp = json.optLong("lastUpdatedTimestamp", json.optLong("last_updated_timestamp", 0L)),
            authoritativePositionMs = json.optLong("authoritativePositionMs", json.optLong("currentPositionMs", json.optLong("current_position_ms", 0L))),
            hostOnlyControl = json.optBoolean("hostOnlyControl", json.optBoolean("host_only_control", true)),
            hasPasscode = json.optBoolean("hasPasscode", false),
            status = json.optString("status", "active"),
            serverTime = json.optLong("serverTime", System.currentTimeMillis()),
            participants = participantsList
        )
    }

    private fun parseAndEmitRoomState(args: Array<Any>, isCreate: Boolean = false, isJoin: Boolean = false) {
        if (args.isEmpty()) return
        try {
            val json = parseJsonObject(args[0])
            val roomJson = json.optJSONObject("room") ?: json
            val roomState = parseRoomStateFromJson(roomJson)
            val triggeredBy = if (json.isNull("triggeredBy")) null else json.optString("triggeredBy")

            _currentRoomState.value = roomState

            val event = when {
                isCreate -> WatchPartyRoomEvent.Created(roomState)
                isJoin -> WatchPartyRoomEvent.Joined(roomState)
                else -> WatchPartyRoomEvent.StateUpdated(roomState, triggeredBy)
            }
            _eventFlow.tryEmit(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear estado de sala", e)
        }
    }

    private fun parseAndEmitMediaChanged(args: Array<Any>) {
        if (args.isEmpty()) return
        try {
            val json = parseJsonObject(args[0])
            val roomJson = json.optJSONObject("room") ?: json
            val roomState = parseRoomStateFromJson(roomJson)

            _currentRoomState.value = roomState
            _eventFlow.tryEmit(WatchPartyRoomEvent.MediaChanged(roomState.mediaId ?: "", roomState.mediaTitle, roomState))
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando media changed", e)
        }
    }

    @Synchronized
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionState.value = WebSocketState.DISCONNECTED
        _currentRoomState.value = null
        _chatHistoryFlow.value = emptyList()
    }

    fun createRoom(
        mediaId: String? = null,
        hostOnlyControl: Boolean = true,
        passcode: String? = null,
        onSuccess: ((WatchPartyRoomState) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val prefs = appContext.getSharedPreferences("venus_secure_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("jwt_token", null)?.ifBlank { null }
        if (token.isNullOrBlank()) {
            onError?.invoke("Sesión no iniciada. Por favor inicia sesión para crear una sala de Watch Party.")
            return
        }

        val action: () -> Unit = {
            val payload = JSONObject().apply {
                if (!mediaId.isNullOrBlank()) put("mediaId", mediaId)
                put("hostOnlyControl", hostOnlyControl)
                if (!passcode.isNullOrBlank()) put("passcode", passcode)
            }
            Log.d(TAG, "Emitiendo watch_party_create -> mediaId: $mediaId")

            val sock = socket
            if (sock != null && sock.connected()) {
                sock.emit("watch_party_create", payload, io.socket.client.Ack { args ->
                    if (args.isNotEmpty() && args[0] != null) {
                        try {
                            val json = parseJsonObject(args[0])
                            if (json.optBoolean("success", false)) {
                                val roomJson = json.optJSONObject("room") ?: json
                                val roomState = parseRoomStateFromJson(roomJson)
                                _currentRoomState.value = roomState
                                _eventFlow.tryEmit(WatchPartyRoomEvent.Created(roomState))
                                onSuccess?.invoke(roomState)
                            } else {
                                val err = json.optString("error", "Error al crear sala")
                                onError?.invoke(err)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parseando ack de createRoom", e)
                            onError?.invoke("Error al procesar la respuesta del servidor.")
                        }
                    } else {
                        onError?.invoke("Sin respuesta de la sala del servidor.")
                    }
                })
            } else {
                onError?.invoke("No se pudo conectar con el servidor.")
            }
        }

        if (socket?.connected() == true) {
            action()
        } else {
            synchronized(pendingActions) {
                pendingActions.add(action)
            }
            connect()
        }
    }

    fun joinRoom(roomCodeOrId: String, passcode: String? = null) {
        val action: () -> Unit = {
            val payload = JSONObject().apply {
                put("roomCode", roomCodeOrId)
                put("roomId", roomCodeOrId)
                if (!passcode.isNullOrBlank()) put("passcode", passcode)
            }
            Log.d(TAG, "Emitiendo watch_party_join -> code: $roomCodeOrId")
            socket?.emit("watch_party_join", payload)
        }

        if (socket?.connected() == true) {
            action()
        } else {
            synchronized(pendingActions) {
                pendingActions.add(action)
            }
            connect()
            if (socket?.connected() == true) {
                flushPendingActions()
            }
        }
    }

    fun leaveRoom(roomId: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
        }
        socket?.emit("watch_party_leave", payload)
        _currentRoomState.value = null
        _chatHistoryFlow.value = emptyList()
    }

    fun requestRoomState(roomId: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
        }
        socket?.emit("watch_party_request_state", payload)
    }

    fun sendSync(roomId: String, isPlaying: Boolean, positionMs: Long) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("isPlaying", isPlaying)
            put("positionMs", positionMs)
        }
        socket?.emit("watch_party_sync", payload)
    }

    fun changeMedia(roomId: String, mediaId: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("mediaId", mediaId)
        }
        socket?.emit("watch_party_change_media", payload)
    }

    fun grantControl(roomId: String, targetUserId: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("targetUserId", targetUserId)
        }
        socket?.emit("watch_party_grant_control", payload)
    }

    fun revokeControl(roomId: String, targetUserId: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("targetUserId", targetUserId)
        }
        socket?.emit("watch_party_revoke_control", payload)
    }

    fun sendChatMessage(roomId: String, message: String) {
        if (message.isBlank()) return
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("message", message.trim())
        }
        socket?.emit("watch_party_chat_send", payload)
    }

    fun requestChatHistory(roomId: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
        }
        socket?.emit("watch_party_chat_history", payload)
    }
}
