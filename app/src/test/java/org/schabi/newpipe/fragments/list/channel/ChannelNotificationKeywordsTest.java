package org.schabi.newpipe.fragments.list.channel;

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

public class ChannelNotificationKeywordsTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";
    private static final String APP_NAMESPACE =
            "http://schemas.android.com/apk/res-auto";

    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("") : Path.of("app");

    @Test
    public void subscribedChannelMenuExposesNotificationKeywords() throws Exception {
        final Document menu = parse("src/main/res/menu/menu_channel.xml");
        final Element item = findByAndroidId(
                menu, "@+id/menu_item_notification_keywords");

        assertNotNull(item);
        assertEquals("@string/notification_keywords_title",
                item.getAttributeNS(ANDROID_NAMESPACE, "title"));
        assertEquals("false", item.getAttributeNS(ANDROID_NAMESPACE, "visible"));
        assertEquals("never", item.getAttributeNS(APP_NAMESPACE, "showAsAction"));
    }

    @Test
    public void channelUsesSharedNotificationDialogAndTracksKeywordMode() throws Exception {
        final String source = read(
                "src/main/java/org/schabi/newpipe/fragments/list/channel/ChannelFragment.java");

        assertTrue(source.contains(
                "menu.findItem(R.id.menu_item_notification_keywords)"));
        assertTrue(source.contains(
                "menuNotificationKeywordsButton.setVisible(subscription != null)"));
        assertTrue(source.contains(
                "subscription.getNotificationMode() != NotificationMode.DISABLED"));
        assertTrue(source.contains("NotificationConfigDialog.show("));
        assertTrue(source.contains(
                "subscriptionManager\n                        .updateNotificationSettings("));
    }

    @Test
    public void settingsAndChannelShareValidationAndPersistence() throws Exception {
        final String settingsSource = read(
                "src/main/java/org/schabi/newpipe/settings/notifications/"
                        + "NotificationModeConfigFragment.kt");
        final String dialogSource = read(
                "src/main/java/org/schabi/newpipe/settings/notifications/"
                        + "NotificationConfigDialog.kt");

        assertTrue(settingsSource.contains("NotificationConfigDialog.show("));
        assertTrue(dialogSource.contains("NotificationKeywordFilter.normalize("));
        assertTrue(dialogSource.contains("NotificationKeywordFilter.isValid("));
        assertTrue(dialogSource.contains("saveListener.onSave(mode, normalizedKeywords)"));
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

    private Document parse(final String relativePath) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder()
                .parse(projectDirectory.resolve(relativePath).toFile());
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(projectDirectory.resolve(relativePath));
    }
}
