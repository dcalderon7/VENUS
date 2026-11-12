package com.example.venus.ui.player

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.venus.MainActivity
import com.example.venus.VideoPlayerActivity
import com.example.venus.models.PeliculaMock
import com.example.venus.player.YouTubePlayerActivity

@androidx.media3.common.util.UnstableApi
object EmbeddedVideoPlayer {

    fun reproducirVideo(activity: MainActivity, uriTexto: String) {
        val uri = Uri.parse(uriTexto)
        val nombre = activity.nombreArchivo(uri)
        val intent = Intent(activity, VideoPlayerActivity::class.java).apply {
            putExtra("VIDEO_URI", uriTexto)
            putExtra("VIDEO_TITLE", nombre)
            putExtra("VIDEO_POSITION", 0L)
            putExtra("VIDEO_WINDOW", 0)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

    fun reproducirUrlServidor(activity: MainActivity, mediaId: String, streamUrl: String, tituloStr: String, tmdbId: String, mediaType: String) {
        val intent = Intent(activity, VideoPlayerActivity::class.java).apply {
            putExtra("VIDEO_URI", streamUrl)
            putExtra("VIDEO_TITLE", tituloStr)
            putExtra("MEDIA_ID", mediaId)
            putExtra("TMDB_ID", tmdbId)
            putExtra("MEDIA_TYPE", mediaType)
        }
        activity.startActivity(intent)
    }

    fun reproducirUrl(activity: MainActivity, url: String, tituloStr: String) {
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            var videoId = ""
            try {
                val uri = Uri.parse(url)
                if (url.contains("youtu.be/")) {
                    videoId = uri.lastPathSegment ?: ""
                } else if (url.contains("v=")) {
                    videoId = uri.getQueryParameter("v") ?: ""
                } else if (url.contains("embed/")) {
                    videoId = uri.lastPathSegment ?: ""
                }
            } catch (_: Exception) {}

            if (videoId.isNotBlank()) {
                val intent = Intent(activity, YouTubePlayerActivity::class.java).apply {
                    putExtra("VIDEO_ID", videoId)
                    putExtra("VIDEO_TITLE", tituloStr)
                }
                activity.startActivity(intent)
            } else {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(activity, "No se pudo reproducir el trailer", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val intent = Intent(activity, VideoPlayerActivity::class.java).apply {
                putExtra("VIDEO_URI", url)
                putExtra("VIDEO_TITLE", tituloStr)
                putExtra("VIDEO_POSITION", 0L)
                putExtra("VIDEO_WINDOW", 0)
            }
            activity.startActivity(intent)
        }
    }

    fun reproducirDemo(activity: MainActivity, pelicula: PeliculaMock) {
        reproducirUrl(activity, pelicula.videoUrl, pelicula.titulo)
    }
}
