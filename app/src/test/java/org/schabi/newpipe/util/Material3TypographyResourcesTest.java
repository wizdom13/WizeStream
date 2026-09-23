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
        final String popupBackground = Files.readString(
                resourcesDirectory.resolve("drawable/popup_menu_background.xml"));
        final String streamActionRow = Files.readString(
                resourcesDirectory.resolve("layout/dialog_stream_action_item.xml"));
        final String dialogTitle = Files.readString(
                resourcesDirectory.resolve("layout/dialog_title.xml"));
        final String dialogStyles = Files.readString(
                resourcesDirectory.resolve("values/styles.xml"));
        final Path infoItemDialogPath = Files.exists(Path.of(
                "src/main/java/org/schabi/newpipe/info_list/dialog/InfoItemDialog.java"))
                ? Path.of("src/main/java/org/schabi/newpipe/info_list/dialog/InfoItemDialog.java")
                : Path.of("app/src/main/java/org/schabi/newpipe/info_list/dialog/InfoItemDialog.java");
        final String infoItemDialog = Files.readString(infoItemDialogPath);

        assertTrue(styles.contains(
                "<style name=\"PlayQueueItemTitle\" "
                        + "parent=\"TextAppearance.Material3.TitleMedium\">"));
        assertTrue(styles.contains(
                "<style name=\"PlayQueueItemSubtitle\" "
                        + "parent=\"TextAppearance.Material3.BodySmall\">"));
        assertTrue(styles.contains(
                "parent=\"TextAppearance.Material3.BodyLarge\""));
        assertTrue(styles.contains(
                "<style name=\"TextAppearance.WizeStream.PopupMenu.Primary\""
                        + "\n        parent=\"TextAppearance.Material3.BodyLarge\">"));
        assertTrue(styles.contains(
                "<style name=\"TextAppearance.WizeStream.PopupMenu.Secondary\""
                        + "\n        parent=\"TextAppearance.Material3.BodyMedium\">"));
        assertTrue(styles.contains(
                "<item name=\"android:colorBackground\">"
                        + "?attr/colorSurfaceContainer</item>"));
        assertTrue(popupBackground.contains(
                "<solid android:color=\"?attr/colorSurfaceContainer\" />"));
        assertTrue(streamActionRow.contains(
                "android:textAppearance=\"@style/TextAppearance.Material3.BodyLarge\""));
        assertTrue(streamActionRow.contains("android:minHeight=\"48dp\""));
        assertTrue(dialogTitle.contains(
                "android:textAppearance=\"@style/TextAppearance.Material3.TitleLarge\""));
        assertTrue(dialogTitle.contains(
                "android:textAppearance=\"@style/TextAppearance.Material3.BodyMedium\""));
        assertTrue(dialogStyles.contains(
                "<style name=\"ThemeOverlay_wizestream_StreamActionDialog\""));
        assertTrue(dialogStyles.contains(
                "<item name=\"colorSurface\">?attr/colorSurfaceContainer</item>"));
        assertTrue(infoItemDialog.contains(
                "R.style.ThemeOverlay_wizestream_StreamActionDialog"));
        assertTrue(infoItemDialog.contains("R.layout.dialog_stream_action_item"));
        assertTrue(infoItemDialog.contains(".setAdapter(actionAdapter, action)"));
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
