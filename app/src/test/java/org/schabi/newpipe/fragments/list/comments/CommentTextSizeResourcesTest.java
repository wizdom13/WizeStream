package org.schabi.newpipe.fragments.list.comments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class CommentTextSizeResourcesTest {
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res")
            : Path.of("app/src/main/res");

    @Test
    public void appearanceSettingsExposeTheFourCommentTextSizes() throws Exception {
        final Document document = parse("xml/appearance_settings.xml");
        final Element preference = findListPreference(document, "@string/comment_text_size_key");

        assertEquals("@string/comment_text_size_medium_key",
                preference.getAttribute("android:defaultValue"));
        assertEquals("@array/comment_text_size_description",
                preference.getAttribute("android:entries"));
        assertEquals("@array/comment_text_size_values",
                preference.getAttribute("android:entryValues"));
        assertEquals("true", preference.getAttribute("app:useSimpleSummaryProvider"));

        final String keys = Files.readString(
                resourcesDirectory.resolve("values/settings_keys.xml"));
        assertTrue(keys.contains("<string name=\"comment_text_size_small_key\">12</string>"));
        assertTrue(keys.contains("<string name=\"comment_text_size_medium_key\">14</string>"));
        assertTrue(keys.contains("<string name=\"comment_text_size_large_key\">16</string>"));
        assertTrue(keys.contains(
                "<string name=\"comment_text_size_extra_large_key\">18</string>"));

        final String dimensions = Files.readString(
                resourcesDirectory.resolve("values/dimens.xml"));
        assertTrue(dimensions.contains(
                "<dimen name=\"comment_item_content_text_size\">14sp</dimen>"));
    }

    private Element findListPreference(final Document document, final String key) {
        final var preferences = document.getElementsByTagName("ListPreference");
        for (int index = 0; index < preferences.getLength(); index++) {
            final Element preference = (Element) preferences.item(index);
            if (key.equals(preference.getAttribute("android:key"))) {
                return preference;
            }
        }
        throw new AssertionError("Missing preference: " + key);
    }

    private Document parse(final String relativePath) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resourcesDirectory.resolve(relativePath).toFile());
    }
}
