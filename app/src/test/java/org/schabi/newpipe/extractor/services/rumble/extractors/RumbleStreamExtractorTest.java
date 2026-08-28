package org.schabi.newpipe.extractor.services.rumble.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.junit.jupiter.api.Test;

import java.util.List;

class RumbleStreamExtractorTest {

    @Test
    void returnsNoRelatedNodesWhenTheOptionalSectionIsMissing() {
        final Document document = Jsoup.parse(
                "<html><body><main>Video metadata only</main></body></html>",
                "https://rumble.com/v7epypu");

        assertTrue(RumbleStreamExtractor.getRelatedItemNodes(document).isEmpty());
    }

    @Test
    void retainsRelatedNodesWhenTheOptionalSectionExists() {
        final Document document = Jsoup.parse(
                "<ul class=\"mediaList-list\"><li class=\"mediaList-item\"></li></ul>",
                "https://rumble.com/example");
        final List<Node> nodes = RumbleStreamExtractor.getRelatedItemNodes(document);

        assertEquals(1, nodes.size());
        assertTrue(nodes.get(0) instanceof Element);
    }
}
