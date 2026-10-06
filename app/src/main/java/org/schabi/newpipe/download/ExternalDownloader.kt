package org.schabi.newpipe.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

object ExternalDownloader {
    const val ENABLED = "external_downloader_enabled"
    const val PACKAGE = "external_downloader_package"

    @JvmStatic
    fun isEnabled(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ENABLED, false)

    @JvmStatic
    fun isValidPackage(value: String): Boolean = value.isBlank() || value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))

    @JvmStatic
    fun createIntent(url: String, packageName: String): Intent {
        require(Uri.parse(url).scheme in listOf("https", "http"))
        require(isValidPackage(packageName))
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            if (packageName.isNotBlank()) setPackage(packageName)
        }
    }

    @JvmStatic
    fun open(context: Context, url: String) {
        val packageName = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PACKAGE, "").orEmpty().trim()
        if (!isValidPackage(packageName) || packageName == context.packageName) {
            Toast.makeText(context, R.string.external_downloader_invalid_package, Toast.LENGTH_LONG).show()
            return
        }
        val intent = try {
            createIntent(url, packageName)
        } catch (_: IllegalArgumentException) {
            Toast.makeText(context, R.string.external_downloader_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        try {
            launch(context, intent, packageName.isBlank())
        } catch (_: ActivityNotFoundException) {
            if (packageName.isNotBlank()) {
                Toast.makeText(context, R.string.external_downloader_missing_app, Toast.LENGTH_LONG).show()
                try {
                    launch(context, createIntent(url, ""), true)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.external_downloader_unavailable, Toast.LENGTH_LONG).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, R.string.external_downloader_unavailable, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, R.string.external_downloader_unavailable, Toast.LENGTH_LONG).show()
            }
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.external_downloader_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun launch(context: Context, intent: Intent, chooser: Boolean) {
        val launchIntent = if (chooser) Intent.createChooser(intent, context.getString(R.string.download_externally)) else intent
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
