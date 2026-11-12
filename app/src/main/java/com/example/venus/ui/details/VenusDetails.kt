package com.example.venus.ui.details

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.venus.BuildConfig
import com.example.venus.MainActivity
import com.example.venus.R
import com.example.venus.models.PeliculaMock
import com.example.venus.ui.dialogs.WatchPartyDialogs
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@androidx.media3.common.util.UnstableApi
object VenusDetails {

    fun mostrarDetallePelicula(activity: MainActivity, pelicula: PeliculaMock) {
        val bottomSheetDialog = BottomSheetDialog(activity)
        val vistaSheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(22), activity.dp(22), activity.dp(22), activity.dp(28))
            background = activity.fondoRedondeado(activity.fondo, 24)
        }

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val imgPoster = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(90), activity.dp(130)).apply {
                marginEnd = activity.dp(16)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = activity.fondoRedondeado(activity.superficie2, 12)
            clipToOutline = true
        }

        val infoCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val txtBadge = TextView(activity).apply {
            text = "Calificacion ${pelicula.calificacion} - ${pelicula.duracion}".uppercase()
            textSize = 11f
            setTextColor(Color.rgb(255, 193, 7))
            setTypeface(null, Typeface.BOLD)
        }

        val txtTitulo = TextView(activity).apply {
            text = pelicula.titulo
            textSize = 20f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
        }

        val txtCat = TextView(activity).apply {
            text = pelicula.categoria
            textSize = 12f
            setTextColor(activity.gris)
        }

        infoCol.addView(txtBadge)
        infoCol.addView(txtTitulo)
        infoCol.addView(txtCat)

        headerRow.addView(imgPoster)
        headerRow.addView(infoCol)

        val txtSinopsis = TextView(activity).apply {
            text = pelicula.descripcion
            textSize = 13f
            setTextColor(activity.blanco)
            setLineSpacing(activity.dp(2).toFloat(), 1f)
        }

        val containerAcciones = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, activity.dp(16), 0, 0)
        }

        val btnPlay = TextView(activity).apply {
            text = "REPRODUCIR"
            textSize = 14f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = activity.fondoRedondeado(activity.rojo, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                bottomSheetDialog.dismiss()
                activity.mostrarDialogoEstadoPelicula(pelicula)
            }
        }

        val filaSecundaria = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = activity.dp(10)
            }
        }

        var estaEnLista = false
        val btnLista = TextView(activity).apply {
            text = "MI LISTA"
            textSize = 12f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(6), activity.dp(10), activity.dp(6), activity.dp(10))
            background = activity.fondoRedondeado(activity.superficie2, 12)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = activity.dp(6)
            }
            isClickable = true
            isFocusable = true
        }

        activity.lifecycleScope.launch {
            val res = activity.repository.getWatchlist()
            val items = res.getOrDefault(emptyList())
            estaEnLista = items.any { it.tmdbId == pelicula.id }
            activity.runOnUiThread {
                btnLista.text = if (estaEnLista) "EN LISTA" else "MI LISTA"
                btnLista.background = activity.fondoRedondeado(if (estaEnLista) Color.rgb(135, 56, 245) else activity.superficie2, 12)
            }
        }

        btnLista.setOnClickListener {
            estaEnLista = !estaEnLista
            btnLista.text = if (estaEnLista) "EN LISTA" else "MI LISTA"
            btnLista.background = activity.fondoRedondeado(if (estaEnLista) Color.rgb(135, 56, 245) else activity.superficie2, 12)

            activity.lifecycleScope.launch {
                if (estaEnLista) {
                    activity.repository.addToWatchlist(pelicula.id, if (pelicula.esSerie) "tv" else "movie")
                } else {
                    activity.repository.removeFromWatchlist(pelicula.id)
                }
            }

            Toast.makeText(
                activity,
                if (estaEnLista) "Añadido a Mi Lista" else "Quitado de Mi Lista",
                Toast.LENGTH_SHORT
            ).show()
        }

        var estaDescargado = activity.almacen.estaDescargado(pelicula.id)
        val btnDescargar = TextView(activity).apply {
            text = if (estaDescargado) "GUARDADO" else "DESCARGAR"
            textSize = 12f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(6), activity.dp(10), activity.dp(6), activity.dp(10))
            background = activity.fondoRedondeado(if (estaDescargado) Color.rgb(30, 100, 200) else activity.superficie2, 12)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = activity.dp(6)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                estaDescargado = activity.almacen.toggleDescarga(pelicula.id)
                text = if (estaDescargado) "GUARDADO" else "DESCARGAR"
                background = activity.fondoRedondeado(if (estaDescargado) Color.rgb(30, 100, 200) else activity.superficie2, 12)
                Toast.makeText(
                    activity,
                    if (estaDescargado) "Video guardado en Descargas offline" else "Eliminado de Descargas",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val btnWatchParty = TextView(activity).apply {
            text = "WATCH PARTY"
            textSize = 12f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(6), activity.dp(10), activity.dp(6), activity.dp(10))
            background = activity.fondoRedondeado(Color.rgb(135, 56, 245), 12)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                bottomSheetDialog.dismiss()
                WatchPartyDialogs.mostrarDialogoSalaCreadaConCodigo(
                    context = activity,
                    mediaId = pelicula.id,
                    title = pelicula.titulo,
                    onStartPlayer = {
                        val token = activity.repository.getToken() ?: ""
                        val streamUrl = "${com.example.venus.network.ApiClient.getBaseUrl(activity)}media/${pelicula.id}/stream?token=$token"
                        activity.reproducirUrlServidor(pelicula.id, streamUrl, pelicula.titulo, pelicula.id, "movie")
                    }
                )
            }
        }

        containerAcciones.addView(btnPlay)
        filaSecundaria.addView(btnLista)
        filaSecundaria.addView(btnDescargar)
        filaSecundaria.addView(btnWatchParty)
        containerAcciones.addView(filaSecundaria)

        vistaSheet.addView(headerRow, activity.margenAbajo(14))
        vistaSheet.addView(txtSinopsis, activity.margenAbajo(14))
        vistaSheet.addView(containerAcciones)

        bottomSheetDialog.setContentView(vistaSheet)
        bottomSheetDialog.show()

        Executors.newSingleThreadExecutor().execute {
            val bmp = activity.descargasImagen(pelicula.imageUrl)
            if (bmp != null) {
                activity.runOnUiThread { imgPoster.setImageBitmap(bmp) }
            }

            try {
                val tipo = if (pelicula.esSerie) "tv" else "movie"
                val url = java.net.URL("https://api.themoviedb.org/3/$tipo/${pelicula.id}?language=es-ES")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.TMDB_ACCESS_TOKEN}")
                conn.setRequestProperty("accept", "application/json")

                if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = org.json.JSONObject(json)
                    val genresArr = obj.optJSONArray("genres")
                    val genresList = mutableListOf<String>()
                    if (genresArr != null) {
                        for (i in 0 until genresArr.length()) {
                            genresList.add(genresArr.optJSONObject(i)?.optString("name") ?: "")
                        }
                    }
                    val genresStr = genresList.filter { it.isNotBlank() }.joinToString(", ")

                    val runtime = if (pelicula.esSerie) {
                        val seasons = obj.optInt("number_of_seasons", 1)
                        "$seasons Temporada${if (seasons > 1) "s" else ""}"
                    } else {
                        val mins = obj.optInt("runtime", 0)
                        if (mins > 0) "$mins min" else ""
                    }

                    val urlCredits = java.net.URL("https://api.themoviedb.org/3/$tipo/${pelicula.id}/credits?language=es-ES")
                    val connCredits = urlCredits.openConnection() as java.net.HttpURLConnection
                    connCredits.requestMethod = "GET"
                    connCredits.setRequestProperty("Authorization", "Bearer ${BuildConfig.TMDB_ACCESS_TOKEN}")
                    connCredits.setRequestProperty("accept", "application/json")

                    var castStr = ""
                    if (connCredits.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        val jsonC = connCredits.inputStream.bufferedReader().use { it.readText() }
                        val castArr = org.json.JSONObject(jsonC).optJSONArray("cast")
                        val castList = mutableListOf<String>()
                        if (castArr != null) {
                            for (i in 0 until minOf(5, castArr.length())) {
                                castList.add(castArr.optJSONObject(i)?.optString("name") ?: "")
                            }
                        }
                        castStr = castList.filter { it.isNotBlank() }.joinToString(", ")
                    }

                    val badgeText = "Calificación ${pelicula.calificacion} • ${pelicula.duracion} • $runtime".uppercase()
                    val catText = if (genresStr.isNotBlank()) "$genresStr • ${pelicula.categoria}" else pelicula.categoria
                    val synopsisText = if (castStr.isNotBlank()) "${pelicula.descripcion}\n\nReparto: $castStr" else pelicula.descripcion

                    activity.runOnUiThread {
                        txtBadge.text = badgeText
                        txtCat.text = catText
                        txtSinopsis.text = synopsisText
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
