package org.schabi.newpipe.settings.export

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.sync.RoomSubscriptionSyncStore

/** Preview and data snapshots remain in a ViewModel, never in saved-state Bundles. */
class FreeTubeTransferFragment : Fragment() {
    private lateinit var model: FreeTubeTransferModel
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::inspect) }
    private val writer = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-freetube-db")) { uri -> model.writeExport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[FreeTubeTransferModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val help = MaterialTextView(context).apply { setText(R.string.freetube_transfer_help) }
        val choose = MaterialButton(context).apply {
            setText(R.string.freetube_choose)
            setOnClickListener {
                try {
                    picker.launch(arrayOf("application/x-freetube-db", "application/json", "application/octet-stream", "text/*"))
                } catch (_: android.content.ActivityNotFoundException) {
                    model.message(R.string.freetube_picker_missing)
                }
            }
        }
        val progress = ProgressBar(context)
        val status = MaterialTextView(context).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val subscriptions = MaterialCheckBox(context).apply {
            isChecked = model.importSubscriptions
            setOnCheckedChangeListener { _, checked -> model.importSubscriptions = checked }
        }
        val playlists = MaterialCheckBox(context).apply {
            isChecked = model.importPlaylists
            setOnCheckedChangeListener { _, checked -> model.importPlaylists = checked }
        }
        val apply = MaterialButton(context).apply {
            setText(R.string.freetube_import)
            setOnClickListener { model.commit() }
        }
        fun exportButton(kind: String, label: Int): MaterialButton = MaterialButton(context).apply {
            setText(label)
            setOnClickListener {
                if (!model.beginExport(kind)) return@setOnClickListener
                try {
                    writer.launch("freetube-$kind.db")
                } catch (_: android.content.ActivityNotFoundException) {
                    model.writeExport(null)
                    model.message(R.string.freetube_picker_missing)
                }
            }
        }
        val exportSubscriptions = exportButton("subscriptions", R.string.freetube_export_subscriptions)
        val exportPlaylists = exportButton("playlists", R.string.freetube_export_playlists)
        listOf(help, choose, progress, status, subscriptions, playlists, apply, exportSubscriptions, exportPlaylists).forEach(content::addView)
        render = { state ->
            progress.visibility = if (state.busy) View.VISIBLE else View.GONE
            listOf(choose, subscriptions, playlists, apply, exportSubscriptions, exportPlaylists).forEach { it.isEnabled = !state.busy }
            status.text = state.message
            val data = state.preview
            listOf(subscriptions, playlists, apply).forEach { it.visibility = if (data == null) View.GONE else View.VISIBLE }
            if (data != null) {
                subscriptions.text = getString(R.string.freetube_subscriptions_count, data.subscriptions.size, data.groups.size)
                playlists.text = getString(R.string.freetube_playlists_count, data.playlists.size, data.playlists.sumOf { it.videos.size })
            }
        }
        return ScrollView(context).apply { addView(content) }
    }

    private var render: ((FreeTubeTransferState) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { render?.invoke(it) } }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.freetube_transfer_title)
    }

    override fun onDestroyView() {
        render = null
        super.onDestroyView()
    }
}

internal data class FreeTubeTransferState(val busy: Boolean = false, val preview: LibreTubeBackup? = null, val message: String = "")

class FreeTubeTransferModel(application: Application) : AndroidViewModel(application) {
    internal val state = MutableStateFlow(FreeTubeTransferState())
    internal var importSubscriptions = true
    internal var importPlaylists = true
    private var targetProfile: String? = null
    private var exportTarget: Pair<String, String>? = null

    internal fun message(resource: Int) {
        state.value = state.value.copy(message = getApplication<Application>().getString(resource))
    }

    internal fun inspect(uri: Uri) {
        if (state.value.busy) return
        val context = getApplication<Application>()
        val target = ProfileManager.getActiveProfileId(context)
        targetProfile = target
        state.value = FreeTubeTransferState(busy = true)
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openInputStream(uri)).use(FreeTubeBackup::parse) }
                val profile = requireNotNull(ProfileManager.getProfile(context, target))
                state.value = FreeTubeTransferState(preview = data, message = context.getString(R.string.freetube_preview, profile.name, data.skipped))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = FreeTubeTransferState(message = context.getString(R.string.freetube_invalid))
            }
        }
    }

    internal fun commit() {
        val snapshot = state.value
        val data = snapshot.preview ?: return
        val target = targetProfile ?: return
        if (snapshot.busy) return
        val selected = buildSet {
            if (importSubscriptions) add(LibreTubeCategory.SUBSCRIPTIONS)
            if (importPlaylists) add(LibreTubeCategory.PLAYLISTS)
        }
        if (selected.isEmpty()) return
        val context = getApplication<Application>()
        state.value = snapshot.copy(busy = true)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val scope = this
                    LibreTubeImporter(
                        NewPipeDatabase.getInstance(context),
                        target,
                        recordSubscription = RoomSubscriptionSyncStore.get(context)::recordLocalUpsert,
                        checkActive = {
                            scope.ensureActive()
                            check(ProfileManager.getProfile(context, target) != null)
                        }
                    ).import(data, selected, System.currentTimeMillis(), sourceName = "FreeTube")
                }
                state.value = FreeTubeTransferState(message = context.getString(R.string.freetube_import_complete, result.added, result.skipped))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = snapshot.copy(message = context.getString(R.string.freetube_import_failed))
            }
        }
    }

    internal fun beginExport(kind: String): Boolean {
        if (state.value.busy || kind !in setOf("subscriptions", "playlists")) return false
        exportTarget = kind to ProfileManager.getActiveProfileId(getApplication())
        state.value = state.value.copy(busy = true)
        return true
    }

    internal fun writeExport(uri: Uri?) {
        val target = exportTarget ?: return
        exportTarget = null
        if (uri == null) {
            state.value = state.value.copy(busy = false)
            return
        }
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val database = NewPipeDatabase.getInstance(context)
                    var payload = ""
                    var skipped = 0
                    database.runInTransaction {
                        val profile = requireNotNull(ProfileManager.getProfile(context, target.second))
                        payload = if (target.first == "subscriptions") {
                            val rows = database.subscriptionDAO().getAllDirectForProfile(target.second)
                            val channels = rows.mapNotNull { row ->
                                val id = if (row.serviceId == 0) FreeTubeBackup.identifier(row.url, true) else null
                                if (id == null) {
                                    skipped++
                                    null
                                } else {
                                    LibreTubeChannel(id, row.name.orEmpty())
                                }
                            }
                            FreeTubeBackup.exportSubscriptions(profile.name, channels)
                        } else {
                            val rows = database.playlistDAO().getAllDirectForProfile(target.second)
                            val playlists = rows.map { row ->
                                val videos = database.playlistStreamDAO().getOrderedStreamsDirectForProfile(target.second, row.uid).mapNotNull { video ->
                                    val id = if (video.serviceId == 0) FreeTubeBackup.identifier(video.url, false) else null
                                    if (id == null) {
                                        skipped++
                                        null
                                    } else {
                                        LibreTubeVideo(id, video.title, video.uploader, video.duration)
                                    }
                                }
                                LibreTubePlaylist(row.name.orEmpty(), videos)
                            }
                            FreeTubeBackup.exportPlaylists(playlists, System.currentTimeMillis())
                        }
                    }
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    withContext(Dispatchers.Main) { state.value = state.value.copy(busy = false, message = context.getString(R.string.freetube_export_complete, skipped)) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = state.value.copy(busy = false, message = context.getString(R.string.freetube_export_failed))
            }
        }
    }
}
