package com.example.venus.player

import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.venus.R
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

class YouTubePlayerActivity : AppCompatActivity() {

    private lateinit var youtubePlayerView: YouTubePlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración de pantalla completa inmersiva
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val rawVideoId = intent.getStringExtra("VIDEO_ID") ?: run {
            finish()
            return
        }

        // Limpiar el ID del video de parámetros o Query extras
        val videoId = rawVideoId.trim().split("&", "?", "/").firstOrNull() ?: rawVideoId.trim()

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        youtubePlayerView = YouTubePlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val btnClose = ImageButton(this).apply {
            val size = (44 * resources.displayMetrics.density).toInt()
            val margin = (16 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = margin
                marginStart = margin
            }
            setImageResource(R.drawable.ic_close_white)
            background = ContextCompat.getDrawable(this@YouTubePlayerActivity, R.drawable.bg_control_circle)
            val pad = (10 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            elevation = (8 * resources.displayMetrics.density)
            setOnClickListener { finish() }
        }

        container.addView(youtubePlayerView)
        container.addView(btnClose)
        setContentView(container)

        lifecycle.addObserver(youtubePlayerView)

        youtubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                youTubePlayer.loadVideo(videoId, 0f)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::youtubePlayerView.isInitialized) {
            youtubePlayerView.release()
        }
    }
}