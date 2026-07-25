package org.schabi.newpipe.settings.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

public class SettingMigrationsTest {
    @Test
    public void addingPodcastTabPreservesExistingSelectionWithoutMutatingIt() {
        final Set<String> existingTabs = Set.of("videos", "about");

        final Set<String> migratedTabs =
                SettingMigrations.copyAndAdd(existingTabs, "podcasts");

        assertEquals(Set.of("videos", "about"), existingTabs);
        assertFalse(existingTabs.contains("podcasts"));
        assertTrue(migratedTabs.containsAll(existingTabs));
        assertTrue(migratedTabs.contains("podcasts"));
    }
}
