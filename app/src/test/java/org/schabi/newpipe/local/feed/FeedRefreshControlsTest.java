package org.schabi.newpipe.local.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void filtersRemainAvailableWhileFeedRefreshes() throws Exception {
        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");
        final String showLoading = methodBody(
                source, "override fun showLoading()", "override fun hideLoading()");

        assertFalse(showLoading.contains("streamFilterChips.root.animate(false, 0)"));
        assertFalse(showLoading.contains("refreshRootView.animate(false, 0)"));
        assertTrue(showLoading.contains("itemsList.animate(true, 0)"));
        assertTrue(showLoading.contains("showRefreshProgress(binding)"));
        assertTrue(methodBody(source, "override fun hideLoading()", "override fun showEmptyState()")
                .contains("streamFilterChips.root.animate(true, 200)"));
    }

    @Test
    public void refreshProgressLivesInHeaderWithoutBlockingFeedContent() throws Exception {
        final Document document = parseLayout();
        final Element progressContainer = findByAndroidId(
                document, "@+id/refresh_progress_container");

        assertNotNull(progressContainer);
        assertEquals("gone",
                progressContainer.getAttributeNS(ANDROID_NAMESPACE, "visibility"));
        assertNull(findByAndroidId(document, "@+id/refresh_loading_overlay"));
        assertNull(findByAndroidId(document, "@+id/refresh_loading_scrim"));
        assertNotNull(findByAndroidId(document, "@+id/items_list"));

        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");
        assertTrue(source.contains("showRefreshProgress(binding)"));
        assertTrue(source.contains("hideRefreshProgress(binding)"));
        assertFalse(source.contains("refreshLoadingOverlay.animate("));
        assertFalse(source.contains("private var isRefreshing"));
        assertFalse(source.contains("&& !isRefreshing"));
    }

    @Test
    public void progressAndCancelControlsUseCompactHeaderSurfaces() throws Exception {
        final Document document = parseLayout();
        final Element progress = findByAndroidId(
                document, "@+id/loading_progress_bar");
        final Element progressContainer = findByAndroidId(
                document, "@+id/refresh_progress_container");
        final Element cancel = findByAndroidId(
                document, "@+id/cancel_refresh_button");

        assertNotNull(progress);
        assertNotNull(progressContainer);
        assertEquals(
                "org.schabi.newpipe.local.feed.FeedProgressIndicator",
                progress.getTagName());
        assertEquals("0dp",
                progress.getAttributeNS(ANDROID_NAMESPACE, "layout_width"));
        assertEquals("24dp",
                progress.getAttributeNS(ANDROID_NAMESPACE, "layout_height"));
        assertEquals("1",
                progress.getAttributeNS(ANDROID_NAMESPACE, "layout_weight"));
        assertEquals("polite",
                progress.getAttributeNS(ANDROID_NAMESPACE, "accessibilityLiveRegion"));
        assertNull(findByAndroidId(
                document, "@+id/loading_indeterminate_progress_bar"));
        assertEquals("48dp",
                progressContainer.getAttributeNS(ANDROID_NAMESPACE, "layout_height"));

        assertNotNull(cancel);
        assertEquals("ImageButton", cancel.getTagName());
        assertEquals("48dp", cancel.getAttributeNS(ANDROID_NAMESPACE, "layout_width"));
        assertEquals("48dp", cancel.getAttributeNS(ANDROID_NAMESPACE, "layout_height"));
        assertEquals("?attr/selectableItemBackgroundBorderless",
                cancel.getAttributeNS(ANDROID_NAMESPACE, "background"));
        assertEquals("@string/cancel_refresh",
                cancel.getAttributeNS(ANDROID_NAMESPACE, "contentDescription"));
        assertEquals("@drawable/ic_close",
                cancel.getAttributeNS(ANDROID_NAMESPACE, "src"));
    }

    @Test
    public void feedProgressIndicatorUsesMaterialWaveForBothProgressModes()
            throws Exception {
        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedProgressIndicator.java");
        final String fragment = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");

        assertTrue(source.contains("class FeedProgressIndicator extends ProgressBar"));
        assertTrue(source.contains("Math.sin("));
        assertTrue(source.contains("colorSurfaceVariant"));
        assertTrue(source.contains("colorPrimary"));
        assertTrue(source.contains("ValueAnimator.INFINITE"));
        assertTrue(source.contains("clipDeterminateSegment"));
        assertTrue(source.contains("clipIndeterminateSegment"));
        assertTrue(source.contains("AccessibilityNodeInfo.RangeInfo.obtain"));
        assertFalse(source.contains("CircularProgressIndicator"));
        assertTrue(fragment.contains(
                "loadingProgressBar.setIndeterminate(isIndeterminate)"));
        assertFalse(fragment.contains("loadingIndeterminateProgressBar"));
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
