package org.schabi.newpipe.settings.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import org.schabi.newpipe.R

/** The launcher component state is authoritative; backup/profile preferences never change it. */
object LauncherIcons {
    internal val variants = listOf("default", "blue", "green", "purple")
    private const val PREFIX = "org.schabi.newpipe.launcher."

    internal fun component(context: Context, variant: String) = ComponentName(context.packageName, PREFIX + variant)

    fun selected(context: Context): String = variants.firstOrNull { variant ->
        val state = context.packageManager.getComponentEnabledSetting(component(context, variant))
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && variant == "default"
    } ?: "default"

    internal fun apply(variant: String, read: (String) -> Int, write: (String, Int) -> Unit) {
        require(variant in variants)
        val original = variants.associateWith(read)
        try {
            // Enable first so there is always a launchable entry, including on older Android.
            write(variant, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            variants.filter { it != variant }.forEach { write(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED) }
        } catch (error: Exception) {
            // Restore enabled/default entries before disabling the temporary new entry.
            original.entries.sortedBy { if (it.value == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) 1 else 0 }
                .forEach { (key, state) -> runCatching { write(key, state) } }
            throw error
        }
    }

    @JvmStatic fun bind(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val preference = requireNotNull(fragment.findPreference<ListPreference>("launcher_icon"))
        preference.isPersistent = false
        preference.value = selected(context)
        preference.setOnPreferenceChangeListener { _, value ->
            try {
                apply(
                    value.toString(),
                    read = { context.packageManager.getComponentEnabledSetting(component(context, it)) },
                    write = { variant, state -> context.packageManager.setComponentEnabledSetting(component(context, variant), state, PackageManager.DONT_KILL_APP) }
                )
                preference.value = selected(context)
                Toast.makeText(context, R.string.launcher_icon_changed, Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                preference.value = selected(context)
                Toast.makeText(context, R.string.launcher_icon_failed, Toast.LENGTH_LONG).show()
            }
            false
        }
    }
}
