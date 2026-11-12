package com.example.venus.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.example.venus.BuildConfig
import com.example.venus.models.PeliculaMock
import java.util.concurrent.Executors

class TmdbRepository(private val context: Context) {

    var peliculasPopulares = mutableListOf<PeliculaMock>()
    var peliculasTendencia = mutableListOf<PeliculaMock>()
    var peliculasEstrenos = mutableListOf<PeliculaMock>()
    var peliculasMejorValoradas = mutableListOf<PeliculaMock>()
    var seriesPopulares = mutableListOf<PeliculaMock>()
    var seriesTendencia = mutableListOf<PeliculaMock>()
    var catalogosMock = mutableListOf<PeliculaMock>()
    var datosTmdbCargados = false

    private val handler = Handler(Looper.getMainLooper())

    fun fetchTmdbList(endpoint: String, isSerieDefault: Boolean, categoryName: String): List<PeliculaMock> {
        val list = mutableListOf<PeliculaMock>()
        try {
            val url = java.net.URL("https://api.themoviedb.org/3/$endpoint")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.TMDB_ACCESS_TOKEN}")
            conn.setRequestProperty("accept", "application/json")
            if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val results = org.json.JSONObject(json).optJSONArray("results") ?: org.json.JSONArray()
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val id = item.optInt("id").toString()
                    val title = item.optString("title").ifBlank { item.optString("name") }
                    val overview = item.optString("overview")
                    val posterPath = item.optString("poster_path")
                    val imageUrl = if (posterPath.isNotBlank()) "https://image.tmdb.org/t/p/w500$posterPath" else ""
                    val voteAverage = item.optDouble("vote_average", 0.0)
                    val date = item.optString("release_date").ifBlank { item.optString("first_air_date") }
                    val isTv = item.has("name") || isSerieDefault

                    list.add(
                        PeliculaMock(
                            id = id,
                            titulo = title,
                            categoria = categoryName,
                            calificacion = String.format(java.util.Locale.US, "%.1f", voteAverage),
                            duracion = date.take(4).ifBlank { "2026" },
                            descripcion = overview.ifBlank { "Sin descripción disponible." },
                            imageUrl = imageUrl,
                            videoUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                            esSerie = isTv
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun cargarCatalogosTmdb(onLoaded: () -> Unit) {
        if (datosTmdbCargados && catalogosMock.isNotEmpty()) {
            onLoaded()
            return
        }
        Executors.newSingleThreadExecutor().execute {
            if (BuildConfig.TMDB_ACCESS_TOKEN.isBlank()) {
                handler.post { onLoaded() }
                return@execute
            }

            val pPop = fetchTmdbList("movie/popular?language=es-ES&page=1", false, "Película Popular")
            val pTen = fetchTmdbList("trending/movie/day?language=es-ES", false, "Película en Tendencia")
            val pEst = fetchTmdbList("movie/upcoming?language=es-ES&page=1", false, "Próximo Estreno")
            val pTop = fetchTmdbList("movie/top_rated?language=es-ES&page=1", false, "Mejor Valorada")
            val sPop = fetchTmdbList("tv/popular?language=es-ES&page=1", true, "Serie Popular")
            val sTen = fetchTmdbList("trending/tv/day?language=es-ES", true, "Serie en Tendencia")

            val listaTotal = mutableListOf<PeliculaMock>()
            listaTotal.addAll(pPop)
            listaTotal.addAll(pTen)
            listaTotal.addAll(pEst)
            listaTotal.addAll(pTop)
            listaTotal.addAll(sPop)
            listaTotal.addAll(sTen)

            handler.post {
                peliculasPopulares.clear(); peliculasPopulares.addAll(pPop)
                peliculasTendencia.clear(); peliculasTendencia.addAll(pTen)
                peliculasEstrenos.clear(); peliculasEstrenos.addAll(pEst)
                peliculasMejorValoradas.clear(); peliculasMejorValoradas.addAll(pTop)
                seriesPopulares.clear(); seriesPopulares.addAll(sPop)
                seriesTendencia.clear(); seriesTendencia.addAll(sTen)

                if (listaTotal.isNotEmpty()) {
                    catalogosMock.clear()
                    catalogosMock.addAll(listaTotal.distinctBy { it.id })
                    datosTmdbCargados = true
                }
                onLoaded()
            }
        }
    }

    fun descargasImagen(urlString: String): Bitmap? {
        if (urlString.isBlank()) return null
        return try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)")
            connection.doInput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()
            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                BitmapFactory.decodeStream(connection.inputStream)
            } else {
                null
            }
        } catch (_: Exception) {
            try {
                val input = java.net.URL(urlString).openStream()
                BitmapFactory.decodeStream(input)
            } catch (_: Exception) {
                null
            }
        }
    }
}
