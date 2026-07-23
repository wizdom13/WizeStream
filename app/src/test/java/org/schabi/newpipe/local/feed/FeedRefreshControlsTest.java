package org.schabi.newpipe.local.feed;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class FeedRefreshControlsTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void filtersFollowTheFeedLoadingLifecycle() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/local/feed/FeedFragment.kt"));

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

    private String methodBody(final String source, final String signature,
                              final String nextSignature) {
        final int start = source.indexOf(signature);
        final int nextMethod = source.indexOf(nextSignature, start + signature.length());
        return source.substring(start, nextMethod);
    }
}
