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
import org.schabi.newpipe.sync.HistorySyncRecorder
import org.schabi.newpipe.sync.RoomSubscriptionSyncStore

/** Payloads stay in a ViewModel, never in saved-state Bundles or preference values. */
class LibreTubeImportFragment : Fragment() {
    private lateinit var model: LibreTubeImportModel
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::inspect) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[LibreTubeImportModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val help = MaterialTextView(context).apply { setText(R.string.libretube_import_help) }
        val choose = MaterialButton(context).apply {
            setText(R.string.libretube_import_choose)
            setOnClickListener {
                try {
                    picker.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                } catch (_: android.content.ActivityNotFoundException) {
                    model.state.value = LibreTubeImportState(message = getString(R.string.libretube_import_picker_missing))
                }
            }
        }
        val progress = ProgressBar(context)
        val status = MaterialTextView(context).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val choices = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val apply = MaterialButton(context).apply {
            setText(R.string.libretube_import_apply)
            setOnClickListener { model.commit() }
        }
        listOf(help, choose, progress, status, choices, apply).forEach(content::addView)
        return ScrollView(context).apply { addView(content) }.also {
            // Collect against the view lifecycle in onViewCreated, after its owner exists.
            render = { state ->
                progress.visibility = if (state.busy) View.VISIBLE else View.GONE
                choose.isEnabled = !state.busy
                status.text = state.message
                choices.removeAllViews()
                val data = state.backup
                if (data != null) {
                    val labels = linkedMapOf(
                        LibreTubeCategory.SUBSCRIPTIONS to getString(R.string.libretube_import_subscriptions, data.subscriptions.size, data.groups.size),
                        LibreTubeCategory.HISTORY to getString(R.string.libretube_import_history, data.history.size),
                        LibreTubeCategory.POSITIONS to getString(R.string.libretube_import_positions, data.positions.size),
                        LibreTubeCategory.PLAYLISTS to getString(R.string.libretube_import_playlists, data.playlists.size, data.playlists.sumOf { list -> list.videos.size }),
                        LibreTubeCategory.BOOKMARKS to getString(R.string.libretube_import_bookmarks, data.bookmarks.size)
                    )
                    for ((category, label) in labels) {
                        choices.addView(
                            MaterialCheckBox(context).apply {
                                text = label
                                isChecked = category in model.selected
                                isEnabled = !state.busy
                                setOnCheckedChangeListener { _, checked ->
                                    if (checked) model.selected.add(category) else model.selected.remove(category)
                                    apply.isEnabled = !model.state.value.busy && model.selected.isNotEmpty()
                                }
                            }
                        )
                    }
                }
                apply.visibility = if (data != null) View.VISIBLE else View.GONE
                apply.isEnabled = data != null && !state.busy && model.selected.isNotEmpty()
            }
        }
    }

    private var render: ((LibreTubeImportState) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { render?.invoke(it) } }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.libretube_import_title)
    }

    override fun onDestroyView() {
        render = null
        super.onDestroyView()
    }
}

internal data class LibreTubeImportState(val busy: Boolean = false, val backup: LibreTubeBackup? = null, val message: String = "")

class LibreTubeImportModel(application: Application) : AndroidViewModel(application) {
    internal val state = MutableStateFlow(LibreTubeImportState())
    internal val selected = LibreTubeCategory.entries.toMutableSet()
    private var profileId: String? = null

    fun inspect(uri: Uri) {
        if (state.value.busy) return
        val context = getApplication<Application>()
        val target = ProfileManager.getActiveProfileId(context)
        profileId = target
        state.value = LibreTubeImportState(busy = true)
        viewModelScope.launch {
            try {
                val backup = withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use(LibreTubeBackupParser::parse)
                }
                selected.clear()
                selected.addAll(LibreTubeCategory.entries)
                val name = requireNotNull(ProfileManager.getProfile(context, target)).name
                state.value = LibreTubeImportState(backup = backup, message = context.getString(R.string.libretube_import_preview, name, backup.skipped))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = LibreTubeImportState(message = context.getString(R.string.libretube_import_invalid))
            }
        }
    }

    fun commit() {
        val snapshot = state.value
        val backup = snapshot.backup ?: return
        val target = profileId ?: return
        if (snapshot.busy || selected.isEmpty()) return
        val categories = selected.toSet()
        val context = getApplication<Application>()
        state.value = snapshot.copy(busy = true)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val scope = this
                    val history = HistorySyncRecorder.get(context)
                    LibreTubeImporter(
                        NewPipeDatabase.getInstance(context),
                        target,
                        recordWatch = { id, time -> history.recordWatchEventForProfile(target, id, time, 1) },
                        recordProgress = { id, position, time -> history.recordProgressForProfile(target, id, position, time) },
                        recordSubscription = RoomSubscriptionSyncStore.get(context)::recordLocalUpsert,
                        checkActive = {
                            scope.ensureActive()
                            check(ProfileManager.getProfile(context, target) != null) { "Profile removed" }
                        }
                    ).import(backup, categories, System.currentTimeMillis())
                }
                state.value = LibreTubeImportState(message = context.getString(R.string.libretube_import_complete, result.added, result.skipped))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = snapshot.copy(message = context.getString(R.string.libretube_import_failed))
            }
        }
    }
}
