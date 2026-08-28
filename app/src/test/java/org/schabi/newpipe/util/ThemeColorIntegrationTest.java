package org.schabi.newpipe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ThemeColorIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path resourceDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void blackThemeKeepsDynamicAccentsAndRestoresOnlyBlackSurfaces() throws Exception {
        final String helper = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/util/ThemeHelper.java"));
        final String styles = Files.readString(resourceDirectory.resolve("values/styles.xml"));
        final String blackOverlay = styleBody(styles,
                "ThemeOverlay_wizestream_BlackSurfaces");

        assertTrue(helper.contains("&& isFollowSystemThemeColor(context);"));
        assertFalse(helper.contains("&& !isBlackThemeSelected(context)"));
        assertTrue(helper.indexOf("DynamicColors.applyToActivityIfAvailable((Activity) context);")
                < helper.indexOf("applyBlackSurfaceOverlay(context);"));
        assertTrue(blackOverlay.contains(
                "<item name=\"colorSurface\">@color/black_background_color</item>"));
        assertFalse(blackOverlay.contains("colorPrimary"));
        assertFalse(blackOverlay.contains("colorSecondary"));
    }

    @Test
    public void playerSeekBarUsesMaterialColorsFromItsThemedViewContext() throws Exception {
        final String playerUi = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/ui/VideoPlayerUi.java"));

        assertTrue(playerUi.contains(
                "final Context seekBarContext = binding.playbackSeekBar.getContext();"));
        assertTrue(playerUi.contains("seekBarContext, R.attr.colorPrimaryFixedDim"));
        assertTrue(playerUi.contains("seekBarContext, R.attr.colorPrimaryContainer"));
        assertTrue(playerUi.contains("seekBarContext, R.attr.colorSurfaceVariant"));
        assertTrue(playerUi.contains("setSecondaryProgressTintList(bufferedColor)"));
        assertTrue(playerUi.contains("setProgressBackgroundTintList(inactiveColor)"));
        assertFalse(playerUi.contains("context, R.attr.colorPrimaryFixedDim"));
    }

    @Test
    public void xmlSeekBarsShareTheMaterialColorStyle() throws Exception {
        final String styles = Files.readString(
                resourceDirectory.resolve("values/styles_misc.xml"));
        final String materialSeekBar = styleBody(styles, "Widget.WizeStream.SeekBar");

        assertTrue(materialSeekBar.contains(
                "<item name=\"android:thumbTint\">?attr/colorPrimary</item>"));
        assertTrue(materialSeekBar.contains(
                "<item name=\"android:progressTint\">?attr/colorPrimary</item>"));
        assertTrue(materialSeekBar.contains(
                "<item name=\"android:progressBackgroundTint\">"
                        + "?attr/colorSurfaceVariant</item>"));
        assertTrue(materialSeekBar.contains(
                "<item name=\"android:secondaryProgressTint\">"
                        + "?attr/colorPrimaryContainer</item>"));

        assertLayoutUsesMaterialSeekBar("layout/player.xml");
        assertLayoutUsesMaterialSeekBar("layout/activity_player_queue_control.xml");
        assertLayoutUsesMaterialSeekBar("layout-land/activity_player_queue_control.xml");
        assertLayoutUsesMaterialSeekBar("layout/dialog_sponsor_block_color_picker.xml");
    }

    private void assertLayoutUsesMaterialSeekBar(final String layout) throws Exception {
        final String source = Files.readString(resourceDirectory.resolve(layout));
        assertTrue(source.contains("style=\"@style/Widget.WizeStream.SeekBar\""));
    }

    private String styleBody(final String styles, final String styleName) {
        final String openingTag = "<style name=\"" + styleName + "\"";
        final int start = styles.indexOf(openingTag);
        if (start < 0) {
            throw new AssertionError("Missing style: " + styleName);
        }
        final int end = styles.indexOf("</style>", start);
        if (end < 0) {
            throw new AssertionError("Unclosed style: " + styleName);
        }
        return styles.substring(start, end);
    }
}
