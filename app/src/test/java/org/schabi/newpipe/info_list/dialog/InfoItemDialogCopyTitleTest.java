package org.schabi.newpipe.info_list.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class InfoItemDialogCopyTitleTest {
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void defaultMenuPlacesCopyTitleBesideShare() throws Exception {
        final String dialog = source("InfoItemDialog.java");

        assertTrue(dialog.contains("StreamDialogDefaultEntry.COPY_TITLE,"
                + System.lineSeparator()
                + "                    StreamDialogDefaultEntry.SHARE,"));
    }

    @Test
    public void copyTitleCopiesOnlyTheDisplayedTitle() throws Exception {
        final String entries = source("StreamDialogDefaultEntry.java");
        final int copyTitleStart = entries.indexOf(
                "COPY_TITLE(R.string.copy_video_title");
        final int shareStart = entries.indexOf("SHARE(R.string.share", copyTitleStart);
        final String copyTitleEntry = entries.substring(copyTitleStart, shareStart);

        assertTrue(copyTitleEntry.contains(
                "ShareUtils.copyToClipboard(fragment.requireContext(), item.getName())"));
        assertFalse(copyTitleEntry.contains("item.getUrl()"));
        assertFalse(copyTitleEntry.contains("item.getUploaderName()"));
    }

    @Test
    public void copyTitleLabelIsAvailable() throws Exception {
        final String strings = Files.readString(resourcesDirectory.resolve("values/strings.xml"));

        assertTrue(strings.contains(
                "<string name=\"copy_video_title\">Copy title</string>"));
    }

    private String source(final String fileName) throws Exception {
        return Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/info_list/dialog/" + fileName));
    }
}
