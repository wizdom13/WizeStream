package org.schabi.newpipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class MainSearchNavigationPersistenceTest {
    private final Path mainDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main") : Path.of("app/src/main");

    @Test
    public void searchNavigationMatchesTheMainNavigationMode() {
        assertTrue(MainActivity.shouldShowSearchNavigation(false, 4, 5, true));
        assertFalse(MainActivity.shouldShowSearchNavigation(false, 4, 5, false));
        assertTrue(MainActivity.shouldShowSearchNavigation(true, 7, 7, false));
        assertFalse(MainActivity.shouldShowSearchNavigation(true, 8, 7, true));
    }

    @Test
    public void invalidRememberedTabFallsBackToTheFirstTab() {
        assertEquals(0, MainActivity.normalizeSearchNavigationTabPosition(-1, 4));
        assertEquals(0, MainActivity.normalizeSearchNavigationTabPosition(5, 4));
        assertEquals(2, MainActivity.normalizeSearchNavigationTabPosition(2, 4));
    }

    @Test
    public void searchFragmentHandsNavigationVisibilityToTheActivity() throws Exception {
        final String source = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/fragments/list/search/SearchFragment.java"));
        assertTrue(source.contains("showMainNavigationForSearch()"));
        assertTrue(source.contains("hideMainNavigationForSearch()"));
    }

    @Test
    public void mainFragmentConsumesTheDestinationSelectedFromSearch() throws Exception {
        final String source = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/fragments/MainFragment.java"));
        assertTrue(source.contains("consumePendingMainTabPosition()"));
        assertTrue(source.contains("rememberMainTabPosition(position)"));
    }
}
