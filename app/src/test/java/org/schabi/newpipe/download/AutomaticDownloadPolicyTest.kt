package org.schabi.newpipe.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticDownloadPolicyTest {
    @Test
    fun enablingDoesNotDownloadTheChannelArchive() {
        assertFalse(AutomaticDownloadPolicy.shouldDownload(10, 20, false, false, false))
        assertFalse(AutomaticDownloadPolicy.shouldDownload(null, 20, false, false, false))
        assertTrue(AutomaticDownloadPolicy.shouldDownload(30, 20, false, false, false))
    }

    @Test
    fun newUndatedUploadsAreAcceptedAfterTheBaseline() {
        assertTrue(AutomaticDownloadPolicy.shouldDownload(null, 20, true, false, false))
        assertFalse(AutomaticDownloadPolicy.shouldDownload(null, 20, true, true, false))
    }

    @Test
    fun liveStreamsWaitUntilAnArchivedCopyIsAvailable() {
        assertFalse(AutomaticDownloadPolicy.shouldDownload(30, 20, true, false, true))
        assertTrue(AutomaticDownloadPolicy.shouldDownload(30, 20, true, false, false))
    }

    @Test
    fun oldUploadsReappearingInAFeedAreNotBackfilled() {
        assertFalse(AutomaticDownloadPolicy.shouldDownload(1, 200_000_000, true, false, false))
    }
}
