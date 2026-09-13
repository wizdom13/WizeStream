package org.schabi.newpipe.settings.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.sync.HistorySyncRecorder
import org.schabi.newpipe.sync.RoomPlaylistSyncStore
import org.schabi.newpipe.sync.RoomSubscriptionSyncStore

class TakeoutImportWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        val file = File.createTempFile("takeout-", ".import", context.cacheDir)
        return try {
            val uri = Uri.parse(requireNotNull(inputData.getString("uri")))
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else "takeout.zip"
            } ?: "takeout.zip"
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        check(!isStopped) { "Import cancelled" }
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 256L * 1024 * 1024) { "Select a Takeout export under 256 MiB, without uploaded video files." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val data = TakeoutParser.parse(file, name)
            val history = HistorySyncRecorder.get(context)
            val result = TakeoutImporter(
                NewPipeDatabase.getInstance(context),
                recordSearch = { query, time -> history.recordSearch(0, query, time) },
                recordSubscription = RoomSubscriptionSyncStore.get(context)::recordLocalUpsert,
                recordWatch = history::recordWatchEvent
            ).import(data)
            RoomPlaylistSyncStore.get(context).reconcileLocalPlaylists()
            status(
                context.getString(
                    R.string.takeout_import_result,
                    result.playlists, result.videos, result.watches, result.skipped,
                    result.bookmarks, result.subscriptions, result.searches
                )
            )
            Result.success()
        } catch (error: Exception) {
            status(context.getString(R.string.takeout_import_error, error.localizedMessage ?: error.javaClass.simpleName))
            Result.failure()
        } finally {
            file.delete()
        }
    }

    private fun status(message: String) {
        PreferenceManager.getDefaultSharedPreferences(applicationContext).edit().putString(STATUS, message).apply()
    }

    companion object {
        const val STATUS = "takeout_import_status"
        private const val WORK = "takeout-import"

        @JvmStatic
        fun enqueue(context: Context, uri: Uri) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TakeoutImportWorker>().setInputData(workDataOf("uri" to uri.toString())).build()
            )
        }
    }
}

internal fun InputStream.readTakeoutBytes(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() <= limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
