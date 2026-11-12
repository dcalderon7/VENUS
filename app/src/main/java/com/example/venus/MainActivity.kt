package com.example.venus

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.venus.network.*
import com.example.venus.models.*
import com.example.venus.player.YouTubePlayerActivity
import com.example.venus.ui.home.HomeScreen
import com.example.venus.ui.components.VenusComponents
import com.example.venus.ui.dialogs.VenusDialogs
import com.example.venus.ui.details.VenusDetails
import com.example.venus.ui.explore.ExploreScreen
import com.example.venus.ui.mylist.MyListScreen
import com.example.venus.ui.profile.ProfileScreen
import com.example.venus.ui.library.LibraryScreens
import com.example.venus.ui.connection.ServerConnectionScreen
import com.example.venus.ui.auth.AuthScreens
import com.example.venus.util.VenusUtils
import com.example.venus.ui.player.EmbeddedVideoPlayer
import com.example.venus.data.repository.TmdbRepository
import com.example.venus.data.upload.UploadWorker
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@androidx.media3.common.util.UnstableApi
class MainActivity : AppCompatActivity() {

    internal lateinit var content: FrameLayout
    internal lateinit var selectorVideo: ActivityResultLauncher<Array<String>>
    internal lateinit var almacen: LocalMediaStore
    internal lateinit var repository: VenusRepository

    internal val fondo = Color.rgb(5, 5, 5)
    internal val superficie = Color.rgb(23, 19, 33)
    internal val superficie2 = Color.rgb(17, 16, 21)
    internal val rojo = Color.rgb(135, 56, 245)
    internal val verdeExito = Color.rgb(0, 200, 83)
    internal val blanco = Color.rgb(255, 255, 255)
    internal val gris = Color.rgb(185, 178, 201)

    internal val tmdbRepository by lazy { TmdbRepository(applicationContext) }

    internal var peliculasPopulares: MutableList<PeliculaMock>
        get() = tmdbRepository.peliculasPopulares
        set(value) { tmdbRepository.peliculasPopulares = value }

    internal var peliculasTendencia: MutableList<PeliculaMock>
        get() = tmdbRepository.peliculasTendencia
        set(value) { tmdbRepository.peliculasTendencia = value }

    internal var peliculasEstrenos: MutableList<PeliculaMock>
        get() = tmdbRepository.peliculasEstrenos
        set(value) { tmdbRepository.peliculasEstrenos = value }

    internal var peliculasMejorValoradas: MutableList<PeliculaMock>
        get() = tmdbRepository.peliculasMejorValoradas
        set(value) { tmdbRepository.peliculasMejorValoradas = value }

    internal var seriesPopulares: MutableList<PeliculaMock>
        get() = tmdbRepository.seriesPopulares
        set(value) { tmdbRepository.seriesPopulares = value }

    internal var seriesTendencia: MutableList<PeliculaMock>
        get() = tmdbRepository.seriesTendencia
        set(value) { tmdbRepository.seriesTendencia = value }

    internal var catalogosMock: MutableList<PeliculaMock>
        get() = tmdbRepository.catalogosMock
        set(value) { tmdbRepository.catalogosMock = value }

