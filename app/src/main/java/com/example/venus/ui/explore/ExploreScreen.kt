package com.example.venus.ui.explore

import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import com.example.venus.MainActivity
import com.example.venus.R
import com.example.venus.models.PeliculaMock
import com.example.venus.ui.components.VenusComponents
import java.util.concurrent.Executors

@androidx.media3.common.util.UnstableApi
class ExploreScreen(private val activity: MainActivity) {

    fun mostrarExplorar() {
        activity.seleccionarNavegacion(-1)
        val pantalla = activity.prepararPantalla()

        activity.etiqueta(pantalla, "Catálogo completo y buscador")
        activity.titulo(pantalla, "Explorar películas y series", 27f)

        val inputBusqueda = EditText(activity).apply {
            hint = "Buscar por título..."
            setHintTextColor(Color.rgb(185, 178, 201))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.superficie2, 12, activity)
            isSingleLine = true
        }
        pantalla.addView(inputBusqueda, activity.margenAbajo(16))

        val contenedorResultados = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun filtrarYMostrar(query: String) {
            contenedorResultados.removeAllViews()
            val queryLower = query.lowercase().trim()

            val todasLasPeliculas = (activity.peliculasPopulares + activity.peliculasTendencia + activity.peliculasEstrenos + activity.peliculasMejorValoradas + activity.catalogosMock).distinctBy { it.id }

            val filtradas = if (queryLower.isBlank()) {
                todasLasPeliculas
            } else {
                todasLasPeliculas.filter {
                    it.titulo.lowercase().contains(queryLower) ||
                    it.categoria.lowercase().contains(queryLower) ||
                    it.descripcion.lowercase().contains(queryLower)
                }
            }

            if (filtradas.isEmpty()) {
                val txtVacio = TextView(activity).apply {
                    text = "No se encontraron resultados para \"$query\""
                    textSize = 14f
                    setTextColor(Color.rgb(185, 178, 201))
                    setPadding(0, activity.dp(20), 0, 0)
                }
                contenedorResultados.addView(txtVacio)
            } else {
                filtradas.forEach { pelicula ->
                    val tarjetaItem = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
                        background = VenusComponents.fondoRedondeadoConRipple(activity.superficie, 14, activity)
                    }

                    val imgPoster = ImageView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(activity.dp(60), activity.dp(90)).apply {
                            marginEnd = activity.dp(14)
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = VenusComponents.fondoRedondeado(activity.superficie2, 8, activity)
                        clipToOutline = true
                    }

                    val infoLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val txtT = TextView(activity).apply {
                        text = pelicula.titulo
                        textSize = 15f
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        maxLines = 1
                    }

                    val txtMeta = TextView(activity).apply {
                        text = "${pelicula.duracion} • ⭐ ${pelicula.calificacion} • ${pelicula.categoria}"
                        textSize = 12f
                        setTextColor(Color.rgb(185, 178, 201))
                        setPadding(0, activity.dp(4), 0, 0)
                    }

                    infoLayout.addView(txtT)
                    infoLayout.addView(txtMeta)

                    val btnPlay = ImageView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(activity.dp(36), activity.dp(36))
                        setImageResource(R.drawable.ic_play_white)
                        setColorFilter(activity.rojo)
                        setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6))
                    }

                    tarjetaItem.addView(imgPoster)
                    tarjetaItem.addView(infoLayout)
                    tarjetaItem.addView(btnPlay)

                    tarjetaItem.setOnClickListener {
                        activity.mostrarDetallePelicula(pelicula)
                    }
                    VenusComponents.aplicarEfectoPulso(tarjetaItem)

                    contenedorResultados.addView(tarjetaItem, activity.margenAbajo(10))

                    if (pelicula.imageUrl.isNotBlank()) {
                        Executors.newSingleThreadExecutor().execute {
                            val bmp = activity.descargasImagen(pelicula.imageUrl)
                            if (bmp != null) {
                                activity.runOnUiThread { imgPoster.setImageBitmap(bmp) }
                            }
                        }
                    }
                }
            }
        }

        inputBusqueda.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filtrarYMostrar(s.toString())
            }
        })

        pantalla.addView(contenedorResultados)
        filtrarYMostrar("")
    }
}
