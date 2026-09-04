/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredDirectoryHelper

internal enum class DownloadDestinationAction {
    PICK_DIRECTORY,
    SAVE_AS,
    INSUFFICIENT_STORAGE,
    USE_CONFIGURED_STORAGE
}

internal object DownloadDestinationPolicy {
    @JvmStatic
    fun resolve(
        askForSavePath: Boolean,
        mainStorage: StoredDirectoryHelper?,
        useStorageAccessFramework: Boolean,
        estimatedSize: Long
    ): DownloadDestinationAction {
        if (askForSavePath) {
            return DownloadDestinationAction.SAVE_AS
        }
        if (
            mainStorage == null ||
            mainStorage.isDirect == useStorageAccessFramework ||
            mainStorage.isInvalidSafStorage
        ) {
            return DownloadDestinationAction.PICK_DIRECTORY
        }
        return if (mainStorage.freeStorageSpace <= estimatedSize) {
            DownloadDestinationAction.INSUFFICIENT_STORAGE
        } else {
            DownloadDestinationAction.USE_CONFIGURED_STORAGE
        }
    }
}

internal fun interface ConfiguredDownloadTargetListener {
    fun onConfiguredTarget(
        mainStorage: StoredDirectoryHelper,
        targetFile: Uri?,
        filename: String,
        mimeType: String?
    )
}

internal fun interface DownloadSaveAsListener {
    fun onSaveAsRequested(filename: String, mimeType: String?, initialPath: Uri?)
}

/** Routes an output plan to a folder picker, Save As picker, or configured storage. */
internal class DownloadDestinationCoordinator(
    private val context: Context,
    private val saveAsListener: DownloadSaveAsListener,
    private val audioFolderLauncher: ActivityResultLauncher<Intent>,
    private val videoFolderLauncher: ActivityResultLauncher<Intent>,
    private val targetListener: ConfiguredDownloadTargetListener
) {
    /** Returns true only when the configured storage path was used. */
    fun prepare(
        isAudio: Boolean,
        mainStorage: StoredDirectoryHelper?,
        outputPlan: DownloadOutputPlan,
        askForSavePath: Boolean
    ): Boolean {
        val useStorageAccessFramework = NewPipeSettings.useStorageAccessFramework(context)
        return when (
            DownloadDestinationPolicy.resolve(
                askForSavePath,
                mainStorage,
                useStorageAccessFramework,
                outputPlan.estimatedSize
            )
        ) {
            DownloadDestinationAction.PICK_DIRECTORY -> {
                Toast.makeText(context, R.string.no_dir_yet, Toast.LENGTH_LONG).show()
                val launcher = if (isAudio) audioFolderLauncher else videoFolderLauncher
                NoFileManagerSafeGuard.launchSafe(
                    launcher,
                    StoredDirectoryHelper.getPicker(context),
                    TAG,
                    context
                )
                false
            }

            DownloadDestinationAction.SAVE_AS -> {
                val initialPath = if (useStorageAccessFramework) {
                    null
                } else {
                    val directory = if (isAudio) {
                        Environment.DIRECTORY_MUSIC
                    } else {
                        Environment.DIRECTORY_MOVIES
                    }
                    Uri.parse(NewPipeSettings.getDir(directory).absolutePath)
                }
                saveAsListener.onSaveAsRequested(
                    outputPlan.filename,
                    outputPlan.mimeType,
                    initialPath
                )
                false
            }

            DownloadDestinationAction.INSUFFICIENT_STORAGE -> {
                Toast.makeText(
                    context,
                    R.string.error_insufficient_storage,
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
                false
            }

            DownloadDestinationAction.USE_CONFIGURED_STORAGE -> {
                checkNotNull(mainStorage)
                targetListener.onConfiguredTarget(
                    mainStorage,
                    mainStorage.findFile(outputPlan.filename),
                    outputPlan.filename,
                    outputPlan.mimeType
                )
                true
            }
        }
    }

    private companion object {
        const val TAG = "DownloadDestinationCoordinator"
    }
}
