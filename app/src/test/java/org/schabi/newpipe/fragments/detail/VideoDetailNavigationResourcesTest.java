package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class VideoDetailNavigationResourcesTest {
    private static final String BOTTOM_NAVIGATION_VIEW =
            "com.google.android.material.bottomnavigation.BottomNavigationView";
    private static final String TAB_LAYOUT = "com.google.android.material.tabs.TabLayout";

    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res")
            : Path.of("app/src/main/res");

    @Test
    public void phoneAndLargeLandscapeLayoutsUseExpressiveNavigation() throws Exception {
        assertExpressiveNavigation("layout/fragment_video_detail.xml");
        assertExpressiveNavigation("layout-large-land/fragment_video_detail.xml");
    }

    @Test
    public void navigationMenuHasStableLocalizedDestinations() throws Exception {
        final Document document = parse("menu/video_detail_navigation.xml");
        final var items = document.getElementsByTagName("item");

        assertEquals(4, items.getLength());
        assertMenuItem((Element) items.item(0),
                "@+id/video_detail_navigation_comments",
                "@string/comments_tab_description");
        assertMenuItem((Element) items.item(1),
                "@+id/video_detail_navigation_related",
                "@string/related_items_tab_description");
        assertMenuItem((Element) items.item(2),
                "@+id/video_detail_navigation_description",
                "@string/description_tab_description");
        assertMenuItem((Element) items.item(3),
                "@+id/video_detail_navigation_notes",
                "@string/learning_notes_tab_description");
    }

    private void assertExpressiveNavigation(final String layoutPath) throws Exception {
        final Document document = parse(layoutPath);

        assertEquals(0, document.getElementsByTagName(TAB_LAYOUT).getLength());
        final var navigationViews = document.getElementsByTagName(BOTTOM_NAVIGATION_VIEW);
        assertEquals(1, navigationViews.getLength());

        final var navigation = (Element) navigationViews.item(0);
        assertEquals("@+id/detail_navigation", navigation.getAttribute("android:id"));
        assertEquals("selected", navigation.getAttribute("app:labelVisibilityMode"));
        assertEquals("@menu/video_detail_navigation", navigation.getAttribute("app:menu"));
        assertEquals("?attr/colorSurface", navigation.getAttribute("android:background"));
        assertEquals("8dp", navigation.getAttribute("android:elevation"));
        assertEquals(
                "@style/wizestreamBottomNavigationActiveIndicator",
                navigation.getAttribute("app:itemActiveIndicatorStyle"));
        assertEquals("@color/tab_layout_material_item_color",
                navigation.getAttribute("app:itemIconTint"));
        assertEquals("@color/tab_layout_material_item_color",
                navigation.getAttribute("app:itemTextColor"));

        final var viewPagers = document.getElementsByTagName(
                "androidx.viewpager.widget.ViewPager");
        assertEquals(1, viewPagers.getLength());
        assertEquals("@dimen/video_detail_navigation_height",
                ((Element) viewPagers.item(0)).getAttribute("android:paddingBottom"));
    }

    private void assertMenuItem(final Element item,
                                final String id,
                                final String title) {
        assertEquals(id, item.getAttribute("android:id"));
        assertEquals(title, item.getAttribute("android:title"));
    }

    private Document parse(final String relativePath) throws Exception {
        final var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
                .parse(resourcesDirectory.resolve(relativePath).toFile());
    }
}
