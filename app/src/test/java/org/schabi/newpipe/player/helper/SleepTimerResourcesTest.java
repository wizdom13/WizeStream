package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class SleepTimerResourcesTest {
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void playerAndQueueExposeSleepTimerActions() throws Exception {
        final Element playerButton = findByAndroidId(parse("layout/player.xml"),
                "@+id/sleepTimerButton");
        assertNotNull(playerButton);
        assertEquals("@drawable/ic_sleep_timer",
                playerButton.getAttributeNS(ANDROID_NAMESPACE, "src"));
        assertEquals("@string/sleep_timer",
                playerButton.getAttributeNS(ANDROID_NAMESPACE, "contentDescription"));
        assertNotNull(findByAndroidId(parse("layout/player.xml"),
                "@+id/sleepTimerCountdown"));

        final Element queueAction = findByAndroidId(parse("menu/menu_play_queue.xml"),
                "@+id/action_sleep_timer");
        assertNotNull(queueAction);
        assertEquals("@drawable/ic_sleep_timer",
                queueAction.getAttributeNS(ANDROID_NAMESPACE, "icon"));
    }

    @Test
    public void dialogProvidesAllModesAndOptionalFadeOut() throws Exception {
        final Document dialog = parse("layout/dialog_sleep_timer.xml");

        assertNotNull(findByAndroidId(dialog, "@+id/timer15Minutes"));
        assertNotNull(findByAndroidId(dialog, "@+id/timer30Minutes"));
        assertNotNull(findByAndroidId(dialog, "@+id/timer45Minutes"));
        assertNotNull(findByAndroidId(dialog, "@+id/timer60Minutes"));
        assertNotNull(findByAndroidId(dialog, "@+id/timerEndCurrent"));
        assertNotNull(findByAndroidId(dialog, "@+id/timerEndQueue"));
        assertNotNull(findByAndroidId(dialog, "@+id/timerCustom"));
        assertNotNull(findByAndroidId(dialog, "@+id/customMinutes"));
        assertNotNull(findByAndroidId(dialog, "@+id/fadeOut"));
    }

    @Test
    public void playerHandlesDurationAndNaturalPlaybackEndpoints() throws Exception {
        final String player = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/Player.kt"));
        final String listenerController = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/PlayerMedia3ListenerController.kt"));
        final String controller = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/SleepTimerPlaybackController.kt"));

        assertTrue(listenerController.contains("sleepTimerController.onItemEnded"));
        assertTrue(listenerController.contains("DISCONTINUITY_REASON_AUTO_TRANSITION"));
        assertTrue(player.contains("sleepTimerController.startDuration"));
        assertTrue(controller.contains("timer.hasDurationExpired()"));
        assertTrue(controller.contains("setVolumeMultiplier(1.0f)"));
        assertTrue(controller.contains("player.pause()"));
    }

    private Element findByAndroidId(final Document document, final String id) {
        final NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            final Element element = (Element) elements.item(i);
            if (id.equals(element.getAttributeNS(ANDROID_NAMESPACE, "id"))) {
                return element;
            }
        }
        return null;
    }

    private Document parse(final String relativePath) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder()
                .parse(resourcesDirectory.resolve(relativePath).toFile());
    }
}
