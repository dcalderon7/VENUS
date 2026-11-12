package com.example.venus.ui.home

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.example.venus.MainActivity
import com.example.venus.models.PeliculaMock
import com.example.venus.ui.components.VenusComponents
import java.util.concurrent.Executors

@androidx.media3.common.util.UnstableApi
class HomeScreen(private val activity: MainActivity) {

    fun mostrarInicio() {
        activity.seleccionarNavegacion(0)
        val pantalla = activity.prepararPantalla()

        if (activity.catalogosMock.isEmpty()) {
            val loadingText = TextView(activity).apply {
                text = "Cargando catálogo de películas y series..."
                textSize = 14f
                setTextColor(Color.rgb(185, 178, 201))
                setPadding(0, 20, 0, 0)
            }
            pantalla.addView(loadingText)
            activity.cargarCatalogosTmdb {
                mostrarInicio()
            }
            return
        }

        val catalogos = activity.catalogosMock
        if (catalogos.isNotEmpty()) {
            heroBanner(pantalla, catalogos.random())
        }

        val populares = activity.peliculasPopulares
        if (populares.isNotEmpty()) {
            filaHorizontalPeliculas(pantalla, "Tendencias de hoy", "Populares en streaming", populares)
        }

        val tendencia = activity.peliculasTendencia
        if (tendencia.isNotEmpty()) {
            filaHorizontalPeliculas(pantalla, "Lo más visto", "Tendencias mundiales", tendencia)
        }

        val estrenos = activity.peliculasEstrenos
        if (estrenos.isNotEmpty()) {
            filaHorizontalPeliculas(pantalla, "Nuevos lanzamientos", "Estrenos recientes", estrenos)
        }

        val mejorValoradas = activity.peliculasMejorValoradas
        if (mejorValoradas.isNotEmpty()) {
            filaHorizontalPeliculas(pantalla, "Aclamadas por la crítica", "Mejor valoradas", mejorValoradas)
        }

        val series = activity.seriesPopulares
        if (series.isNotEmpty()) {
            filaHorizontalPeliculas(pantalla, "Series imperdibles", "Series de televisión", series)
        }
    }

    private fun heroBanner(contenedor: LinearLayout, pelicula: PeliculaMock) {
        val cajaBanner = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(310)
            )
            background = VenusComponents.fondoRedondeado(activity.superficie, 20, activity)
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setOnClickListener { activity.mostrarDetallePelicula(pelicula) }
        }
        VenusComponents.aplicarEfectoPulso(cajaBanner)

        val imgFondo = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val overlayGradiente = View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                activity.dp(180)
            ).apply {
                gravity = Gravity.BOTTOM
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(140, 17, 16, 21), Color.argb(245, 5, 5, 5))
            )
        }

        val infoCaja = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setPadding(activity.dp(20), activity.dp(16), activity.dp(20), activity.dp(20))
        }

        val txtBadge = TextView(activity).apply {
            text = "★  DESTACADO HOY • ⭐ ${pelicula.calificacion}".uppercase()
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(255, 193, 7))
            setTypeface(null, Typeface.BOLD)
        }

        val txtTitulo = TextView(activity).apply {
            text = pelicula.titulo
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, activity.dp(4), 0, activity.dp(4))
        }

        val txtSub = TextView(activity).apply {
            text = pelicula.descripcion
            textSize = 13f
            setTextColor(Color.rgb(196, 181, 253))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        infoCaja.addView(txtBadge)
        infoCaja.addView(txtTitulo)
        infoCaja.addView(txtSub)

        cajaBanner.addView(imgFondo)
        cajaBanner.addView(overlayGradiente)
        cajaBanner.addView(infoCaja)

        contenedor.addView(cajaBanner, VenusComponents.margenAbajo(activity, 28))

        Executors.newSingleThreadExecutor().execute {
            val bmp = activity.descargasImagen(pelicula.imageUrl)
            if (bmp != null) {
                activity.runOnUiThread {
                    imgFondo.setImageBitmap(bmp)
                }
            }
        }
    }

    private fun filaHorizontalPeliculas(
        contenedor: LinearLayout,
        subtituloTag: String,
        tituloSeccion: String,
        peliculas: List<PeliculaMock>
    ) {
        activity.etiqueta(contenedor, subtituloTag)
        activity.titulo(contenedor, tituloSeccion, 20f)

        val scroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val fila = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        peliculas.forEach { pelicula ->
            val tarjeta = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(140), activity.dp(210)).apply {
                    marginEnd = activity.dp(16)
                }
                background = VenusComponents.fondoRedondeadoConRipple(activity.superficie, 16, activity)
                clipToOutline = true
                isClickable = true
                isFocusable = true
                setOnClickListener { activity.mostrarDetallePelicula(pelicula) }
            }
            VenusComponents.aplicarEfectoPulso(tarjeta)

            val imgPoster = ImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val overlayGradiente = View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    activity.dp(120)
                ).apply {
                    gravity = Gravity.BOTTOM
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, Color.argb(180, 17, 16, 21), Color.argb(240, 5, 5, 5))
                )
            }

            val infoCaja = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM
                }
                setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(12))
            }

            val txtBadge = TextView(activity).apply {
                text = "★ ${pelicula.calificacion}"
                textSize = 11f
                setTextColor(Color.rgb(255, 193, 7))
                setTypeface(null, Typeface.BOLD)
            }

            val txtTitulo = TextView(activity).apply {
                text = pelicula.titulo
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val txtSub = TextView(activity).apply {
                text = pelicula.duracion
                textSize = 11f
                setTextColor(Color.rgb(196, 181, 253))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            infoCaja.addView(txtBadge)
            infoCaja.addView(txtTitulo)
            infoCaja.addView(txtSub)

            tarjeta.addView(imgPoster)
            tarjeta.addView(overlayGradiente)
            tarjeta.addView(infoCaja)

            val pos = activity.almacen.obtenerPosicion(pelicula.id)
            val dur = activity.almacen.obtenerDuracion(pelicula.id)
            if (pos > 0L && dur > 0L) {
                val ratio = (pos.toFloat() / dur.toFloat()).coerceIn(0.05f, 1.0f)
                val barraProgreso = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams((activity.dp(140) * ratio).toInt(), activity.dp(3)).apply {
                        gravity = Gravity.BOTTOM
                    }
                    background = GradientDrawable().apply { setColor(activity.rojo) }
                }
                tarjeta.addView(barraProgreso)
            }

            fila.addView(tarjeta)

            Executors.newSingleThreadExecutor().execute {
                val bmp = activity.descargasImagen(pelicula.imageUrl)
                if (bmp != null) {
                    activity.runOnUiThread {
                        imgPoster.setImageBitmap(bmp)
                    }
                }
            }
        }

        scroll.addView(fila)
        contenedor.addView(scroll, VenusComponents.margenAbajo(activity, 28))
    }
}
