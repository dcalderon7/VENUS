package com.example.venus.ui.mylist

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.venus.MainActivity
import com.example.venus.R
import com.example.venus.models.PeliculaMock
import com.example.venus.ui.components.VenusComponents
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@androidx.media3.common.util.UnstableApi
class MyListScreen(private val activity: MainActivity) {

    fun mostrarMiLista() {
        activity.seleccionarNavegacion(2)
        val pantalla = activity.prepararPantalla()

        activity.etiqueta(pantalla, "Sincronizado con PostgreSQL")
        activity.titulo(pantalla, "Mi lista personal", 27f)

        val cargando = TextView(activity).apply {
            text = "Cargando tu lista..."
            textSize = 14f
            setTextColor(Color.rgb(185, 178, 201))
            setPadding(0, 20, 0, 0)
        }
        pantalla.addView(cargando)

        activity.lifecycleScope.launch {
            val watchlist = activity.repository.getWatchlist().getOrDefault(emptyList())
            pantalla.removeView(cargando)

            if (watchlist.isEmpty()) {
                val cajaVacia = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(activity.dp(24), activity.dp(32), activity.dp(24), activity.dp(32))
                    background = VenusComponents.fondoRedondeado(activity.superficie, 20, activity)
                }
                val txtVacia = TextView(activity).apply {
                    text = "Tu lista está vacía. Explora películas o series y añade tus favoritas."
                    textSize = 14f
                    setTextColor(Color.rgb(185, 178, 201))
                    setPadding(0, 0, 0, 16)
                }
                cajaVacia.addView(txtVacia)
                pantalla.addView(cajaVacia)
            } else {
                val contenedorLista = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                pantalla.addView(contenedorLista)

                watchlist.forEach { item ->
                    val peliculasCatalogo = activity.peliculasPopulares + activity.peliculasTendencia + activity.peliculasEstrenos + activity.peliculasMejorValoradas + activity.catalogosMock
                    var pelicula = peliculasCatalogo.find { item.tmdbId.isNotBlank() && it.id == item.tmdbId }

                    if (pelicula == null) {
                        pelicula = PeliculaMock(
                            id = item.tmdbId,
                            titulo = "Película TMDb #${item.tmdbId}",
                            categoria = item.mediaType.uppercase(),
                            calificacion = "S/N",
                            duracion = "Desconocida",
                            descripcion = "Película sincronizada desde tu cuenta.",
                            imageUrl = "",
                            videoUrl = "",
                            esSerie = item.mediaType == "tv"
                        )
                    }

                    val caja = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
                        background = VenusComponents.fondoRedondeadoConRipple(activity.superficie, 14, activity)
                    }

                    val marcoImg = FrameLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(activity.dp(60), activity.dp(90)).apply {
                            marginEnd = activity.dp(14)
                        }
                        background = VenusComponents.fondoRedondeado(activity.superficie2, 8, activity)
                        clipToOutline = true
                    }

                    val img = ImageView(activity).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    marcoImg.addView(img)

                    val colInfo = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val txtT = TextView(activity).apply { text = pelicula!!.titulo; textSize = 15f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); maxLines = 1 }
                    val txtC = TextView(activity).apply { text = "${pelicula!!.duracion} • ⭐ ${pelicula!!.calificacion} • ${pelicula!!.categoria}"; textSize = 11f; setTextColor(Color.rgb(185, 178, 201)) }
                    colInfo.addView(txtT)
                    colInfo.addView(txtC)

                    val btnPlay = ImageView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(activity.dp(26), activity.dp(26))
                        setImageResource(R.drawable.ic_play_white)
                        setColorFilter(activity.rojo)
                    }

                    caja.addView(marcoImg)
                    caja.addView(colInfo)
                    caja.addView(btnPlay)
                    contenedorLista.addView(caja, activity.margenAbajo(10))

                    caja.setOnClickListener {
                        activity.mostrarDetallePelicula(pelicula!!)
                    }
                    VenusComponents.aplicarEfectoPulso(caja)

                    if (pelicula!!.imageUrl.isNotBlank()) {
                        Executors.newSingleThreadExecutor().execute {
                            val bmp = activity.descargasImagen(pelicula!!.imageUrl)
                            if (bmp != null) activity.runOnUiThread { img.setImageBitmap(bmp) }
                        }
                    }
                }
            }
        }
    }
}
