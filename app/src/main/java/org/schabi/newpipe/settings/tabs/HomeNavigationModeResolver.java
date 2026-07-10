package org.schabi.newpipe.settings.tabs;

public final class HomeNavigationModeResolver {
    private static final int BOTTOM_NAVIGATION_MAX_ITEM_COUNT = 5;

    private HomeNavigationModeResolver() { }

    public static HomeNavigationMode resolveNavigationMode(final int tabCount,
                                                           final boolean bottomPosition) {
        if (tabCount <= 1) {
            return HomeNavigationMode.NONE;
        }
        if (bottomPosition && tabCount <= BOTTOM_NAVIGATION_MAX_ITEM_COUNT) {
            return HomeNavigationMode.BOTTOM_NAVIGATION;
        }
        return HomeNavigationMode.SCROLLABLE_TABS;
    }
}
