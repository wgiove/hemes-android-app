package de.adversum.hermescompanion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.adversum.hermescompanion.databinding.ItemMediaBinding

/**
 * Zeigt die Medien-Liste an: Mini-Vorschau (Thumbnail), Dateiname,
 * Typ (Foto/Video), Größe und Dauer.
 */
class MediaAdapter(
    private var items: List<MediaScanner.MediaItem> = emptyList(),
    private val thumbnailLoader: (MediaScanner.MediaItem) -> Unit = {},
) : RecyclerView.Adapter<MediaAdapter.ViewHolder>() {

    class ViewHolder(
        private val binding: ItemMediaBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MediaScanner.MediaItem) {
            binding.tvName.text = item.displayName
            binding.tvMeta.text = buildString {
                append(if (item.isVideo) "🎬 Video" else "🖼 Foto")
                append(" · ")
                append(item.sizeHuman)
                if (item.durationHuman.isNotEmpty()) {
                    append(" · ")
                    append(item.durationHuman)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submit(newItems: List<MediaScanner.MediaItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}