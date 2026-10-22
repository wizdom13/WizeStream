package org.schabi.newpipe.local.history

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.testUtil.TestDatabase

class IncognitoHistoryTest {
    @Test
    fun incognitoSuppressesHistoryProgressAndSyncAcrossServicesAndProfiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val searchKey = context.getString(R.string.enable_search_history_key)
        val watchKey = context.getString(R.string.enable_watch_history_key)
        val previous = listOf(searchKey, watchKey, IncognitoMode.KEY).associateWith { prefs.all[it] as? Boolean }
        val database = TestDatabase.createReplacingNewPipeDatabase()
        try {
            prefs.edit().putBoolean(searchKey, true).putBoolean(watchKey, true).commit()
            IncognitoMode.setEnabled(context, false)
            val manager = HistoryRecordManager(context)
            val info = StreamInfo(0, "fixture", "https://example.test/video", "Private video").apply {
                streamType = StreamType.VIDEO_STREAM
                duration = 120
                uploaderName = "Channel"
            }
            // An operation created before the toggle must also check at subscription time.
            val pending = manager.onSearched(0, "pending")
            IncognitoMode.setEnabled(context, true)
            pending.blockingGet()
            for (records in listOf(manager, HistoryRecordManager(context, "other-profile"))) {
                records.onSearched(0, "private search").blockingGet()
                records.onSearched(1, "other service").blockingGet()
                records.onViewed(info).blockingGet()
                records.saveStreamState(info, 30_000).blockingAwait()
            }
            for (table in listOf("search_history", "stream_history", "stream_state", "history_sync_changes", "history_sync_records")) {
                database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
                    it.moveToFirst()
                    assertEquals(table, 0, it.getInt(0))
                }
            }
            IncognitoMode.setEnabled(context, false)
            manager.onSearched(0, "public search").blockingGet()
            manager.onViewed(info).blockingGet()
            manager.saveStreamState(info, 30_000).blockingAwait()
            assertEquals(listOf("public search"), manager.getRelatedSearches("", 20, 20).blockingFirst())
            assertEquals(30_000L, manager.loadStreamState(info).blockingGet()!!.progressMillis)
        } finally {
            previous.forEach { (key, value) ->
                if (value == null) prefs.edit().remove(key).commit() else prefs.edit().putBoolean(key, value).commit()
            }
            database.close()
        }
    }
}
