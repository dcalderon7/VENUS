package com.example.venus.ui.watchparty

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.venus.MainActivity
import com.example.venus.R
import com.example.venus.VideoPlayerActivity
import com.example.venus.network.ApiClient
import com.example.venus.network.MediaFileDto
import com.example.venus.network.VenusWebSocketManager
import com.example.venus.network.WatchPartyChatMessage
import com.example.venus.network.WatchPartyParticipant
import com.example.venus.network.WatchPartyRoomEvent
import com.example.venus.network.WatchPartyRoomState
import com.example.venus.ui.components.VenusComponents
import com.example.venus.ui.dialogs.WatchPartyDialogs
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@UnstableApi
class WatchPartyScreen(private val activity: MainActivity) {

    private val wsManager by lazy { VenusWebSocketManager.getInstance(activity.applicationContext) }

    private var cachedLibraryItems = listOf<MediaFileDto>()
    private var exoPlayer: ExoPlayer? = null
    private var currentLoadedStreamUrl: String? = null
    private var activeRoomId: String? = null

    private var roomStateJob: Job? = null
    private var chatHistoryJob: Job? = null
    private var chatMsgJob: Job? = null
    private var updateProgressRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private var isSeeking = false
    private var isChatExpanded = true
    private var isMovieInfoExpanded = true

    private enum class ViewMode { NONE, CATALOG, ROOM }
    private var currentViewMode = ViewMode.NONE

    private var rootContainer: LinearLayout? = null

    // Componentes dinámicos de la sala de reproducción
    private var videoContainerFrame: FrameLayout? = null
    private var playerViewRef: PlayerView? = null
    private var btnPlayPauseRef: ImageView? = null
    private var txtCurrentTimeRef: TextView? = null
    private var txtTotalDurationRef: TextView? = null
    private var seekBarRef: SeekBar? = null
    private var txtMovieTitleRef: TextView? = null
    private var txtMovieMetaRef: TextView? = null
    private var imgMoviePosterRef: ImageView? = null
    private var txtParticipantCountHeaderRef: TextView? = null
    private var txtParticipantCountTopBadgeRef: TextView? = null
    private var participantsListContainerRef: LinearLayout? = null
    private var chatMessagesContainerRef: LinearLayout? = null
    private var scrollChatRef: ScrollView? = null
    private var txtChatUnreadBadgeRef: TextView? = null
    private var inputChatMessageRef: EditText? = null

    fun mostrarWatchPartyScreen() {
        activity.mostrarTopHeader(false)
        activity.seleccionarNavegacion(1)
        val pantalla = activity.prepararPantalla()
        rootContainer = pantalla

        wsManager.connect()

        roomStateJob?.cancel()
        roomStateJob = activity.lifecycleScope.launch {
            wsManager.currentRoomState.collect { room ->
                if (room != null && room.status == "active") {
                    if (currentViewMode != ViewMode.ROOM || activeRoomId != room.id) {
                        currentViewMode = ViewMode.ROOM
                        activeRoomId = room.id
                        renderizarSalaDeReproduccion(pantalla, room)
                    } else {
                        actualizarDatosSala(room)
                    }
                } else {
                    if (currentViewMode != ViewMode.CATALOG) {
                        currentViewMode = ViewMode.CATALOG
                        activeRoomId = null
                        liberarPlayer()
                        renderizarCatalogoWatchParty(pantalla)
                    }
                }
            }
        }
    }

