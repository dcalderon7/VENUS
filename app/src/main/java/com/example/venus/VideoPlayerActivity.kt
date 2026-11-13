package com.example.venus

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.venus.network.VenusRepository
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@UnstableApi
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var bottomHint: LinearLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var txtVideoTitle: TextView
    private lateinit var txtLoadingStatus: TextView
    private lateinit var btnClose: ImageButton
    private var btnFullscreen: ImageButton? = null
    private var btnSettings: ImageButton? = null
    private var btnVolume: ImageButton? = null
    private var btnPlayPauseSmall: ImageButton? = null
    private var txtTimeFormatted: TextView? = null
    private var btnCast: ImageButton? = null
    private var isCoverMode = false
    private var isMuted = false

    // In-App YouTube WebView Player
    private lateinit var webViewContainer: FrameLayout
    private lateinit var webViewPlayer: WebView
    private lateinit var btnCloseWebView: ImageButton

    private var exoPlayer: ExoPlayer? = null
    private var videoUri: String? = null
    private var videoTitle: String = "Video"
    private var videoPosition: Long = 0
    private var currentWindow: Int = 0
    private var playWhenReady: Boolean = true

    private var isFullscreen = false

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isSeekingGesture = false
    private var isVolumeGesture = false
    private var isBrightnessGesture = false
    private var initialVolume = 0
    private var initialBrightness = 0f
    private var maxVolume = 0
    private var audioManager: AudioManager? = null

    private val speedOptions = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var currentSpeedIndex = 2 // 1.0x

    private val venusSensorManager by lazy { com.example.venus.sensor.VenusSensorManager(this) }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen immersive setup
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.activity_video_player)

        // Get intent data
        videoUri = intent.getStringExtra("VIDEO_URI")
        videoTitle = intent.getStringExtra("VIDEO_TITLE") ?: "Video local"
        videoPosition = intent.getLongExtra("VIDEO_POSITION", 0)
        currentWindow = intent.getIntExtra("VIDEO_WINDOW", 0)

        // Si no se especificó posición explícita en el intent, intentar recuperar posición guardada
        if (videoPosition == 0L && videoUri != null) {
            val guardado = LocalMediaStore(this).obtenerPosicion(videoUri!!)
            if (guardado > 0L) {
                videoPosition = guardado
            }
        }

        // Take persistable URI permission for playback only if scheme is content
        videoUri?.let { uriString ->
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    Log.w("VideoPlayer", "No persistable URI permission", e)
                }
            }
        }

        initViews()
        initAudioManager()
        initTouchListener()
        setupClickListeners()
        setupSystemBars()

        txtVideoTitle.text = videoTitle

        initPlayer()
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        bottomHint = findViewById(R.id.bottomHint)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        txtLoadingStatus = findViewById(R.id.txtLoadingStatus)

        // WebView container
        webViewContainer = findViewById(R.id.webViewContainer)
        webViewPlayer = findViewById(R.id.webViewPlayer)
        btnCloseWebView = findViewById(R.id.btnCloseWebView)

        // Controller layout views
        txtVideoTitle = playerView.findViewById(R.id.txtVideoTitle) ?: findViewById(R.id.txtVideoTitle)
        btnClose = playerView.findViewById(R.id.btnClose) ?: findViewById(R.id.btnClose)
        btnCast = playerView.findViewById(R.id.btnCast)
        btnSettings = playerView.findViewById(R.id.btnSettings)
        btnVolume = playerView.findViewById(R.id.btnVolume)
        btnPlayPauseSmall = playerView.findViewById(R.id.exo_play_pause_small)
        btnFullscreen = playerView.findViewById(R.id.btnFullscreen)
        txtTimeFormatted = playerView.findViewById(R.id.txtTimeFormatted)
    }

    private fun initAudioManager() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        initialVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVolume / 2)
    }

    private fun initTouchListener() {
        playerView.setOnTouchListener { _, event ->
            handleTouchEvent(event)
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                isSeekingGesture = false
                isVolumeGesture = false
                isBrightnessGesture = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - initialTouchX
                val deltaY = event.y - initialTouchY

                if (!isSeekingGesture && !isVolumeGesture && !isBrightnessGesture) {
                    if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && kotlin.math.abs(deltaX) > 60) {
                        isSeekingGesture = true
                    } else if (kotlin.math.abs(deltaY) > 40) {
                        if (event.x > width / 2f) {
                            isVolumeGesture = true
                            initialVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                        } else {
                            isBrightnessGesture = true
                            initialBrightness = window.attributes.screenBrightness
                            if (initialBrightness < 0) {
                                try {
                                    initialBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                                } catch (_: Exception) {
                                    initialBrightness = 0.5f
                                }
                            }
                        }
                    }
                }

                if (isSeekingGesture && exoPlayer != null && exoPlayer!!.duration > 0) {
                    val seekDelta = (deltaX / width) * exoPlayer!!.duration
                    val newPosition = (exoPlayer!!.currentPosition + seekDelta.toLong())
                        .coerceAtLeast(0)
                        .coerceAtMost(exoPlayer!!.duration)
                    exoPlayer!!.seekTo(newPosition)
                    return true
                } else if (isVolumeGesture) {
                    val volumeChange = (-deltaY / height * maxVolume).toInt()
                    val newVolume = (initialVolume + volumeChange).coerceAtLeast(0).coerceAtMost(maxVolume)
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                    return true
                } else if (isBrightnessGesture) {
                    val brightnessChange = -deltaY / height
                    var newBrightness = initialBrightness + brightnessChange
                    newBrightness = newBrightness.coerceIn(0.01f, 1f)
                    setScreenBrightness(newBrightness)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSeekingGesture = false
                isVolumeGesture = false
                isBrightnessGesture = false
            }
        }
        return false
    }

    private fun initPlayer() {
        val urlStr = videoUri ?: return

        // Comprobar si es un video/tráiler de YouTube
        val ytId = extraerYouTubeId(urlStr)
        if (ytId.isNotBlank()) {
            cargarYouTubeWebView(ytId)
            return
        }

        if (exoPlayer != null) return

        val trackSelector = DefaultTrackSelector(this)

        exoPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()

        playerView.player = exoPlayer
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> showLoading("Cargando video...")
                    Player.STATE_READY -> {
                        hideLoading()
                        if (videoPosition > 0) {
                            exoPlayer?.seekTo(currentWindow, videoPosition)
                            videoPosition = 0
                        }
                        showBottomHint()
                    }
                    Player.STATE_ENDED -> hideLoading()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hideLoading()
                // Fallback a WebView para URLs no-YouTube que fallen con ExoPlayer
                val urlStr = videoUri ?: ""
                val ytId = extraerYouTubeId(urlStr)
                if (ytId.isBlank() && urlStr.startsWith("http")) {
                    cargarVideoWebViewGenerico(urlStr)
                } else {
                    Toast.makeText(
                        this@VideoPlayerActivity,
                        "Error al reproducir el video: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        prepareMediaSource()
    }

    private fun extraerYouTubeId(url: String): String {
        return try {
            val uri = Uri.parse(url)
            if (url.contains("youtu.be/")) {
                uri.lastPathSegment ?: ""
            } else if (url.contains("watch")) {
                uri.getQueryParameter("v") ?: ""
            } else if (url.contains("embed/")) {
                uri.lastPathSegment ?: ""
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun cargarYouTubeWebView(videoId: String) {
        playerView.visibility = View.GONE
        webViewContainer.visibility = View.VISIBLE
        hideLoading()

        webViewPlayer.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
        }
        webViewPlayer.webChromeClient = WebChromeClient()
        webViewPlayer.webViewClient = WebViewClient()

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000; overflow: hidden; }
              iframe { width: 100%; height: 100%; border: none; }
            </style>
            </head>
            <body>
            <iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&controls=1&rel=0&modestbranding=1&playsinline=1"
                    allow="autoplay; encrypted-media; fullscreen"
                    allowfullscreen></iframe>
            </body>
            </html>
        """.trimIndent()

        webViewPlayer.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun cargarVideoWebViewGenerico(url: String) {
        playerView.visibility = View.GONE
        webViewContainer.visibility = View.VISIBLE
        hideLoading()

        webViewPlayer.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
        }
        webViewPlayer.webChromeClient = WebChromeClient()
        webViewPlayer.webViewClient = WebViewClient()

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000; overflow: hidden; }
              video { width: 100%; height: 100%; object-fit: contain; }
            </style>
            </head>
            <body>
            <video controls autoplay playsinline webkit-playsinline>
                <source src="$url" type="video/mp4">
                Tu navegador no soporta reproducción de video HTML5.
            </video>
            </body>
            </html>
        """.trimIndent()

        webViewPlayer.loadDataWithBaseURL("https://", html, "text/html", "UTF-8", null)
    }

    private fun prepareMediaSource() {
        videoUri?.let { uriString ->
            showLoading("Cargando video...")
            val uri = Uri.parse(uriString)

            if (uri.scheme == "content") {
                try {
                    grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)

            val token = getSharedPreferences("venus_secure_prefs", Context.MODE_PRIVATE).getString("jwt_token", null)
            if (!token.isNullOrBlank()) {
                httpDataSourceFactory.setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            }

            val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            val mediaItem = MediaItem.fromUri(uri)
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

            exoPlayer?.setMediaSource(mediaSource)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = playWhenReady
        }
    }

    private fun setupClickListeners() {
        btnClose.setOnClickListener { savePositionAndFinish() }
        btnCloseWebView.setOnClickListener { savePositionAndFinish() }
        btnPlayPauseSmall?.setOnClickListener { togglePlayPause() }
        btnVolume?.setOnClickListener { toggleVolumeMute() }
        btnSettings?.setOnClickListener { showSettingsMenu() }
        btnCast?.setOnClickListener { showCastAndWirelessDisplayMenu() }
        btnFullscreen?.setOnClickListener { toggleResizeMode() }
    }

    private fun togglePlayPause() {
        if (exoPlayer?.isPlaying == true) {
            exoPlayer?.pause()
        } else {
            exoPlayer?.play()
        }
    }

    private fun toggleVolumeMute() {
        isMuted = !isMuted
        if (isMuted) {
            exoPlayer?.volume = 0f
            btnVolume?.setColorFilter(Color.rgb(255, 100, 100))
            Toast.makeText(this, "Audio silenciado", Toast.LENGTH_SHORT).show()
        } else {
            exoPlayer?.volume = 1f
            btnVolume?.setColorFilter(Color.rgb(0, 168, 255))
            Toast.makeText(this, "Audio activado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSettingsMenu() {
        val anchor = btnSettings ?: btnClose
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Velocidad de reproducción (${speedOptions[currentSpeedIndex]}x)").setOnMenuItemClickListener {
            showSpeedMenu()
            true
        }
        popup.menu.add("Audio: Español (Estéreo)").setOnMenuItemClickListener {
            Toast.makeText(this, "Audio: Español (Estéreo)", Toast.LENGTH_SHORT).show()
            true
        }
        popup.menu.add("Subtítulos: Desactivados").setOnMenuItemClickListener {
            Toast.makeText(this, "Subtítulos: Desactivados", Toast.LENGTH_SHORT).show()
            true
        }
        popup.menu.add("Calidad de video: 1080p HD").setOnMenuItemClickListener {
            Toast.makeText(this, "Calidad: 1080p HD", Toast.LENGTH_SHORT).show()
            true
        }
        popup.menu.add("Modo PIP (Picture-in-Picture)").setOnMenuItemClickListener {
            enterPipMode()
            true
        }
        popup.menu.add("Gesto Flip-to-Pause (${if (venusSensorManager.isFlipToPauseEnabled()) "Activado" else "Desactivado"})").setOnMenuItemClickListener {
            val newState = !venusSensorManager.isFlipToPauseEnabled()
            venusSensorManager.setFlipToPauseEnabled(newState)
            if (newState) {
                venusSensorManager.startListening(
                    onFlipDown = {
                        if (exoPlayer?.isPlaying == true) {
                            exoPlayer?.pause()
                            Toast.makeText(this@VideoPlayerActivity, "Pausado (Gesto voltear)", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Toast.makeText(this, "Flip-to-Pause Activado (Coloca la pantalla boca abajo para pausar)", Toast.LENGTH_LONG).show()
            } else {
                venusSensorManager.stopListening()
                Toast.makeText(this, "Flip-to-Pause Desactivado", Toast.LENGTH_SHORT).show()
            }
            true
        }
        popup.menu.add("Watch Party (Salas Sincronizadas)").setOnMenuItemClickListener {
            com.example.venus.ui.dialogs.WatchPartyDialogs.mostrarMenuWatchParty(this)
            true
        }
        popup.show()
    }

    private fun showCastAndWirelessDisplayMenu() {
        val anchor = btnCast ?: btnClose
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Transmitir con Google Cast").setOnMenuItemClickListener {
            Toast.makeText(this, "Buscando dispositivos Google Cast en la red local...", Toast.LENGTH_LONG).show()
            true
        }
        popup.menu.add("Pantalla Inalámbrica / Miracast").setOnMenuItemClickListener {
            try {
                val castIntent = Intent("android.settings.CAST_SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(castIntent)
            } catch (_: Exception) {
                try {
                    val wifiDisplayIntent = Intent("android.settings.WIFI_DISPLAY_SETTINGS").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(wifiDisplayIntent)
                } catch (_: Exception) {
                    Toast.makeText(this, "No se encontraron pantallas inalámbricas o Miracast compatibles.", Toast.LENGTH_LONG).show()
                }
            }
            true
        }
        popup.show()
    }

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun updateTimeDisplay() {
        val pos = exoPlayer?.currentPosition ?: 0L
        val dur = exoPlayer?.duration ?: 0L
        val posStr = formatMs(pos)
        val durStr = if (dur > 0) formatMs(dur) else "00:00"
        txtTimeFormatted?.text = "$posStr / $durStr"
    }

    private fun toggleResizeMode() {
        isCoverMode = !isCoverMode
        playerView.resizeMode = if (isCoverMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        Toast.makeText(this, "Ajuste de pantalla: ${if (isCoverMode) "Cover" else "Fit"}", Toast.LENGTH_SHORT).show()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
            } catch (_: Exception) {
                Toast.makeText(this, "Modo PIP no soportado", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Modo PIP requiere Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSystemBars() {
        hideSystemBars()
    }

    private fun showSpeedMenu() {
        val popup = PopupMenu(this, btnSettings ?: btnClose)
        speedOptions.forEachIndexed { index, speed ->
            popup.menu.add("Velocidad ×${"%.2f".format(speed)}").setOnMenuItemClickListener {
                setPlaybackSpeed(speed)
                currentSpeedIndex = index
                true
            }
            if (index == currentSpeedIndex) {
                popup.menu.getItem(index)?.setCheckable(true)?.isChecked = true
            }
        }
        popup.show()
    }

    private fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
        Toast.makeText(this, "Velocidad: ×${"%.2f".format(speed)}", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            btnFullscreen?.setImageResource(R.drawable.ic_fullscreen_exit_white)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            btnFullscreen?.setImageResource(R.drawable.ic_fullscreen_white)
        }
    }

    private fun showBottomHint() {
        bottomHint.visibility = View.VISIBLE
        handler.postDelayed({ bottomHint.visibility = View.GONE }, 4000)
    }

    private fun showLoading(message: String) {
        loadingOverlay.visibility = View.VISIBLE
        txtLoadingStatus.text = message
    }

    private fun hideLoading() {
        loadingOverlay.visibility = View.GONE
    }

    private fun setScreenBrightness(brightness: Float) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24) {
            exoPlayer?.playWhenReady = playWhenReady
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (Util.SDK_INT < 24 || exoPlayer == null) {
            initPlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        guardarProgresoActual()
        if (Util.SDK_INT < 24) {
            releasePlayer()
        } else {
            playWhenReady = exoPlayer?.playWhenReady == true
            currentWindow = exoPlayer?.currentMediaItemIndex ?: 0
            videoPosition = exoPlayer?.currentPosition ?: 0
        }
        if (::webViewPlayer.isInitialized) {
            webViewPlayer.onPause()
        }
    }

    override fun onStop() {
        super.onStop()
        guardarProgresoActual()
        if (Util.SDK_INT >= 24) {
            releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        guardarProgresoActual()
        if (::webViewPlayer.isInitialized) {
            webViewPlayer.destroy()
        }
        releasePlayer()
    }

    private fun guardarProgresoActual() {
        val uriStr = videoUri ?: return
        val pos = exoPlayer?.currentPosition ?: 0L
        val dur = exoPlayer?.duration ?: 0L
        if (pos > 0L && dur > 0L) {
            val mediaId = intent.getStringExtra("MEDIA_ID") ?: uriStr
            LocalMediaStore(this).guardarPosicion(mediaId, pos, dur)

            val tmdbId = intent.getStringExtra("TMDB_ID")
            val mediaType = intent.getStringExtra("MEDIA_TYPE")
            if (!mediaId.isNullOrBlank() && !tmdbId.isNullOrBlank() && !mediaType.isNullOrBlank()) {
                lifecycleScope.launch {
                    try {
                        VenusRepository(this@VideoPlayerActivity).saveHistory(mediaId, tmdbId, mediaType, pos, dur)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun releasePlayer() {
        guardarProgresoActual()
        exoPlayer?.run {
            playWhenReady = this.playWhenReady
            currentWindow = currentMediaItemIndex
            videoPosition = currentPosition
            release()
        }
        exoPlayer = null
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isFullscreen = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        btnFullscreen?.setImageResource(
            if (isFullscreen) R.drawable.ic_fullscreen_exit_white else R.drawable.ic_fullscreen_white
        )
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            savePositionAndFinish()
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun savePositionAndFinish() {
        val result = intent.apply {
            putExtra("VIDEO_POSITION", exoPlayer?.currentPosition ?: 0)
            putExtra("VIDEO_WINDOW", exoPlayer?.currentMediaItemIndex ?: 0)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private val width: Float
        get() = resources.displayMetrics.widthPixels.toFloat()

    private val height: Float
        get() = resources.displayMetrics.heightPixels.toFloat()
}