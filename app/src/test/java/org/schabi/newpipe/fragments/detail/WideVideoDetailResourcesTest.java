package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class WideVideoDetailResourcesTest {
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res")
            : Path.of("app/src/main/res");

    @Test
    public void wideDetailGivesRelatedItemsMoreSpaceWithoutShrinkingMainContent() throws Exception {
        final Document document = parse("layout-w840dp-land/fragment_video_detail.xml");

        final Element mainContent = findById(document, "@+id/detail_main_content");
        final Element relatedItems = findById(document, "@+id/relatedItemsLayout");
        final Element appBar = findById(document, "@+id/app_bar_layout");

        assertEquals("5", mainContent.getAttribute("android:layout_weight"));
        assertEquals("4", relatedItems.getAttribute("android:layout_weight"));
        assertEquals("", appBar.getAttribute("android:layout_marginStart"));
    }

    @Test
    public void wideRelatedThumbnailsScaleWithTheAvailablePaneWidth() throws Exception {
        final Document document = parse("layout/list_stream_related_wide_item.xml");
        final Element container = findById(document, "@+id/itemThumbnailContainer");
        final Element thumbnail = findById(document, "@+id/itemThumbnailView");

        assertEquals("0dp", container.getAttribute("android:layout_width"));
        assertEquals("0dp", container.getAttribute("android:layout_height"));
        assertEquals("0.42", container.getAttribute("app:layout_constraintWidth_percent"));
        assertEquals("16:9", container.getAttribute("app:layout_constraintDimensionRatio"));
        assertEquals("match_parent", thumbnail.getAttribute("android:layout_width"));
        assertEquals("match_parent", thumbnail.getAttribute("android:layout_height"));
    }

    private Element findById(final Document document, final String id) {
        final var elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            final Element element = (Element) elements.item(index);
            if (id.equals(element.getAttribute("android:id"))) {
                return element;
            }
        }
        throw new AssertionError("Missing view: " + id);
    }

    private Document parse(final String relativePath) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resourcesDirectory.resolve(relativePath).toFile());
    }
}
