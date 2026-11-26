/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.Locale
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Routes an explicit local-file play action without asking an online extractor for streams. */
object LocalMediaExternalPlayback {
    /** A handled failure still consumes the action, rather than starting a different player. */
    @JvmStatic
    @JvmOverloads
    fun playIfEnabled(context: Context, item: PlayQueueItem?, audioOnly: Boolean = false): Boolean {
        if (item?.isLocalMedia != true) return false
        val preference = if (audioOnly || item.streamType == StreamType.AUDIO_STREAM) {
            R.string.use_external_audio_player_key
        } else {
            R.string.use_external_video_player_key
        }
        if (!PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(preference), false)
        ) {
            return false
        }
        try {
            context.startActivity(chooser(context, item))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_to_open_intent, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.invalid_file, Toast.LENGTH_LONG).show()
        } catch (_: IllegalArgumentException) {
            Toast.makeText(context, R.string.invalid_file, Toast.LENGTH_LONG).show()
        } catch (_: IOException) {
            Toast.makeText(context, R.string.invalid_file, Toast.LENGTH_LONG).show()
        }
        return true
    }

    internal fun chooser(context: Context, item: PlayQueueItem): Intent {
        require(item.isLocalMedia)
        val original = Uri.parse(item.url)
        val uri = when (original.scheme) {
            "content" -> {
                require(!original.authority.isNullOrBlank())
                original
            }

            "file" -> {
                val file = File(URI.create(item.url)).canonicalFile
                require(file.isFile && file.canRead())
                // Keep the updater's provider limited to APKs in its own cache directory.
                FileProvider.getUriForFile(context, context.packageName + ".localmedia", file)
            }

            else -> throw IllegalArgumentException("Unsupported local media URI")
        }
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType(context, item, original))
            .putExtra(Intent.EXTRA_TITLE, item.title)
            .putExtra("title", item.title)
            .putExtra("artist", item.uploader)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = ClipData.newRawUri("", uri)
        return Intent.createChooser(view, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = view.clipData
        }
    }

    private fun mimeType(context: Context, item: PlayQueueItem, uri: Uri): String {
        fun String?.mediaType(): String? = this?.takeIf {
            it.startsWith("audio/") || it.startsWith("video/")
        }
        item.mimeType.mediaType()?.let { return it }
        if (uri.scheme == "content") {
            context.contentResolver.getType(uri).mediaType()?.let { return it }
        }
        val extension = (uri.lastPathSegment ?: item.title).substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).mediaType()?.let { return it }
        return if (item.streamType == StreamType.AUDIO_STREAM) "audio/*" else "video/*"
    }
}
