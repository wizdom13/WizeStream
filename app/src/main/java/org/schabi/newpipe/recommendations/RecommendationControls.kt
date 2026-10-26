package org.schabi.newpipe.recommendations

import android.content.Context
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.profiles.ProfileManager

object RecommendationControls {
    @JvmStatic
    fun show(context: Context, changed: Runnable) {
        val profile = ProfileManager.getActiveProfileId(context)
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(TextView(context).apply { setText(R.string.local_recommendations_help) })
        }
        val fields = linkedMapOf<String, CheckBox>()
        for ((key, title) in listOf("enabled" to R.string.local_recommendations_enable, "paused" to R.string.local_recommendations_pause, "shorts" to R.string.local_recommendations_shorts, "autoplay" to R.string.local_recommendations_autoplay)) {
            fields[key] = CheckBox(context).apply {
                setText(title)
                isChecked = LocalRecommendations.setting(context, key)
                content.addView(this)
            }
        }
        content.addView(TextView(context).apply { setText(R.string.local_recommendations_topics) })
        val topics = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(LocalRecommendations.topics(context))
            content.addView(this)
        }
        MaterialAlertDialogBuilder(context).setTitle(R.string.local_recommendations_title)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.ok) { _, _ ->
                if (profile == ProfileManager.getActiveProfileId(context)) {
                    LocalRecommendations.configure(context, fields.mapValues { it.value.isChecked }, topics.text.toString())
                    changed.run()
                }
            }.setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.discovery_reset) { _, _ ->
                MaterialAlertDialogBuilder(context).setTitle(R.string.discovery_reset)
                    .setMessage(R.string.local_recommendations_reset_help)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        if (profile == ProfileManager.getActiveProfileId(context)) {
                            LocalRecommendations.reset(context)
                            changed.run()
                        }
                    }.setNegativeButton(R.string.cancel, null).show()
            }.show()
    }
}
