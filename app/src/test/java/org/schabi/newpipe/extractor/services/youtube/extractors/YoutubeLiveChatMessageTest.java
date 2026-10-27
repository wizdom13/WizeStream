package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YoutubeLiveChatMessageTest {
    @Test
    void extractsAuthorAndMessageRuns() throws Exception {
        final var data = JsonParser.object().from("{\"authorName\":{\"simpleText\":\"Alex\"},"
                + "\"message\":{\"runs\":[{\"text\":\"Hello\"},{\"text\":\" world\"}]}}");
        final var item = new YoutubeBulletCommentsInfoItemExtractor(data, 0, -1);
        assertEquals("Alex", item.getName());
        assertEquals("Hello world", item.getCommentText());
    }

    @Test
    void paidMessagesRetainAuthorAndAmount() throws Exception {
        final var data = JsonParser.object().from("{\"authorName\":{\"simpleText\":\"Alex\"},"
                + "\"purchaseAmountText\":{\"simpleText\":\"$5\"},"
                + "\"message\":{\"runs\":[{\"text\":\"Thanks\"}]}}");
        final var item = new YoutubeSuperChatInfoItemExtractor(data, 0, -1);
        assertEquals("Alex", item.getName());
        assertEquals("($5) Thanks", item.getCommentText());
    }
}
