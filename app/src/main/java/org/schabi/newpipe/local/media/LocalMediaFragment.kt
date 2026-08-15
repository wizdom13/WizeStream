/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Locale
import java.util.concurrent.Executors
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper

@LayoutRes
internal fun localMediaItemLayout(itemViewMode: ItemViewMode): Int = when (itemViewMode) {
    ItemViewMode.GRID -> R.layout.list_stream_grid_item
    ItemViewMode.CARD -> R.layout.list_stream_card_item
    else -> R.layout.list_stream_item
}

class LocalMediaFragment : Fragment() {
    private enum class Filter { ALL, AUDIO, VIDEO }
    private enum class Sort { TITLE, ARTIST, ALBUM, FOLDER, RECENT }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val disposables = CompositeDisposable()
    private var allItems = emptyList<LocalMediaItem>()
    private var shownItems = emptyList<LocalMediaItem>()
    private var filter = Filter.ALL
    private var sort = Sort.TITLE
    private var query = ""
    private lateinit var adapter: LocalMediaAdapter
    private lateinit var list: RecyclerView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { loadOrExplainPermission() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_local_media, container, false)
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        list = view.findViewById(R.id.localMediaList)
        adapter = LocalMediaAdapter(::play, ::showActions)
        list.adapter = adapter
        applyItemViewMode()

        view.findViewById<View>(R.id.localMediaGrantAccess).setOnClickListener {
            permissionLauncher.launch(requiredPermissions())
        }
        view.findViewById<Chip>(R.id.localMediaAll).setOnClickListener {
            filter = Filter.ALL
            updateList()
        }
        view.findViewById<Chip>(R.id.localMediaAudio).setOnClickListener {
            filter = Filter.AUDIO
            updateList()
        }
        view.findViewById<Chip>(R.id.localMediaVideo).setOnClickListener {
            filter = Filter.VIDEO
            updateList()
        }
        view.findViewById<Chip>(R.id.localMediaSort).setOnClickListener { showSortDialog() }
        view.findViewById<TextInputEditText>(R.id.localMediaSearch).addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty()
                    updateList()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
        loadOrExplainPermission()
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.local_media))
        if (::adapter.isInitialized) applyItemViewMode()
    }

    override fun onDestroy() {
        disposables.dispose()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO
        )

        Build.VERSION.SDK_INT >= 23 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        else -> emptyArray()
    }

    private fun hasAnyPermission(): Boolean = requiredPermissions().isEmpty() ||
        requiredPermissions().any {
            ContextCompat.checkSelfPermission(requireContext(), it) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun loadOrExplainPermission() {
        if (!hasAnyPermission()) {
            showMessage(R.string.local_media_permission_description, true)
            return
        }
        view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
        view?.findViewById<View>(R.id.localMediaProgress)?.visibility = View.VISIBLE
        val appContext = requireContext().applicationContext
        executor.execute {
            val result = LocalMediaRepository(appContext).query()
            mainHandler.post {
                if (!isAdded || view == null) return@post
                allItems = result
                view?.findViewById<View>(R.id.localMediaProgress)?.visibility = View.GONE
                updateList()
            }
        }
    }

    private fun updateList() {
        if (!::adapter.isInitialized) return
        val term = query.trim().lowercase(Locale.getDefault())
        val comparator: Comparator<LocalMediaItem> = when (sort) {
            Sort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::title)
            Sort.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::artist)
            Sort.ALBUM -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::album)
            Sort.FOLDER -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::folder)
            Sort.RECENT -> compareByDescending(LocalMediaItem::addedAtSeconds)
        }
        shownItems = allItems.asSequence()
            .filter {
                filter == Filter.ALL ||
                    filter == Filter.VIDEO && it.isVideo ||
                    filter == Filter.AUDIO && !it.isVideo
            }
            .filter {
                term.isEmpty() || listOf(it.title, it.artist, it.album, it.folder).any { value ->
                    value.lowercase(Locale.getDefault()).contains(term)
                }
            }
            .sortedWith(comparator)
            .toList()
        adapter.submit(shownItems)
        if (shownItems.isEmpty()) {
            showMessage(R.string.local_media_empty, false)
        } else {
            view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
        }
    }

    private fun applyItemViewMode() {
        val mode = ThemeHelper.getItemViewMode(requireContext())
        list.layoutManager = if (mode == ItemViewMode.GRID) {
            GridLayoutManager(requireContext(), ThemeHelper.getGridSpanCountStreams(requireContext()))
        } else {
            LinearLayoutManager(requireContext())
        }
        adapter.setItemViewMode(mode)
    }

    private fun showMessage(message: Int, showButton: Boolean) {
        view?.findViewById<TextView>(R.id.localMediaMessage)?.setText(message)
        view?.findViewById<View>(R.id.localMediaGrantAccess)?.visibility =
            if (showButton) View.VISIBLE else View.GONE
        view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.localMediaProgress)?.visibility = View.GONE
    }

    private fun play(item: LocalMediaItem) {
        val index = shownItems.indexOf(item).coerceAtLeast(0)
        val queue = LocalMediaPlayQueue(shownItems.map(LocalMediaItem::toPlayQueueItem), index)
        NavigationHelper.playOnMainPlayer(requireActivity() as AppCompatActivity, queue)
    }

    private fun showActions(item: LocalMediaItem) {
        val actions = arrayOf(
            getString(R.string.play),
            getString(R.string.local_media_play_background),
            getString(R.string.enqueue),
            getString(R.string.local_media_enqueue_next),
            getString(R.string.add_to_playlist)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title)
            .setItems(actions) { _, which ->
                val queue = LocalMediaPlayQueue(listOf(item.toPlayQueueItem()), 0)
                when (which) {
                    0 -> play(item)

                    1 -> NavigationHelper.playOnBackgroundPlayer(requireContext(), queue, true)

                    2 -> NavigationHelper.enqueueOnPlayer(requireContext(), queue)

                    3 -> NavigationHelper.enqueueNextOnPlayer(requireContext(), queue)

                    4 -> disposables.add(
                        PlaylistDialog.createCorrespondingDialog(
                            requireContext(),
                            listOf(StreamEntity(item.toPlayQueueItem()))
                        ) { dialog ->
                            dialog.show(parentFragmentManager, "LocalMediaPlaylist")
                        }
                    )
                }
            }.show()
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.local_media_sort_title),
            getString(R.string.local_media_sort_artist),
            getString(R.string.local_media_sort_album),
            getString(R.string.local_media_sort_folder),
            getString(R.string.local_media_sort_recent)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.local_media_sort)
            .setSingleChoiceItems(options, sort.ordinal) { dialog, which ->
                sort = Sort.values()[which]
                view?.findViewById<Chip>(R.id.localMediaSort)?.text = options[which]
                updateList()
                dialog.dismiss()
            }.show()
    }
}

