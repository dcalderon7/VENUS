package com.example.venus.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.venus.network.VenusWebSocketManager
import com.example.venus.network.WatchPartyRoomState
import com.example.venus.ui.components.VenusComponents
import com.google.android.material.bottomsheet.BottomSheetDialog

@androidx.media3.common.util.UnstableApi
object WatchPartyDialogs {

    fun mostrarDialogoSalaCreadaConCodigo(
        context: Context,
        mediaId: String?,
        title: String,
        onStartPlayer: () -> Unit
    ) {
        val wsManager = VenusWebSocketManager.getInstance(context)

        val bottomSheet = BottomSheetDialog(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24, context), dp(28, context), dp(24, context), dp(28, context))
            background = VenusComponents.fondoRedondeado(Color.rgb(23, 19, 33), 24, context)
        }

        val txtTitulo = TextView(context).apply {
            text = "Sala de Watch Party Creada"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8, context))
        }

        val txtMensaje = TextView(context).apply {
            text = "Comparte este código de invitación para sincronizar \"$title\":"
            textSize = 13f
            setTextColor(Color.rgb(185, 178, 201))
            setPadding(0, 0, 0, dp(16, context))
        }

        val rowCode = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16, context), dp(16, context), dp(16, context), dp(16, context))
            background = VenusComponents.fondoRedondeado(Color.rgb(17, 16, 21), 16, context)
        }

        val txtCode = TextView(context).apply {
            text = "Generando código..."
            textSize = 24f
            setTextColor(Color.rgb(135, 56, 245))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnCopy = TextView(context).apply {
            text = "COPIAR"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(14, context), dp(8, context), dp(14, context), dp(8, context))
            background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 10, context)
            isClickable = true
            setOnClickListener {
                val currentCode = txtCode.text.toString()
                if (currentCode.isNotBlank() && !currentCode.contains("Generando") && !currentCode.contains("Error")) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Código Watch Party", currentCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Código $currentCode copiado al portapapeles", Toast.LENGTH_SHORT).show()
                }
            }
        }

        rowCode.addView(txtCode)
        rowCode.addView(btnCopy)

        val btnRetry = TextView(context).apply {
            text = "REINTENTAR GENERACIÓN"
            textSize = 12f
            setTextColor(Color.rgb(255, 193, 7))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(10, context), 0, dp(10, context))
            visibility = View.GONE
            isClickable = true
        }

        val btnStart = TextView(context).apply {
            text = "ENTRAR AL VIDEO Y REPRODUCIR"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(14, context), 0, dp(14, context))
            background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 14, context)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16, context)
            }
            isClickable = true
            setOnClickListener {
                bottomSheet.dismiss()
                onStartPlayer()
            }
        }

        fun solicitarCreacion() {
            txtCode.text = "Generando código..."
            btnRetry.visibility = View.GONE

            val activeRoom = wsManager.currentRoomState.value
            if (activeRoom != null && activeRoom.status == "active" && activeRoom.roomCode.isNotBlank()) {
                txtCode.text = activeRoom.roomCode
            }

            wsManager.connect()
            wsManager.createRoom(
                mediaId = mediaId,
                onSuccess = { roomState ->
                    (context as? android.app.Activity)?.runOnUiThread {
                        txtCode.text = roomState.roomCode
                        btnRetry.visibility = View.GONE
                    }
                },
                onError = { errMsg ->
                    (context as? android.app.Activity)?.runOnUiThread {
                        val currentRoomState = wsManager.currentRoomState.value
                        if (currentRoomState != null && currentRoomState.status == "active" && currentRoomState.roomCode.isNotBlank()) {
                            txtCode.text = currentRoomState.roomCode
                            btnRetry.visibility = View.GONE
                        } else {
                            txtCode.text = "Error al generar código"
                            btnRetry.visibility = View.VISIBLE
                            Toast.makeText(context, "Error: $errMsg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }

        btnRetry.setOnClickListener {
            solicitarCreacion()
        }

        solicitarCreacion()

        layout.addView(txtTitulo)
        layout.addView(txtMensaje)
        layout.addView(rowCode)
        layout.addView(btnRetry)
        layout.addView(btnStart)

        bottomSheet.setContentView(layout)
        bottomSheet.show()
    }

    fun obtenerIdUsuarioActual(context: Context): String {
        try {
            val securePrefs = context.getSharedPreferences("venus_secure_prefs", Context.MODE_PRIVATE)
            val token = securePrefs.getString("jwt_token", null)
            if (!token.isNullOrBlank() && token.contains(".")) {
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val payloadBytes = android.util.Base64.decode(
                        parts[1],
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                    )
                    val payloadJson = org.json.JSONObject(String(payloadBytes, Charsets.UTF_8))
                    val idFromJwt = payloadJson.optString("userId", payloadJson.optString("id", payloadJson.optString("sub", "")))
                    if (idFromJwt.isNotBlank()) {
                        return idFromJwt
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VenusUser", "Error extrayendo userId del token JWT", e)
        }

        val localPrefs = context.getSharedPreferences("venus_local_media", Context.MODE_PRIVATE)
        return localPrefs.getString("active_user_id", "") ?: ""
    }

    fun mostrarMenuWatchParty(context: Context, onRoomJoined: ((WatchPartyRoomState) -> Unit)? = null) {
        val wsManager = VenusWebSocketManager.getInstance(context)
        val room = wsManager.currentRoomState.value
        val currentUserId = obtenerIdUsuarioActual(context)

        val bottomSheet = BottomSheetDialog(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20, context), dp(24, context), dp(20, context), dp(24, context))
            background = VenusComponents.fondoRedondeado(Color.rgb(23, 19, 33), 24, context)
        }

        val title = TextView(context).apply {
            text = "Watch Party - VENUS Sync"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16, context))
        }
        layout.addView(title)

        if (room != null && room.status == "active") {
            val cardRoom = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16, context), dp(16, context), dp(16, context), dp(16, context))
                background = VenusComponents.fondoRedondeado(Color.rgb(17, 16, 21), 16, context)
            }

            val txtCodeLabel = TextView(context).apply {
                text = "CÓDIGO DE SALA ACTIVA:"
                textSize = 11f
                setTextColor(Color.rgb(185, 178, 201))
            }

            val rowCode = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6, context), 0, dp(12, context))
            }

            val txtCode = TextView(context).apply {
                text = room.roomCode
                textSize = 24f
                setTextColor(Color.rgb(135, 56, 245))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnCopy = TextView(context).apply {
                text = "COPIAR CÓDIGO"
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(12, context), dp(8, context), dp(12, context), dp(8, context))
                background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 10, context)
                isClickable = true
                setOnClickListener {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Código Watch Party", room.roomCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Código ${room.roomCode} copiado", Toast.LENGTH_SHORT).show()
                }
            }

            rowCode.addView(txtCode)
            rowCode.addView(btnCopy)
            cardRoom.addView(txtCodeLabel)
            cardRoom.addView(rowCode)

            val txtParticipantsHeader = TextView(context).apply {
                text = "PARTICIPANTES EN SALA (${room.participants.size}):"
                textSize = 12f
                setTextColor(Color.rgb(185, 178, 201))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(8, context), 0, dp(6, context))
            }
            cardRoom.addView(txtParticipantsHeader)

            val isHost = room.hostId == currentUserId

            room.participants.forEach { p ->
                val rowParticipant = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4, context), 0, dp(4, context))
                }

                val txtName = TextView(context).apply {
                    text = "${p.name} (@${p.username}) ${if (p.userId == room.hostId) "[Anfitrión]" else if (p.allowedControl) "[Control]" else ""}"
                    textSize = 13f
                    setTextColor(if (p.userId == room.hostId) Color.rgb(255, 215, 0) else Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                rowParticipant.addView(txtName)

                if (isHost && p.userId != room.hostId) {
                    val btnToggleControl = TextView(context).apply {
                        text = if (p.allowedControl) "Revocar Control" else "Conceder Control"
                        textSize = 11f
                        setTextColor(if (p.allowedControl) Color.rgb(255, 100, 100) else Color.rgb(0, 200, 83))
                        setPadding(dp(8, context), dp(4, context), dp(8, context), dp(4, context))
                        isClickable = true
                        setOnClickListener {
                            if (p.allowedControl) {
                                wsManager.revokeControl(room.id, p.userId)
                            } else {
                                wsManager.grantControl(room.id, p.userId)
                            }
                            bottomSheet.dismiss()
                        }
                    }
                    rowParticipant.addView(btnToggleControl)
                }

                cardRoom.addView(rowParticipant)
            }

            layout.addView(cardRoom)

            // Chat de Sala
            val cardChat = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16, context), dp(16, context), dp(16, context), dp(16, context))
                background = VenusComponents.fondoRedondeado(Color.rgb(17, 16, 21), 16, context)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(14, context)
                }
            }

            val txtChatHeader = TextView(context).apply {
                text = "CHAT EN VIVO DE SALA:"
                textSize = 12f
                setTextColor(Color.rgb(185, 178, 201))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(8, context))
            }
            cardChat.addView(txtChatHeader)

            val scrollChat = android.widget.ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150, context))
            }
            val chatMessagesContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            scrollChat.addView(chatMessagesContainer)
            cardChat.addView(scrollChat)

            val renderMessages = { messages: List<com.example.venus.network.WatchPartyChatMessage> ->
                chatMessagesContainer.removeAllViews()
                messages.forEach { msg ->
                    val msgRow = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(8, context), dp(4, context), dp(8, context), dp(4, context))
                        background = VenusComponents.fondoRedondeado(
                            if (msg.userId == currentUserId) Color.rgb(45, 35, 65) else Color.rgb(27, 24, 35),
                            8, context
                        )
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(6, context) }
                    }

                    val txtSender = TextView(context).apply {
                        text = "${msg.name} ${if (msg.isHost) "[Anfitrión]" else ""}"
                        textSize = 11f
                        setTextColor(if (msg.isHost) Color.rgb(255, 215, 0) else Color.rgb(196, 181, 253))
                        setTypeface(null, Typeface.BOLD)
                    }

                    val txtBody = TextView(context).apply {
                        text = msg.message
                        textSize = 13f
                        setTextColor(Color.WHITE)
                    }

                    msgRow.addView(txtSender)
                    msgRow.addView(txtBody)
                    chatMessagesContainer.addView(msgRow)
                }
                scrollChat.post { scrollChat.fullScroll(View.FOCUS_DOWN) }
            }

            wsManager.requestChatHistory(room.id)
            renderMessages(wsManager.chatHistoryFlow.value)

            val rowInput = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8, context), 0, 0)
            }

            val inputMsg = EditText(context).apply {
                hint = "Escribe un mensaje..."
                setHintTextColor(Color.rgb(185, 178, 201))
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(dp(12, context), dp(8, context), dp(12, context), dp(8, context))
                background = VenusComponents.fondoRedondeado(Color.rgb(27, 24, 35), 10, context)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8, context)
                }
            }

            val btnSendMsg = TextView(context).apply {
                text = "ENVIAR"
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(12, context), dp(10, context), dp(12, context), dp(10, context))
                background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 10, context)
                isClickable = true
                setOnClickListener {
                    val txt = inputMsg.text.toString()
                    if (txt.isNotBlank()) {
                        wsManager.sendChatMessage(room.id, txt)
                        inputMsg.setText("")
                    }
                }
            }

            rowInput.addView(inputMsg)
            rowInput.addView(btnSendMsg)
            cardChat.addView(rowInput)

            layout.addView(cardChat)

            val btnLeave = TextView(context).apply {
                text = "SALIR DE LA SALA"
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(14, context), 0, dp(14, context))
                background = VenusComponents.fondoRedondeado(Color.rgb(180, 40, 40), 12, context)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(16, context)
                }
                isClickable = true
                setOnClickListener {
                    wsManager.leaveRoom(room.id)
                    bottomSheet.dismiss()
                    Toast.makeText(context, "Has salido de la sala de Watch Party", Toast.LENGTH_SHORT).show()
                }
            }
            layout.addView(btnLeave)

        } else {
            val btnCreate = TextView(context).apply {
                text = "CREAR NUEVA SALA DE WATCH PARTY"
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(14, context), 0, dp(14, context))
                background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 14, context)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(12, context)
                }
                isClickable = true
                setOnClickListener {
                    wsManager.connect()
                    wsManager.createRoom()
                    bottomSheet.dismiss()
                    Toast.makeText(context, "Creando sala en el servidor...", Toast.LENGTH_SHORT).show()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val newRoom = wsManager.currentRoomState.value
                        if (newRoom != null) {
                            Toast.makeText(context, "Sala creada. Código: ${newRoom.roomCode}", Toast.LENGTH_LONG).show()
                        }
                        mostrarMenuWatchParty(context, onRoomJoined)
                    }, 800)
                }
            }

            val btnJoin = TextView(context).apply {
                text = "UNIRSE CON CÓDIGO DE SALA"
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(14, context), 0, dp(14, context))
                background = VenusComponents.fondoRedondeado(Color.rgb(35, 30, 48), 14, context)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                isClickable = true
                setOnClickListener {
                    bottomSheet.dismiss()
                    mostrarDialogoIngresarCodigo(context, onRoomJoined)
                }
            }

            layout.addView(btnCreate)
            layout.addView(btnJoin)
        }

        bottomSheet.setContentView(layout)
        bottomSheet.show()
    }

    private fun mostrarDialogoIngresarCodigo(context: Context, onRoomJoined: ((WatchPartyRoomState) -> Unit)?) {
        val bottomSheet = BottomSheetDialog(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24, context), dp(28, context), dp(24, context), dp(28, context))
            background = VenusComponents.fondoRedondeado(Color.rgb(23, 19, 33), 24, context)
        }

        val txtTitulo = TextView(context).apply {
            text = "Unirse a Watch Party"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8, context))
        }

        val txtMensaje = TextView(context).apply {
            text = "Ingresa el código único de 6 caracteres proporcionado por el anfitrión:"
            textSize = 13f
            setTextColor(Color.rgb(185, 178, 201))
            setPadding(0, 0, 0, dp(16, context))
        }

        val input = EditText(context).apply {
            hint = "Ejemplo: VP-8921"
            setHintTextColor(Color.rgb(185, 178, 201))
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(dp(16, context), dp(16, context), dp(16, context), dp(16, context))
            background = VenusComponents.fondoRedondeado(Color.rgb(17, 16, 21), 12, context)
        }

        val filaBotones = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(20, context), 0, 0)
        }

        val btnCancelar = TextView(context).apply {
            text = "CANCELAR"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16, context), dp(12, context), dp(16, context), dp(12, context))
            background = VenusComponents.fondoRedondeado(Color.rgb(35, 30, 48), 12, context)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8, context)
            }
            isClickable = true
            setOnClickListener {
                bottomSheet.dismiss()
            }
        }

        val btnUnirse = TextView(context).apply {
            text = "UNIRSE"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16, context), dp(12, context), dp(16, context), dp(12, context))
            background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 12, context)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8, context)
            }
            isClickable = true
            setOnClickListener {
                val codeInput = input.text.toString().trim().uppercase()
                if (codeInput.isNotBlank()) {
                    bottomSheet.dismiss()
                    val cleanCode = if (!codeInput.startsWith("VP-")) "VP-$codeInput" else codeInput
                    val wsManager = VenusWebSocketManager.getInstance(context)
                    wsManager.connect()
                    wsManager.joinRoom(cleanCode)
                    Toast.makeText(context, "Conectando a la sala $cleanCode...", Toast.LENGTH_SHORT).show()
                    (context as? com.example.venus.MainActivity)?.mostrarWatchPartyScreen()
                }
            }
        }

        filaBotones.addView(btnCancelar)
        filaBotones.addView(btnUnirse)

        layout.addView(txtTitulo)
        layout.addView(txtMensaje)
        layout.addView(input)
        layout.addView(filaBotones)

        bottomSheet.setContentView(layout)
        bottomSheet.show()
    }

    private fun dp(value: Int, context: Context): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
