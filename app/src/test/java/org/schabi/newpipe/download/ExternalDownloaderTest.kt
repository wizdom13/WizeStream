package org.schabi.newpipe.download

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ExternalDownloaderTest {
    @Test
    fun sendsOriginalVideoUrlToConfiguredApp() {
        val url = "https://www.youtube.com/watch?v=video&t=42"
        val intent = ExternalDownloader.createIntent(url, "com.example.downloader")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(url, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("com.example.downloader", intent.`package`)
        assertNull(intent.data)
    }

    @Test
    fun emptyPackageLeavesRoutingToChooser() {
        assertNull(ExternalDownloader.createIntent("https://odysee.com/video:abc", "").`package`)
        assertTrue(ExternalDownloader.isValidPackage(""))
        assertTrue(ExternalDownloader.isValidPackage("com.example.Downloader_2"))
        assertFalse(ExternalDownloader.isValidPackage("com.example/app"))
        assertFalse(ExternalDownloader.isValidPackage("com..example"))
    }

    @Test
    fun rejectsLocalFilesAndMalformedPackages() {
        assertThrows(IllegalArgumentException::class.java) {
            ExternalDownloader.createIntent("file:///private/media.mp4", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExternalDownloader.createIntent("https://example.com/video", "bad/package")
        }
    }
}