private class LocalMediaAdapter(
    private val onClick: (LocalMediaItem) -> Unit,
    private val onLongClick: (LocalMediaItem) -> Unit
) : RecyclerView.Adapter<LocalMediaAdapter.Holder>() {
    private var items = emptyList<LocalMediaItem>()
    private var itemViewMode = ItemViewMode.LIST

    fun setItemViewMode(updated: ItemViewMode) {
        if (itemViewMode == updated) return
        itemViewMode = updated
        notifyDataSetChanged()
    }

    fun submit(updated: List<LocalMediaItem>) {
        items = updated
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = itemViewMode.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = localMediaItemLayout(ItemViewMode.values()[viewType])
        return Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun onViewRecycled(holder: Holder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail = view.findViewById<ImageView>(R.id.itemThumbnailView)
        private val duration = view.findViewById<TextView>(R.id.itemDurationView)
        private val title = view.findViewById<TextView>(R.id.itemVideoTitleView)
        private val uploader = view.findViewById<TextView>(R.id.itemUploaderView)
        private val details = view.findViewById<TextView>(R.id.itemAdditionalDetails)

        fun bind(item: LocalMediaItem) {
            title.text = item.title
            uploader.text = item.artist.ifBlank { item.album.ifBlank { item.folder } }
            details.setText(R.string.local_media_on_device)
            duration.text = Localization.getDurationString(item.durationSeconds)
            duration.visibility = if (item.durationSeconds > 0) View.VISIBLE else View.GONE
            itemView.findViewById<View>(R.id.itemUploaderAvatarView).visibility = View.GONE
            itemView.findViewById<View>(R.id.itemMembersOnlyView).visibility = View.GONE
            itemView.findViewById<View>(R.id.itemProgressView).visibility = View.GONE
            LocalMediaThumbnailLoader.load(thumbnail, item)
            itemView.setOnClickListener { onClick(item) }
            itemView.findViewById<View>(R.id.itemUploaderRoot).apply {
                setOnClickListener { onClick(item) }
                setOnLongClickListener {
                    onLongClick(item)
                    true
                }
            }
            itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }

        fun recycle() {
            LocalMediaThumbnailLoader.clear(thumbnail)
        }
    }
}
