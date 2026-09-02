package org.schabi.newpipe.views;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class NewPipeTextViewTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void nativePipInterceptionIsLimitedToTheDetailPopupAction() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/views/NewPipeTextView.java"));

        assertTrue(source.contains("getId() == R.id.detail_controls_popup"));
        assertTrue(source.contains("if (!isPrimaryDetailPipAction())"));
        assertTrue(source.contains("super.setOnClickListener(listener)"));
        assertTrue(source.contains("super.setOnLongClickListener(listener)"));
        assertTrue(source.contains("super.setOnTouchListener(listener)"));
    }
}
