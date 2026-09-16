/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

public class SurfaceRebindPolicyTest {
    @Test
    public void surfaceChangedRebindIsLimitedToPreAndroid14() {
        assertTrue(SurfaceHolderCallback.shouldRebindOnSurfaceChanged(
                Build.VERSION_CODES.TIRAMISU));
        assertFalse(SurfaceHolderCallback.shouldRebindOnSurfaceChanged(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE));
        assertFalse(SurfaceHolderCallback.shouldRebindOnSurfaceChanged(37));
    }
}
