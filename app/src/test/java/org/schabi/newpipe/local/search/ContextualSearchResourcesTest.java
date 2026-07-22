package org.schabi.newpipe.local.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class ContextualSearchResourcesTest {
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void toolbarHostsDedicatedContextualSearchSurface() throws Exception {
        final Document toolbar = parse("layout/toolbar_layout.xml");
        final Element include = findByAndroidId(toolbar,
                "@+id/toolbar_contextual_search_container");

        assertNotNull(include);
        assertEquals("@layout/toolbar_contextual_search_layout",
                include.getAttribute("layout"));
        assertEquals("gone", include.getAttributeNS(ANDROID_NAMESPACE, "visibility"));
    }

    @Test
    public void contextualToolbarExposesQueryCloseAndGlobalSearchControls() throws Exception {
        final Document searchToolbar = parse("layout/toolbar_contextual_search_layout.xml");

        assertNotNull(findByAndroidId(searchToolbar, "@+id/contextual_search_edit_text"));
        assertNotNull(findByAndroidId(searchToolbar, "@+id/contextual_search_close"));
        final Element globalSearch = findByAndroidId(searchToolbar,
                "@+id/contextual_global_search_button");
        assertNotNull(globalSearch);
        assertEquals("48dp", globalSearch.getAttributeNS(ANDROID_NAMESPACE, "layout_width"));
        assertEquals("@string/search",
                globalSearch.getAttributeNS(ANDROID_NAMESPACE, "contentDescription"));
        assertNull(findDescendantWithAndroidAttribute(globalSearch, "text"));
        final Element icon = findDescendantWithAndroidAttribute(globalSearch, "src");
        assertNotNull(icon);
        assertEquals("@drawable/ic_search",
                icon.getAttributeNS(ANDROID_NAMESPACE, "src"));
    }

    private Element findDescendantWithAndroidAttribute(
            final Element parent, final String attribute) {
        final NodeList elements = parent.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            final Element element = (Element) elements.item(i);
            if (element.hasAttributeNS(ANDROID_NAMESPACE, attribute)) {
                return element;
            }
        }
        return null;
    }

    private Element findByAndroidId(final Document document, final String id) {
        final NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            final Element element = (Element) elements.item(i);
            if (id.equals(element.getAttributeNS(ANDROID_NAMESPACE, "id"))) {
                return element;
            }
        }
        return null;
    }

    private Document parse(final String relativePath) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder()
                .parse(resourcesDirectory.resolve(relativePath).toFile());
    }
}
