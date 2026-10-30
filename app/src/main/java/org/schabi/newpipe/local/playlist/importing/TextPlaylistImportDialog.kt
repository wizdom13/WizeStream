package org.schabi.newpipe.local.playlist.importing

import android.app.Application
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ExtractorHelper

internal data class TextPlaylistDestination(val id: Long, val name: String)
internal data class TextPlaylistState(
    val busy: Boolean = false,
    val preview: TextPlaylistPreview? = null,
    val destinations: List<TextPlaylistDestination> = emptyList(),
    val message: String = ""
)

class TextPlaylistImportDialog : DialogFragment() {
    private lateinit var model: TextPlaylistImportModel
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::inspect) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[TextPlaylistImportModel::class.java]
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val help = MaterialTextView(context).apply { setText(R.string.text_playlist_help) }
        val choose = MaterialButton(context).apply {
            setText(R.string.text_playlist_choose)
            setOnClickListener {
                try {
                    picker.launch(arrayOf("text/*", "application/octet-stream"))
                } catch (_: android.content.ActivityNotFoundException) {
                    model.state.value = TextPlaylistState(message = getString(R.string.text_playlist_picker_missing))
                }
            }
        }
        val status = MaterialTextView(context).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val progress = ProgressBar(context)
        val destinationLabel = MaterialTextView(context).apply { setText(R.string.text_playlist_destination) }
        val destination = Spinner(context).apply { contentDescription = getString(R.string.text_playlist_destination) }
        val name = TextInputEditText(context).apply {
            setSingleLine()
            filters = arrayOf(android.text.InputFilter.LengthFilter(200))
            setText(model.name)
            doAfterTextChanged { model.name = it.toString() }
        }
        val nameBox = TextInputLayout(context).apply {
            hint = getString(R.string.text_playlist_name)
            addView(name)
        }
        val skip = MaterialCheckBox(context).apply {
            setText(R.string.text_playlist_skip_duplicates)
            isChecked = model.skipDuplicates
            setOnCheckedChangeListener { _, checked -> model.skipDuplicates = checked }
        }
        val previewText = MaterialTextView(context).apply { setTextIsSelectable(true) }
        val start = MaterialButton(context).apply {
            setText(R.string.text_playlist_start)
            setOnClickListener {
                if (model.destinationId == null && model.name.isBlank()) {
                    nameBox.error = getString(R.string.text_playlist_name_required)
                } else {
                    nameBox.error = null
                    model.start()
                }
            }
        }
        val cancel = MaterialButton(context).apply {
            setText(android.R.string.cancel)
            setOnClickListener {
                if (model.state.value.busy) model.cancel() else dismiss()
            }
        }
        listOf(help, choose, progress, status, destinationLabel, destination, nameBox, skip, previewText, start, cancel).forEach(content::addView)
        destination.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                model.destinationId = model.state.value.destinations.getOrNull(position - 1)?.id
                nameBox.visibility = if (position == 0) View.VISIBLE else View.GONE
            }
        }
        var rendered: TextPlaylistPreview? = null
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    progress.visibility = if (state.busy) View.VISIBLE else View.GONE
                    status.text = state.message
                    choose.isEnabled = !state.busy
                    destination.isEnabled = !state.busy
                    name.isEnabled = !state.busy
                    skip.isEnabled = !state.busy
                    start.isEnabled = !state.busy && state.preview?.links?.isNotEmpty() == true
                    cancel.setText(if (state.busy) android.R.string.cancel else R.string.close)
                    val preview = state.preview
                    if (preview != null && preview !== rendered) {
                        rendered = preview
                        destination.adapter = ArrayAdapter(
                            context,
                            android.R.layout.simple_spinner_dropdown_item,
                            listOf(getString(R.string.text_playlist_new)) + state.destinations.map { "${it.name} (#${it.id})" }
                        )
                        destination.setSelection(state.destinations.indexOfFirst { it.id == model.destinationId } + 1)
                        previewText.text = getString(R.string.text_playlist_preview, preview.links.size, preview.invalidLines.size) +
                            "\n" + preview.links.take(50).joinToString("\n") { "${it.line}: ${it.url}" } +
                            if (preview.invalidLines.isNotEmpty()) "\n" + getString(R.string.text_playlist_invalid_lines, preview.invalidLines.joinToString(", ")) else ""
                    }
                    val visibility = if (preview == null) View.GONE else View.VISIBLE
                    listOf(destinationLabel, destination, skip, previewText, start).forEach { it.visibility = visibility }
                    nameBox.visibility = if (preview != null && model.destinationId == null) View.VISIBLE else View.GONE
                }
            }
        }
        return MaterialAlertDialogBuilder(context).setTitle(R.string.text_playlist_title)
            .setView(ScrollView(context).apply { addView(content) }).create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.85).toInt())
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        model.cancel()
        super.onCancel(dialog)
    }

    companion object {
        @JvmStatic fun show(manager: FragmentManager) {
            if (manager.findFragmentByTag("text-playlist-import") == null) TextPlaylistImportDialog().show(manager, "text-playlist-import")
        }
    }
}

