package org.schabi.newpipe.fragments;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class MainFragmentFeedReselectionTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void navigationReselectionScrollsWhatsNewFeedToTop() throws Exception {
        final String main = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/fragments/MainFragment.java"));
        final String feed = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/local/feed/FeedFragment.kt"));

        assertTrue(main.contains("scrollCurrentFeedToTop(position);"));
        assertTrue(main.contains("scrollCurrentFeedToTop(tab.getPosition());"));
        assertTrue(main.contains("tabsList.get(position).getTabId() != Tab.FeedTab.ID"));
        assertTrue(main.contains("feedFragment.scrollToTop();"));
        assertTrue(feed.contains("fun scrollToTop()"));
        assertTrue(feed.contains("binding.itemsList.stopScroll()"));
        assertTrue(feed.contains("binding.itemsList.scrollToPosition(0)"));
        assertTrue(feed.contains("binding.feedHeader.setExpanded(true, true)"));
    }

    @Test
    public void newItemsButtonReusesTheSameScrollToTopPath() throws Exception {
        final String feed = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/local/feed/FeedFragment.kt"));

        assertTrue(feed.contains("newItemsLoadedButton.setOnClickListener {\n"
                + "            scrollToTop()"));
    }
}
