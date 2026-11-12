package com.example.venus.ui.watchparty

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.venus.MainActivity
import com.example.venus.R
import com.example.venus.network.ApiClient
import com.example.venus.network.MediaFileDto
import com.example.venus.network.VenusWebSocketManager
import com.example.venus.network.WatchPartyRoomState
import com.example.venus.ui.components.VenusComponents
import com.example.venus.ui.dialogs.WatchPartyDialogs
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
class WatchPartyScreen(private val activity: MainActivity) {

    private val wsManager by lazy { VenusWebSocketManager.getInstance(activity.applicationContext) }

    fun mostrarWatchPartyScreen() {
        activity.seleccionarNavegacion(2)
        val pantalla = activity.prepararPantalla()

        wsManager.connect()

        // 1. HEADER SECCIÓN (TITULO, INSIGNIA Y SUBTITULO)
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
            setTextColor(Color.rgb(0, 200, 83)) // Green #00C853
            setTypeface(null, Typeface.BOLD)
            setPadding(activity.dp(10), activity.dp(4), activity.dp(10), activity.dp(4))
            background = VenusComponents.fondoRedondeado(Color.argb(40, 0, 200, 83), 12, activity)
        }

        headerNav.addView(btnVolver)
        headerNav.addView(colTitles)
        headerNav.addView(badgeSalaCompartida)
        pantalla.addView(headerNav, activity.margenAbajo(16))

        // Banner de estado de sala activa (si existe sala en curso)
        val contenedorSalaActiva = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        pantalla.addView(contenedorSalaActiva)

        activity.lifecycleScope.launch {
            wsManager.currentRoomState.collect { room ->
                renderizarBannerSalaActiva(contenedorSalaActiva, room)
            }
        }

