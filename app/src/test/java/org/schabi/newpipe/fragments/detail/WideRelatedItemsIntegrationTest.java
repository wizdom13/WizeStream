package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class WideRelatedItemsIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java")
            : Path.of("app/src/main/java");

    @Test
    public void onlyEmbeddedWideRelatedItemsUseResponsiveListRows() throws Exception {
        final String videoDetail = read("org/schabi/newpipe/fragments/detail/"
                + "VideoDetailFragment.java");
        final String relatedItems = read("org/schabi/newpipe/fragments/list/videos/"
                + "RelatedItemsFragment.java");
        final String adapter = read("org/schabi/newpipe/info_list/InfoListAdapter.java");

        assertTrue(videoDetail.contains("RelatedItemsFragment.getInstance(info, true)"));
        assertTrue(videoDetail.contains("RelatedItemsFragment.getInstance(info)"));
        assertTrue(relatedItems.contains("setUseWideRelatedVariant("));
        assertTrue(adapter.contains("R.layout.list_stream_related_wide_item"));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(sourceDirectory.resolve(relativePath));
    }
}
