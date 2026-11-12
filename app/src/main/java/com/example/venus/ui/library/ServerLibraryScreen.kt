package com.example.venus.ui.library

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.venus.MainActivity
import com.example.venus.R
import com.example.venus.network.ApiClient
import com.example.venus.ui.components.VenusComponents
import com.example.venus.ui.dialogs.VenusDialogs
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@androidx.media3.common.util.UnstableApi
class ServerLibraryScreen(private val activity: MainActivity) {

    fun mostrarMiBibliotecaIndependiente() {
        activity.seleccionarNavegacion(3)
        val pantalla = activity.prepararPantalla()

        val headerNav = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(16))
        }

        val btnVolver = ImageView(activity).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(activity.blanco)
            setPadding(activity.dp(8), activity.dp(8), activity.dp(16), activity.dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { activity.mostrarInicio() }
        }

        val tituloNav = TextView(activity).apply {
            text = "Biblioteca del Servidor"
            textSize = 24f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
        }

        headerNav.addView(btnVolver)
        headerNav.addView(tituloNav)
        pantalla.addView(headerNav)

        // Estado de carga inicial con estilo moderno
        val cargaContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(60), 0, activity.dp(60))
        }
        val progressBar = ProgressBar(activity).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(activity.rojo)
        }
        val txtCargando = TextView(activity).apply {
            text = "Sincronizando biblioteca del servidor..."
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
            android.util.Log.d(
                "VenusLibrary",
                "Library fetched - Cataloged: ${library?.cataloged?.size}, Independent: ${library?.independent?.size}"
            )
            if (library == null) {
                val cajaError = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(activity.dp(20), activity.dp(20), activity.dp(20), activity.dp(20))
                    background = VenusComponents.fondoRedondeado(activity.superficie, 16, activity)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                val errText = TextView(activity).apply {
                    text = "No se pudo conectar con el servidor para cargar la biblioteca."
                    textSize = 14f
                    setTextColor(Color.rgb(255, 100, 100))
                    setPadding(0, 0, 0, activity.dp(12))
                    gravity = Gravity.CENTER
                }
                cajaError.addView(errText)
                val btnRetry = TextView(activity).apply {
                    text = "REINTENTAR"
                    textSize = 13f
                    setTextColor(activity.blanco)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(activity.dp(20), activity.dp(12), activity.dp(20), activity.dp(12))
                    background = VenusComponents.fondoRedondeado(activity.rojo, 12, activity)
                    isClickable = true
                    setOnClickListener { mostrarMiBibliotecaIndependiente() }
                }
                cajaError.addView(btnRetry)
                pantalla.addView(cajaError)
                return@launch
            }

            // Botón de subida destacado
            activity.boton(pantalla, "＋ SUBIR VIDEO INDEPENDIENTE", {
                VenusDialogs.mostrarDialogoSubirVideoIndependiente(activity)
            })

            // Cálculo dinámico de ancho para cuadrícula de 2 columnas
            val screenWidth = activity.resources.displayMetrics.widthPixels
            val paddingTotal = activity.dp(40) // 20dp cada lado
            val spacing = activity.dp(12)
            val cardWidth = (screenWidth - paddingTotal - spacing) / 2
            val posterHeight = (cardWidth * 1.45f).toInt()

            // 1. Películas y series del catálogo (Cuadrícula)
            activity.etiqueta(pantalla, "Películas y series del catálogo")
            activity.titulo(pantalla, "Catálogo sincronizado (${library.cataloged.size})", 20f)

            if (library.cataloged.isEmpty()) {
                val cajaVaciaCat = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(activity.dp(20), activity.dp(24), activity.dp(20), activity.dp(24))
                    background = VenusComponents.fondoRedondeado(activity.superficie, 16, activity)
                    gravity = Gravity.CENTER
                }
                val txtVacia = TextView(activity).apply {
                    text = "No hay películas del catálogo con video en el servidor."
                    textSize = 13f
                    setTextColor(activity.gris)
                    gravity = Gravity.CENTER
                }
                cajaVaciaCat.addView(txtVacia)
                pantalla.addView(cajaVaciaCat, activity.margenAbajo(16))
            } else {
                var rowLayout: LinearLayout? = null
                library.cataloged.forEachIndexed { index, media ->
                    if (index % 2 == 0) {
                        rowLayout = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = activity.dp(14)
                            }
                        }
                        pantalla.addView(rowLayout)
                    }

                    val peliculaMatch = activity.catalogosMock.find { it.id == media.tmdbId }
                    val title = when {
                        peliculaMatch != null -> peliculaMatch.titulo
                        activity.catalogosMock.isEmpty() -> "Cargando información..."
                        else -> "Película TMDb #${media.tmdbId}"
                    }

                    val tarjeta = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            cardWidth,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (index % 2 == 0) marginEnd = spacing
                        }
                        background = VenusComponents.fondoRedondeadoConRipple(
                            activity.superficie,
                            16,
                            activity
                        )
                        setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(10))
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            val token = activity.repository.getToken() ?: ""
                            val streamUrl =
                                "${ApiClient.getBaseUrl(activity)}media/${media.id}/stream?token=$token"
                            activity.reproducirUrlServidor(
                                media.id,
                                streamUrl,
                                title,
                                media.tmdbId ?: "",
                                media.mediaType
                            )
                        }
                    }
                    VenusComponents.aplicarEfectoPulso(tarjeta)

                    val marcoImg = FrameLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            posterHeight
                        ).apply {
                            bottomMargin = activity.dp(8)
                        }
                        background =
                            VenusComponents.fondoRedondeado(activity.superficie2, 12, activity)
                        clipToOutline = true
                    }

                    val imgPoster = ImageView(activity).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageResource(R.drawable.ic_film_reel)
                    }
                    marcoImg.addView(imgPoster)

                    val txtT = TextView(activity).apply {
                        text = title
                        textSize = 14f
                        setTextColor(activity.blanco)
                        setTypeface(null, Typeface.BOLD)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }

                    val txtD = TextView(activity).apply {
                        text =
                            peliculaMatch?.duracion?.let { "★ ${peliculaMatch.calificacion} • $it" }
                                ?: "${(media.fileSize / (1024 * 1024))} MB"
                        textSize = 11f
                        setTextColor(Color.rgb(196, 181, 253))
                        maxLines = 1
                    }

                    tarjeta.addView(marcoImg)
                    tarjeta.addView(txtT)
                    tarjeta.addView(txtD)

                    rowLayout?.addView(tarjeta)

                    if (peliculaMatch != null && peliculaMatch.imageUrl.isNotBlank()) {
                        Executors.newSingleThreadExecutor().execute {
                            val bmp = activity.descargasImagen(peliculaMatch.imageUrl)
                            if (bmp != null) {
                                activity.runOnUiThread { imgPoster.setImageBitmap(bmp) }
                            }
                        }
                    }
                }
            }


