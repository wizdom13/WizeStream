/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AdditionalServicesIntegrationTest {
    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of(".") : Path.of("app");

    @Test
    public void bilibiliAndNiconicoAreVisibleWithDistinctIcons() {
        final List<StreamingService> visibleServices = ServiceHelper.getVisibleServices();

        assertTrue(visibleServices.contains(ServiceList.BiliBili));
        assertTrue(visibleServices.contains(ServiceList.NicoNico));
        assertEquals(R.drawable.ic_bilibili,
                ServiceHelper.getIcon(ServiceList.BiliBili.getServiceId()));
        assertEquals(R.drawable.ic_niconico,
                ServiceHelper.getIcon(ServiceList.NicoNico.getServiceId()));
    }

    @Test
    public void servicesExposeCoreNavigationAndExtractionFactories() throws Exception {
        assertCoreFactories(ServiceList.BiliBili);
        assertCoreFactories(ServiceList.NicoNico);

        assertNotNull(ServiceList.BiliBili.getCommentsLHFactory());
        assertNotNull(ServiceList.BiliBili.getBulletCommentsLHFactory());
        assertNotNull(ServiceList.NicoNico.getCommentsLHFactory());
        assertNotNull(ServiceList.NicoNico.getBulletCommentsLHFactory());
    }

    @Test
    public void appRoutesVisibleServicesThroughDrawerKiosksAndSubscriptions() throws Exception {
        final String helper = read("src/main/java/org/schabi/newpipe/util/ServiceHelper.kt");
        final String activity = read("src/main/java/org/schabi/newpipe/MainActivity.java");
        final String kiosks = read(
                "src/main/java/org/schabi/newpipe/settings/SelectKioskFragment.java");
        final String subscriptions = read(
                "src/main/java/org/schabi/newpipe/local/subscription/SubscriptionFragment.kt");

        assertFalse(helper.contains("TEMPORARILY_HIDDEN_SERVICE_IDS"));
        assertFalse(helper.contains("takeIf(::isServiceVisible)"));
        assertTrue(activity.contains("ServiceHelper.getVisibleServices()"));
        assertTrue(kiosks.contains("ServiceHelper.getVisibleServices()"));
        assertTrue(subscriptions.contains("ServiceHelper.getVisibleServices()"));
    }

    @Test
    public void serviceSpecificTransportAndCachePoliciesAreConfigured() throws Exception {
        final String downloader = read("src/main/java/org/schabi/newpipe/DownloaderImpl.java");
        final String helper = read("src/main/java/org/schabi/newpipe/util/ServiceHelper.kt");
        final String playerDataSource = read(
                "src/main/java/org/schabi/newpipe/player/helper/PlayerDataSource.java");
        final String playbackResolver = read(
                "src/main/java/org/schabi/newpipe/player/resolver/PlaybackResolver.java");

        assertTrue(downloader.contains("BilibiliService.isBiliBiliDownloadUrl(url)"));
        assertTrue(downloader.contains("BilibiliService.getUserAgentHeaders(WWW_REFERER)"));
        assertTrue(playerDataSource.contains(
                ".setDefaultRequestProperties(getBilibiliPlaybackHeaders())"));
        assertTrue(playbackResolver.contains(
                "metadata.getServiceId() == ServiceList.BiliBili.getServiceId()"));
        assertTrue(playbackResolver.contains("getBilibiliProgressiveMediaSourceFactory()"));
        assertTrue(helper.contains("ServiceList.NicoNico.serviceId"));
        assertTrue(helper.contains("TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES)"));
        assertTrue(helper.contains(
                "ServiceList.BiliBili.setSponsorBlockApiSettings(sponsorBlockApiSettings)"));
    }

    private static void assertCoreFactories(final StreamingService service) throws Exception {
        assertSame(service, ServiceHelper.getServiceById(service.getServiceId()));
        assertNotNull(service.getStreamLHFactory());
        assertNotNull(service.getChannelLHFactory());
        assertNotNull(service.getPlaylistLHFactory());
        assertNotNull(service.getSearchQHFactory());
        assertNotNull(service.getKioskList());
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(projectDirectory.resolve(relativePath));
    }
}
