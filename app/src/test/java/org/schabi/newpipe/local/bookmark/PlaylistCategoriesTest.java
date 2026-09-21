package org.schabi.newpipe.local.bookmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaylistCategoriesTest {
    @Test
    public void roundTripPreservesLocalAndRemoteMemberships() throws Exception {
        final PlaylistCategories categories = new PlaylistCategories();
        final String music = categories.create(" Music ");
        categories.assign("local:42", music);
        categories.assign("remote:0:https://example.com/list", music);
        final PlaylistCategories restored = PlaylistCategories.fromJson(categories.toJson());
        assertEquals("Music", restored.name(music));
        assertTrue(restored.matches("local:42", music));
        assertTrue(restored.matches("remote:0:https://example.com/list", music));
        assertFalse(restored.matches("local:42", PlaylistCategories.UNCATEGORIZED));
        assertTrue(restored.matches("local:43", PlaylistCategories.UNCATEGORIZED));
    }

    @Test
    public void movingAndRenamingKeepMembershipUnambiguous() {
        final PlaylistCategories categories = new PlaylistCategories();
        final String first = categories.create("First");
        final String second = categories.create("Second");
        categories.assign("local:1", first);
        categories.assign("local:1", second);
        categories.rename(second, "Renamed");
        assertFalse(categories.matches("local:1", first));
        assertTrue(categories.matches("local:1", second));
        assertTrue(categories.matches("local:1", PlaylistCategories.ALL));
    }

    @Test
    public void deletingCategoryReturnsMembersToUncategorized() {
        final PlaylistCategories categories = new PlaylistCategories();
        final String category = categories.create("Temporary");
        categories.assign("local:1", category);
        categories.delete(category);
        assertTrue(categories.ids().isEmpty());
        assertTrue(categories.matches("local:1", PlaylistCategories.UNCATEGORIZED));
    }

    @Test
    public void preferenceKeysAreIsolatedPerProfile() {
        final String first = PlaylistCategories.preferenceKey("profile-a");
        final String second = PlaylistCategories.preferenceKey("profile-b");
        assertNotEquals(first, second);
        assertTrue(first.startsWith(PlaylistCategories.PREFERENCE_KEY));
        assertTrue(second.startsWith(PlaylistCategories.PREFERENCE_KEY));
    }

    @Test
    public void namesMustBeUniqueAndNonempty() {
        final PlaylistCategories categories = new PlaylistCategories();
        categories.create("Music");
        assertThrows(IllegalArgumentException.class, () -> categories.create(" music "));
        assertThrows(IllegalArgumentException.class, () -> categories.create("  "));
    }
}
