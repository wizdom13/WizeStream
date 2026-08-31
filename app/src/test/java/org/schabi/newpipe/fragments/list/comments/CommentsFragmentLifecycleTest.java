package org.schabi.newpipe.fragments.list.comments;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class CommentsFragmentLifecycleTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void commentScrollingWaitsForListViewsToBeInitialized() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/fragments/list/comments/CommentsFragment.java"));

        final int lifecycleGuard = source.indexOf(
                "if (infoListAdapter == null || itemsList == null)");
        final int adapterAccess = source.indexOf(
                "infoListAdapter.getItemsList().indexOf(comment)");

        assertTrue("The lifecycle guard must exist", lifecycleGuard >= 0);
        assertTrue("The lifecycle guard must run before adapter access",
                lifecycleGuard < adapterAccess);
    }
}
