/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.os.Bundle
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Locale
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.util.GridLayoutManagerHelper
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

    private val disposables = CompositeDisposable()
    private var allItems = emptyList<LocalMediaItem>()
    private var shownItems = emptyList<LocalMediaItem>()
    private var filter = Filter.ALL
    private var sort = Sort.TITLE
    private var query = ""
    private var audioCategory = LocalMediaAudioCategory.TRACKS
    private var activeGroup: LocalMediaGroup? = null
    private lateinit var adapter: LocalMediaAdapter
    private lateinit var groupAdapter: LocalMediaGroupAdapter
    private lateinit var list: RecyclerView
    private lateinit var viewModel: LocalMediaViewModel

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
        viewModel = ViewModelProvider(this)[LocalMediaViewModel::class.java]
        adapter = LocalMediaAdapter(::play, ::showActions)
        groupAdapter = LocalMediaGroupAdapter(::openGroup) { audioCategory }
        list.adapter = adapter
        applyItemViewMode()

        view.findViewById<View>(R.id.localMediaGrantAccess).setOnClickListener {
            permissionLauncher.launch(requiredPermissions())
        }
        view.findViewById<Chip>(R.id.localMediaAll).setOnClickListener {
            selectFilter(Filter.ALL)
        }
        view.findViewById<Chip>(R.id.localMediaAudio).setOnClickListener {
            selectFilter(Filter.AUDIO)
        }
        view.findViewById<Chip>(R.id.localMediaVideo).setOnClickListener {
            selectFilter(Filter.VIDEO)
        }
        view.findViewById<Chip>(R.id.localMediaSort).setOnClickListener { showSortDialog() }
        view.findViewById<Chip>(R.id.localMediaAudioTracks).setOnClickListener {
            selectAudioCategory(LocalMediaAudioCategory.TRACKS)
        }
        view.findViewById<Chip>(R.id.localMediaAudioArtists).setOnClickListener {
            selectAudioCategory(LocalMediaAudioCategory.ARTISTS)
        }
        view.findViewById<Chip>(R.id.localMediaAudioAlbums).setOnClickListener {
            selectAudioCategory(LocalMediaAudioCategory.ALBUMS)
        }
        view.findViewById<Chip>(R.id.localMediaAudioGenres).setOnClickListener {
            selectAudioCategory(LocalMediaAudioCategory.GENRES)
        }
        view.findViewById<Chip>(R.id.localMediaAudioPlaylists).setOnClickListener {
            NavigationHelper.openBookmarksFragment(parentFragmentManager)
        }
        view.findViewById<View>(R.id.localMediaGroupBack).setOnClickListener {
            activeGroup = null
            updateList()
        }
        view.findViewById<TextInputEditText>(R.id.localMediaSearch).addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty()
                    updateList()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
        viewModel.state.observe(viewLifecycleOwner, ::renderState)
        loadOrExplainPermission()
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.local_media))
        if (::adapter.isInitialized) applyItemViewMode()
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    private fun requiredPermissions(): Array<String> = LocalMediaPermissionPolicy
        .requiredPermissions()

    private fun loadOrExplainPermission() {
        val access = LocalMediaPermissionPolicy.access(requireContext())
        if (!access.hasAnyAccess) {
            showMessage(R.string.local_media_permission_description, true)
            return
        }
        view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
        viewModel.load(access)
    }

    private fun renderState(state: LocalMediaLibraryState) {
        if (!isAdded || view == null) return
        view?.findViewById<View>(R.id.localMediaProgress)?.visibility =
            if (state.isLoading) View.VISIBLE else View.GONE
        allItems = state.library.allItems
        if (!state.isLoading) updateList()
    }

    private fun selectFilter(selected: Filter) {
        filter = selected
        activeGroup = null
        view?.findViewById<View>(R.id.localMediaAudioNavigation)?.visibility =
            if (selected == Filter.AUDIO) View.VISIBLE else View.GONE
        updateList()
    }

    private fun selectAudioCategory(selected: LocalMediaAudioCategory) {
        audioCategory = selected
        activeGroup = null
        updateList()
    }

    private fun openGroup(group: LocalMediaGroup) {
        activeGroup = group
        updateList()
    }

    private fun updateList() {
        if (!::adapter.isInitialized) return
        val term = query.trim().lowercase(Locale.getDefault())
        if (
            filter == Filter.AUDIO &&
            audioCategory != LocalMediaAudioCategory.TRACKS &&
            activeGroup == null
        ) {
            showAudioGroups(term)
            return
        }
        val comparator: Comparator<LocalMediaItem> = when (sort) {
            Sort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::title)
            Sort.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::artist)
            Sort.ALBUM -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::album)
            Sort.FOLDER -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::folder)
            Sort.RECENT -> compareByDescending(LocalMediaItem::addedAtSeconds)
        }
        val sourceItems = activeGroup?.items ?: allItems
        shownItems = sourceItems.asSequence()
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
        list.adapter = adapter
        applyItemViewMode()
        renderGroupHeader()
        adapter.submit(shownItems)
        if (shownItems.isEmpty()) {
            showMessage(R.string.local_media_empty, false)
        } else {
            view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
        }
    }

    private fun showAudioGroups(term: String) {
        val groups = LocalMediaAudioIndex.groups(
            items = allItems.filter(LocalMediaItem::isAudio),
            category = audioCategory,
            unknownArtist = getString(R.string.local_media_unknown_artist),
            unknownAlbum = getString(R.string.local_media_unknown_album),
            unknownGenre = getString(R.string.local_media_unknown_genre)
        ).filter { group ->
            term.isEmpty() || listOf(group.title, group.subtitle).any { value ->
                value.lowercase(Locale.getDefault()).contains(term)
            }
        }
        list.adapter = groupAdapter
        list.layoutManager = LinearLayoutManager(requireContext())
        view?.findViewById<View>(R.id.localMediaGroupHeader)?.visibility = View.GONE
        groupAdapter.submit(groups)
        if (groups.isEmpty()) {
            showMessage(R.string.local_media_empty, false)
        } else {
            view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
        }
    }

    private fun renderGroupHeader() {
        val group = activeGroup
        view?.findViewById<View>(R.id.localMediaGroupHeader)?.visibility =
            if (group == null) View.GONE else View.VISIBLE
        view?.findViewById<TextView>(R.id.localMediaGroupTitle)?.text = group?.title
    }

    private fun applyItemViewMode() {
        val mode = ThemeHelper.getItemViewMode(requireContext())
        list.adapter = adapter
        list.layoutManager = if (mode == ItemViewMode.GRID) {
            val minimumItemWidth = resources.getDimensionPixelSize(
                R.dimen.video_item_grid_thumbnail_image_width
            ) + resources.getDimensionPixelSize(R.dimen.video_item_search_padding) * 2
            GridLayoutManagerHelper.create(list, minimumItemWidth)
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

private class LocalMediaGroupAdapter(
    private val onClick: (LocalMediaGroup) -> Unit,
    private val category: () -> LocalMediaAudioCategory
) : RecyclerView.Adapter<LocalMediaGroupAdapter.Holder>() {
    private var items = emptyList<LocalMediaGroup>()

    fun submit(updated: List<LocalMediaGroup>) {
        items = updated
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.list_local_media_group_item,
            parent,
            false
        )
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.localMediaGroupIcon)
        private val title = view.findViewById<TextView>(R.id.localMediaGroupItemTitle)
        private val subtitle = view.findViewById<TextView>(R.id.localMediaGroupItemSubtitle)

        fun bind(group: LocalMediaGroup) {
            val count = itemView.resources.getQuantityString(
                R.plurals.local_media_track_count,
                group.items.size,
                group.items.size
            )
            icon.setImageResource(
                if (category() == LocalMediaAudioCategory.ARTISTS) {
                    R.drawable.ic_person
                } else {
                    R.drawable.ic_music_note
                }
            )
            title.text = group.title
            subtitle.text = listOf(group.subtitle, count)
                .filter(String::isNotBlank)
                .joinToString(" • ")
            itemView.setOnClickListener { onClick(group) }
        }
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
