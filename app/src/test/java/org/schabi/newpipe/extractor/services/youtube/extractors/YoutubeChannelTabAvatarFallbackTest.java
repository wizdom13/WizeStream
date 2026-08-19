package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class YoutubeChannelTabAvatarFallbackTest {
    @Test
    public void usesChannelAvatarWhenItemAvatarIsMissing() {
        assertEquals("https://example.com/channel.jpg",
                YoutubeChannelTabExtractor.resolveUploaderAvatarUrl(null,
                        Arrays.asList("Channel", "https://example.com/channel",
                                "https://example.com/channel.jpg")));
    }

    @Test
    public void preservesAvatarProvidedByVideoItem() {
        assertEquals("https://example.com/item.jpg",
                YoutubeChannelTabExtractor.resolveUploaderAvatarUrl(
                        "https://example.com/item.jpg",
                        Arrays.asList("Channel", "https://example.com/channel",
                                "https://example.com/channel.jpg")));
    }

    @Test
    public void toleratesContinuationMetadataWithoutAvatar() {
        assertNull(YoutubeChannelTabExtractor.resolveUploaderAvatarUrl(null,
                Collections.singletonList("Channel")));
    }
}
