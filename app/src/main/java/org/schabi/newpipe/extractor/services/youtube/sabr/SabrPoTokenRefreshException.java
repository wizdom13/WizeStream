package org.schabi.newpipe.extractor.services.youtube.sabr;

import java.util.Objects;

/** A protected SABR response could not be recovered within the bounded PO-token refresh budget. */
public final class SabrPoTokenRefreshException extends SabrProtocolException {
    private final String videoId;

    public SabrPoTokenRefreshException(final String videoId, final String message) {
        super(message);
        this.videoId = Objects.requireNonNull(videoId);
    }

    public String getVideoId() {
        return videoId;
    }
}
