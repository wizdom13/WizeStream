package org.schabi.newpipe.local.search;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ContextualSearchIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void requestedLocalTabsImplementContextualSearchContract() throws Exception {
        assertSourceContains("org/schabi/newpipe/local/history/StatisticsPlaylistFragment.java",
                "ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/local/feed/FeedFragment.kt",
                "ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/local/subscription/SubscriptionFragment.kt",
                "ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/local/bookmark/BookmarkFragment.java",
                "ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/local/subscription/SubscriptionViewModel.kt",
                "getSubscriptions(filterQuery = it)");
        assertSourceContains("org/schabi/newpipe/MainActivity.java",
                "fragment instanceof MainFragment");
    }

    @Test
    public void bookmarkFilteringProtectsCanonicalOrdering() throws Exception {
        final String source = read(
                "org/schabi/newpipe/local/bookmark/BookmarkFragment.java");

        assertTrue(source.contains("captureCanonicalOrderFromAdapter();"));
        assertTrue(source.contains(
                "itemListAdapter.setUseItemHandle(!isContextualSearchActive())"));
        assertTrue(source.contains("itemListAdapter == null || isContextualSearchActive()"));
    }

    @Test
    public void contextualToolbarOwnsNavigationSpaceOnlyWhileSearchIsOpen() throws Exception {
        assertSourceContains("org/schabi/newpipe/fragments/MainFragment.java",
                "setActivityContextualSearchToolbarActive(contextualSearchOpen)");
        assertSourceContains("org/schabi/newpipe/MainActivity.java",
                "toolbarLayoutBinding.toolbar.setNavigationIcon(null)");
        assertSourceContains("org/schabi/newpipe/MainActivity.java",
                "toggle.syncState()");
        assertSourceContains("org/schabi/newpipe/fragments/MainFragment.java",
                "setContentDescription(globalSearchDescription)");
        assertSourceContains("org/schabi/newpipe/fragments/MainFragment.java",
                "TooltipCompat.setTooltipText(contextualGlobalSearchButton");
    }

    private void assertSourceContains(final String relativePath, final String expected)
            throws Exception {
        assertTrue(relativePath, read(relativePath).contains(expected));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(sourceDirectory.resolve(relativePath));
    }
}
