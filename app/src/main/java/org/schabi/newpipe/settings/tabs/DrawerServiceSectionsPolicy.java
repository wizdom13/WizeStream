package org.schabi.newpipe.settings.tabs;

import android.content.SharedPreferences;

public final class DrawerServiceSectionsPolicy {
    private DrawerServiceSectionsPolicy() {
    }

    public static boolean shouldShow(final SharedPreferences preferences,
                                     final String preferenceKey) {
        return preferences.getBoolean(preferenceKey, true);
    }
}
