package org.schabi.newpipe.settings.export

import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.sync.HistorySyncRecorder

class HistoryArchiveWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try {
        val profileId = requireNotNull(inputData.getString("profile"))
        require(ProfileManager.getProfile(applicationContext, profileId) != null) { "The selected profile no longer exists" }
        val uri = Uri.parse(requireNotNull(inputData.getString("uri")))
        val history = HistorySyncRecorder.get(applicationContext)
        val store = HistoryArchiveStore(
            NewPipeDatabase.getInstance(applicationContext),
            profileId,
            { id, time, count -> history.recordWatchEventForProfile(profileId, id, time, count) },
            history::recordSearch
        )
        val resolver = applicationContext.contentResolver
        val action = inputData.getString("action")
        val count = if (action == "import") {
            val archive = requireNotNull(resolver.openInputStream(uri)).use(HistoryArchiveCodec::decode)
            check(!isStopped) { "Import cancelled" }
            store.merge(archive)
        } else {
            require(action == "json" || action == "csv")
            val archive = store.export()
            val text = if (action == "json") HistoryArchiveCodec.encode(archive) else HistoryArchiveCodec.csv(archive)
            val bytes = text.toByteArray(Charsets.UTF_8)
            require(bytes.size <= HistoryArchiveCodec.MAX_BYTES) { "History export exceeds 32 MiB" }
            check(!isStopped) { "Export cancelled" }
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
            archive.watches.size + archive.searches.size
        }
        Result.success(workDataOf("count" to count))
    } catch (error: Exception) {
        Result.failure(workDataOf("error" to (error.localizedMessage ?: "History operation failed").take(500)))
    }
}
