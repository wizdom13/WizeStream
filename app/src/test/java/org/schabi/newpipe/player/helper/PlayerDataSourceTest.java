package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;

import java.util.Map;

public class PlayerDataSourceTest {
    @Test
    public void invalidatesEveryGeneratedYoutubeManifestCache() {
        YoutubeProgressiveDashManifestCreator.getCache().put("progressive", "manifest");
        YoutubeOtfDashManifestCreator.getCache().put("otf", "manifest");
        YoutubePostLiveStreamDvrDashManifestCreator.getCache().put("post-live", "manifest");

        PlayerDataSource.invalidateYoutubeManifestCaches();

        assertEquals(0, YoutubeProgressiveDashManifestCreator.getCache().size());
        assertEquals(0, YoutubeOtfDashManifestCreator.getCache().size());
        assertEquals(0, YoutubePostLiveStreamDvrDashManifestCreator.getCache().size());
    }

    @Test
    public void bilibiliPlaybackUsesCanonicalAntiLeechHeadersWithoutCookies() {
        final Map<String, String> headers = PlayerDataSource.getBilibiliPlaybackHeaders();

        assertEquals("https://www.bilibili.com/", headers.get("Referer"));
        assertEquals("zh-CN,zh;q=0.9", headers.get("Accept-Language"));
        assertFalse(headers.containsKey("Cookie"));
    }
}