// 2. Videos independientes (Cuadrícula)
            activity.etiqueta(pantalla, "Videos independientes")
            activity.titulo(pantalla, "Videos independientes (${library.independent.size})", 20f)

            if (library.independent.isEmpty()) {
                val cajaVaciaInd = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(activity.dp(20), activity.dp(24), activity.dp(20), activity.dp(24))
                    background = VenusComponents.fondoRedondeado(activity.superficie, 16, activity)
                    gravity = Gravity.CENTER
                }

                val txtVacia = TextView(activity).apply {
                    text = "No hay videos independientes en el servidor."
                    textSize = 13f
                    setTextColor(activity.gris)
                    gravity = Gravity.CENTER
                }

                cajaVaciaInd.addView(txtVacia)
                pantalla.addView(cajaVaciaInd, activity.margenAbajo(16))
            } else {
                var rowLayoutInd: LinearLayout? = null

                library.independent.forEachIndexed { index, media ->
                    if (index % 2 == 0) {
                        rowLayoutInd = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = activity.dp(14)
                            }
                        }
                        pantalla.addView(rowLayoutInd)
                    }

                    val title = if (!media.displayTitle.isNullOrBlank()) {
                        media.displayTitle
                    } else {
                        media.fileName
                    }

                    val tarjeta = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            cardWidth,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (index % 2 == 0) marginEnd = spacing
                        }
                        background = VenusComponents.fondoRedondeadoConRipple(
                            activity.superficie, 16, activity
                        )
                        setPadding(
                            activity.dp(8), activity.dp(8),
                            activity.dp(8), activity.dp(10)
                        )
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            val token = activity.repository.getToken() ?: ""
                            val streamUrl =
                                "${ApiClient.getBaseUrl(activity)}media/${media.id}/stream?token=$token"
                            activity.reproducirUrlServidor(
                                media.id, streamUrl, title, "", media.mediaType
                            )
                        }
                    }

                    VenusComponents.aplicarEfectoPulso(tarjeta)

                    val marcoImg = FrameLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            posterHeight
                        ).apply {
                            bottomMargin = activity.dp(8)
                        }
                        background = VenusComponents.fondoRedondeado(
                            activity.superficie2, 12, activity
                        )
                        clipToOutline = true
                    }

                    val imgPoster = ImageView(activity).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageResource(R.drawable.ic_film_reel)
                        setColorFilter(Color.rgb(100, 90, 120))
                    }

                    marcoImg.addView(imgPoster)

                    val txtT = TextView(activity).apply {
                        text = title
                        textSize = 14f
                        setTextColor(activity.blanco)
                        setTypeface(null, Typeface.BOLD)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }

                    val txtD = TextView(activity).apply {
                        text = "Local • ${media.fileSize / (1024 * 1024)} MB"
                        textSize = 11f
                        setTextColor(Color.rgb(196, 181, 253))
                        maxLines = 1
                    }

                    val rowAcciones = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, activity.dp(8), 0, 0)
                    }

                    val btnPlay = TextView(activity).apply {
                        text = "Reproducir"
                        textSize = 12f
                        setTextColor(activity.blanco)
                        setTypeface(null, Typeface.BOLD)
                        setPadding(
                            activity.dp(8), activity.dp(8),
                            activity.dp(8), activity.dp(8)
                        )
                        background = VenusComponents.fondoRedondeado(
                            activity.rojo, 10, activity
                        )
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = activity.dp(6)
                        }
                        isClickable = true
                        setOnClickListener {
                            val token = activity.repository.getToken() ?: ""
                            val streamUrl =
                                "${ApiClient.getBaseUrl(activity)}media/${media.id}/stream?token=$token"
                            activity.reproducirUrlServidor(
                                media.id, streamUrl, title, "", media.mediaType
                            )
                        }
                    }

                    val rowBottom = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val btnEditar = TextView(activity).apply {
                        text = "Editar"
                        textSize = 11f
                        setTextColor(activity.blanco)
                        setTypeface(null, Typeface.BOLD)
                        setPadding(
                            activity.dp(6), activity.dp(6),
                            activity.dp(6), activity.dp(6)
                        )
                        background = VenusComponents.fondoRedondeado(
                            activity.superficie2, 8, activity
                        )
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                        ).apply {
                            marginEnd = activity.dp(4)
                        }
                        isClickable = true
                        setOnClickListener {
                            val input = EditText(activity).apply {
                                setText(title)
                                setTextColor(activity.blanco)
                                setPadding(
                                    activity.dp(16), activity.dp(14),
                                    activity.dp(16), activity.dp(14)
                                )
                                background = VenusComponents.fondoRedondeado(
                                    activity.superficie2, 12, activity
                                )
                            }

                            AlertDialog.Builder(activity)
                                .setTitle("Editar Título del Video")
                                .setView(input)
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Guardar") { _, _ ->
                                    val newTitle = input.text.toString().trim()
                                    if (newTitle.isNotBlank()) {
                                        activity.lifecycleScope.launch {
                                            val success = activity.repository.updateMediaTitle(
                                                media.id, newTitle
                                            )
                                            if (success) {
                                                Toast.makeText(
                                                    activity, "Título actualizado",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                mostrarMiBibliotecaIndependiente()
                                            } else {
                                                Toast.makeText(
                                                    activity, "Error al actualizar título",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                                .show()
                        }
                    }

                    val btnEliminar = TextView(activity).apply {
                        text = "Eliminar"
                        textSize = 11f
                        setTextColor(Color.rgb(255, 100, 100))
                        setTypeface(null, Typeface.BOLD)
                        setPadding(
                            activity.dp(6), activity.dp(6),
                            activity.dp(6), activity.dp(6)
                        )
                        background = VenusComponents.fondoRedondeado(
                            activity.superficie2, 8, activity
                        )
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                        ).apply {
                            marginStart = activity.dp(4)
                        }
                        isClickable = true
                        setOnClickListener {
                            AlertDialog.Builder(activity)
                                .setTitle("Eliminar video")
                                .setMessage(
                                    "¿Estás seguro de que deseas eliminar este video?"
                                )
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Eliminar") { _, _ ->
                                    activity.lifecycleScope.launch {
                                        val success = activity.repository.deleteMedia(media.id)
                                        if (success) {
                                            Toast.makeText(
                                                activity, "Video eliminado",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            mostrarMiBibliotecaIndependiente()
                                        } else {
                                            Toast.makeText(
                                                activity, "Error al eliminar video",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                .show()
                        }
                    }

                    rowBottom.addView(btnEditar)
                    rowBottom.addView(btnEliminar)

                    val containerAcciones = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, activity.dp(8), 0, 0)
                    }

                    containerAcciones.addView(btnPlay)
                    containerAcciones.addView(rowBottom)

                    tarjeta.addView(marcoImg)
                    tarjeta.addView(txtT)
                    tarjeta.addView(txtD)
                    tarjeta.addView(containerAcciones)

                    // CORRECCIÓN: agregar la tarjeta a la fila correspondiente.
                    rowLayoutInd?.addView(tarjeta)
                }
            }}}}
