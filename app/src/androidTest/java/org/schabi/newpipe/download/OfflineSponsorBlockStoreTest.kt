package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment

class OfflineSponsorBlockStoreTest {
    @Test
    fun persistedSegmentsKeepTheirTimingAndRejectChangedOrMissingFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "update").apply { mkdirs() }
        val file = File.createTempFile("offline-sponsor", ".mp4", directory)
        val uri = Uri.fromFile(file).toString()
        try {
            file.writeBytes(ByteArray(128))
            val segment = SponsorBlockSegment("fixture", 10_000.0, 20_000.0, SponsorBlockCategory.SPONSOR, SponsorBlockAction.SKIP, 0)
            OfflineSponsorBlockStore.save(context, uri, file.length(), arrayOf(segment))
            val restored = OfflineSponsorBlockStore.load(context, uri).single()
            assertEquals(segment.startTime, restored.startTime, 0.0)
            assertEquals(segment.endTime, restored.endTime, 0.0)
            assertEquals(segment.category, restored.category)
            assertEquals(segment.action, restored.action)
            val providerUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            assertEquals("fixture", OfflineSponsorBlockStore.load(context, providerUri.toString()).single().uuid)
            assertEquals(128L, file.length())
            file.appendBytes(byteArrayOf(1))
            assertTrue(OfflineSponsorBlockStore.load(context, uri).isEmpty())
            file.delete()
            assertTrue(OfflineSponsorBlockStore.load(context, uri).isEmpty())
        } finally {
            OfflineSponsorBlockStore.remove(context, uri)
            file.delete()
        }
    }

    @Test
    fun emptyMetadataClearsAnEarlierDownloadAtTheSameLocation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("offline-empty", ".mp4", context.cacheDir)
        val uri = Uri.fromFile(file).toString()
        try {
            file.writeBytes(ByteArray(128))
            OfflineSponsorBlockStore.save(
                context,
                uri,
                file.length(),
                arrayOf(
                    SponsorBlockSegment("fixture", 1000.0, 2000.0, SponsorBlockCategory.SPONSOR, SponsorBlockAction.SKIP, 0)
                )
            )
            OfflineSponsorBlockStore.save(context, uri, file.length(), null)
            assertTrue(OfflineSponsorBlockStore.load(context, uri).isEmpty())
        } finally {
            OfflineSponsorBlockStore.remove(context, uri)
            file.delete()
        }
    }
}
