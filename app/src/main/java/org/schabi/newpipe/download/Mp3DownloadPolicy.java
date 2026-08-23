package org.schabi.newpipe.download;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.MediaFormat;

final class Mp3DownloadPolicy {
    private Mp3DownloadPolicy() {
    }

    static boolean shouldTranscode(final boolean mp3OutputSelected,
                                   @Nullable final MediaFormat sourceFormat) {
        return mp3OutputSelected && sourceFormat != MediaFormat.MP3;
    }
}
