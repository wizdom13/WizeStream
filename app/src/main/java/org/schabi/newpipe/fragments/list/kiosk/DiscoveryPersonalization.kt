package org.schabi.newpipe.fragments.list.kiosk

import android.content.Context
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

internal data class DiscoveryRules(
    val interests: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    val hideWatched: Boolean = false,
    val hideShorts: Boolean = false,
    val hideLive: Boolean = false
) {
    fun apply(items: List<StreamInfoItem>, watched: Set<String>): List<StreamInfoItem> = items.filter { item ->
        val text = "${item.name} ${item.uploaderName}".lowercase(Locale.ROOT)
        hidden.none { text.contains(it) } &&
            (!hideWatched || "${item.serviceId}:${item.url}" !in watched) &&
            (!hideShorts || !item.isShortFormContent) &&
            (!hideLive || item.streamType !in setOf(StreamType.LIVE_STREAM, StreamType.AUDIO_LIVE_STREAM))
    }.sortedByDescending { item ->
        val text = "${item.name} ${item.uploaderName}".lowercase(Locale.ROOT)
        interests.count { text.contains(it) }
    }
}

class DiscoveryPersonalization private constructor(private val rules: DiscoveryRules, private val watched: Set<String>) {
    fun apply(items: List<StreamInfoItem>): List<StreamInfoItem> = rules.apply(items, watched)

    companion object {
        private const val PREFIX = "discovery_"

        private fun terms(text: String): List<String> = text.split(',', '\n').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct().take(100)

        private fun rules(context: Context): DiscoveryRules {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return DiscoveryRules(
                terms(prefs.getString(PREFIX + "interests", "").orEmpty()),
                terms(prefs.getString(PREFIX + "hidden", "").orEmpty()),
                prefs.getBoolean(PREFIX + "watched", false),
                prefs.getBoolean(PREFIX + "shorts", false),
                prefs.getBoolean(PREFIX + "live", false)
            )
        }

        /** Call on an IO scheduler: history is consulted only when requested. */
        @JvmStatic
        fun load(context: Context): DiscoveryPersonalization {
            val rules = rules(context)
            val watched = if (rules.hideWatched) {
                NewPipeDatabase.getInstance(context).streamHistoryDAO().discoveryWatchedKeys().toSet()
            } else {
                emptySet()
            }
            return DiscoveryPersonalization(rules, watched)
        }

        @JvmStatic
        fun configure(context: Context, onChanged: Runnable) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val padding = (24 * context.resources.displayMetrics.density).toInt()
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, 0)
                addView(TextView(context).apply { setText(R.string.discovery_personalize_help) })
            }
            fun field(key: String, label: Int): EditText {
                content.addView(TextView(context).apply { setText(label) })
                return EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setText(prefs.getString(PREFIX + key, ""))
                    maxLines = 4
                    content.addView(this)
                }
            }
            fun check(key: String, label: Int): CheckBox = CheckBox(context).apply {
                setText(label)
                isChecked = prefs.getBoolean(PREFIX + key, false)
                content.addView(this)
            }
            val interests = field("interests", R.string.discovery_interests)
            val hidden = field("hidden", R.string.discovery_hidden)
            val watched = check("watched", R.string.discovery_hide_watched)
            val shorts = check("shorts", R.string.discovery_hide_shorts)
            val live = check("live", R.string.discovery_hide_live)
            MaterialAlertDialogBuilder(context).setTitle(R.string.discovery_personalize)
                .setView(ScrollView(context).apply { addView(content) })
                .setPositiveButton(R.string.ok) { _, _ ->
                    prefs.edit().putString(PREFIX + "interests", interests.text.toString().take(10000))
                        .putString(PREFIX + "hidden", hidden.text.toString().take(10000))
                        .putBoolean(PREFIX + "watched", watched.isChecked)
                        .putBoolean(PREFIX + "shorts", shorts.isChecked)
                        .putBoolean(PREFIX + "live", live.isChecked).apply()
                    onChanged.run()
                }.setNeutralButton(R.string.discovery_reset) { _, _ ->
                    val editor = prefs.edit()
                    listOf("interests", "hidden", "watched", "shorts", "live").forEach { editor.remove(PREFIX + it) }
                    editor.apply()
                    onChanged.run()
                }.setNegativeButton(R.string.cancel, null).show()
        }
    }
}
