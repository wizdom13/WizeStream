package org.schabi.newpipe.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

public class BrandingResourcesTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";
    private static final String NEW_MARK_SIGNATURE = "M1.8240072,1.4553195";
    private static final String LEGACY_MARK_SIGNATURE = "M34,26 L86,54 L34,82";
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void adaptiveIconUsesBrandBackgroundAndDedicatedMarkResources() throws Exception {
        final Element adaptiveIcon = parse("mipmap-anydpi-v26/ic_launcher.xml")
                .getDocumentElement();

        assertEquals("@color/ic_launcher_background",
                firstAttribute(adaptiveIcon, "background", "drawable"));
        assertEquals("@drawable/ic_launcher_material_foreground",
                firstAttribute(adaptiveIcon, "foreground", "drawable"));
        assertEquals("@drawable/ic_launcher_material_monochrome",
                firstAttribute(adaptiveIcon, "monochrome", "drawable"));
        assertEquals("#C32025", findColorValue("wizestream_brand_red"));
        assertEquals("@color/wizestream_brand_red", findColorValue("ic_launcher_background"));
    }

    @Test
    public void activeBrandVectorsUseTheNewMarkOnly() throws Exception {
        final List<String> vectors = List.of(
                "drawable/ic_launcher_material_foreground.xml",
                "drawable/ic_launcher_material_monochrome.xml",
                "drawable/ic_wizestream_splash.xml",
                "drawable/ic_wizestream_splash_system.xml",
                "drawable/ic_wizestream_splash_themed.xml",
                "drawable/splash_foreground.xml",
                "mipmap-anydpi/wizestream_tv_banner.xml"
        );

        for (final String vector : vectors) {
            final String xml = Files.readString(resourcesDirectory.resolve(vector));
            assertTrue(vector, xml.contains(NEW_MARK_SIGNATURE));
            assertFalse(vector, xml.contains(LEGACY_MARK_SIGNATURE));
        }
    }

    @Test
    public void runtimeTvBannerUsesOfficialBrandColors() throws Exception {
        final String banner = Files.readString(
                resourcesDirectory.resolve("mipmap-anydpi/wizestream_tv_banner.xml"));

        assertTrue(banner.contains("@color/wizestream_brand_container"));
        assertTrue(banner.contains("@color/wizestream_brand_red"));
    }

    private String firstAttribute(final Element parent,
                                  final String tag,
                                  final String attribute) {
        return ((Element) parent.getElementsByTagName(tag).item(0))
                .getAttributeNS(ANDROID_NAMESPACE, attribute);
    }

    private String findColorValue(final String name) throws Exception {
        final NodeList colors = parse("values/colors.xml").getElementsByTagName("color");
        for (int i = 0; i < colors.getLength(); i++) {
            final Element color = (Element) colors.item(i);
            if (name.equals(color.getAttribute("name"))) {
                return color.getTextContent().trim();
            }
        }
        throw new AssertionError("Missing color: " + name);
    }

    private Document parse(final String relativePath) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
