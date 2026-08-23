package org.schabi.newpipe.download;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.MediaFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mp3DownloadPolicyTest {
    @Test
    void transcodesM4aAndOpusOnlyWhenMp3IsSelected() {
        assertTrue(Mp3DownloadPolicy.shouldTranscode(true, MediaFormat.M4A));
        assertTrue(Mp3DownloadPolicy.shouldTranscode(true, MediaFormat.WEBMA_OPUS));
        assertFalse(Mp3DownloadPolicy.shouldTranscode(false, MediaFormat.M4A));
    }

    @Test
    void keepsNativeMp3WithoutAnotherLossyConversion() {
        assertFalse(Mp3DownloadPolicy.shouldTranscode(true, MediaFormat.MP3));
    }
}