        // Estado de Carga
        val cargaContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(40), 0, activity.dp(40))
        }
        val progressBar = ProgressBar(activity).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(activity.rojo)
        }
        val txtCargando = TextView(activity).apply {
            text = "Verificando películas con video disponible en el servidor..."
            textSize = 14f
            setTextColor(activity.gris)
            setPadding(0, activity.dp(12), 0, 0)
            gravity = Gravity.CENTER
        }
        cargaContainer.addView(progressBar)
        cargaContainer.addView(txtCargando)
        pantalla.addView(cargaContainer)

        // Carga de películas exclusivas con archivo de video real disponible
        activity.lifecycleScope.launch {
            if (activity.catalogosMock.isEmpty()) {
                activity.cargarCatalogosTmdb {}
            }

            val res = activity.repository.getLibrary()
            pantalla.removeView(cargaContainer)

            val library = res.getOrNull()
            if (library == null) {
                val cajaError = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(activity.dp(20), activity.dp(20), activity.dp(20), activity.dp(20))
                    background = VenusComponents.fondoRedondeado(activity.superficie, 16, activity)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                val errText = TextView(activity).apply {
                    text = "No se pudo conectar con el servidor para verificar videos disponibles."
                    textSize = 14f
                    setTextColor(Color.rgb(255, 100, 100))
                    gravity = Gravity.CENTER
                }
                cajaError.addView(errText)
                pantalla.addView(cajaError)
                return@launch
            }

            // Filtrar ÚNICAMENTE elementos que tengan archivo de video real disponible en el servidor
            val todosLosArchivosDisponibles = mutableListOf<MediaFileDto>()
            todosLosArchivosDisponibles.addAll(library.cataloged)
            todosLosArchivosDisponibles.addAll(library.independent)

            cachedLibraryItems = todosLosArchivosDisponibles
            renderizarBannerSalaActiva(contenedorSalaActiva, wsManager.currentRoomState.value)

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

            // TEXTO EXPLICATIVO DE FILTRADO (PIE DE PÁGINA)
            val txtNotaExplicativa = TextView(activity).apply {
                text = "No aparecen películas que todavía no tengan un archivo de video en el servidor."
                textSize = 12f
                setTextColor(Color.rgb(185, 178, 201))
                setPadding(0, activity.dp(16), 0, activity.dp(16))
            }
            pantalla.addView(txtNotaExplicativa)

            // BOTONES DE ACCIÓN AL PIE DE PÁGINA
            val btnCrearSalaBottom = TextView(activity).apply {
                text = "Crear sala"
                textSize = 14f
                setTextColor(activity.blanco)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, activity.dp(14), 0, activity.dp(14))
                background = VenusComponents.fondoRedondeado(activity.superficie, 20, activity)
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

    private var cachedLibraryItems = listOf<MediaFileDto>()

    private fun renderizarBannerSalaActiva(contenedor: LinearLayout, room: WatchPartyRoomState?) {
        contenedor.removeAllViews()

        if (room == null || room.status != "active") {
            contenedor.visibility = View.GONE
            return
        }

        contenedor.visibility = View.VISIBLE
        val banner = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16))
            background = VenusComponents.fondoRedondeado(activity.superficie, 20, activity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = activity.dp(16)
            }
        }

        val rowHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(12))
        }

        val mediaItem = cachedLibraryItems.find { it.id == room.mediaId }
        val peliculaMatch = activity.catalogosMock.find { 
            it.id == mediaItem?.tmdbId || it.id == room.mediaId 
        }

        val displayTitle = when {
            peliculaMatch != null -> peliculaMatch.titulo
            mediaItem != null && !mediaItem.displayTitle.isNullOrBlank() -> mediaItem.displayTitle
            !room.mediaTitle.isNullOrBlank() && !room.mediaTitle.endsWith(".mp4") && !room.mediaTitle.endsWith(".mkv") -> room.mediaTitle
            else -> "Película en Sintonía"
        }

        val imgPosterBanner = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(50), activity.dp(70)).apply {
                marginEnd = activity.dp(14)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_film_reel)
            background = VenusComponents.fondoRedondeado(activity.superficie2, 10, activity)
            clipToOutline = true
        }

        val imageUrl = peliculaMatch?.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                val bmp = activity.descargasImagen(imageUrl)
                if (bmp != null) {
                    activity.runOnUiThread { imgPosterBanner.setImageBitmap(bmp) }
                }
            }
        }

        val colInfo = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val txtEstadoLabel = TextView(activity).apply {
            text = "SALA ACTIVA: ${room.roomCode}"
            textSize = 13f
            setTextColor(Color.rgb(135, 56, 245))
            setTypeface(null, Typeface.BOLD)
        }

        val txtTitleBanner = TextView(activity).apply {
            text = displayTitle
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val txtDetalle = TextView(activity).apply {
            text = "${room.participants.size} participante${if (room.participants.size > 1) "s" else ""} en vivo"
            textSize = 12f
            setTextColor(activity.gris)
        }

        colInfo.addView(txtEstadoLabel)
        colInfo.addView(txtTitleBanner)
        colInfo.addView(txtDetalle)

        rowHeader.addView(imgPosterBanner)
        rowHeader.addView(colInfo)

        val rowButtons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnEntrarVideo = TextView(activity).apply {
            text = "VER VIDEO"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(16), activity.dp(10), activity.dp(16), activity.dp(10))
            background = VenusComponents.fondoRedondeado(activity.rojo, 12, activity)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = activity.dp(8)
            }
            isClickable = true
            setOnClickListener {
                if (!room.mediaId.isNullOrBlank()) {
                    val token = activity.repository.getToken() ?: ""
                    val streamUrl = "${ApiClient.getBaseUrl(activity)}media/${room.mediaId}/stream?token=$token"
                    activity.reproducirUrlServidor(room.mediaId, streamUrl, displayTitle, "", "movie")
                } else {
                    Toast.makeText(activity, "Selecciona una película para comenzar el video en sala", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnVerControles = TextView(activity).apply {
            text = "CONTROLES / CHAT"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(16), activity.dp(10), activity.dp(16), activity.dp(10))
            background = VenusComponents.fondoRedondeado(activity.superficie2, 12, activity)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            setOnClickListener {
                WatchPartyDialogs.mostrarMenuWatchParty(activity)
            }
        }

        rowButtons.addView(btnEntrarVideo)
        rowButtons.addView(btnVerControles)

        banner.addView(rowHeader)
        banner.addView(rowButtons)

        contenedor.addView(banner)
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

        val scrollList = android.widget.ScrollView(activity).apply {
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
                    iniciarWatchPartyParaMedio(media)
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
                val token = activity.repository.getToken() ?: ""
                val streamUrl = "${ApiClient.getBaseUrl(activity)}media/${media.id}/stream?token=$token"
                activity.reproducirUrlServidor(media.id, streamUrl, title, media.tmdbId ?: "", media.mediaType)
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
