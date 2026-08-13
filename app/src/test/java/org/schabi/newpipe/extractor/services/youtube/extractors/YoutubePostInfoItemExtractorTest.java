package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.post.PostInfoItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class YoutubePostInfoItemExtractorTest {
    @Test
    void extractsTextImagesCountsAndVideoAttachment() throws Exception {
        final JsonObject renderer = JsonParser.object().from("""
                {
                  "postId": "Ugkx-post-id",
                  "authorText": {"simpleText": "Example channel"},
                  "authorEndpoint": {"browseEndpoint": {"browseId": "UC123"}},
                  "authorThumbnail": {"thumbnails": [{
                    "url": "https://yt3.example/avatar.jpg", "width": 48, "height": 48
                  }]},
                  "contentText": {"runs": [{"text": "A community post"}]},
                  "publishedTimeText": {"simpleText": "2 days ago (edited)"},
                  "voteCount": {"simpleText": "1.2K"},
                  "actionButtons": {"commentActionButtonsRenderer": {
                    "replyButton": {"buttonRenderer": {"text": {"simpleText": "42"}}}
                  }},
                  "backstageAttachment": {"videoRenderer": {
                    "videoId": "abcdefghijk",
                    "title": {"simpleText": "Attached video"},
                    "descriptionSnippet": {"simpleText": "Description"},
                    "thumbnail": {"thumbnails": [{
                      "url": "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg",
                      "width": 480, "height": 360
                    }]}
                  }}
                }
                """);
        final YoutubePostInfoItemExtractor extractor = new YoutubePostInfoItemExtractor(
                renderer, mock(TimeAgoParser.class));

        assertEquals("https://www.youtube.com/post/Ugkx-post-id", extractor.getUrl());
        assertEquals("A community post", extractor.getContent());
        assertEquals("Example channel", extractor.getUploaderName());
        assertEquals("https://www.youtube.com/channel/UC123", extractor.getUploaderUrl());
        assertEquals(1200, extractor.getLikeCount());
        assertEquals(42, extractor.getCommentCount());
        assertTrue(extractor.isEdited());
        assertEquals(PostInfoItem.AttachmentType.VIDEO, extractor.getAttachment().getType());
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk",
                extractor.getAttachment().getUrl());
    }

    @Test
    void extractsMultipleImages() throws Exception {
        final JsonObject renderer = baseRendererWithAttachment("""
                {"postMultiImageRenderer": {"images": [
                  {"backstageImageRenderer": {"image": {"thumbnails": [{
                    "url": "https://example.com/one.jpg", "width": 640, "height": 640
                  }]}}},
                  {"backstageImageRenderer": {"image": {"thumbnails": [{
                    "url": "https://example.com/two.jpg", "width": 640, "height": 640
                  }]}}}
                ]}}
                """);
        final YoutubePostInfoItemExtractor extractor = new YoutubePostInfoItemExtractor(
                renderer, mock(TimeAgoParser.class));

        assertEquals(2, extractor.getImages().size());
        assertNull(extractor.getAttachment());
        assertNull(extractor.getPoll());
    }

    @Test
    void extractsReadOnlyPollResults() throws Exception {
        final JsonObject renderer = baseRendererWithAttachment("""
                {"pollRenderer": {
                  "choices": [
                    {"text": {"simpleText": "First"},
                     "votePercentage": {"simpleText": "60%"}},
                    {"text": {"simpleText": "Second"},
                     "votePercentage": {"simpleText": "40%"}}
                  ],
                  "totalVotes": {"simpleText": "100 votes"}
                }}
                """);
        final YoutubePostInfoItemExtractor extractor = new YoutubePostInfoItemExtractor(
                renderer, mock(TimeAgoParser.class));

        assertEquals(2, extractor.getPoll().getChoices().size());
        assertEquals("60%", extractor.getPoll().getChoices().get(0).getVotePercentage());
        assertEquals("100 votes", extractor.getPoll().getTotalVotes());
    }

    private static JsonObject baseRendererWithAttachment(final String attachment)
            throws Exception {
        return JsonParser.object().from("""
                {
                  "postId": "Ugkx-post-id",
                  "authorText": {"simpleText": "Example channel"},
                  "contentText": {"simpleText": "Post"},
                  "publishedTimeText": {"simpleText": "2 days ago"},
                  "backstageAttachment": %s
                }
                """.formatted(attachment));
    }
}
