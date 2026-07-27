package org.schabi.newpipe;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class YouTubeMusicModeIntegrationTest {
    private final Path appSource = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path extractorSource = Files.exists(Path.of("../external/WizeStreamExtractor"))
            ? Path.of("../external/WizeStreamExtractor/extractor/src/main/java")
            : Path.of("external/WizeStreamExtractor/extractor/src/main/java");

    @Test
    public void drawerModeRetainsYoutubeServiceIdentity() throws Exception {
        final String serviceHelper = readApp("org/schabi/newpipe/util/ServiceHelper.kt");
        final String activity = readApp("org/schabi/newpipe/MainActivity.java");

        assertTrue(serviceHelper.contains("const val YOUTUBE_MUSIC_MODE = \"youtube_music\""));
        assertTrue(serviceHelper.contains("ServiceList.YouTube.serviceInfo.name"));
        assertTrue(activity.contains("ITEM_ID_YOUTUBE_MUSIC"));
        assertTrue(activity.contains("ServiceHelper.setYoutubeMusicMode(this)"));
    }

    @Test
    public void modeUsesMusicHomeSearchAndBackgroundPlayback() throws Exception {
        final String defaultKiosk = readApp(
                "org/schabi/newpipe/fragments/list/kiosk/DefaultKioskFragment.java");
        final String search = readApp(
                "org/schabi/newpipe/fragments/list/search/SearchFragment.java");
        final String list = readApp(
                "org/schabi/newpipe/fragments/list/BaseListFragment.java");
        final String musicExtractor = readExtractor(
                "org/schabi/newpipe/extractor/services/youtube/extractors/"
                        + "YoutubeMusicSongOrVideoInfoItemExtractor.java");

        assertTrue(defaultKiosk.contains("? \"trending_music\""));
        assertTrue(search.contains("YOUTUBE_MUSIC_SONGS_FILTER"));
        assertTrue(list.contains("NavigationHelper.playOnBackgroundPlayer"));
        assertTrue(musicExtractor.contains("? StreamType.AUDIO_STREAM"));
    }

    @Test
    public void modeSeparatesChannelsAndFeedUsingSharedMembership() throws Exception {
        final String subscription = readApp(
                "org/schabi/newpipe/database/subscription/SubscriptionEntity.kt");
        final String manager = readApp(
                "org/schabi/newpipe/local/subscription/SubscriptionManager.kt");
        final String feed = readApp(
                "org/schabi/newpipe/database/feed/dao/FeedDAO.kt");
        final String feedFragment = readApp(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");
        final String sync = readApp(
                "org/schabi/newpipe/sync/SubscriptionSyncModels.kt");

        assertTrue(subscription.contains("YOUTUBE_MODE_MUSIC"));
        assertTrue(manager.contains("existing.youtubeModeMask or"));
        assertTrue(manager.contains("currentYoutubeModeMask"));
        assertTrue(feed.contains("sub.youtube_mode_mask & :youtubeModeMask"));
        assertTrue(feedFragment.contains("SinglePlayQueue(stream.toStreamInfoItem())"));
        assertTrue(sync.contains("val youtubeModeMask: Int"));
    }

    private String readApp(final String relativePath) throws Exception {
        return Files.readString(appSource.resolve(relativePath));
    }

    private String readExtractor(final String relativePath) throws Exception {
        return Files.readString(extractorSource.resolve(relativePath));
    }
}
