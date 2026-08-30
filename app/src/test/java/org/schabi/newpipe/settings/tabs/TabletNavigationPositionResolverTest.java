package org.schabi.newpipe.settings.tabs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;

import org.junit.Test;

public class TabletNavigationPositionResolverTest {
    @Test
    public void automaticUsesBottomInPortraitAndRailInLandscape() {
        assertFalse(TabletNavigationPositionResolver.useNavigationRail(true,
                Configuration.ORIENTATION_PORTRAIT,
                TabletNavigationPositionResolver.AUTOMATIC));
        assertTrue(TabletNavigationPositionResolver.useNavigationRail(true,
                Configuration.ORIENTATION_LANDSCAPE,
                TabletNavigationPositionResolver.AUTOMATIC));
    }

    @Test
    public void explicitPositionOverridesOrientation() {
        assertTrue(TabletNavigationPositionResolver.useNavigationRail(true,
                Configuration.ORIENTATION_PORTRAIT,
                TabletNavigationPositionResolver.LEFT));
        assertFalse(TabletNavigationPositionResolver.useNavigationRail(true,
                Configuration.ORIENTATION_LANDSCAPE,
                TabletNavigationPositionResolver.BOTTOM));
    }

    @Test
    public void phonesAlwaysUseBottomNavigation() {
        assertFalse(TabletNavigationPositionResolver.useNavigationRail(false,
                Configuration.ORIENTATION_LANDSCAPE,
                TabletNavigationPositionResolver.LEFT));
    }

    @Test
    public void unknownPositionFallsBackToAutomatic() {
        assertTrue(TabletNavigationPositionResolver.useNavigationRail(true,
                Configuration.ORIENTATION_LANDSCAPE, "unknown"));
        assertFalse(TabletNavigationPositionResolver.useNavigationRail(true,
                Configuration.ORIENTATION_PORTRAIT, null));
    }
}
