package org.schabi.newpipe.settings.tabs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HomeNavigationModeResolverTest {
    @Test
    public void zeroOrOneTabShowsNoNavigation() {
        assertEquals(HomeNavigationMode.NONE,
                HomeNavigationModeResolver.resolveNavigationMode(0, true));
        assertEquals(HomeNavigationMode.NONE,
                HomeNavigationModeResolver.resolveNavigationMode(1, true));
    }

    @Test
    public void twoToFiveBottomPositionUsesBottomNavigation() {
        assertEquals(HomeNavigationMode.BOTTOM_NAVIGATION,
                HomeNavigationModeResolver.resolveNavigationMode(2, true));
        assertEquals(HomeNavigationMode.BOTTOM_NAVIGATION,
                HomeNavigationModeResolver.resolveNavigationMode(5, true));
    }

    @Test
    public void sixOrMoreUsesScrollableTabs() {
        assertEquals(HomeNavigationMode.SCROLLABLE_TABS,
                HomeNavigationModeResolver.resolveNavigationMode(6, true));
    }

    @Test
    public void topPositionUsesScrollableTabsWhenThereAreMultipleTabs() {
        assertEquals(HomeNavigationMode.SCROLLABLE_TABS,
                HomeNavigationModeResolver.resolveNavigationMode(2, false));
        assertEquals(HomeNavigationMode.SCROLLABLE_TABS,
                HomeNavigationModeResolver.resolveNavigationMode(6, false));
    }
}
