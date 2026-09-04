/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.nononsenseapps.filepicker.Utils
import java.io.IOException
import org.schabi.newpipe.R
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.FilePickerActivityHelper
import us.shandian.giga.service.DownloadManager

internal enum class DownloadPickerResultAction {
    CANCELLED,
    INVALID,
    OWN_FILE,
    DOCUMENT
}

internal object DownloadPickerResultPolicy {
    fun resolve(
        resultAccepted: Boolean,
        hasUri: Boolean,
        isOwnFileUri: Boolean
    ): DownloadPickerResultAction {
        return when {
            !resultAccepted -> DownloadPickerResultAction.CANCELLED
            !hasUri -> DownloadPickerResultAction.INVALID
            isOwnFileUri -> DownloadPickerResultAction.OWN_FILE
            else -> DownloadPickerResultAction.DOCUMENT
        }
    }
}

/** Normalizes external picker results before handing a target to the storage coordinator. */
internal class DownloadPickerResultHandler(
    private val context: Context,
    private val downloadManager: DownloadManager?,
    private val readyListener: DownloadReadyListener,
    private val failureListener: DownloadFailureListener
) {
    fun handleSaveAs(result: ActivityResult) {
        val resolved = resolve(result)
        when (resolved.action) {
            DownloadPickerResultAction.CANCELLED -> return

            DownloadPickerResultAction.INVALID -> {
                failureListener.onDownloadFailure(R.string.general_error)
                return
            }

            DownloadPickerResultAction.OWN_FILE -> {
                val file = Utils.getFileForUri(checkNotNull(resolved.uri))
                checkTarget(
                    mainStorage = null,
                    targetFile = Uri.fromFile(file),
                    filename = file.name,
                    mime = StoredFileHelper.DEFAULT_MIME
                )
            }

            DownloadPickerResultAction.DOCUMENT -> {
                val uri = checkNotNull(resolved.uri)
                val document = DocumentFile.fromSingleUri(context, uri)
                val filename = document?.name
                if (filename == null) {
                    failureListener.onDownloadFailure(R.string.general_error)
                    return
                }
                checkTarget(
                    mainStorage = null,
                    targetFile = uri,
                    filename = filename,
                    mime = document.type
                )
            }
        }
    }

    fun handleFolder(
        result: ActivityResult,
        preferenceKey: String,
        storageTag: String,
        pendingFilename: String?,
        pendingMimeType: String?
    ) {
        val resolved = resolve(result)
        when (resolved.action) {
            DownloadPickerResultAction.CANCELLED -> return

            DownloadPickerResultAction.INVALID -> {
                failureListener.onDownloadFailure(R.string.general_error)
                return
            }

            DownloadPickerResultAction.OWN_FILE,
            DownloadPickerResultAction.DOCUMENT -> Unit
        }

        if (pendingFilename == null) {
            failureListener.onDownloadFailure(R.string.general_error)
            return
        }

        val selectedUri = checkNotNull(resolved.uri)
        val directoryUri = if (resolved.action == DownloadPickerResultAction.OWN_FILE) {
            Uri.fromFile(Utils.getFileForUri(selectedUri))
        } else {
            context.grantUriPermission(
                context.packageName,
                selectedUri,
                StoredDirectoryHelper.PERMISSION_FLAGS
            )
            selectedUri
        }

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(preferenceKey, directoryUri.toString())
            .apply()

        try {
            val mainStorage = StoredDirectoryHelper(context, directoryUri, storageTag)
            checkTarget(
                mainStorage = mainStorage,
                targetFile = mainStorage.findFile(pendingFilename),
                filename = pendingFilename,
                mime = pendingMimeType
            )
        } catch (_: IOException) {
            failureListener.onDownloadFailure(R.string.general_error)
        }
    }

    private fun checkTarget(
        mainStorage: StoredDirectoryHelper?,
        targetFile: Uri?,
        filename: String,
        mime: String?
    ) {
        val manager = downloadManager
        if (manager == null) {
            failureListener.onDownloadFailure(R.string.general_error)
            return
        }
        DownloadStorageCoordinator(
            context,
            manager,
            readyListener,
            failureListener
        ).check(mainStorage, targetFile, filename, mime)
    }

    private fun resolve(result: ActivityResult): ResolvedPickerResult {
        val resultAccepted = result.resultCode == Activity.RESULT_OK
        val uri = if (resultAccepted) result.data?.data else null
        val isOwnFileUri = uri != null && FilePickerActivityHelper.isOwnFileUri(context, uri)
        return ResolvedPickerResult(
            action = DownloadPickerResultPolicy.resolve(
                resultAccepted,
                uri != null,
                isOwnFileUri
            ),
            uri = uri
        )
    }

    private data class ResolvedPickerResult(
        val action: DownloadPickerResultAction,
        val uri: Uri?
    )
}
