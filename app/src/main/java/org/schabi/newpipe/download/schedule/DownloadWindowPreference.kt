package org.schabi.newpipe.download.schedule

import android.app.TimePickerDialog
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R

class DownloadWindowPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    override fun onClick() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val builder = MaterialAlertDialogBuilder(context)
        val content = LinearLayout(builder.context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
        fun check(label: Int, key: String, default: Boolean) = MaterialCheckBox(builder.context).apply {
            setText(label)
            isChecked = prefs.getBoolean(key, default)
            content.addView(this)
        }
        val enabled = check(R.string.download_window_enable, DownloadWindow.ENABLED, false)
        var start = prefs.getInt(DownloadWindow.START, 0).coerceIn(0, 1439)
        var end = prefs.getInt(DownloadWindow.END, 360).coerceIn(0, 1439)
        fun timeButton(label: Int, get: () -> Int, set: (Int) -> Unit) {
            val button = MaterialButton(builder.context)
            fun refreshLabel() {
                button.text = context.getString(label, formatTime(get()))
            }
            refreshLabel()
            button.setOnClickListener {
                TimePickerDialog(context, { _, hour, minute ->
                    set(hour * 60 + minute)
                    refreshLabel()
                }, get() / 60, get() % 60, true).show()
            }
            content.addView(button)
        }
        timeButton(R.string.download_window_start, { start }, { start = it })
        timeButton(R.string.download_window_end, { end }, { end = it })
        val unmetered = check(R.string.download_window_unmetered, DownloadWindow.UNMETERED, true)
        val charging = check(R.string.download_window_charging, DownloadWindow.CHARGING, false)
        builder.setTitle(R.string.download_window_title).setMessage(R.string.download_window_explanation).setView(content)
            .setPositiveButton(R.string.ok) { _, _ ->
                prefs.edit().putBoolean(DownloadWindow.ENABLED, enabled.isChecked)
                    .putInt(DownloadWindow.START, start).putInt(DownloadWindow.END, end)
                    .putBoolean(DownloadWindow.UNMETERED, unmetered.isChecked)
                    .putBoolean(DownloadWindow.CHARGING, charging.isChecked).apply()
                DownloadWindow.schedule(context)
                notifyChanged()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    override fun getSummary(): CharSequence {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return if (prefs.getBoolean(DownloadWindow.ENABLED, false)) {
            context.getString(R.string.download_window_summary, formatTime(prefs.getInt(DownloadWindow.START, 0)), formatTime(prefs.getInt(DownloadWindow.END, 360)))
        } else {
            context.getString(R.string.download_window_disabled)
        }
    }

    private fun formatTime(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
}