    internal var datosTmdbCargados: Boolean
        get() = tmdbRepository.datosTmdbCargados
        set(value) { tmdbRepository.datosTmdbCargados = value }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectorVideo = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                if (targetMediaTypeForUpload == "independent") {
                    importarVideo(uri, "", "independent", targetIndependentTitle)
                } else {
                    mostrarDialogoConfirmarSubida(uri, targetTmdbIdForUpload.ifBlank { "155" }, targetMediaTypeForUpload)
                }
            }
        }

        almacen = LocalMediaStore(this)
        repository = VenusRepository(applicationContext)

        setContentView(R.layout.activity_main)

        window.statusBarColor = fondo
        window.navigationBarColor = fondo

        content = findViewById(R.id.contentContainer)

        findViewById<View>(R.id.navHome).setOnClickListener {
            mostrarInicio()
        }

        findViewById<View>(R.id.navExplore).setOnClickListener {
            mostrarExplorar()
        }

        findViewById<View>(R.id.navWatchParty).setOnClickListener {
            mostrarWatchPartyScreen()
        }

        findViewById<View>(R.id.navMyList).setOnClickListener {
            mostrarMiLista()
        }

        findViewById<View>(R.id.navLibrary).setOnClickListener {
            mostrarMiBibliotecaIndependiente()
        }

        findViewById<View>(R.id.navProfile).setOnClickListener {
            mostrarPerfil()
        }

        findViewById<View>(R.id.btnSearchTop).setOnClickListener {
            mostrarExplorar()
        }

        findViewById<View>(R.id.txtProfile).setOnClickListener {
            mostrarPerfil()
        }

        if (!handleIntent(intent)) {
            if (repository.getToken().isNullOrBlank()) {
                mostrarPantallaAutenticacion()
            } else {
                cargarCatalogosTmdb {
                    mostrarInicio()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    internal fun handleIntent(intent: Intent?): Boolean {
        return AuthScreens(this).handleDeepLinkIntent(intent)
    }

    internal var targetTmdbIdForUpload = ""
    internal var targetMediaTypeForUpload = "movie"
    internal var targetIndependentTitle = ""
    internal var targetIndependentDesc = ""
    internal var targetIndependentCategory = "Personal"
    internal var targetIndependentYear = ""

    private fun getUriHash(uri: Uri): String {
        return try {
            val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            uri.toString().hashCode().toString()
        }
    }

    internal fun importarVideo(uri: Uri, tmdbId: String = "", mediaType: String = targetMediaTypeForUpload, displayTitle: String = targetIndependentTitle) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            almacen.agregarVideo(uri)

            val resolvedTmdbId = if (mediaType == "independent") "" else tmdbId.ifBlank { targetTmdbIdForUpload.ifBlank { "155" } }

            val bottomSheetDialog = BottomSheetDialog(this).apply {
                setCancelable(false)
            }
            val vistaSheet = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(32), dp(24), dp(36))
                background = fondoRedondeado(superficie, 28)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val tituloModal = TextView(this).apply {
                text = if (mediaType == "independent") "Subiendo Video..." else "Subiendo Película..."
                textSize = 20f
                setTextColor(blanco)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(20))
            }
            vistaSheet.addView(tituloModal)

            val progressContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply { bottomMargin = dp(20) }
                background = fondoRedondeado(superficie2, 60)
            }

            val txtPercent = TextView(this).apply {
                text = "0%"
                textSize = 22f
                setTextColor(rojo)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            progressContainer.addView(txtPercent)
            vistaSheet.addView(progressContainer)

            val descModal = TextView(this).apply {
                text = if (mediaType == "independent") "Subiendo \"$displayTitle\" al servidor..." else "Por favor espera mientras se sube tu película al servidor..."
                textSize = 13f
                setTextColor(gris)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(16))
            }
            vistaSheet.addView(descModal)

            val uriHash = getUriHash(uri)
            val workName = "${UploadWorker.WORK_NAME_PREFIX}$uriHash"
            val inputData = androidx.work.workDataOf(
                UploadWorker.KEY_URI to uri.toString(),
                UploadWorker.KEY_TMDB_ID to resolvedTmdbId,
                UploadWorker.KEY_MEDIA_TYPE to mediaType,
                UploadWorker.KEY_DISPLAY_TITLE to displayTitle
            )

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(inputData)
                .addTag("VENUS_UPLOAD_TAG")
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()

            val workManager = androidx.work.WorkManager.getInstance(applicationContext)
            workManager.enqueueUniqueWork(
                workName,
                androidx.work.ExistingWorkPolicy.KEEP,
                workRequest
            )

            val btnCancelarSubida = TextView(this).apply {
                text = "Cancelar"
                textSize = 14f
                setTextColor(blanco)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(12), dp(20), dp(12))
                background = GradientDrawable().apply {
                    setColor(superficie)
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(2), rojo)
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    workManager.cancelWorkById(workRequest.id)
                    bottomSheetDialog.dismiss()
                    Toast.makeText(this@MainActivity, "Subida cancelada", Toast.LENGTH_SHORT).show()
                }
            }
            vistaSheet.addView(btnCancelarSubida)

            bottomSheetDialog.setContentView(vistaSheet)
            bottomSheetDialog.show()

            workManager.getWorkInfoByIdLiveData(workRequest.id).observe(this) { workInfo ->
                if (workInfo != null) {
                    val progress = workInfo.progress.getInt(UploadWorker.KEY_PROGRESS, 0)
                    if (progress > 0) {
                        txtPercent.text = "$progress%"
                    }

                    if (workInfo.state.isFinished) {
                        bottomSheetDialog.dismiss()
                        if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                            mostrarDialogoExitoSubida()
                        } else if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                            val errorMsg = workInfo.outputData.getString(UploadWorker.KEY_RESULT_MESSAGE) ?: "Error desconocido"
                            mostrarDialogoErrorConexion("Error al subir archivo: $errorMsg")
                        } else if (workInfo.state == androidx.work.WorkInfo.State.CANCELLED) {
                            // Cancelado por el usuario
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Android no permitió conservar el acceso a este archivo.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo importar el video.", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun mostrarDialogoErrorConexion(mensaje: String) = VenusDialogs.mostrarDialogoErrorConexion(this, mensaje)

    internal fun mostrarDialogoConfirmarSubida(uri: Uri, tmdbId: String, mediaType: String) = VenusDialogs.mostrarDialogoConfirmarSubida(this, uri, tmdbId, mediaType)



    internal fun mostrarVistaCatalogoVacio(contenedor: LinearLayout) {
        val cajaVacia = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(48))
            background = fondoRedondeado(superficie, 24)
        }

        val icono = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(16) }
            setImageResource(R.drawable.ic_film_reel)
        }

        val tituloVacio = TextView(this).apply {
            text = "No hay películas"
            textSize = 20f
            setTextColor(blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }

        val descVacio = TextView(this).apply {
            text = "Aún no hay contenido en el catálogo. ¡Sé el primero en agregar una!"
            textSize = 13f
            setTextColor(gris)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }

        val btnAgregar = TextView(this).apply {
            text = "Agregar Película"
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_cloud_upload, 0, 0, 0)
            compoundDrawablePadding = dp(8)
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(this, android.content.res.ColorStateList.valueOf(Color.WHITE))
            textSize = 14f
            setTextColor(blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = fondoRedondeado(rojo, 16)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                mostrarDialogoAgregarPelicula("155", "movie")
            }
        }

        cajaVacia.addView(icono)
        cajaVacia.addView(tituloVacio)
        cajaVacia.addView(descVacio)
        cajaVacia.addView(btnAgregar)

        contenedor.addView(cajaVacia, margenAbajo(20))
    }

    internal fun nombreArchivo(uri: Uri): String = VenusUtils.nombreArchivo(this, uri)

    @androidx.media3.common.util.UnstableApi
    internal fun reproducirVideo(uriTexto: String) = EmbeddedVideoPlayer.reproducirVideo(this, uriTexto)

    internal fun eliminarReferencia(uri: String) {
        AlertDialog.Builder(this)
            .setTitle("Quitar de VENUS")
            .setMessage("Se quitará el video de tu biblioteca de VENUS. El archivo original no se eliminará.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Quitar") { _, _ ->
                almacen.eliminarVideo(uri)
                try {
                    contentResolver.releasePersistableUriPermission(
                        Uri.parse(uri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                mostrarBiblioteca()
            }
            .show()
    }

    @androidx.media3.common.util.UnstableApi
    fun mostrarWatchPartyScreen() {
        com.example.venus.ui.watchparty.WatchPartyScreen(this).mostrarWatchPartyScreen()
    }

    internal fun seleccionarNavegacion(seleccionado: Int) {
        val iconos = listOf(R.id.navHomeIcon, R.id.navExploreIcon, R.id.navWatchPartyIcon, R.id.navMyListIcon, R.id.navLibraryIcon, R.id.navProfileIcon)
        val etiquetas = listOf(R.id.navHomeLabel, R.id.navExploreLabel, R.id.navWatchPartyLabel, R.id.navMyListLabel, R.id.navLibraryLabel, R.id.navProfileLabel)

        iconos.forEachIndexed { indice, id ->
            findViewById<ImageView>(id).setColorFilter(if (indice == seleccionado) rojo else gris)
        }

        etiquetas.forEachIndexed { indice, id ->
            findViewById<TextView>(id).apply {
                setTextColor(if (indice == seleccionado) rojo else gris)
                setTypeface(null, if (indice == seleccionado) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    internal fun prepararPantalla(): LinearLayout {
        content.removeAllViews()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
            alpha = 0f
            animate().alpha(1f).setDuration(220).start()
        }
        scroll.addView(columna)
        content.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return columna
    }

    internal fun aplicarEfectoPulso(view: View) = VenusComponents.aplicarEfectoPulso(view)

    internal fun titulo(contenedor: LinearLayout, mensaje: String, tamano: Float = 22f) = VenusComponents.titulo(this, contenedor, mensaje, tamano)

    internal fun texto(contenedor: LinearLayout, mensaje: String, color: Int = gris, tamano: Float = 14f) = VenusComponents.texto(this, contenedor, mensaje, color, tamano)

    internal fun etiqueta(contenedor: LinearLayout, mensaje: String) = VenusComponents.etiqueta(this, contenedor, mensaje)

    internal fun boton(contenedor: LinearLayout, mensaje: String, accion: () -> Unit, secundario: Boolean = false) = VenusComponents.boton(this, contenedor, mensaje, accion, secundario)

    internal fun tarjeta(contenedor: LinearLayout, encabezado: String, descripcion: String, accion: (() -> Unit)? = null, alMantener: (() -> Unit)? = null) = VenusComponents.tarjeta(this, contenedor, encabezado, descripcion, accion, alMantener)

    internal fun fondoRedondeadoConRipple(color: Int, radio: Int) = VenusComponents.fondoRedondeadoConRipple(color, radio, this)

    internal fun tarjetaVideo(contenedor: LinearLayout, uriTexto: String, nombre: String, accion: () -> Unit, alMantener: () -> Unit) {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = fondoRedondeadoConRipple(superficie, 12)
            setPadding(dp(10), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { accion() }
            isLongClickable = true
            setOnLongClickListener {
                alMantener()
                true
            }
        }

        val marcoThumbnail = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(124), dp(70)).apply { marginEnd = dp(14) }
            background = fondoRedondeado(superficie2, 8)
            clipToOutline = true
        }

        val imgThumbnail = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val btnPlayOverlay = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)).apply { gravity = Gravity.CENTER }
            setImageResource(R.drawable.ic_play_white)
            background = fondoRedondeado(Color.argb(180, 229, 9, 20), 16)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        marcoThumbnail.addView(imgThumbnail)
        marcoThumbnail.addView(btnPlayOverlay)

        val pos = almacen.obtenerPosicion(uriTexto)
        val dur = almacen.obtenerDuracion(uriTexto)
        if (pos > 0L && dur > 0L) {
            val ratio = (pos.toFloat() / dur.toFloat()).coerceIn(0.05f, 1.0f)
            val barraFondo = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(3)).apply { gravity = Gravity.BOTTOM }
                background = GradientDrawable().apply { setColor(Color.argb(120, 255, 255, 255)) }
            }
            val barraProgreso = View(this).apply {
                layoutParams = FrameLayout.LayoutParams((dp(124) * ratio).toInt(), dp(3)).apply { gravity = Gravity.BOTTOM }
                background = GradientDrawable().apply { setColor(rojo) }
            }
            marcoThumbnail.addView(barraFondo)
            marcoThumbnail.addView(barraProgreso)
        }

        val infoColumna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val txtTitulo = TextView(this).apply {
            text = nombre
            textSize = 14f
            setTextColor(blanco)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val txtSub = TextView(this).apply {
            text = "Video local"
            textSize = 12f
            setTextColor(gris)
        }

        infoColumna.addView(txtTitulo)
        infoColumna.addView(txtSub)

        val btnAccion = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            setImageResource(R.drawable.ic_play_white)
            setColorFilter(rojo)
        }

        caja.addView(marcoThumbnail)
        caja.addView(infoColumna)
        caja.addView(btnAccion)

        contenedor.addView(caja, margenAbajo(10))

        val uri = Uri.parse(uriTexto)
        Executors.newSingleThreadExecutor().execute {
            val bmp = cargarMiniaturaVideo(uri)
            if (bmp != null) {
                runOnUiThread { imgThumbnail.setImageBitmap(bmp) }
            }
        }
    }

    internal fun cargarMiniaturaVideo(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, Size(640, 360), null)
            } else {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                bitmap
            }
        } catch (_: Exception) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    internal fun fetchTmdbList(endpoint: String, isSerieDefault: Boolean, categoryName: String) =
        tmdbRepository.fetchTmdbList(endpoint, isSerieDefault, categoryName)

    internal fun cargarCatalogosTmdb(onLoaded: () -> Unit) =
        tmdbRepository.cargarCatalogosTmdb(onLoaded)

    internal fun descargasImagen(urlString: String) =
        tmdbRepository.descargasImagen(urlString)

    internal fun reproducirUrlServidor(mediaId: String, streamUrl: String, tituloStr: String, tmdbId: String, mediaType: String) =
        EmbeddedVideoPlayer.reproducirUrlServidor(this, mediaId, streamUrl, tituloStr, tmdbId, mediaType)

    internal fun reproducirUrl(url: String, tituloStr: String) =
        EmbeddedVideoPlayer.reproducirUrl(this, url, tituloStr)

    internal fun reproducirDemo(pelicula: PeliculaMock) =
        EmbeddedVideoPlayer.reproducirDemo(this, pelicula)

    internal fun mostrarDetallePelicula(pelicula: PeliculaMock) = VenusDetails.mostrarDetallePelicula(this, pelicula)

    internal fun heroBanner(contenedor: LinearLayout, pelicula: PeliculaMock) {
        val cajaBanner = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(310)
            )
            background = fondoRedondeado(superficie, 20)
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setOnClickListener { mostrarDetallePelicula(pelicula) }
        }
        aplicarEfectoPulso(cajaBanner)

        val imgFondo = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val overlayGradiente = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(180)
            ).apply {
                gravity = Gravity.BOTTOM
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(140, 17, 16, 21), Color.argb(245, 5, 5, 5))
            )
        }

        val infoCaja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }

        val txtBadge = TextView(this).apply {
            text = "★  DESTACADO HOY • ⭐ ${pelicula.calificacion}".uppercase()
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(255, 193, 7))
            setTypeface(null, Typeface.BOLD)
        }

        val txtTitulo = TextView(this).apply {
            text = pelicula.titulo
            textSize = 22f
            setTextColor(blanco)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, dp(4))
        }

        val txtSub = TextView(this).apply {
            text = pelicula.descripcion
            textSize = 13f
            setTextColor(Color.rgb(196, 181, 253)) // #C4B5FD
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        infoCaja.addView(txtBadge)
        infoCaja.addView(txtTitulo)
        infoCaja.addView(txtSub)

        cajaBanner.addView(imgFondo)
        cajaBanner.addView(overlayGradiente)
        cajaBanner.addView(infoCaja)

        contenedor.addView(cajaBanner, margenAbajo(28))

        Executors.newSingleThreadExecutor().execute {
            val bmp = descargasImagen(pelicula.imageUrl)
            if (bmp != null) {
                runOnUiThread {
                    imgFondo.setImageBitmap(bmp)
                }
            }
        }
    }

    internal fun filaHorizontalPeliculas(
        contenedor: LinearLayout,
        subtituloTag: String,
        tituloSeccion: String,
        peliculas: List<PeliculaMock>
    ) {
        etiqueta(contenedor, subtituloTag)
        titulo(contenedor, tituloSeccion, 20f)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        peliculas.forEach { pelicula ->
            val tarjeta = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(140), dp(210)).apply {
                    marginEnd = dp(16)
                }
                background = fondoRedondeadoConRipple(superficie, 16)
                clipToOutline = true
                isClickable = true
                isFocusable = true
                setOnClickListener { mostrarDetallePelicula(pelicula) }
            }
            aplicarEfectoPulso(tarjeta)

            val imgPoster = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val overlayGradiente = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(120)
                ).apply {
                    gravity = Gravity.BOTTOM
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, Color.argb(180, 17, 16, 21), Color.argb(240, 5, 5, 5))
                )
            }

            val infoCaja = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM
                }
                setPadding(dp(12), dp(10), dp(12), dp(12))
            }

            val txtBadge = TextView(this).apply {
                text = "★ ${pelicula.calificacion}"
                textSize = 11f
                setTextColor(Color.rgb(255, 193, 7))
                setTypeface(null, Typeface.BOLD)
            }

            val txtTitulo = TextView(this).apply {
                text = pelicula.titulo
                textSize = 14f
                setTextColor(blanco)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val txtSub = TextView(this).apply {
                text = pelicula.duracion
                textSize = 11f
                setTextColor(Color.rgb(196, 181, 253)) // #C4B5FD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            infoCaja.addView(txtBadge)
            infoCaja.addView(txtTitulo)
            infoCaja.addView(txtSub)

            tarjeta.addView(imgPoster)
            tarjeta.addView(overlayGradiente)
            tarjeta.addView(infoCaja)

            val pos = almacen.obtenerPosicion(pelicula.id)
            val dur = almacen.obtenerDuracion(pelicula.id)
            if (pos > 0L && dur > 0L) {
                val ratio = (pos.toFloat() / dur.toFloat()).coerceIn(0.05f, 1.0f)
                val barraProgreso = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams((dp(140) * ratio).toInt(), dp(3)).apply {
                        gravity = Gravity.BOTTOM
                    }
                    background = GradientDrawable().apply { setColor(rojo) }
                }
                tarjeta.addView(barraProgreso)
            }

            fila.addView(tarjeta)

            Executors.newSingleThreadExecutor().execute {
                val bmp = descargasImagen(pelicula.imageUrl)
                if (bmp != null) {
                    runOnUiThread {
                        imgPoster.setImageBitmap(bmp)
                    }
                }
            }
        }

        scroll.addView(fila)
        contenedor.addView(scroll, margenAbajo(28))
    }

    internal fun mostrarInicio() {
        HomeScreen(this).mostrarInicio()
    }

    internal fun mostrarExplorar() {
        ExploreScreen(this).mostrarExplorar()
    }

    internal fun mostrarMiLista() {
        MyListScreen(this).mostrarMiLista()
    }

    internal fun mostrarDescargas() {
        LibraryScreens(this).mostrarDescargas()
    }

    internal fun mostrarBiblioteca() {
        LibraryScreens(this).mostrarBiblioteca()
    }

    internal fun mostrarPerfil() {
        ProfileScreen(this).mostrarPerfil()
    }

    internal fun mostrarMiBibliotecaIndependiente() {
        LibraryScreens(this).mostrarMiBibliotecaIndependiente()
    }

    internal fun mostrarDialogoCambiarPassword() = VenusDialogs.mostrarDialogoCambiarPassword(this)

    internal fun mostrarPantallaAutenticacion() {
        AuthScreens(this).mostrarPantallaAutenticacion()
    }

    internal fun mostrarPantallaRecuperarPassword() {
        AuthScreens(this).mostrarPantallaRecuperarPassword()
    }

    internal fun mostrarPantallaCompletarRecuperacion(token: String) {
        AuthScreens(this).mostrarPantallaCompletarRecuperacion(token)
    }

    internal fun mostrarConexion() {
        ServerConnectionScreen(this).mostrarConexion()
    }

    internal fun mostrarDialogoVinculacion() = VenusDialogs.mostrarDialogoVinculacion(this)

    internal fun mostrarDialogoEstadoPelicula(pelicula: PeliculaMock) = VenusDialogs.mostrarDialogoEstadoPelicula(this, pelicula)

    internal fun mostrarDialogoAgregarPelicula(tmdbId: String, mediaType: String) = VenusDialogs.mostrarDialogoAgregarPelicula(this, tmdbId, mediaType)

    internal fun mostrarDialogoExitoSubida() = VenusDialogs.mostrarDialogoExitoSubida(this)

    internal fun fondoRedondeado(color: Int, radio: Int) = VenusComponents.fondoRedondeado(color, radio, this)

    internal fun margenAbajo(margen: Int) = VenusComponents.margenAbajo(this, margen)

    internal fun dp(valor: Int) = VenusComponents.dp(this, valor)
}
