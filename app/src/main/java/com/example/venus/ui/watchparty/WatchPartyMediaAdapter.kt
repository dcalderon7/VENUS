package com.example.venus.ui.watchparty

import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.venus.MainActivity
import com.example.venus.R
import com.example.venus.network.MediaFileDto
import com.example.venus.ui.components.VenusComponents
import java.util.concurrent.Executors

@androidx.media3.common.util.UnstableApi
class WatchPartyMediaAdapter(
    private val activity: MainActivity,
    private val onItemClick: (MediaFileDto) -> Unit
) : ListAdapter<MediaFileDto, WatchPartyMediaAdapter.WatchPartyViewHolder>(MediaDiffCallback) {

    object MediaDiffCallback : DiffUtil.ItemCallback<MediaFileDto>() {
        override fun areItemsTheSame(oldItem: MediaFileDto, newItem: MediaFileDto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaFileDto, newItem: MediaFileDto): Boolean {
            return oldItem == newItem
        }
    }

    class WatchPartyViewHolder(
        val cardView: LinearLayout,
        val txtTitle: TextView,
        val imgPoster: ImageView
    ) : RecyclerView.ViewHolder(cardView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WatchPartyViewHolder {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val paddingTotal = activity.dp(40)
        val spacing = activity.dp(12)
        val cardWidth = (screenWidth - paddingTotal - spacing) / 2
        val posterHeight = (cardWidth * 1.45f).toInt()

        val tarjeta = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(16)
                marginEnd = spacing / 2
                marginStart = spacing / 2
            }
            background = VenusComponents.fondoRedondeadoConRipple(activity.superficie, 16, activity)
            setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(10))
            isClickable = true
            isFocusable = true
        }

        val marcoImg = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, posterHeight).apply {
                bottomMargin = activity.dp(8)
            }
            background = VenusComponents.fondoRedondeado(activity.superficie2, 14, activity)
            clipToOutline = true
        }

        val imgPoster = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_film_reel)
        }
        marcoImg.addView(imgPoster)

        val txtT = TextView(activity).apply {
            textSize = 14f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        // Green Pill Badge: "Video disponible"
        val badgeContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, activity.dp(6), 0, 0)
        }

        val badgeText = TextView(activity).apply {
            text = "Video disponible"
            textSize = 11f
            setTextColor(Color.rgb(0, 200, 83)) // Green #00C853
            setTypeface(null, Typeface.BOLD)
            setPadding(activity.dp(8), activity.dp(3), activity.dp(8), activity.dp(3))
            background = VenusComponents.fondoRedondeado(Color.argb(40, 0, 200, 83), 10, activity)
        }
        badgeContainer.addView(badgeText)

        tarjeta.addView(marcoImg)
        tarjeta.addView(txtT)
        tarjeta.addView(badgeContainer)

        return WatchPartyViewHolder(tarjeta, txtT, imgPoster)
    }

    override fun onBindViewHolder(holder: WatchPartyViewHolder, position: Int) {
        val item = getItem(position)
        val peliculaMatch = activity.catalogosMock.find { it.id == item.tmdbId }
        val title = when {
            peliculaMatch != null -> peliculaMatch.titulo
            !item.displayTitle.isNullOrBlank() -> item.displayTitle
            else -> item.fileName
        }

        holder.txtTitle.text = title
        holder.cardView.setOnClickListener { onItemClick(item) }

        if (peliculaMatch != null && peliculaMatch.imageUrl.isNotBlank()) {
            Executors.newSingleThreadExecutor().execute {
                val bmp = activity.descargasImagen(peliculaMatch.imageUrl)
                if (bmp != null) {
                    activity.runOnUiThread { holder.imgPoster.setImageBitmap(bmp) }
                }
            }
        }
    }
}
