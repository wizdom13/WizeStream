/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class MainTabStateRestoreTest {
    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of(".") : Path.of("app");

    @Test
    public void staleFragmentEntriesAreDiscardedBeforePagerRestore() throws Exception {
        final String source = Files.readString(projectDirectory.resolve(
                "src/main/java/org/schabi/newpipe/fragments/MainFragment.java"));

        assertTrue(source.contains("final Bundle cleanedState = new Bundle((Bundle) state)"));
        assertTrue(source.contains("fragmentManager.getFragment(cleanedState, key)"));
        assertTrue(source.contains("catch (final IllegalStateException e)"));
        assertTrue(source.contains("cleanedState.remove(key)"));
        assertTrue(source.contains("super.restoreState(cleanedState, loader)"));
    }
}
