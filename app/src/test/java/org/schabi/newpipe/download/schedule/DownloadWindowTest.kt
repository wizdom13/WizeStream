package org.schabi.newpipe.download.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWindowTest {
    @Test
    fun daytimeWindowIncludesStartButExcludesEnd() {
        assertFalse(DownloadWindow.contains(479, 480, 1020))
        assertTrue(DownloadWindow.contains(480, 480, 1020))
        assertTrue(DownloadWindow.contains(1019, 480, 1020))
        assertFalse(DownloadWindow.contains(1020, 480, 1020))
    }

    @Test
    fun overnightWindowCrossesMidnight() {
        assertTrue(DownloadWindow.contains(1380, 1380, 360))
        assertTrue(DownloadWindow.contains(0, 1380, 360))
        assertTrue(DownloadWindow.contains(359, 1380, 360))
        assertFalse(DownloadWindow.contains(360, 1380, 360))
        assertFalse(DownloadWindow.contains(1379, 1380, 360))
    }

    @Test
    fun equalBoundsAllowAllDayButInvalidValuesFailClosed() {
        assertTrue(DownloadWindow.contains(0, 480, 480))
        assertTrue(DownloadWindow.contains(1439, 480, 480))
        assertFalse(DownloadWindow.contains(1440, 0, 0))
        assertFalse(DownloadWindow.contains(0, -1, 360))
        assertFalse(DownloadWindow.contains(0, 0, 1440))
    }
}
