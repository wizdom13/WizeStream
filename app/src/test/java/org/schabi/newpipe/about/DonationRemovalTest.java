package org.schabi.newpipe.about;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class DonationRemovalTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main") : Path.of("app/src/main");

    @Test
    public void donationEntryPointsAreRemoved() throws Exception {
        final String mainActivity = Files.readString(sourceDirectory.resolve(
                "java/org/schabi/newpipe/MainActivity.java"));
        final String aboutActivity = Files.readString(sourceDirectory.resolve(
                "java/org/schabi/newpipe/about/AboutActivity.kt"));
        final String aboutLayout = Files.readString(sourceDirectory.resolve(
                "res/layout/fragment_about.xml"));

        assertFalse(mainActivity.contains("ITEM_ID_DONATION"));
        assertFalse(mainActivity.contains("R.string.donation_url"));
        assertFalse(aboutActivity.contains("aboutDonationLink"));
        assertFalse(aboutActivity.contains("R.string.donation_url"));
        assertFalse(aboutLayout.contains("about_donation_link"));
        assertFalse(aboutLayout.contains("donation_encouragement"));
        assertFalse(aboutLayout.contains("give_back"));
    }

    @Test
    public void donationStringResourcesAreRemoved() throws Exception {
        final String[] names = {
                "donation_title",
                "donation_encouragement",
                "donation_url",
                "give_back",
                "wizestream_upstream_donation_title"
        };
        try (Stream<Path> files = Files.walk(sourceDirectory.resolve("res"))) {
            for (final Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".xml")).toList()) {
                final String xml = Files.readString(path);
                for (final String name : names) {
                    assertFalse(path + " still defines " + name,
                            xml.contains("name=\"" + name + "\""));
                }
            }
        }
    }
}
