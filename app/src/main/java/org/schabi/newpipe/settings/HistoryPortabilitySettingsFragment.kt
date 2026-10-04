package org.schabi.newpipe.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import org.schabi.newpipe.R
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.export.HistoryArchiveWorker

class HistoryPortabilitySettingsFragment : BasePreferenceFragment() {
    private var selectedProfile: String? = null
    private var workId: UUID? = null
    private val jsonExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> enqueue(uri, "json") }
    private val csvExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> enqueue(uri, "csv") }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> enqueue(uri, "import") }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        selectedProfile = savedInstanceState?.getString("profile")
        workId = savedInstanceState?.getString("work")?.let(UUID::fromString)
        addPreferencesFromResourceRegistry()
        findPreference<Preference>("history_export_json")!!.setOnPreferenceClickListener {
            selectedProfile = ProfileManager.getActiveProfileId(requireContext())
            jsonExport.launch("WizeStream-history.json")
            true
        }
        findPreference<Preference>("history_export_csv")!!.setOnPreferenceClickListener {
            selectedProfile = ProfileManager.getActiveProfileId(requireContext())
            csvExport.launch("WizeStream-history.csv")
            true
        }
        findPreference<Preference>("history_import_json")!!.setOnPreferenceClickListener {
            selectedProfile = ProfileManager.getActiveProfileId(requireContext())
            importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeWork()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("profile", selectedProfile)
        outState.putString("work", workId?.toString())
    }

    private fun enqueue(uri: Uri?, action: String) {
        if (uri == null) return
        val profile = selectedProfile ?: return
        val flags = if (action == "import") Intent.FLAG_GRANT_READ_URI_PERMISSION else Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Some providers only grant access until the activity finishes.
        }
        val request = OneTimeWorkRequestBuilder<HistoryArchiveWorker>()
            .setInputData(workDataOf("uri" to uri.toString(), "profile" to profile, "action" to action)).build()
        workId = request.id
        WorkManager.getInstance(requireContext()).enqueue(request)
        observeWork()
    }

    private fun observeWork() {
        val id = workId ?: return
        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(id).observe(viewLifecycleOwner) { work ->
            if (work == null) return@observe
            listOf("history_export_json", "history_export_csv", "history_import_json").forEach {
                findPreference<Preference>(it)?.isEnabled = work.state.isFinished
            }
            findPreference<Preference>("history_transfer_status")?.summary = when {
                !work.state.isFinished -> getString(R.string.history_transfer_running)
                work.state == androidx.work.WorkInfo.State.SUCCEEDED -> getString(R.string.history_transfer_complete, work.outputData.getInt("count", 0))
                else -> getString(R.string.history_transfer_failed, work.outputData.getString("error").orEmpty())
            }
        }
    }
}
