package org.schabi.newpipe.views;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class SplashResourcesTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void api27DisablesTheLegacyStartingWindow() throws Exception {
        final Element openingTheme = findStyle(
                parse("values/styles.xml"), "Base.V21.OpeningTheme");

        assertEquals("true", findItemValue(openingTheme,
                "android:windowDisablePreview"));
    }

    @Test
    public void api27OemFallbacksUseTheStaticBrandIcon() throws Exception {
        assertBrandedFallback("drawable-v23/splash_background.xml");
        assertBrandedFallback("drawable-night-v23/splash_background.xml");
    }

    @Test
    public void api31KeepsTheSystemSplashEnabled() throws Exception {
        assertSystemSplash("values-v31/styles.xml");
        assertSystemSplash("values-night-v31/styles.xml");
    }

    private void assertBrandedFallback(final String relativePath) throws Exception {
        final NodeList items = parse(relativePath).getElementsByTagName("item");

        assertEquals(2, items.getLength());
        assertEquals("?attr/colorSurface",
                ((Element) items.item(0)).getAttributeNS(ANDROID_NAMESPACE, "drawable"));
        assertEquals("@drawable/ic_wizestream_splash_system",
                ((Element) items.item(1)).getAttributeNS(ANDROID_NAMESPACE, "drawable"));
    }

    private void assertSystemSplash(final String relativePath) throws Exception {
        final Element openingTheme = findStyle(parse(relativePath), "OpeningTheme");

        assertEquals("false", findItemValue(openingTheme,
                "android:windowDisablePreview"));
        assertEquals("@drawable/ic_wizestream_splash_system", findItemValue(openingTheme,
                "android:windowSplashScreenAnimatedIcon"));
    }

    private Element findStyle(final Document document, final String name) {
        final NodeList styles = document.getElementsByTagName("style");
        for (int i = 0; i < styles.getLength(); i++) {
            final Element style = (Element) styles.item(i);
            if (name.equals(style.getAttribute("name"))) {
                return style;
            }
        }
        throw new AssertionError("Missing style: " + name);
    }

    private String findItemValue(final Element style, final String name) {
        final NodeList items = style.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            final Element item = (Element) items.item(i);
            if (name.equals(item.getAttribute("name"))) {
                return item.getTextContent().trim();
            }
        }
        throw new AssertionError("Missing item: " + name);
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
