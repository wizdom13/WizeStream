package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;

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
}
