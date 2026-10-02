/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MediaDisplayPreferencesTest {
    @Test
    public void invalidTextSettingsRestoreTheLayoutDefault() {
        for (final String value : new String[]{null, "", "0", "-20", "NaN", "10000"}) {
            assertEquals(1.0f, MediaTextSize.scale(value), 0.0001f);
        }
        assertEquals(0.7f, MediaTextSize.scale("70"), 0.0001f);
        assertEquals(0.8f, MediaTextSize.scale("80"), 0.0001f);
        assertEquals(1.25f, MediaTextSize.scale("125"), 0.0001f);
        assertEquals(1.5f, MediaTextSize.scale("150"), 0.0001f);
    }

    @Test
    public void tvDefaultsLeaveMoreRoomForVideoAndNeverChangeTabletProportions() {
        final float original = 4.0f / 9.0f;
        assertEquals(0.25f, TvRelatedSidebar.sidebarShare(true, null), 0.0001f);
        assertEquals(0.25f, TvRelatedSidebar.sidebarShare(true, "invalid"), 0.0001f);
        assertEquals(original, TvRelatedSidebar.sidebarShare(true, "wide"), 0.0001f);
        assertTrue(TvRelatedSidebar.sidebarShare(true, "balanced") < original);
        for (final String value : new String[]{null, "compact", "balanced", "wide"}) {
            assertEquals(original, TvRelatedSidebar.sidebarShare(false, value), 0.0001f);
        }
    }
}
