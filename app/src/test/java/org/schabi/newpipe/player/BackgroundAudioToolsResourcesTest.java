package org.schabi.newpipe.player;

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

public class BackgroundAudioToolsResourcesTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";
    private static final String APP_NAMESPACE =
            "http://schemas.android.com/apk/res-auto";

    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void backgroundPlayerMenuExposesEqualizerAndVisualizer() throws Exception {
        final Document document = parse(resourcesDirectory.resolve("menu/menu_play_queue.xml"));
        final Element equalizer = findByAndroidId(document, "@+id/action_equalizer");
        final Element visualizer = findByAndroidId(document, "@+id/action_visualizer");

        assertNotNull(equalizer);
        assertEquals("@drawable/ic_equalizer",
                equalizer.getAttributeNS(ANDROID_NAMESPACE, "icon"));
        assertEquals("@string/equalizer",
                equalizer.getAttributeNS(ANDROID_NAMESPACE, "title"));
        assertEquals("true", equalizer.getAttributeNS(ANDROID_NAMESPACE, "checkable"));

        assertNotNull(visualizer);
        assertEquals("@drawable/ic_waveform",
                visualizer.getAttributeNS(ANDROID_NAMESPACE, "icon"));
        assertEquals("@string/show_visualizer",
                visualizer.getAttributeNS(ANDROID_NAMESPACE, "title"));
        assertEquals("false", visualizer.getAttributeNS(ANDROID_NAMESPACE, "visible"));
        assertEquals("ifRoom", visualizer.getAttributeNS(APP_NAMESPACE, "showAsAction"));
    }

    @Test
    public void backgroundPlayerHostsAnAccessibleVisualizerSurface() throws Exception {
        final Document document = parse(
                resourcesDirectory.resolve("layout/activity_player_queue_control.xml"));
        final Element visualizer = findByAndroidId(document, "@+id/audio_visualizer");

        assertNotNull(visualizer);
        assertEquals("org.schabi.newpipe.views.AudioVisualizerView",
                visualizer.getTagName());
        assertEquals("@id/appbar",
                visualizer.getAttributeNS(ANDROID_NAMESPACE, "layout_below"));
        assertEquals("@id/metadata",
                visualizer.getAttributeNS(ANDROID_NAMESPACE, "layout_above"));
        assertEquals("@string/audio_visualizer",
                visualizer.getAttributeNS(ANDROID_NAMESPACE, "contentDescription"));
        assertEquals("gone",
                visualizer.getAttributeNS(ANDROID_NAMESPACE, "visibility"));
    }

    @Test
    public void visualizerProcessingOnlyRunsWhileItsSurfaceIsVisible() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/PlayQueueActivity.java"));

        assertTrue(source.contains("EqualizerDialog.forPlayer(player)"));
        assertTrue(source.contains("player.getVisualizerAudioProcessor()"));
        assertTrue(source.contains("PlaybackPresentationMode.LISTEN_VISUALIZER"));
        assertTrue(source.contains("PlaybackPresentationMode.AUDIO_BACKGROUND"));
        assertTrue(source.contains("queueControlBinding.audioVisualizer.setAudioProcessor(null)"));
        assertTrue(methodBody(source, "protected void onStart()",
                "protected void onStop()").contains("updateVisualizerPresentation()"));
        assertTrue(methodBody(source, "protected void onStop()",
                "protected void onSaveInstanceState")
                .contains("suspendVisualizerProcessing()"));
    }

    @Test
    public void backgroundNotificationContinuesToOpenTheSharedQueuePlayer() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/notification/NotificationUtil.java"));

        assertTrue(source.contains("player.audioPlayerSelected()"));
        assertTrue(source.contains("NavigationHelper.getPlayQueueActivityIntent"));
    }

    private String methodBody(final String source, final String signature,
                              final String nextSignature) {
        final int start = source.indexOf(signature);
        final int end = source.indexOf(nextSignature, start + signature.length());
        return source.substring(start, end);
    }

    private Element findByAndroidId(final Document document, final String id) {
        final NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            final Element element = (Element) elements.item(index);
            if (id.equals(element.getAttributeNS(ANDROID_NAMESPACE, "id"))) {
                return element;
            }
        }
        return null;
    }

    private Document parse(final Path path) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }
}
