package org.schabi.newpipe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class Material3TypographyResourcesTest {
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void layoutsDoNotUseLegacyTextAppearances() throws Exception {
        try (var paths = Files.walk(resourcesDirectory)) {
            for (final Path path : paths.filter(this::isLayoutXml).toList()) {
                final String source = Files.readString(path);
                assertFalse(path.toString(),
                        source.contains("?android:attr/textAppearance"));
                assertFalse(path.toString(),
                        source.contains("?android:textAppearance"));
                assertFalse(path.toString(),
                        source.contains("@android:style/TextAppearance"));
                assertFalse(path.toString(),
                        source.contains("TextAppearance.AppCompat"));
            }
        }
    }

    @Test
    public void customPlayerTextAppearancesInheritMaterial3Roles() throws Exception {
        final String styles = Files.readString(
                resourcesDirectory.resolve("values/styles_misc.xml"));

        assertTrue(styles.contains(
                "<style name=\"PlayQueueItemTitle\" "
                        + "parent=\"TextAppearance.Material3.TitleMedium\">"));
        assertTrue(styles.contains(
                "<style name=\"PlayQueueItemSubtitle\" "
                        + "parent=\"TextAppearance.Material3.BodySmall\">"));
        assertTrue(styles.contains(
                "parent=\"TextAppearance.Material3.BodyLarge\""));
        assertFalse(styles.contains(
                "parent=\"TextAppearance.AppCompat.Widget.PopupMenu"));
    }

    private boolean isLayoutXml(final Path path) {
        final Path parent = path.getParent();
        return Files.isRegularFile(path)
                && path.getFileName().toString().endsWith(".xml")
                && parent != null
                && parent.getFileName().toString().startsWith("layout");
    }
}
