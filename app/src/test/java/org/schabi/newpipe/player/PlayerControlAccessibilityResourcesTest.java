package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class PlayerControlAccessibilityResourcesTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";

    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void queueControlsHaveAccessibleTargetsAndLabels() throws Exception {
        assertControls(
                "layout/activity_player_queue_control.xml",
                "control_repeat",
                "control_backward",
                "control_fast_rewind",
                "control_play_pause",
                "control_fast_forward",
                "control_forward",
                "control_shuffle");
        assertControls(
                "layout-land/activity_player_queue_control.xml",
                "control_repeat",
                "control_backward",
                "control_fast_rewind",
                "control_play_pause",
                "control_fast_forward",
                "control_forward",
                "control_shuffle");
    }

    @Test
    public void playerIconControlsHaveAccessibleTargetsAndLabels() throws Exception {
        assertControls(
                "layout/player.xml",
                "playerCloseButton",
                "queueButton",
                "segmentsButton",
                "moreOptionsButton",
                "learningNoteButton",
                "sleepTimerButton",
                "equalizerButton",
                "listenModeButton",
                "fullScreenButton",
                "screenRotationButton");
    }

    @Test
    public void playerOverlayKeepsCompactVisualsInsideAccessibleTargets() throws Exception {
        final Document player = parse("layout/player.xml");
        assertAttribute(player, "playerCloseButton", "padding",
                "@dimen/player_main_icon_buttons_padding");
        assertAttribute(player, "moreOptionsButton", "padding",
                "@dimen/player_main_icon_buttons_padding");
        assertAttribute(player, "queueButton", "paddingStart", "9dp");
        assertAttribute(player, "queueButton", "paddingTop", "11dp");

        for (final String id : new String[]{
                "qualityTextView", "playbackSpeed", "resizeTextView", "captionTextView",
                "sleepTimerCountdown", "playbackCurrentTime", "playbackEndTime"
        }) {
            assertAttribute(player, id, "textSize",
                    "@dimen/player_main_controls_text_size");
        }

        for (final String id : new String[]{
                "playWithKodi", "openInBrowser", "share", "learningNoteButton",
                "sleepTimerButton", "equalizerButton", "listenModeButton", "switchMute",
                "fullScreenButton", "screenRotationButton"
        }) {
            assertAttribute(player, id, "padding",
                    "@dimen/player_main_icon_buttons_padding");
        }
    }

    @Test
    public void fullscreenMetadataOwnsSpaceBeforeTheCappedAudioSelector() throws Exception {
        final Document player = parse("layout/player.xml");
        assertAttribute(player, "metadataView", "layout_width", "0dp");
        assertAttribute(player, "metadataView", "layout_weight", "1");
        assertAttribute(player, "metadataControls", "layout_height", "wrap_content");
        assertAttribute(player, "metadataControls", "minHeight", "48dp");
        assertAttribute(player, "audioTrackContainer", "layout_width", "wrap_content");
        assertAttribute(player, "audioTrackContainer", "layout_weight", "");
        assertAttribute(player, "audioTrackTextView", "maxWidth",
                "@dimen/player_audio_track_max_width");
    }

    @Test
    public void seekGestureUsesCompactRoundedScrim() throws Exception {
        final Document player = parse("layout/player.xml");
        assertAttribute(player, "swipeSeekDisplay", "background",
                "@drawable/player_seek_gesture_background");
        assertAttribute(player, "swipeSeekDisplay", "paddingStart",
                "@dimen/player_seek_gesture_horizontal_padding");
        assertAttribute(player, "swipeSeekDisplay", "paddingTop",
                "@dimen/player_seek_gesture_vertical_padding");
        assertAttribute(player, "swipeSeekDisplay", "textSize",
                "@dimen/player_seek_gesture_text_size");

        final Element background = parse("drawable/player_seek_gesture_background.xml")
                .getDocumentElement();
        assertEquals("rectangle",
                background.getAttributeNS(ANDROID_NAMESPACE, "shape"));
        assertEquals("@dimen/player_seek_gesture_corner_radius",
                ((Element) background.getElementsByTagName("corners").item(0))
                        .getAttributeNS(ANDROID_NAMESPACE, "radius"));
        assertEquals("#99000000",
                ((Element) background.getElementsByTagName("solid").item(0))
                        .getAttributeNS(ANDROID_NAMESPACE, "color"));
    }

    @Test
    public void learningNoteActionsHaveAccessibleTargetsAndLabels() throws Exception {
        assertControls(
                "layout/item_learning_note.xml",
                "learning_note_edit",
                "learning_note_delete");
    }

    private void assertControls(final String layout, final String... ids) throws Exception {
        final Document document = parse(layout);
        for (final String id : ids) {
            final Element element = findById(document, id);
            assertNotNull(layout + ": " + id, element);
            assertAtLeast48Dp(layout, id, element, "layout_width");
            assertAtLeast48Dp(layout, id, element, "layout_height");
            assertFalse(layout + ": " + id,
                    element.getAttributeNS(ANDROID_NAMESPACE, "contentDescription").isBlank());
        }
    }

    private void assertAtLeast48Dp(final String layout,
                                   final String id,
                                   final Element element,
                                   final String attribute) {
        final String value = element.getAttributeNS(ANDROID_NAMESPACE, attribute);
        if ("wrap_content".equals(value) || "0dp".equals(value)) {
            final String minimumAttribute = "layout_width".equals(attribute)
                    ? "minWidth" : "minHeight";
            final String minimum = element.getAttributeNS(ANDROID_NAMESPACE, minimumAttribute);
            assertTrue(layout + ": " + id + " " + minimumAttribute,
                    parseDp(minimum) >= 48);
        } else {
            assertTrue(layout + ": " + id + " " + attribute,
                    parseDp(value) >= 48);
        }
    }

    private void assertAttribute(final Document document,
                                 final String id,
                                 final String attribute,
                                 final String expected) {
        final Element element = findById(document, id);
        assertNotNull(id, element);
        assertEquals(id + " " + attribute, expected,
                element.getAttributeNS(ANDROID_NAMESPACE, attribute));
    }

    private int parseDp(final String value) {
        assertTrue(value, value.endsWith("dp"));
        return Integer.parseInt(value.substring(0, value.length() - 2));
    }

    private Element findById(final Document document, final String id) {
        final var elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            final Element element = (Element) elements.item(index);
            final String androidId = element.getAttributeNS(ANDROID_NAMESPACE, "id");
            if (("@+id/" + id).equals(androidId) || ("@id/" + id).equals(androidId)) {
                return element;
            }
        }
        return null;
    }

    private Document parse(final String relativePath) throws Exception {
        final var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
                .parse(resourcesDirectory.resolve(relativePath).toFile());
    }
}