    private fun renderizarCatalogoWatchParty(pantalla: LinearLayout) {
        pantalla.removeAllViews()

        // Header Catálogo
        val headerNav = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(8))
        }

        val btnVolver = ImageView(activity).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(activity.blanco)
            setPadding(activity.dp(4), activity.dp(8), activity.dp(12), activity.dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { activity.mostrarInicio() }
        }

        val colTitles = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tituloNav = TextView(activity).apply {
            text = "Watch Party"
            textSize = 24f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
        }

        val subTituloNav = TextView(activity).apply {
            text = "Películas disponibles para reproducción compartida"
            textSize = 12f
            setTextColor(Color.rgb(185, 178, 201))
            setPadding(0, activity.dp(2), 0, 0)
        }

        colTitles.addView(tituloNav)
        colTitles.addView(subTituloNav)

        val badgeSalaCompartida = TextView(activity).apply {
            text = "Sala compartida"
            textSize = 11f
            setTextColor(Color.rgb(0, 200, 83))
            setTypeface(null, Typeface.BOLD)
            setPadding(activity.dp(10), activity.dp(4), activity.dp(10), activity.dp(4))
            background = VenusComponents.fondoRedondeado(Color.argb(40, 0, 200, 83), 12, activity)
        }

        headerNav.addView(btnVolver)
        headerNav.addView(colTitles)
        headerNav.addView(badgeSalaCompartida)
        pantalla.addView(headerNav, activity.margenAbajo(16))

        // Carga de videos
        val cargaContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(40), 0, activity.dp(40))
        }
        val progressBar = ProgressBar(activity).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(activity.rojo)
        }
        val txtCargando = TextView(activity).apply {
            text = "Verificando películas disponibles..."
            textSize = 14f
            setTextColor(activity.gris)
            setPadding(0, activity.dp(12), 0, 0)
            gravity = Gravity.CENTER
        }
        cargaContainer.addView(progressBar)
        cargaContainer.addView(txtCargando)
        pantalla.addView(cargaContainer)

        activity.lifecycleScope.launch {
            if (activity.catalogosMock.isEmpty()) {
                activity.cargarCatalogosTmdb {}
            }

            val res = activity.repository.getLibrary()
            pantalla.removeView(cargaContainer)

            val library = res.getOrNull()
            val todosLosArchivosDisponibles = mutableListOf<MediaFileDto>()
            if (library != null) {
                todosLosArchivosDisponibles.addAll(library.cataloged)
                todosLosArchivosDisponibles.addAll(library.independent)
            }
            cachedLibraryItems = todosLosArchivosDisponibles

            if (todosLosArchivosDisponibles.isEmpty()) {
                val cajaVacia = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(activity.dp(24), activity.dp(40), activity.dp(24), activity.dp(40))
                    background = VenusComponents.fondoRedondeado(activity.superficie, 20, activity)
                    gravity = Gravity.CENTER
                }
                val txtVacio = TextView(activity).apply {
                    text = "No hay películas con archivo de video subido al servidor en este momento."
                    textSize = 14f
                    setTextColor(activity.gris)
                    gravity = Gravity.CENTER
                }
                cajaVacia.addView(txtVacio)
                pantalla.addView(cajaVacia)
            } else {
                val recyclerView = RecyclerView(activity).apply {
                    layoutManager = GridLayoutManager(activity, 2)
                    isNestedScrollingEnabled = false
                }

                val mediaAdapter = WatchPartyMediaAdapter(
                    activity = activity,
                    onItemClick = { media ->
                        iniciarWatchPartyParaMedio(media)
                    }
                )

                recyclerView.adapter = mediaAdapter
                mediaAdapter.submitList(todosLosArchivosDisponibles)
                pantalla.addView(recyclerView)
            }

            val txtNotaExplicativa = TextView(activity).apply {
                text = "Toca una película para crear o unirte a una sala de sintonía en vivo."
                textSize = 12f
                setTextColor(Color.rgb(185, 178, 201))
                setPadding(0, activity.dp(16), 0, activity.dp(16))
            }
            pantalla.addView(txtNotaExplicativa)

            val btnCrearSalaBottom = TextView(activity).apply {
                text = "Crear sala"
                textSize = 14f
                setTextColor(activity.blanco)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, activity.dp(14), 0, activity.dp(14))
                background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 20, activity)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = activity.dp(12)
                }
                isClickable = true
                setOnClickListener {
                    if (todosLosArchivosDisponibles.isNotEmpty()) {
                        mostrarDialogoSeleccionarPelicula(todosLosArchivosDisponibles)
                    } else {
                        wsManager.createRoom()
                        Toast.makeText(activity, "Creando sala en el servidor...", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val btnUnirseSalaBottom = TextView(activity).apply {
                text = "Unirse a una sala"
                textSize = 14f
                setTextColor(activity.blanco)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, activity.dp(14), 0, activity.dp(14))
                background = VenusComponents.fondoRedondeadoConRipple(activity.superficie2, 20, activity)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isClickable = true
                setOnClickListener {
                    mostrarDialogoIngresarCodigo()
                }
            }

            pantalla.addView(btnCrearSalaBottom)
            pantalla.addView(btnUnirseSalaBottom)
        }
    }

    // =========================================================================
    // RENDERIZADO COMPLETO DE LA SALA DE REPRODUCCIÓN (MATCHING USER SCREENSHOT)
    // =========================================================================
    private fun renderizarSalaDeReproduccion(pantalla: LinearLayout, room: WatchPartyRoomState) {
        pantalla.removeAllViews()

        // 1. TOP BRAND & APP HEADER (VENUS Logo + Badge Participantes + Botón Salir)
        val topAppHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(10))
        }

        val txtLogoBrand = TextView(activity).apply {
            text = "VENUS"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Badge de participantes en Top Bar
        val badgeParticipants = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(10), activity.dp(6), activity.dp(10), activity.dp(6))
            background = VenusComponents.fondoRedondeado(Color.argb(50, 135, 56, 245), 16, activity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = activity.dp(10) }
            isClickable = true
            setOnClickListener {
                WatchPartyDialogs.mostrarMenuWatchParty(activity)
            }
        }
        val imgIconUsers = ImageView(activity).apply {
            setImageResource(R.drawable.ic_user)
            setColorFilter(Color.rgb(196, 181, 253))
            layoutParams = LinearLayout.LayoutParams(activity.dp(16), activity.dp(16)).apply {
                marginEnd = activity.dp(6)
            }
        }
        val txtCountBadge = TextView(activity).apply {
            text = "${room.participants.size}"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        txtParticipantCountTopBadgeRef = txtCountBadge
        badgeParticipants.addView(imgIconUsers)
        badgeParticipants.addView(txtCountBadge)

        // Botón Salir (Borde rojo con icono de salida)
        val btnSalir = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(12), activity.dp(6), activity.dp(14), activity.dp(6))
            background = VenusComponents.fondoRedondeado(Color.argb(40, 230, 40, 40), 16, activity)
            isClickable = true
            setOnClickListener {
                wsManager.leaveRoom(room.id)
                activity.mostrarTopHeader(true)
                Toast.makeText(activity, "Has salido de la sala de Watch Party", Toast.LENGTH_SHORT).show()
            }
        }
        val imgIconExit = ImageView(activity).apply {
            setImageResource(R.drawable.ic_logout)
            setColorFilter(Color.rgb(255, 90, 90))
            layoutParams = LinearLayout.LayoutParams(activity.dp(14), activity.dp(14)).apply {
                marginEnd = activity.dp(6)
            }
        }
        val txtSalir = TextView(activity).apply {
            text = "Salir"
            textSize = 12f
            setTextColor(Color.rgb(255, 90, 90))
            setTypeface(null, Typeface.BOLD)
        }
        btnSalir.addView(imgIconExit)
        btnSalir.addView(txtSalir)

        topAppHeader.addView(txtLogoBrand)
        topAppHeader.addView(badgeParticipants)
        topAppHeader.addView(btnSalir)

        pantalla.addView(topAppHeader)

        // 2. SUB-HEADER BAR (VOLVER <, WATCH PARTY TITLE, SALA PRIVADA BADGE, MENÚ ...)
        val subHeaderNav = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(14))
        }

        val btnVolver = ImageView(activity).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(activity.blanco)
            setPadding(0, activity.dp(4), activity.dp(10), activity.dp(4))
            isClickable = true
            setOnClickListener {
                currentViewMode = ViewMode.CATALOG
                activity.mostrarTopHeader(true)
                renderizarCatalogoWatchParty(pantalla)
            }
        }

        val imgIconWatchParty = ImageView(activity).apply {
            setImageResource(R.drawable.ic_film_reel)
            setColorFilter(Color.rgb(135, 56, 245))
            layoutParams = LinearLayout.LayoutParams(activity.dp(22), activity.dp(22)).apply {
                marginEnd = activity.dp(8)
            }
        }

        val txtTitleRoom = TextView(activity).apply {
            text = "Watch Party"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badgePrivate = TextView(activity).apply {
            text = "SALA: ${room.roomCode}"
            textSize = 10f
            setTextColor(Color.rgb(196, 181, 253))
            setTypeface(null, Typeface.BOLD)
            setPadding(activity.dp(10), activity.dp(4), activity.dp(10), activity.dp(4))
            background = VenusComponents.fondoRedondeado(Color.rgb(38, 28, 56), 12, activity)
            isClickable = true
            setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Código Watch Party", room.roomCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(activity, "Código ${room.roomCode} copiado al portapapeles", Toast.LENGTH_SHORT).show()
            }
        }

        val btnMenuOverflow = TextView(activity).apply {
            text = "⋮"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(activity.dp(12), 0, 0, 0)
            isClickable = true
            setOnClickListener {
                WatchPartyDialogs.mostrarMenuWatchParty(activity)
            }
        }

        subHeaderNav.addView(btnVolver)
        subHeaderNav.addView(imgIconWatchParty)
        subHeaderNav.addView(txtTitleRoom)
        subHeaderNav.addView(badgePrivate)
        subHeaderNav.addView(btnMenuOverflow)

        pantalla.addView(subHeaderNav)

        // 3. CONTENEDOR DEL REPRODUCTOR DE VIDEO EMBEBIDO CON CONTROLES (16:9 RATIO)
        val videoContainer = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(210)
            ).apply {
                bottomMargin = activity.dp(14)
            }
            background = VenusComponents.fondoRedondeado(Color.BLACK, 16, activity)
            clipToOutline = true
        }
        videoContainerFrame = videoContainer

        val playerView = PlayerView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            useController = false
        }
        playerViewRef = playerView
        videoContainer.addView(playerView)

        // Badge HD superior derecho del reproductor
        val badgeHdVideo = TextView(activity).apply {
            text = "HD"
            textSize = 10f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(activity.dp(8), activity.dp(3), activity.dp(8), activity.dp(3))
            background = VenusComponents.fondoRedondeado(Color.argb(160, 0, 0, 0), 6, activity)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = activity.dp(12)
                marginEnd = activity.dp(12)
            }
        }
        videoContainer.addView(badgeHdVideo)

        // Overlay de controles de reproducción central (Retroceder 10s, Play/Pausa, Adelantar 10s)
        val controlsOverlay = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        val btnRewind = ImageView(activity).apply {
            setImageResource(R.drawable.ic_rewind_10)
            setColorFilter(Color.WHITE)
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            background = VenusComponents.fondoRedondeado(Color.argb(180, 27, 24, 35), 23, activity)
            layoutParams = LinearLayout.LayoutParams(activity.dp(46), activity.dp(46)).apply {
                marginEnd = activity.dp(24)
            }
            isClickable = true
            setOnClickListener {
                if (puedeControlar(room)) {
                    val pos = (exoPlayer?.currentPosition ?: 0L) - 10000L
                    val targetPos = pos.coerceAtLeast(0L)
                    exoPlayer?.seekTo(targetPos)
                    wsManager.sendSync(room.id, exoPlayer?.isPlaying == true, targetPos)
                } else {
                    Toast.makeText(activity, "Solo el anfitrión tiene control de la reproducción", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnPlayPause = ImageView(activity).apply {
            setImageResource(if (room.isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white)
            setColorFilter(Color.WHITE)
            setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16))
            background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 30, activity)
            layoutParams = LinearLayout.LayoutParams(activity.dp(60), activity.dp(60))
            isClickable = true
            setOnClickListener {
                if (puedeControlar(room)) {
                    val isCurrentlyPlaying = exoPlayer?.isPlaying == true
                    if (isCurrentlyPlaying) {
                        exoPlayer?.pause()
                    } else {
                        exoPlayer?.play()
                    }
                    val newPlayingState = !isCurrentlyPlaying
                    wsManager.sendSync(room.id, newPlayingState, exoPlayer?.currentPosition ?: 0L)
                } else {
                    Toast.makeText(activity, "Solo el anfitrión puede controlar la reproducción", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnPlayPauseRef = btnPlayPause

        val btnForward = ImageView(activity).apply {
            setImageResource(R.drawable.ic_forward_10)
            setColorFilter(Color.WHITE)
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            background = VenusComponents.fondoRedondeado(Color.argb(180, 27, 24, 35), 23, activity)
            layoutParams = LinearLayout.LayoutParams(activity.dp(46), activity.dp(46)).apply {
                marginStart = activity.dp(24)
            }
            isClickable = true
            setOnClickListener {
                if (puedeControlar(room)) {
                    val pos = (exoPlayer?.currentPosition ?: 0L) + 10000L
                    exoPlayer?.seekTo(pos)
                    wsManager.sendSync(room.id, exoPlayer?.isPlaying == true, pos)
                } else {
                    Toast.makeText(activity, "Solo el anfitrión tiene control de la reproducción", Toast.LENGTH_SHORT).show()
                }
            }
        }

        controlsOverlay.addView(btnRewind)
        controlsOverlay.addView(btnPlayPause)
        controlsOverlay.addView(btnForward)
        videoContainer.addView(controlsOverlay)

        // Overlay barra inferior del reproductor (Tiempo actual, SeekBar, Tiempo total, Fullscreen)
        val playerBottomBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(12), activity.dp(6), activity.dp(12), activity.dp(8))
            background = VenusComponents.fondoRedondeado(Color.argb(160, 0, 0, 0), 0, activity)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }

        val txtCurrentTime = TextView(activity).apply {
            text = "00:00"
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        txtCurrentTimeRef = txtCurrentTime

        val seekBar = SeekBar(activity).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(135, 56, 245))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(196, 181, 253))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = activity.dp(6)
                marginEnd = activity.dp(6)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && exoPlayer != null) {
                        val duration = exoPlayer!!.duration.coerceAtLeast(1L)
                        val targetMs = (progress.toLong() * duration) / 100L
                        txtCurrentTime.text = formatearTiempoMs(targetMs)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    isSeeking = true
                }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    isSeeking = false
                    if (exoPlayer != null && sb != null) {
                        if (puedeControlar(room)) {
                            val duration = exoPlayer!!.duration.coerceAtLeast(1L)
                            val targetMs = (sb.progress.toLong() * duration) / 100L
                            exoPlayer!!.seekTo(targetMs)
                            wsManager.sendSync(room.id, exoPlayer!!.isPlaying, targetMs)
                        } else {
                            Toast.makeText(activity, "Solo el anfitrión puede cambiar el tiempo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
        seekBarRef = seekBar

        val txtTotalDuration = TextView(activity).apply {
            text = "00:00"
            textSize = 11f
            setTextColor(Color.rgb(185, 178, 201))
        }
        txtTotalDurationRef = txtTotalDuration

        val btnFullscreenToggle = ImageView(activity).apply {
            setImageResource(R.drawable.ic_fullscreen_white)
            setColorFilter(Color.WHITE)
            setPadding(activity.dp(6), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(activity.dp(24), activity.dp(24))
            isClickable = true
            setOnClickListener {
                abrirPantallaCompleta(room)
            }
        }

        playerBottomBar.addView(txtCurrentTime)
        playerBottomBar.addView(seekBar)
        playerBottomBar.addView(txtTotalDuration)
        playerBottomBar.addView(btnFullscreenToggle)

        videoContainer.addView(playerBottomBar)

        pantalla.addView(videoContainer)

        // Inicializar/conectar ExoPlayer
        inicializarExoPlayerParaSala(room)

        // 4. TARJETA DE INFORMACIÓN DE PELÍCULA EN REPRODUCCIÓN
        val cardMovieInfo = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
            background = VenusComponents.fondoRedondeado(Color.rgb(23, 19, 33), 16, activity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = activity.dp(14)
            }
        }

        val imgPosterCard = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(48), activity.dp(66)).apply {
                marginEnd = activity.dp(12)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_film_reel)
            background = VenusComponents.fondoRedondeado(activity.superficie2, 8, activity)
            clipToOutline = true
        }
        imgMoviePosterRef = imgPosterCard

        val colMovieText = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val txtMovieTitle = TextView(activity).apply {
            text = room.mediaTitle ?: "Cargando película..."
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        txtMovieTitleRef = txtMovieTitle

        val rowMetaDetails = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(4), 0, 0)
        }

        val badgeHdSmall = TextView(activity).apply {
            text = "HD"
            textSize = 10f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(activity.dp(6), activity.dp(2), activity.dp(6), activity.dp(2))
            background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 6, activity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = activity.dp(8) }
        }

        val txtMovieMeta = TextView(activity).apply {
            text = "Ciencia Ficción • Sintonía en vivo"
            textSize = 12f
            setTextColor(Color.rgb(185, 178, 201))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        txtMovieMetaRef = txtMovieMeta

        rowMetaDetails.addView(badgeHdSmall)
        rowMetaDetails.addView(txtMovieMeta)

        colMovieText.addView(txtMovieTitle)
        colMovieText.addView(rowMetaDetails)

        val btnExpandMovie = ImageView(activity).apply {
            setImageResource(R.drawable.ic_expand_more)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(activity.dp(24), activity.dp(24))
            isClickable = true
            setOnClickListener {
                if (esHost(room)) {
                    mostrarDialogoSeleccionarPelicula(cachedLibraryItems)
                } else {
                    Toast.makeText(activity, "Película activa en sincronización", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cardMovieInfo.addView(imgPosterCard)
        cardMovieInfo.addView(colMovieText)
        cardMovieInfo.addView(btnExpandMovie)

        pantalla.addView(cardMovieInfo)

        // Actualizar poster y metadata según película
        actualizarInfoPeliculaCard(room)

        // 5. SECCIÓN PARTICIPANTES ("Participantes (4)", "Ver todos ->")
        val cardParticipants = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(Color.rgb(23, 19, 33), 18, activity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = activity.dp(14)
            }
        }

        val rowHeaderParticipants = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(12))
        }

        val imgIconUsersSec = ImageView(activity).apply {
            setImageResource(R.drawable.ic_user)
            setColorFilter(Color.rgb(196, 181, 253))
            layoutParams = LinearLayout.LayoutParams(activity.dp(18), activity.dp(18)).apply {
                marginEnd = activity.dp(8)
            }
        }

        val txtHeaderParticipants = TextView(activity).apply {
            text = "Participantes (${room.participants.size})"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        txtParticipantCountHeaderRef = txtHeaderParticipants

        val btnVerTodos = TextView(activity).apply {
            text = "Ver todos →"
            textSize = 13f
            setTextColor(Color.rgb(196, 181, 253))
            setTypeface(null, Typeface.BOLD)
            isClickable = true
            setOnClickListener {
                WatchPartyDialogs.mostrarMenuWatchParty(activity)
            }
        }

        rowHeaderParticipants.addView(imgIconUsersSec)
        rowHeaderParticipants.addView(txtHeaderParticipants)
        rowHeaderParticipants.addView(btnVerTodos)

        cardParticipants.addView(rowHeaderParticipants)

        val scrollHorizontalParticipants = HorizontalScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val containerParticipantsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        participantsListContainerRef = containerParticipantsRow
        scrollHorizontalParticipants.addView(containerParticipantsRow)

        cardParticipants.addView(scrollHorizontalParticipants)
        pantalla.addView(cardParticipants)

        // Renderizar avatares de participantes
        renderizarParticipantesHorizontal(room)

        // 6. SECCIÓN CHAT DE LA SALA ("Chat de la sala", Badge count, Mensajes, Input)
        val cardChat = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(Color.rgb(23, 19, 33), 18, activity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = activity.dp(14)
            }
        }

        val rowHeaderChat = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(10))
        }

        val imgIconChat = ImageView(activity).apply {
            setImageResource(R.drawable.ic_chat)
            setColorFilter(Color.rgb(196, 181, 253))
            layoutParams = LinearLayout.LayoutParams(activity.dp(18), activity.dp(18)).apply {
                marginEnd = activity.dp(8)
            }
        }

        val txtHeaderChat = TextView(activity).apply {
            text = "Chat de la sala"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badgeUnreadChat = TextView(activity).apply {
            text = "0"
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(8), activity.dp(2), activity.dp(8), activity.dp(2))
            background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 10, activity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = activity.dp(8) }
        }
        txtChatUnreadBadgeRef = badgeUnreadChat

        val btnCollapseChat = ImageView(activity).apply {
            setImageResource(R.drawable.ic_expand_less)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(activity.dp(24), activity.dp(24))
            isClickable = true
            setOnClickListener {
                isChatExpanded = !isChatExpanded
                val scroll = scrollChatRef
                val inputRow = inputChatMessageRef?.parent as? View
                if (scroll != null) {
                    scroll.visibility = if (isChatExpanded) View.VISIBLE else View.GONE
                }
                if (inputRow != null) {
                    inputRow.visibility = if (isChatExpanded) View.VISIBLE else View.GONE
                }
                setImageResource(if (isChatExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
            }
        }

        rowHeaderChat.addView(imgIconChat)
        rowHeaderChat.addView(txtHeaderChat)
        rowHeaderChat.addView(badgeUnreadChat)
        rowHeaderChat.addView(btnCollapseChat)

        cardChat.addView(rowHeaderChat)

        // Lista scrolleable de mensajes de chat
        val scrollChat = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(160)
            ).apply {
                bottomMargin = activity.dp(10)
            }
        }
        scrollChatRef = scrollChat

        val containerMessages = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        chatMessagesContainerRef = containerMessages
        scrollChat.addView(containerMessages)

        cardChat.addView(scrollChat)

        // Fila de input de mensaje con botón emoji y botón de enviar circular
        val rowInputChat = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(4), 0, 0)
        }

        val btnEmojiInput = ImageView(activity).apply {
            setImageResource(R.drawable.ic_emoji)
            setColorFilter(Color.rgb(196, 181, 253))
            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
            layoutParams = LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)).apply {
                marginEnd = activity.dp(6)
            }
            isClickable = true
            setOnClickListener {
                enviarReaccionRapida("🔥")
            }
        }

        val inputChat = EditText(activity).apply {
            hint = "Escribe un mensaje..."
            setHintTextColor(Color.rgb(185, 178, 201))
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10))
            background = VenusComponents.fondoRedondeado(Color.rgb(27, 24, 35), 20, activity)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = activity.dp(8)
            }
        }
        inputChatMessageRef = inputChat

        val btnSendChat = ImageView(activity).apply {
            setImageResource(R.drawable.ic_send)
            setColorFilter(Color.WHITE)
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 20, activity)
            layoutParams = LinearLayout.LayoutParams(activity.dp(40), activity.dp(40))
            isClickable = true
            setOnClickListener {
                val txt = inputChat.text.toString().trim()
                if (txt.isNotBlank()) {
                    wsManager.sendChatMessage(room.id, txt)
                    inputChat.setText("")
                }
            }
        }

        rowInputChat.addView(btnEmojiInput)
        rowInputChat.addView(inputChat)
        rowInputChat.addView(btnSendChat)

        cardChat.addView(rowInputChat)

        pantalla.addView(cardChat)

        // Cargar mensajes de chat históricos y suscribir a flujo
        configurarFlujoChat(room.id)

        // 7. BARRA INFERIOR FLOTANTE DE REACCIONES RÁPIDAS Y CONTROLES
        val barQuickReactions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8))
            background = VenusComponents.fondoRedondeado(Color.rgb(27, 24, 35), 26, activity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = activity.dp(12)
            }
        }

        val btnQuickChat = ImageView(activity).apply {
            setImageResource(R.drawable.ic_chat)
            setColorFilter(Color.rgb(196, 181, 253))
            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
            background = VenusComponents.fondoRedondeado(Color.rgb(38, 30, 56), 18, activity)
            layoutParams = LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)).apply {
                marginEnd = activity.dp(8)
            }
            isClickable = true
            setOnClickListener {
                scrollChatRef?.post { scrollChatRef?.fullScroll(View.FOCUS_DOWN) }
                inputChat.requestFocus()
            }
        }

        val btnQuickEmoji = ImageView(activity).apply {
            setImageResource(R.drawable.ic_emoji)
            setColorFilter(Color.rgb(196, 181, 253))
            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
            background = VenusComponents.fondoRedondeado(Color.rgb(38, 30, 56), 18, activity)
            layoutParams = LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)).apply {
                marginEnd = activity.dp(8)
            }
            isClickable = true
            setOnClickListener {
                enviarReaccionRapida("😍")
            }
        }

        val reaccionEmojis = listOf("👍", "❤️", "😂", "😮")
        val containerEmojiPills = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        }

        reaccionEmojis.forEach { emoji ->
            val txtEmojiBtn = TextView(activity).apply {
                text = emoji
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(activity.dp(8), activity.dp(4), activity.dp(8), activity.dp(4))
                isClickable = true
                setOnClickListener {
                    enviarReaccionRapida(emoji)
                }
            }
            containerEmojiPills.addView(txtEmojiBtn)
        }

        val btnQuickSettings = ImageView(activity).apply {
            setImageResource(R.drawable.ic_settings)
            setColorFilter(Color.rgb(196, 181, 253))
            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
            background = VenusComponents.fondoRedondeado(Color.rgb(38, 30, 56), 18, activity)
            layoutParams = LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)).apply {
                marginStart = activity.dp(8)
            }
            isClickable = true
            setOnClickListener {
                WatchPartyDialogs.mostrarMenuWatchParty(activity)
            }
        }

        barQuickReactions.addView(btnQuickChat)
        barQuickReactions.addView(btnQuickEmoji)
        barQuickReactions.addView(containerEmojiPills)
        barQuickReactions.addView(btnQuickSettings)

        pantalla.addView(barQuickReactions)
    }

    // =========================================================================
    // LÓGICA DE REPRODUCCIÓN, PARTICIPANTES, CHAT Y ANIMACIONES
    // =========================================================================

    private fun inicializarExoPlayerParaSala(room: WatchPartyRoomState) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(activity).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            val dur = duration.coerceAtLeast(0L)
                            txtTotalDurationRef?.text = formatearTiempoMs(dur)
                        }
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        btnPlayPauseRef?.setImageResource(if (isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white)
                    }
                })
            }
        }

        playerViewRef?.player = exoPlayer

        // Carga de URL de streaming
        val mediaItem = cachedLibraryItems.find { it.id == room.mediaId }
        val peliculaMatch = activity.catalogosMock.find { it.id == mediaItem?.tmdbId || it.id == room.mediaId }

        val token = activity.repository.getToken() ?: ""
        val streamUrl = when {
            !room.mediaId.isNullOrBlank() -> "${ApiClient.getBaseUrl(activity)}media/${room.mediaId}/stream?token=$token"
            peliculaMatch != null && peliculaMatch.videoUrl.isNotBlank() -> peliculaMatch.videoUrl
            else -> ""
        }

        if (streamUrl.isNotBlank() && currentLoadedStreamUrl != streamUrl) {
            currentLoadedStreamUrl = streamUrl
            exoPlayer?.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
            exoPlayer?.prepare()

            if (room.currentPositionMs > 0) {
                exoPlayer?.seekTo(room.currentPositionMs)
            }

            if (room.isPlaying) {
                exoPlayer?.play()
            } else {
                exoPlayer?.pause()
            }
        }

        // Bucle de actualización de progreso del Seekbar
        iniciarBucleProgreso()
    }

    private fun iniciarBucleProgreso() {
        updateProgressRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                val player = exoPlayer
                if (player != null && !isSeeking) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(1L)
                    txtCurrentTimeRef?.text = formatearTiempoMs(pos)
                    txtTotalDurationRef?.text = formatearTiempoMs(dur)

                    val progressPercent = ((pos * 100) / dur).toInt()
                    seekBarRef?.progress = progressPercent
                }
                handler.postDelayed(this, 1000)
            }
        }
        updateProgressRunnable = runnable
        handler.post(runnable)
    }

    private fun actualizarDatosSala(room: WatchPartyRoomState) {
        // Actualizar contadores
        txtParticipantCountTopBadgeRef?.text = "${room.participants.size}"
        txtParticipantCountHeaderRef?.text = "Participantes (${room.participants.size})"

        renderizarParticipantesHorizontal(room)
        actualizarInfoPeliculaCard(room)

        // Sincronizar estado de reproducción remoto
        val player = exoPlayer ?: return
        if (room.isPlaying != player.isPlaying) {
            if (room.isPlaying) player.play() else player.pause()
        }

        val currentPos = player.currentPosition
        val targetPos = room.currentPositionMs
        if (Math.abs(currentPos - targetPos) > 2500) {
            player.seekTo(targetPos)
        }
    }

    private fun renderizarParticipantesHorizontal(room: WatchPartyRoomState) {
        val container = participantsListContainerRef ?: return
        container.removeAllViews()

        room.participants.forEach { p ->
            val isHostP = p.userId == room.hostId

            val itemParticipant = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(activity.dp(8), 0, activity.dp(8), 0)
                isClickable = true
                setOnClickListener {
                    if (esHost(room) && p.userId != room.hostId) {
                        if (p.allowedControl) {
                            wsManager.revokeControl(room.id, p.userId)
                            Toast.makeText(activity, "Control revocado a ${p.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            wsManager.grantControl(room.id, p.userId)
                            Toast.makeText(activity, "Control concedido a ${p.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // Frame del avatar circular con badges (Corona de Host e Indicador verde Online)
            val avatarFrame = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(50), activity.dp(50)).apply {
                    bottomMargin = activity.dp(4)
                }
            }

            val imgAvatar = ImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setImageResource(R.drawable.ic_user)
                setColorFilter(Color.rgb(196, 181, 253))
                setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
                background = VenusComponents.fondoRedondeado(
                    if (isHostP) Color.rgb(45, 30, 70) else activity.superficie2,
                    25, activity
                )
            }
            avatarFrame.addView(imgAvatar)

            // Badge Corona de Host
            if (isHostP) {
                val crownBadge = ImageView(activity).apply {
                    setImageResource(R.drawable.ic_crown)
                    layoutParams = FrameLayout.LayoutParams(activity.dp(16), activity.dp(16)).apply {
                        gravity = Gravity.TOP or Gravity.END
                    }
                }
                avatarFrame.addView(crownBadge)
            }

            // Indicador Punto Verde Online
            val greenDot = View(activity).apply {
                background = VenusComponents.fondoRedondeado(Color.rgb(0, 230, 118), 5, activity)
                layoutParams = FrameLayout.LayoutParams(activity.dp(10), activity.dp(10)).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    marginEnd = activity.dp(2)
                    bottomMargin = activity.dp(2)
                }
            }
            avatarFrame.addView(greenDot)

            val txtNameP = TextView(activity).apply {
                text = p.name.ifBlank { p.username }
                textSize = 12f
                setTextColor(if (isHostP) Color.rgb(255, 215, 0) else Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val txtStatusP = TextView(activity).apply {
                text = if (isHostP) "Anfitrión" else if (p.allowedControl) "Control" else "Viendo"
                textSize = 10f
                setTextColor(if (isHostP) Color.rgb(196, 181, 253) else activity.gris)
            }

            itemParticipant.addView(avatarFrame)
            itemParticipant.addView(txtNameP)
            itemParticipant.addView(txtStatusP)

            container.addView(itemParticipant)
        }
    }

    private fun actualizarInfoPeliculaCard(room: WatchPartyRoomState) {
        val mediaItem = cachedLibraryItems.find { it.id == room.mediaId }
        val peliculaMatch = activity.catalogosMock.find { it.id == mediaItem?.tmdbId || it.id == room.mediaId }

        val displayTitle = when {
            peliculaMatch != null -> peliculaMatch.titulo
            mediaItem != null && !mediaItem.displayTitle.isNullOrBlank() -> mediaItem.displayTitle
            !room.mediaTitle.isNullOrBlank() && !room.mediaTitle.endsWith(".mp4") && !room.mediaTitle.endsWith(".mkv") -> room.mediaTitle
            else -> "Película en Sintonía"
        }

        txtMovieTitleRef?.text = displayTitle

        val genreText = peliculaMatch?.categoria ?: "Ciencia Ficción"
        val durationText = peliculaMatch?.duracion ?: "2h 49min"
        txtMovieMetaRef?.text = "$genreText • $durationText"

        val imgPoster = imgMoviePosterRef
        val imageUrl = peliculaMatch?.imageUrl
        if (imgPoster != null && !imageUrl.isNullOrBlank()) {
            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                val bmp = activity.descargasImagen(imageUrl)
                if (bmp != null) {
                    activity.runOnUiThread { imgPoster.setImageBitmap(bmp) }
                }
            }
        }
    }

    private fun configurarFlujoChat(roomId: String) {
        wsManager.requestChatHistory(roomId)

        chatHistoryJob?.cancel()
        chatHistoryJob = activity.lifecycleScope.launch {
            wsManager.chatHistoryFlow.collect { messages ->
                renderizarMensajesChat(messages)
            }
        }

        chatMsgJob?.cancel()
        chatMsgJob = activity.lifecycleScope.launch {
            wsManager.chatMessageFlow.collect {
                renderizarMensajesChat(wsManager.chatHistoryFlow.value)
            }
        }
    }

    private fun renderizarMensajesChat(messages: List<WatchPartyChatMessage>) {
        val container = chatMessagesContainerRef ?: return
        container.removeAllViews()

        txtChatUnreadBadgeRef?.text = "${messages.size}"

        val currentUserId = WatchPartyDialogs.obtenerIdUsuarioActual(activity)

        messages.forEach { msg ->
            val isMyMsg = msg.userId == currentUserId

            val msgRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, activity.dp(3), 0, activity.dp(3))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val imgAvatarMsg = ImageView(activity).apply {
                setImageResource(R.drawable.ic_user)
                setColorFilter(Color.rgb(196, 181, 253))
                setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4))
                background = VenusComponents.fondoRedondeado(activity.superficie2, 14, activity)
                layoutParams = LinearLayout.LayoutParams(activity.dp(28), activity.dp(28)).apply {
                    marginEnd = activity.dp(8)
                }
            }

            val bubbleContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(activity.dp(10), activity.dp(6), activity.dp(10), activity.dp(6))
                background = VenusComponents.fondoRedondeado(
                    if (isMyMsg) Color.rgb(45, 30, 70) else Color.rgb(27, 24, 35),
                    12, activity
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val timeStr = if (msg.createdAt.isNotBlank()) {
                try {
                    val formatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    formatted
                } catch (_: Exception) { "10:24 PM" }
            } else "10:24 PM"

            val txtSender = TextView(activity).apply {
                text = "${msg.name}  $timeStr"
                textSize = 10f
                setTextColor(if (msg.isHost) Color.rgb(255, 215, 0) else Color.rgb(196, 181, 253))
                setTypeface(null, Typeface.BOLD)
            }

            val txtBody = TextView(activity).apply {
                text = msg.message
                textSize = 13f
                setTextColor(Color.WHITE)
            }

            bubbleContainer.addView(txtSender)
            bubbleContainer.addView(txtBody)

            msgRow.addView(imgAvatarMsg)
            msgRow.addView(bubbleContainer)

            container.addView(msgRow)
        }

        scrollChatRef?.post { scrollChatRef?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun enviarReaccionRapida(emoji: String) {
        val room = wsManager.currentRoomState.value ?: return
        wsManager.sendChatMessage(room.id, emoji)

        val frame = videoContainerFrame ?: return
        animarEmojiFlotante(frame, emoji)
    }

    private fun animarEmojiFlotante(container: FrameLayout, emoji: String) {
        val txtEmoji = TextView(activity).apply {
            text = emoji
            textSize = 36f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = activity.dp(20)
            }
        }

        container.addView(txtEmoji)

        txtEmoji.animate()
            .translationY(-activity.dp(140).toFloat())
            .alpha(0f)
            .setDuration(1400)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    container.removeView(txtEmoji)
                }
            })
            .start()
    }

    private fun abrirPantallaCompleta(room: WatchPartyRoomState) {
        val mediaItem = cachedLibraryItems.find { it.id == room.mediaId }
        val peliculaMatch = activity.catalogosMock.find { it.id == mediaItem?.tmdbId || it.id == room.mediaId }

        val token = activity.repository.getToken() ?: ""
        val streamUrl = when {
            !room.mediaId.isNullOrBlank() -> "${ApiClient.getBaseUrl(activity)}media/${room.mediaId}/stream?token=$token"
            peliculaMatch != null && peliculaMatch.videoUrl.isNotBlank() -> peliculaMatch.videoUrl
            else -> ""
        }

        val title = room.mediaTitle ?: peliculaMatch?.titulo ?: "Watch Party"
        val pos = exoPlayer?.currentPosition ?: 0L

        val intent = Intent(activity, VideoPlayerActivity::class.java).apply {
            putExtra("VIDEO_URI", streamUrl)
            putExtra("VIDEO_TITLE", title)
            putExtra("VIDEO_POSITION", pos)
            putExtra("MEDIA_ID", room.mediaId ?: "")
        }
        activity.startActivity(intent)
    }

    private fun esHost(room: WatchPartyRoomState): Boolean {
        val currentUserId = WatchPartyDialogs.obtenerIdUsuarioActual(activity)
        if (currentUserId.isNotBlank() && room.hostId == currentUserId) {
            return true
        }
        if (room.hostId.isBlank()) {
            return true
        }
        val me = room.participants.find { it.userId == currentUserId }
        if (me != null && (me.role == "host" || me.userId == room.hostId)) {
            return true
        }
        if (room.participants.isEmpty() || (room.participants.size == 1 && room.participants[0].userId == currentUserId)) {
            return true
        }
        return false
    }

    private fun puedeControlar(room: WatchPartyRoomState): Boolean {
        if (esHost(room)) return true
        if (!room.hostOnlyControl) return true
        val currentUserId = WatchPartyDialogs.obtenerIdUsuarioActual(activity)
        val me = room.participants.find { it.userId == currentUserId }
        return me?.allowedControl == true
    }

    private fun formatearTiempoMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun liberarPlayer() {
        updateProgressRunnable?.let { handler.removeCallbacks(it) }
        updateProgressRunnable = null
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        currentLoadedStreamUrl = null
    }

    private fun mostrarDialogoSeleccionarPelicula(peliculasDisponibles: List<MediaFileDto>) {
        if (peliculasDisponibles.isEmpty()) {
            Toast.makeText(activity, "No hay películas con video disponible en el servidor", Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheet = BottomSheetDialog(activity)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(24), activity.dp(20), activity.dp(24))
            background = VenusComponents.fondoRedondeado(Color.rgb(23, 19, 33), 24, activity)
        }

        val txtTitulo = TextView(activity).apply {
            text = "Selecciona la película para la sala"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, activity.dp(4))
        }

        val txtSub = TextView(activity).apply {
            text = "Elije cuál película deseas reproducir en sintonía:"
            textSize = 13f
            setTextColor(Color.rgb(185, 178, 201))
            setPadding(0, 0, 0, activity.dp(16))
        }

        val scrollList = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(320)
            )
        }

        val containerItems = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        peliculasDisponibles.forEach { media ->
            val match = activity.catalogosMock.find { it.id == media.tmdbId }
            val title = when {
                match != null -> match.titulo
                !media.displayTitle.isNullOrBlank() -> media.displayTitle
                else -> media.fileName
            }

            val itemRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
                background = VenusComponents.fondoRedondeadoConRipple(activity.superficie, 12, activity)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = activity.dp(8)
                }
                isClickable = true
                setOnClickListener {
                    bottomSheet.dismiss()
                    val room = wsManager.currentRoomState.value
                    if (room != null && room.status == "active") {
                        wsManager.changeMedia(room.id, media.id)
                    } else {
                        iniciarWatchPartyParaMedio(media)
                    }
                }
            }

            val imgItem = ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(40), activity.dp(56)).apply {
                    marginEnd = activity.dp(12)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.ic_film_reel)
                background = VenusComponents.fondoRedondeado(activity.superficie2, 8, activity)
                clipToOutline = true
            }

            if (match != null && match.imageUrl.isNotBlank()) {
                java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                    val bmp = activity.descargasImagen(match.imageUrl)
                    if (bmp != null) {
                        activity.runOnUiThread { imgItem.setImageBitmap(bmp) }
                    }
                }
            }

            val colText = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val txtTitleItem = TextView(activity).apply {
                text = title
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
            }

            val txtBadgeItem = TextView(activity).apply {
                text = "Video disponible"
                textSize = 11f
                setTextColor(Color.rgb(0, 200, 83))
            }

            colText.addView(txtTitleItem)
            colText.addView(txtBadgeItem)

            itemRow.addView(imgItem)
            itemRow.addView(colText)

            containerItems.addView(itemRow)
        }

        scrollList.addView(containerItems)

        layout.addView(txtTitulo)
        layout.addView(txtSub)
        layout.addView(scrollList)

        bottomSheet.setContentView(layout)
        bottomSheet.show()
    }

    private fun iniciarWatchPartyParaMedio(media: MediaFileDto) {
        val title = if (!media.displayTitle.isNullOrBlank()) media.displayTitle else media.fileName

        WatchPartyDialogs.mostrarDialogoSalaCreadaConCodigo(
            context = activity,
            mediaId = media.id,
            title = title,
            onStartPlayer = {
                wsManager.connect()
            }
        )
    }

    private fun mostrarDialogoIngresarCodigo() {
        val bottomSheet = BottomSheetDialog(activity)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(28), activity.dp(24), activity.dp(28))
            background = VenusComponents.fondoRedondeado(Color.rgb(23, 19, 33), 24, activity)
        }

        val txtTitulo = TextView(activity).apply {
            text = "Unirse a Watch Party"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, activity.dp(8))
        }

        val txtMensaje = TextView(activity).apply {
            text = "Ingresa el código único de 6 caracteres enviado por el anfitrión:"
            textSize = 13f
            setTextColor(Color.rgb(185, 178, 201))
            setPadding(0, 0, 0, activity.dp(16))
        }

        val input = EditText(activity).apply {
            hint = "Ejemplo: VP-8921"
            setHintTextColor(Color.rgb(185, 178, 201))
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16))
            background = VenusComponents.fondoRedondeado(Color.rgb(17, 16, 21), 12, activity)
        }

        val filaBotones = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, activity.dp(20), 0, 0)
        }

        val btnCancelar = TextView(activity).apply {
            text = "CANCELAR"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12))
            background = VenusComponents.fondoRedondeado(Color.rgb(35, 30, 48), 12, activity)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = activity.dp(8)
            }
            isClickable = true
            setOnClickListener {
                bottomSheet.dismiss()
            }
        }

        val btnUnirse = TextView(activity).apply {
            text = "UNIRSE"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12))
            background = VenusComponents.fondoRedondeado(Color.rgb(135, 56, 245), 12, activity)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = activity.dp(8)
            }
            isClickable = true
            setOnClickListener {
                val code = input.text.toString().trim().uppercase()
                if (code.isNotBlank()) {
                    bottomSheet.dismiss()
                    wsManager.connect()
                    wsManager.joinRoom(code)
                    Toast.makeText(activity, "Conectando a la sala $code...", Toast.LENGTH_SHORT).show()
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
}
