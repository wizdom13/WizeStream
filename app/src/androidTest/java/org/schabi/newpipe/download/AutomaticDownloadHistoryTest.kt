package org.schabi.newpipe.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticDownloadHistoryTest {
    @Test
    fun receiptsSurviveReopeningAndRemainIsolatedByChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "automatic-downloads-test-${System.nanoTime()}.db"
        try {
            AutomaticDownloadHistory(context, name).use {
                it.add(1, "stream")
                it.add(1, "stream")
                it.add(1, "")
            }
            AutomaticDownloadHistory(context, name).use {
                assertTrue(it.contains(1, "stream"))
                assertTrue(it.contains(1, ""))
                assertFalse(it.contains(2, "stream"))
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
