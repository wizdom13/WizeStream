package org.schabi.newpipe.info_list;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class StreamThumbnailProgressResourcesTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";
    private static final String APP_NAMESPACE =
            "http://schemas.android.com/apk/res-auto";
    private static final String[] STREAM_LAYOUTS = {
        "list_stream_item.xml",
        "list_stream_mini_item.xml",
        "list_stream_grid_item.xml",
        "list_stream_card_item.xml",
        "list_stream_related_wide_item.xml",
        "list_stream_playlist_item.xml",
        "list_stream_playlist_grid_item.xml",
        "list_stream_playlist_card_item.xml"
    };

    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void progressGeometryFitsTheRoundedThumbnailBottomEdge() throws Exception {
        final Document document = parse(resourcesDirectory.resolve("values/dimens.xml"));

        assertEquals("0dp", findDimension(document,
                "stream_thumbnail_progress_bottom_margin"));
        assertEquals("12dp", findDimension(document,
                "stream_thumbnail_progress_horizontal_inset"));
        assertEquals(findDimension(document, "stream_thumbnail_corner_radius"),
                findDimension(document, "stream_thumbnail_progress_horizontal_inset"));
    }

    @Test
    public void everyStreamLayoutUsesTheSharedThumbnailProgressGeometry() throws Exception {
        for (final String layout : STREAM_LAYOUTS) {
            final Document document = parse(resourcesDirectory.resolve("layout").resolve(layout));
            final Element progress = findByAndroidId(document, "@+id/itemProgressView");

            assertNotNull(layout, progress);
            assertEquals(layout, "@dimen/stream_thumbnail_progress_height",
                    progress.getAttributeNS(ANDROID_NAMESPACE, "layout_height"));
            assertEquals(layout, "@dimen/stream_thumbnail_progress_bottom_margin",
                    progress.getAttributeNS(ANDROID_NAMESPACE, "layout_marginBottom"));
            assertEquals(layout, "@dimen/stream_thumbnail_progress_horizontal_inset",
                    progress.getAttributeNS(ANDROID_NAMESPACE, "layout_marginHorizontal"));
            assertTrue(layout, isAnchoredToThumbnailBottom(progress));
        }
    }

    private boolean isAnchoredToThumbnailBottom(final Element progress) {
        final String constraint = progress.getAttributeNS(
                APP_NAMESPACE, "layout_constraintBottom_toBottomOf");
        final String relative = progress.getAttributeNS(ANDROID_NAMESPACE, "layout_alignBottom");
        return isThumbnailReference(constraint) || isThumbnailReference(relative);
    }

    private boolean isThumbnailReference(final String value) {
        return "@id/itemThumbnailView".equals(value)
                || "@+id/itemThumbnailView".equals(value);
    }

    private String findDimension(final Document document, final String name) {
        final NodeList dimensions = document.getElementsByTagName("dimen");
        for (int index = 0; index < dimensions.getLength(); index++) {
            final Element dimension = (Element) dimensions.item(index);
            if (name.equals(dimension.getAttribute("name"))) {
                return dimension.getTextContent().trim();
            }
        }
        return null;
    }

    private Element findByAndroidId(final Document document, final String id) {
        final NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            final Element element = (Element) elements.item(index);
            if (id.equals(element.getAttributeNS(ANDROID_NAMESPACE, "id"))) {
                return element;
            }
        }
        return null;
    }

    private Document parse(final Path path) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }
}
