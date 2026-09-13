package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.sqlite.FinishedMissionStore

class DownloadedCopyLookupTest {
    @Test
    fun completeCopyKeepsRemoteIdentityAndMissingOrDisabledCopiesAreSkipped() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("copy-lookup", ".m4a", context.cacheDir).apply { writeBytes(ByteArray(32) { 1 }) }
        val source = "https://www.youtube.com/watch?v=abcdefghijk"
        val queueItem = PlayQueueItem(
            StreamInfo(0, "abcdefghijk", source, "Saved").apply {
                duration = 1
                streamType = StreamType.AUDIO_STREAM
            }
        )
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = prefs.getBoolean(DownloadedCopyRepository.PREFERENCE, true)
        val store = FinishedMissionStore(context)
        val mission = FinishedMission().apply {
            this.source = source
            storage = StoredFileHelper(context, null, Uri.fromFile(file), "")
            kind = 'a'
            length = file.length()
            timestamp = System.currentTimeMillis()
            syncId = "copy-lookup-test"
            displayName = file.name
            mimeType = "audio/mp4"
        }
        try {
            prefs.edit().putBoolean(DownloadedCopyRepository.PREFERENCE, true).commit()
            store.addFinishedMission(mission)
            val copy = DownloadedCopyRepository.find(context, 0, source, "Saved", true, queueItem)
            assertNotNull(copy)
            assertEquals(source, copy!!.url)
            assertEquals(Uri.fromFile(file).toString(), copy.copyUri)
            assertNull(DownloadedCopyRepository.find(context, 0, source, "Saved", false, queueItem))
            prefs.edit().putBoolean(DownloadedCopyRepository.PREFERENCE, false).commit()
            assertNull(DownloadedCopyRepository.find(context, 0, source, "Saved", true, queueItem))
            prefs.edit().putBoolean(DownloadedCopyRepository.PREFERENCE, true).commit()
            file.delete()
            assertNull(DownloadedCopyRepository.find(context, 0, source, "Saved", true, queueItem))
        } finally {
            store.deleteMission(mission)
            store.close()
            prefs.edit().putBoolean(DownloadedCopyRepository.PREFERENCE, enabled).commit()
            file.delete()
        }
    }
}
