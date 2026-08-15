package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class YoutubeCommentsEUVMInfoItemExtractorTest {
    @Test
    void extractsAvatarThumbnailUrlFromCurrentAuthorPayload() throws Exception {
        final YoutubeCommentsEUVMInfoItemExtractor extractor = createExtractor("""
                {
                  "author": {
                    "avatarThumbnailUrl": "https://yt3.ggpht.com/comment-avatar"
                  }
                }
                """);

        assertEquals("https://yt3.ggpht.com/comment-avatar",
                extractor.getUploaderAvatarUrl());
    }

    @Test
    void collectorPreservesCurrentAvatarThumbnailUrl() throws Exception {
        final YoutubeCommentsEUVMInfoItemExtractor extractor = createExtractor("""
                {
                  "author": {
                    "avatarThumbnailUrl": "https://yt3.ggpht.com/comment-avatar"
                  }
                }
                """);
        final CommentsInfoItem item = new CommentsInfoItemsCollector(0).extract(extractor);

        assertEquals("https://yt3.ggpht.com/comment-avatar", item.getUploaderAvatarUrl());
    }

    @Test
    void retainsCompatibilityWithLegacyRootAvatarPayload() throws Exception {
        final YoutubeCommentsEUVMInfoItemExtractor extractor = createExtractor("""
                {
                  "avatar": {
                    "image": {
                      "sources": [{
                        "url": "https://yt3.ggpht.com/legacy-comment-avatar",
                        "width": 48,
                        "height": 48
                      }]
                    }
                  }
                }
                """);

        assertEquals("https://yt3.ggpht.com/legacy-comment-avatar",
                extractor.getUploaderAvatarUrl());
    }

    @Test
    void retainsCompatibilityWithNestedAuthorAvatarPayload() throws Exception {
        final YoutubeCommentsEUVMInfoItemExtractor extractor = createExtractor("""
                {
                  "author": {
                    "avatar": {
                      "image": {
                        "sources": [{
                          "url": "https://yt3.ggpht.com/nested-comment-avatar",
                          "width": 48,
                          "height": 48
                        }]
                      }
                    }
                  }
                }
                """);

        assertEquals("https://yt3.ggpht.com/nested-comment-avatar",
                extractor.getUploaderAvatarUrl());
    }

    private static YoutubeCommentsEUVMInfoItemExtractor createExtractor(
            final String commentEntityPayload) throws Exception {
        final JsonObject payload = JsonParser.object().from(commentEntityPayload);
        return new YoutubeCommentsEUVMInfoItemExtractor(
                new JsonObject(),
                null,
                payload,
                new JsonObject(),
                "https://www.youtube.com/watch?v=abcdefghijk",
                mock(TimeAgoParser.class));
    }
}
