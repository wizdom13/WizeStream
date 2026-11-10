package org.schabi.newpipe.local.radio

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.schabi.newpipe.R
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper

class RadioFragment : Fragment(R.layout.fragment_radio) {
    private var filters = RadioFilters()
    private var savedOnly = false
    private var searchJob: Job? = null
    private lateinit var nameInput: TextInputEditText
    private lateinit var savedButton: Button
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var stationList: RecyclerView
    private lateinit var header: AppBarLayout
    private lateinit var stations: RadioStationAdapter
    private lateinit var preferences: SharedPreferences
    private var profileId = ""
    private var directoryResults: List<RadioStation>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filters = RadioFilters(
            savedInstanceState?.getString("name").orEmpty(),
            savedInstanceState?.getString("country").orEmpty(),
            savedInstanceState?.getString("language").orEmpty(),
            savedInstanceState?.getString("genre").orEmpty()
        )
        savedOnly = savedInstanceState?.getBoolean("savedOnly") == true
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        val context = requireContext()
        preferences = context.getSharedPreferences("live_radio_stations", Context.MODE_PRIVATE)
        profileId = ProfileManager.getActiveProfileId(context)
        header = view.findViewById(R.id.radio_header)
        nameInput = view.findViewById<TextInputEditText>(R.id.radio_search_input).apply {
            setText(this@RadioFragment.filters.name)
            setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
                startSearch()
                true
            }
        }
        view.findViewById<View>(R.id.radio_search).setOnClickListener { startSearch() }
        view.findViewById<View>(R.id.radio_filters).setOnClickListener { showFilters() }
        savedButton = view.findViewById<Button>(R.id.radio_saved).apply {
            setOnClickListener { toggleSaved() }
        }
        view.findViewById<View>(R.id.radio_add).setOnClickListener { showAddStation() }
        status = view.findViewById(R.id.radio_status)
        progress = view.findViewById(R.id.radio_progress)
        stations = RadioStationAdapter(::play, ::showStationActions)
        stationList = view.findViewById<RecyclerView>(R.id.radio_stations).apply {
            adapter = stations
        }
        if (savedOnly) showSaved() else loadDirectory()
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.live_radio))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("name", if (::nameInput.isInitialized) nameInput.text.toString() else filters.name)
        outState.putString("country", filters.country)
        outState.putString("language", filters.language)
        outState.putString("genre", filters.genre)
        outState.putBoolean("savedOnly", savedOnly)
    }

    private fun startSearch() {
        ViewCompat.getWindowInsetsController(nameInput)?.hide(WindowInsetsCompat.Type.ime())
        nameInput.clearFocus()
        filters = filters.copy(name = nameInput.text.toString())
        savedOnly = false
        loadDirectory()
    }

    private fun loadDirectory() {
        searchJob?.cancel()
        directoryResults = null
        savedButton.setText(R.string.radio_saved)
        progress.isVisible = true
        showStations(emptyList(), R.string.radio_loading)
        val query = filters
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val results = try {
                withContext(Dispatchers.IO) { RadioDirectory.search(query) }
            } catch (_: IOException) {
                null
            }
            progress.isVisible = false
            directoryResults = results
            when {
                results == null -> showStations(emptyList(), R.string.radio_directory_failed)
                results.isEmpty() -> showStations(results, R.string.radio_no_results)
                else -> showStations(results, R.string.radio_station_hint)
            }
        }
    }

    private fun toggleSaved() {
        if (savedOnly) {
            savedOnly = false
            val results = directoryResults
            if (results == null) {
                loadDirectory()
            } else {
                savedButton.setText(R.string.radio_saved)
                showStations(results, if (results.isEmpty()) R.string.radio_no_results else R.string.radio_station_hint)
            }
        } else {
            showSaved()
        }
    }

    private fun showSaved() {
        searchJob?.cancel()
        savedOnly = true
        savedButton.setText(R.string.radio_directory)
        progress.isVisible = false
        val saved = savedStations()
        showStations(saved, if (saved.isEmpty()) R.string.radio_no_saved else R.string.radio_station_hint)
    }

    private fun showStations(items: List<RadioStation>, message: Int) {
        status.setText(message)
        stations.submitList(items) { stationList.scrollToPosition(0) }
        header.setExpanded(true, false)
    }

    private fun savedStations(): List<RadioStation> = try {
        val json = JSONArray(preferences.getString(profileId, "[]"))
        (0 until json.length()).mapNotNull { json.optJSONObject(it)?.let(RadioStation::fromJson) }
    } catch (_: JSONException) {
        emptyList()
    }

    private fun saveStations(items: List<RadioStation>) {
        val json = JSONArray()
        items.forEach { json.put(it.json()) }
        preferences.edit { putString(profileId, json.toString()) }
        if (savedOnly) showSaved()
    }

    private fun showStationActions(station: RadioStation) {
        val saved = savedStations()
        val exists = saved.any { it.url == station.url }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(station.name)
            .setItems(arrayOf(getString(if (exists) R.string.radio_remove_saved else R.string.radio_save_station))) { _, _ ->
                saveStations(if (exists) saved.filterNot { it.url == station.url } else saved + station)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFilters() {
        val country = input(R.string.radio_country, filters.country)
        val language = input(R.string.radio_language, filters.language)
        val genre = input(R.string.radio_genre, filters.genre)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.radio_filters)
            .setView(dialogInputs(country, language, genre))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                filters = filters.copy(
                    country = country.editText?.text.toString(),
                    language = language.editText?.text.toString(),
                    genre = genre.editText?.text.toString()
                )
                startSearch()
            }
            .show()
    }

    private fun showAddStation() {
        val name = input(R.string.radio_station_name)
        val address = input(R.string.radio_stream_url).apply {
            editText?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.radio_add_url)
            .setView(dialogInputs(name, address))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val title = name.editText?.text.toString().trim()
            val url = secureRadioUrl(address.editText?.text.toString())
            when {
                title.isEmpty() -> name.error = getString(R.string.radio_station_name_required)

                url == null -> address.error = getString(R.string.radio_https_required)

                else -> {
                    saveStations(savedStations().filterNot { it.url == url } + RadioStation(title, url, hls = isRadioHls(url)))
                    showSaved()
                    dialog.dismiss()
                }
            }
        }
    }

    private fun input(label: Int, value: String = "") = (layoutInflater.inflate(R.layout.dialog_radio_input, null, false) as TextInputLayout).apply {
        hint = getString(label)
        editText?.setText(value)
    }

    private fun dialogInputs(vararg fields: TextInputLayout) = ScrollView(requireContext()).apply {
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPaddingRelative(padding, 0, padding, 0)
                fields.forEach {
                    addView(
                        it,
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = padding / 2
                        }
                    )
                }
            }
        )
    }

    private fun play(station: RadioStation) {
        val item = PlayQueueItem.radioStation(station.name, station.url, station.details, station.hls)
        NavigationHelper.playOnBackgroundPlayer(requireContext(), LocalMediaPlayQueue(listOf(item), 0), false)
    }
}
