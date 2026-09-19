package org.schabi.newpipe.settings.tabs;

import android.content.res.Configuration;

import androidx.annotation.Nullable;

/** Resolves the adaptive navigation component used on the main large-screen layout. */
public final class TabletNavigationPositionResolver {
    public static final String AUTOMATIC = "automatic";
    public static final String BOTTOM = "bottom";
    public static final String LEFT = "left";

    private TabletNavigationPositionResolver() { }

    public static boolean useNavigationRail(final boolean largeScreen,
                                            final int orientation,
                                            @Nullable final String position) {
        if (!largeScreen || BOTTOM.equals(position)) {
            return false;
        }
        if (LEFT.equals(position)) {
            return true;
        }
        return orientation == Configuration.ORIENTATION_LANDSCAPE;
    }
}
