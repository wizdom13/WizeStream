package org.schabi.newpipe.local.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class FeedRefreshControlsTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";
    private static final String APP_NAMESPACE =
            "http://schemas.android.com/apk/res-auto";

    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void filtersFollowTheFeedLoadingLifecycle() throws Exception {
        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");

        assertTrue(methodBody(source, "override fun showLoading()", "override fun hideLoading()")
                .contains("streamFilterChips.root.animate(false, 0)"));
        assertTrue(methodBody(source, "override fun hideLoading()", "override fun showEmptyState()")
                .contains("streamFilterChips.root.animate(true, 200)"));
        assertTrue(methodBody(source, "override fun showEmptyState()", "override fun handleResult")
                .contains("streamFilterChips.root.animate(true, 200)"));
        assertTrue(methodBody(
                source,
                "override fun handleError()",
                "private fun handleProgressState"
        ).contains("streamFilterChips.root.animate(true, 200)"));
    }

    @Test
    public void refreshOverlayKeepsFeedVisibleBehindAThemeAwareScrim() throws Exception {
        final Document document = parseLayout();
        final Element overlay = findByAndroidId(
                document, "@+id/refresh_loading_overlay");
        final Element scrim = findByAndroidId(
                document, "@+id/refresh_loading_scrim");

        assertNotNull(overlay);
        assertEquals("gone", overlay.getAttributeNS(ANDROID_NAMESPACE, "visibility"));
        assertNotNull(scrim);
        assertEquals("0.78", scrim.getAttributeNS(ANDROID_NAMESPACE, "alpha"));
        assertEquals("?attr/colorSurface",
                scrim.getAttributeNS(ANDROID_NAMESPACE, "background"));

        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");
        assertTrue(methodBody(source, "override fun showLoading()", "override fun hideLoading()")
                .contains("itemsList.animate(true, 0)"));
        assertTrue(source.contains("refreshLoadingOverlay.animate(true, 200)"));
        assertTrue(source.contains("refreshLoadingOverlay.animate(false, 200)"));
    }

    @Test
    public void progressAndCancelControlsUsePolishedCircularSurfaces() throws Exception {
        final Document document = parseLayout();
        final Element progress = findByAndroidId(
                document, "@+id/loading_progress_bar");
        final Element indeterminateProgress = findByAndroidId(
                document, "@+id/loading_indeterminate_progress_bar");
        final Element cancel = findByAndroidId(
                document, "@+id/cancel_refresh_button");

        assertNotNull(progress);
        assertNotNull(indeterminateProgress);
        assertEquals(
                "org.schabi.newpipe.local.feed.FeedProgressIndicator",
                progress.getTagName());
        assertEquals("104dp", progress.getAttributeNS(ANDROID_NAMESPACE, "layout_width"));
        assertEquals("104dp", progress.getAttributeNS(ANDROID_NAMESPACE, "layout_height"));
        assertEquals("polite",
                progress.getAttributeNS(ANDROID_NAMESPACE, "accessibilityLiveRegion"));
        assertEquals("88dp", progress.getAttributeNS(APP_NAMESPACE, "indicatorSize"));
        assertEquals(
                "104dp",
                indeterminateProgress.getAttributeNS(ANDROID_NAMESPACE, "layout_width"));
        assertEquals(
                "104dp",
                indeterminateProgress.getAttributeNS(ANDROID_NAMESPACE, "layout_height"));
        assertEquals(
                "88dp",
                indeterminateProgress.getAttributeNS(APP_NAMESPACE, "indicatorSize"));
        assertEquals("6dp", progress.getAttributeNS(APP_NAMESPACE, "trackThickness"));
        assertEquals("3dp", progress.getAttributeNS(APP_NAMESPACE, "trackCornerRadius"));

        assertNull(findByAndroidId(document, "@+id/loading_progress_text"));

        assertNotNull(cancel);
        assertEquals(
                "com.google.android.material.floatingactionbutton.FloatingActionButton",
                cancel.getTagName());
        assertEquals("bottom|center_horizontal",
                cancel.getAttributeNS(ANDROID_NAMESPACE, "layout_gravity"));
        assertEquals("56dp",
                cancel.getAttributeNS(ANDROID_NAMESPACE, "layout_marginBottom"));
        assertEquals("@string/cancel_refresh",
                cancel.getAttributeNS(ANDROID_NAMESPACE, "contentDescription"));
        assertEquals("@drawable/ic_close",
                cancel.getAttributeNS(APP_NAMESPACE, "srcCompat"));
    }

    @Test
    public void determinateCounterUsesTheCircularIndicatorsExactCanvasCenter()
            throws Exception {
        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedProgressIndicator.java");

        assertTrue(source.contains("super.onDraw(canvas)"));
        assertTrue(source.contains("counterBounds.exactCenterX()"));
        assertTrue(source.contains("counterBounds.exactCenterY()"));
        assertTrue(source.contains("canvas.drawText(counterText"));
    }

    @Test
    public void cancelButtonStopsTheSharedRefreshServicePath() throws Exception {
        final String fragment = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");
        final String service = readSource(
                "org/schabi/newpipe/local/feed/service/FeedLoadService.kt");

        assertTrue(fragment.contains("cancelRefreshButton.setOnClickListener"));
        assertTrue(fragment.contains("FeedLoadService.cancel(requireContext())"));
        assertTrue(service.contains("fun cancel(context: Context)"));
        assertTrue(service.contains("getBroadcast(this, NOTIFICATION_ID, cancelIntent(this)"));
        assertTrue(methodBody(service, "private fun cancelLoading()", "private fun stopService()")
                .contains("feedLoadManager.cancel()"));
        assertTrue(methodBody(service, "private fun cancelLoading()", "private fun stopService()")
                .contains("FeedEventManager.reset(feedScope)"));
        assertTrue(methodBody(service, "private fun cancelLoading()", "private fun stopService()")
                .contains("stopService()"));
        assertTrue(methodBody(
                service,
                "private fun setupBroadcastReceiver()",
                "// Error handling"
        ).contains("cancelLoading()"));
    }

    @Test
    public void preferenceChangesCannotOutliveTheFeedViewBinding() throws Exception {
        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");

        assertTrue(methodBody(
                source,
                "override fun onViewCreated",
                "override fun onResume"
        ).contains("registerOnSharedPreferenceChangeListener(onSettingsChangeListener)"));
        assertTrue(methodBody(
                source,
                "override fun onDestroyView()",
                "// Handling"
        ).contains("unregisterOnSharedPreferenceChangeListener(onSettingsChangeListener)"));
        assertTrue(methodBody(
                source,
                "private fun showFilteredFeedItems",
                "override fun setContextualSearchQuery"
        ).contains("if (_feedBinding == null)"));
    }

    private String readSource(final String relativePath) throws Exception {
        return Files.readString(sourceDirectory.resolve(relativePath));
    }

    private Document parseLayout() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder()
                .parse(resourcesDirectory.resolve("layout/fragment_feed.xml").toFile());
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

    private String methodBody(final String source, final String signature,
                              final String nextSignature) {
        final int start = source.indexOf(signature);
        final int nextMethod = source.indexOf(nextSignature, start + signature.length());
        return source.substring(start, nextMethod);
    }
}
