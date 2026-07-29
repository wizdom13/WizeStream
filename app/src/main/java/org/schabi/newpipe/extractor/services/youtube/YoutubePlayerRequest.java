package org.schabi.newpipe.extractor.services.youtube;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Final player request body together with the visitor identity actually written into it.
 */
public final class YoutubePlayerRequest {
    @Nonnull
    private final byte[] body;
    @Nullable
    private final String visitorData;

    YoutubePlayerRequest(@Nonnull final byte[] body,
                         @Nullable final String visitorData) {
        this.body = body;
        this.visitorData = visitorData;
    }

    @Nonnull
    public byte[] getBody() {
        return body;
    }

    @Nullable
    public String getVisitorData() {
        return visitorData;
    }
}
