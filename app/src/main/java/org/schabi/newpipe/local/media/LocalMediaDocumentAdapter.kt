/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt
import org.schabi.newpipe.R

class LocalMediaDocumentAdapter(
    private val onClick: (LocalMediaDocumentEntry) -> Unit,
    private val onLongClick: (LocalMediaDocumentEntry) -> Unit
) : RecyclerView.Adapter<LocalMediaDocumentAdapter.Holder>() {
    private var entries = emptyList<LocalMediaDocumentEntry>()

    fun submit(updated: List<LocalMediaDocumentEntry>) {
        entries = updated
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.list_local_media_document_item,
            parent,
            false
        )
    )

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(entries[position])

    override fun onViewRecycled(holder: Holder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.localMediaDocumentIcon)
        private val title = view.findViewById<TextView>(R.id.localMediaDocumentTitle)
        private val subtitle = view.findViewById<TextView>(R.id.localMediaDocumentSubtitle)

        fun bind(entry: LocalMediaDocumentEntry) {
            if (entry.isDirectory) {
                LocalMediaThumbnailLoader.clear(icon)
                applyLocalMediaDocumentIconStyle(icon, isDirectory = true)
                icon.setImageResource(R.drawable.ic_create_new_folder)
            } else {
                applyLocalMediaDocumentIconStyle(icon, isDirectory = false)
                LocalMediaThumbnailLoader.load(icon, entry)
            }
            title.text = entry.name
            subtitle.text = when {
                !entry.isAvailable -> itemView.context.getString(
                    R.string.local_media_folder_unavailable
                )

                entry.isRoot -> itemView.context.getString(R.string.local_media_storage_locations)

                entry.isDirectory -> itemView.context.getString(R.string.local_media_folders)

                entry.sizeBytes > 0L -> itemView.context.getString(
                    R.string.local_media_size_megabytes,
                    entry.sizeBytes / 1_048_576.0
                )

                else -> entry.mimeType
            }
            itemView.alpha = if (entry.isAvailable) 1F else 0.5F
            itemView.setOnClickListener { if (entry.isAvailable) onClick(entry) }
            itemView.setOnLongClickListener {
                if (entry.isAvailable) onLongClick(entry)
                entry.isAvailable
            }
        }

        fun recycle() {
            LocalMediaThumbnailLoader.clear(icon)
            applyLocalMediaDocumentIconStyle(icon, isDirectory = false)
        }
    }
}

internal fun applyLocalMediaDocumentIconStyle(icon: ImageView, isDirectory: Boolean) {
    if (isDirectory) {
        val tintColor = MaterialColors.getColor(
            icon,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        icon.imageTintList = ColorStateList.valueOf(tintColor)
        val padding = (DIRECTORY_ICON_PADDING_DP * icon.resources.displayMetrics.density).roundToInt()
        icon.setPadding(padding, padding, padding, padding)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
    } else {
        icon.imageTintList = null
        icon.setPadding(0, 0, 0, 0)
        icon.scaleType = ImageView.ScaleType.CENTER_CROP
    }
}

private const val DIRECTORY_ICON_PADDING_DP = 8