class TextPlaylistImportModel(application: Application) : AndroidViewModel(application) {
    internal val state = MutableStateFlow(TextPlaylistState())
    internal var name = ""
    internal var destinationId: Long? = null
    internal var skipDuplicates = true
    private var profileId: String? = null
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
    }

    fun inspect(uri: Uri) {
        if (state.value.busy) return
        val context = getApplication<Application>()
        val target = ProfileManager.getActiveProfileId(context)
        profileId = target
        destinationId = null
        state.value = TextPlaylistState(busy = true)
        job = viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val preview = requireNotNull(context.contentResolver.openInputStream(uri)).use(TextPlaylistParser::parse)
                    val destinations = NewPipeDatabase.getInstance(context).playlistDAO().getAllDirectForProfile(target)
                        .map { TextPlaylistDestination(it.uid, it.name.orEmpty()) }
                    preview to destinations
                }
                val profileName = requireNotNull(ProfileManager.getProfile(context, target)).name
                state.value = TextPlaylistState(
                    preview = data.first,
                    destinations = data.second,
                    message = context.getString(R.string.text_playlist_profile, profileName)
                )
            } catch (_: CancellationException) {
                state.value = TextPlaylistState(message = context.getString(R.string.text_playlist_cancelled))
            } catch (_: Exception) {
                state.value = TextPlaylistState(message = context.getString(R.string.text_playlist_invalid))
            }
        }
    }

    fun start() {
        val snapshot = state.value
        val preview = snapshot.preview ?: return
        val target = profileId ?: return
        if (snapshot.busy || preview.links.isEmpty()) return
        val selected = destinationId
        val title = name.trim()
        val skip = skipDuplicates
        if (selected == null && title.isBlank()) return
        val context = getApplication<Application>()
        state.value = snapshot.copy(busy = true)
        job = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val scope = this
                    TextPlaylistImporter(
                        NewPipeDatabase.getInstance(context),
                        target,
                        resolve = { url -> StreamEntity(ExtractorHelper.getStreamInfo(0, url, false).blockingGet()) },
                        checkActive = {
                            scope.ensureActive()
                            check(ProfileManager.getProfile(context, target) != null)
                        },
                        progress = { done, total ->
                            state.value = snapshot.copy(
                                busy = true,
                                message = context.getString(R.string.text_playlist_progress, done, total)
                            )
                        }
                    ).import(preview, selected, title, skip)
                }
                state.value = TextPlaylistState(
                    message = context.getString(
                        R.string.text_playlist_complete,
                        result.added,
                        result.duplicates,
                        preview.invalidLines.size,
                        result.failedLines.size
                    ) +
                        if (result.failedLines.isNotEmpty()) "\n" + context.getString(R.string.text_playlist_failed_lines, result.failedLines.joinToString(", ")) else ""
                )
            } catch (_: CancellationException) {
                state.value = snapshot.copy(message = context.getString(R.string.text_playlist_cancelled))
            } catch (_: Exception) {
                state.value = snapshot.copy(message = context.getString(R.string.text_playlist_failed))
            }
        }
    }
}
