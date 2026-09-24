/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import org.junit.Test;

public class MainTabStateRestoreTest {
    @Test
    public void removesOnlyMissingFragmentEntries() {
        final Bundle state = new Bundle();
        state.putString("f0", "missing");
        state.putString("f1", "available");
        state.putString("states", "pager-state");

        final Bundle cleaned =
                MainFragment.SelectedTabsPagerAdapter.removeMissingFragmentEntries(
                        state,
                        getClass().getClassLoader(),
                        (bundle, key) -> {
                            if ("f0".equals(key)) {
                                throw new IllegalStateException("Fragment no longer exists");
                            }
                        }
                );

        assertFalse(cleaned.containsKey("f0"));
        assertTrue(cleaned.containsKey("f1"));
        assertTrue(cleaned.containsKey("states"));
        assertTrue(state.containsKey("f0"));
    }
}
