package org.schabi.newpipe.local.playlist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LocalPlaylistSwipePolicyTest {
    @Test
    void removalRequiresStandaloneEditingAndExplicitOptIn() {
        for (final boolean pinned : new boolean[]{false, true}) {
            for (final boolean editing : new boolean[]{false, true}) {
                for (final boolean enabled : new boolean[]{false, true}) {
                    assertEquals(!pinned && editing && enabled,
                            LocalPlaylistSwipePolicy.isItemRemovalSwipeEnabled(
                                    pinned, editing, enabled));
                }
            }
        }
    }
}
