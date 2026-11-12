package org.schabi.newpipe.local.tv

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.R
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.views.NewPipeEditText

class LiveTvFragment : Fragment(R.layout.fragment_live_tv) {
    private var library = TvLibrary()
    private var profile = ""
    private var query = ""
    private var group = ""
    private var sourceId: String? = null
    private var favoritesOnly = false
    private var work: Job? = null
    private var channels: TvChannelAdapter? = null
    private var status: TextView? = null
    private var progress: View? = null
    private var ready = false
    private var libraryReady = false
    private var generation = 0
    private var stateProfile: String? = null
    private val refreshAttempts = mutableMapOf<String, Long>()
    private val dialogs = mutableListOf<androidx.appcompat.app.AlertDialog>()
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || !libraryReady) return@registerForActivityResult
        val context = context ?: return@registerForActivityResult
        val target = profile
        runWork {
            val imported = withContext(Dispatchers.IO) {
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0)?.take(128) else null
                }.orEmpty().ifBlank { context.getString(R.string.tv_playlists) }
                val result = context.contentResolver.openInputStream(uri)?.use { M3uParser.parse(TvPlaylistLoader.read(it)) }
                    ?: throw IOException("Cannot read playlist")
                name to result
            }
            addPlaylist(TvPlaylist(UUID.randomUUID().toString(), imported.first, null, imported.second.channels), imported.second.skipped, target)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        stateProfile = state?.getString("profile")
        query = state?.getString("query").orEmpty()
        group = state?.getString("group").orEmpty()
        sourceId = state?.getString("source")
        favoritesOnly = state?.getBoolean("favorites") == true
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        status = view.findViewById(R.id.tv_status)
        progress = view.findViewById(R.id.tv_progress)
        channels = TvChannelAdapter({ play(it) }, ::channelActions)
        view.findViewById<RecyclerView>(R.id.tv_stations).adapter = channels
        val input = view.findViewById<NewPipeEditText>(R.id.tv_search_input)
        input.setText(query)
        input.doAfterTextChanged {
            query = it?.toString().orEmpty()
            render()
        }
        view.findViewById<View>(R.id.tv_groups).setOnClickListener { chooseGroup() }
        view.findViewById<View>(R.id.tv_clear_filters).setOnClickListener {
            group = ""
            sourceId = null
            favoritesOnly = false
            input.setText("")
            render()
        }
        view.findViewById<View>(R.id.tv_favorites).setOnClickListener {
            favoritesOnly = !favoritesOnly
            render()
        }
        view.findViewById<View>(R.id.tv_add).setOnClickListener { addUrl() }
        view.findViewById<View>(R.id.tv_import_file).setOnClickListener { importFile.launch(arrayOf("*/*")) }
        view.findViewById<View>(R.id.tv_playlists).setOnClickListener { choosePlaylist() }
        view.findViewById<View>(R.id.tv_browse).setOnClickListener {
            val previous = library.playlists.asReversed().firstNotNullOfOrNull { TvDirectory.selection(requireContext(), it) }
            TvDirectoryPicker(layoutInflater, ::trackDialog).show(previous) { selection ->
                val target = profile
                runWork(R.string.tv_directory_error) {
                    val loaded = withContext(Dispatchers.IO) { TvDirectory.load(selection) }
                    addPlaylist(loaded.first, loaded.second, target)
                    if (target == profile) {
                        sourceId = selection.id
                        group = ""
                        favoritesOnly = false
                        input.setText("")
                        render()
                        status?.text = getString(R.string.tv_imported, loaded.first.channels.size, loaded.second)
                    }
                }
            }
        }
        ready = true
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.live_tv))
        val active = ProfileManager.getActiveProfileId(requireContext())
        if (active != profile || !libraryReady) {
            if (profile.isNotEmpty() && profile != active || stateProfile != null && stateProfile != active) {
                query = ""
                group = ""
                sourceId = null
                favoritesOnly = false
            }
            dialogs.toList().forEach { it.dismiss() }
            profile = active
            stateProfile = active
            libraryReady = false
            library = TvLibrary()
            view?.findViewById<NewPipeEditText>(R.id.tv_search_input)?.setText(query)
            val context = requireContext()
            runWork {
                library = withContext(Dispatchers.IO) { TvPlaylistStore(context, active).read() }
                libraryReady = true
                render()
                refreshDirectoryCache(active)
            }
        } else {
            setActionsEnabled(work?.isActive != true)
            render()
            if (work?.isActive != true && directoryRefreshDue(active)) runWork { refreshDirectoryCache(active) }
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putString("profile", profile)
        state.putString("query", query)
        state.putString("group", group)
        state.putString("source", sourceId)
        state.putBoolean("favorites", favoritesOnly)
    }

    override fun onDestroyView() {
        ready = false
        view?.findViewById<RecyclerView>(R.id.tv_stations)?.adapter = null
        channels = null
        status = null
        progress = null
        dialogs.toList().forEach { it.dismiss() }
        work?.cancel()
        work = null
        super.onDestroyView()
    }

    private fun runWork(@androidx.annotation.StringRes errorMessage: Int = R.string.tv_error, action: suspend () -> Unit) {
        work?.cancel()
        val request = ++generation
        work = viewLifecycleOwner.lifecycleScope.launch {
            progress?.isVisible = true
            setActionsEnabled(false)
            status?.setText(R.string.tv_loading)
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: TvPlaylistLimitException) {
                if (request == generation) status?.setText(R.string.tv_limit_error)
            } catch (_: IOException) {
                if (request == generation) status?.setText(errorMessage)
            } catch (_: SecurityException) {
                if (request == generation) status?.setText(R.string.tv_error)
            } finally {
                if (ready && request == generation) {
                    progress?.isVisible = false
                    setActionsEnabled(libraryReady)
                }
            }
        }
    }

    private fun setActionsEnabled(enabled: Boolean) {
        listOf(R.id.tv_browse, R.id.tv_add, R.id.tv_import_file, R.id.tv_playlists, R.id.tv_groups, R.id.tv_favorites, R.id.tv_clear_filters).forEach {
            view?.findViewById<View>(it)?.isEnabled = enabled
        }
    }

    private fun render() {
        if (!ready) return
        val visible = combineTvChannels(library.playlists.filter { sourceId == null || it.id == sourceId }.flatMap { it.channels }).filter {
            (group.isEmpty() || group in it.group.split(';').map(String::trim)) && (!favoritesOnly || it.urls.any(library.favorites::contains)) &&
                org.schabi.newpipe.local.search.ContextualSearchHelper.matches(query, it.name, it.group)
        }
        channels?.submitList(visible)
        status?.text = when {
            library.playlists.isEmpty() -> getString(R.string.tv_empty)
            visible.isEmpty() -> getString(R.string.tv_no_matches)
            else -> getString(R.string.tv_channels_hint, visible.size)
        }
        view?.findViewById<TextView>(R.id.tv_favorites)?.setText(if (favoritesOnly) R.string.tv_all_channels else R.string.tv_favorites)
    }

    private suspend fun persist(next: TvLibrary, target: String = profile) {
        val context = requireContext()
        withContext(Dispatchers.IO) { TvPlaylistStore(context, target).write(next) }
        if (target == profile && ProfileManager.getActiveProfileId(context) == target) {
            library = next
            render()
        }
    }

    private suspend fun addPlaylist(playlist: TvPlaylist, skipped: Int, target: String) {
        if (target != profile) return
        persist(library.withPlaylist(playlist), target)
        if (target == profile) status?.text = getString(R.string.tv_imported, playlist.channels.size, skipped)
    }

    private fun directoryRefreshDue(target: String): Boolean {
        val now = System.currentTimeMillis()
        val last = refreshAttempts[target]
        if (last != null && now >= last && now - last < 60 * 60 * 1000L) return false
        return library.playlists.any { TvDirectory.stale(it, now) && TvDirectory.selection(requireContext(), it) != null }
    }

    private suspend fun refreshDirectoryCache(target: String) {
        if (!directoryRefreshDue(target)) return
        val now = System.currentTimeMillis()
        val pending = library.playlists.filter { TvDirectory.stale(it, now) }.mapNotNull { TvDirectory.selection(requireContext(), it) }
        refreshAttempts[target] = now
        val limit = Semaphore(2)
        val results = coroutineScope {
            pending.map { selection ->
                async(Dispatchers.IO) {
                    limit.withPermit {
                        try {
                            TvDirectory.load(selection).first
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: IOException) {
                            null
                        }
                    }
                }
            }.awaitAll()
        }
        if (target != profile || ProfileManager.getActiveProfileId(requireContext()) != target) return
        var next = library
        results.filterNotNull().forEach { next = next.withPlaylist(it) }
        if (next != library) persist(next, target) else render()
        if (results.any { it == null }) status?.setText(R.string.tv_directory_cached)
    }

    private fun addUrl() {
        val name = layoutInflater.inflate(R.layout.dialog_radio_input, null) as TextInputLayout
        name.hint = getString(R.string.tv_playlist_name)
        val address = layoutInflater.inflate(R.layout.dialog_radio_input, null) as TextInputLayout
        address.hint = getString(R.string.tv_playlist_url)
        address.editText?.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        val content = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(name)
            addView(address)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.tv_add_url).setView(content)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.ok, null).showTracked()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val title = name.editText?.text.toString().trim().take(128)
            val url = tvUrl(address.editText?.text.toString())
            when {
                title.isBlank() -> name.error = getString(R.string.tv_name_required)

                url == null -> address.error = getString(R.string.tv_https_required)

                else -> {
                    val target = profile
                    dialog.dismiss()
                    runWork {
                        val result = withContext(Dispatchers.IO) { TvPlaylistLoader.load(url, title) }
                        addPlaylist(TvPlaylist(UUID.randomUUID().toString(), title, result.first, result.second.channels), result.second.skipped, target)
                    }
                }
            }
        }
    }

    private fun chooseGroup() {
        val options = library.playlists.filter { sourceId == null || it.id == sourceId }.flatMap { it.channels }.flatMap { it.group.split(';') }.map(String::trim).filter { it.isNotEmpty() }.distinct().sorted()
        val labels = listOf(getString(R.string.tv_all_channels)) + options
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.tv_groups)
            .setSingleChoiceItems(labels.toTypedArray(), options.indexOf(group) + 1) { dialog, index ->
                group = if (index == 0) "" else options[index - 1]
                dialog.dismiss()
                render()
            }
            .setNegativeButton(R.string.cancel, null).showTracked()
    }

    private fun choosePlaylist() {
        val options = library.playlists
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.tv_playlists)
            .setItems((listOf(getString(R.string.tv_all_channels)) + options.map { it.name }).toTypedArray()) { _, index ->
                if (index == 0) {
                    sourceId = null
                    render()
                } else {
                    playlistActions(options[index - 1])
                }
            }.setNegativeButton(R.string.cancel, null).showTracked()
    }

    private fun playlistActions(playlist: TvPlaylist) {
        sourceId = playlist.id
        group = ""
        render()
        val actions = if (playlist.address == null) arrayOf(getString(R.string.tv_remove_playlist)) else arrayOf(getString(R.string.tv_refresh), getString(R.string.tv_remove_playlist))
        MaterialAlertDialogBuilder(requireContext()).setTitle(playlist.name).setItems(actions) { _, which ->
            if (playlist.address != null && which == 0) {
                val target = profile
                runWork {
                    val selection = TvDirectory.selection(requireContext(), playlist)
                    val result = withContext(Dispatchers.IO) {
                        if (selection != null) {
                            TvDirectory.load(selection)
                        } else {
                            val loaded = TvPlaylistLoader.load(playlist.address, playlist.name)
                            playlist.copy(address = loaded.first, channels = loaded.second.channels) to loaded.second.skipped
                        }
                    }
                    addPlaylist(result.first, result.second, target)
                }
            } else {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.tv_remove_playlist).setMessage(R.string.tv_remove_confirm)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ ->
                        runWork {
                            sourceId = null
                            val remaining = library.playlists.filterNot { it.id == playlist.id }
                            val urls = remaining.flatMap { it.channels }.flatMap { it.urls }.toSet()
                            persist(TvLibrary(remaining, library.favorites.intersect(urls)))
                        }
                    }.showTracked()
            }
        }.setNegativeButton(R.string.cancel, null).showTracked()
    }

    private fun channelActions(channel: TvChannel) {
        if (work?.isActive == true || !libraryReady) return
        val saved = channel.urls.any(library.favorites::contains)
        val options = mutableListOf(getString(if (saved) R.string.tv_remove_favorite else R.string.tv_save_favorite))
        if (channel.alternatives.isNotEmpty()) options.add(getString(R.string.tv_choose_stream))
        MaterialAlertDialogBuilder(requireContext()).setTitle(channel.name)
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 1) {
                    chooseStream(channel)
                } else {
                    runWork {
                        persist(library.copy(favorites = if (saved) library.favorites - channel.urls.toSet() else library.favorites + channel.urls))
                    }
                }
            }.setNegativeButton(R.string.cancel, null).showTracked()
    }

    private fun chooseStream(channel: TvChannel) {
        val labels = channel.urls.mapIndexed { index, url -> getString(R.string.tv_stream_choice, index + 1, url.toHttpUrlOrNull()?.host.orEmpty()) }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.tv_choose_stream).setItems(labels.toTypedArray()) { _, which ->
            play(channel, channel.urls[which])
        }.setNegativeButton(R.string.cancel, null).showTracked()
    }

    private fun trackDialog(dialog: androidx.appcompat.app.AlertDialog) {
        dialogs.add(dialog)
        dialog.setOnDismissListener { dialogs.remove(dialog) }
    }

    private fun MaterialAlertDialogBuilder.showTracked(): androidx.appcompat.app.AlertDialog = show().also(::trackDialog)

    private fun play(channel: TvChannel, stream: String = channel.url) {
        val visible = channels?.currentList ?: return
        val items = visible.map {
            val url = if (it == channel) stream else it.url
            val path = url.toHttpUrlOrNull()?.encodedPath.orEmpty()
            val mime = when {
                it.hls || path.endsWith(".m3u8", true) -> "application/x-mpegURL"
                path.endsWith(".mpd", true) -> "application/dash+xml"
                else -> null
            }
            PlayQueueItem.liveTv(it.name, url, it.group, mime, it.logo)
        }
        val index = visible.indexOf(channel)
        if (index >= 0) NavigationHelper.playOnMainPlayer(requireContext(), LocalMediaPlayQueue(items, index), false)
    }
}
