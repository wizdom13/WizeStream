package org.schabi.newpipe.player;

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
