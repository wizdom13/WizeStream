package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class VideoDetailThumbnailLayoutTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";

    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void returnedVideoPreviewFillsThePlayerFrame() throws Exception {
        assertThumbnailFillsFrame("layout/fragment_video_detail.xml");
        assertThumbnailFillsFrame("layout-w840dp-land/fragment_video_detail.xml");
    }

    private void assertThumbnailFillsFrame(final String layout) throws Exception {
        final Element thumbnail = findById(parse(layout), "detail_thumbnail_image_view");
        assertNotNull(layout, thumbnail);
        assertEquals(layout, "centerCrop",
                thumbnail.getAttributeNS(ANDROID_NAMESPACE, "scaleType"));
    }

    private Element findById(final Document document, final String id) {
        final var elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            final Element element = (Element) elements.item(index);
            final String androidId = element.getAttributeNS(ANDROID_NAMESPACE, "id");
            if (("@+id/" + id).equals(androidId) || ("@id/" + id).equals(androidId)) {
                return element;
            }
        }
        return null;
    }

    private Document parse(final String relativePath) throws Exception {
        final var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
                .parse(resourcesDirectory.resolve(relativePath).toFile());
    }
}
