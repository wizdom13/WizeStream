package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.schabi.newpipe.R;

public class VideoDetailNavigationMapperTest {
    @Test
    public void tabTagsMapToStableNavigationItems() {
        assertEquals(R.id.video_detail_navigation_comments,
                VideoDetailNavigationMapper.getNavigationItemId(
                        VideoDetailNavigationMapper.COMMENTS_TAB_TAG));
        assertEquals(R.id.video_detail_navigation_related,
                VideoDetailNavigationMapper.getNavigationItemId(
                        VideoDetailNavigationMapper.RELATED_TAB_TAG));
        assertEquals(R.id.video_detail_navigation_description,
                VideoDetailNavigationMapper.getNavigationItemId(
                        VideoDetailNavigationMapper.DESCRIPTION_TAB_TAG));
    }

    @Test
    public void navigationItemsMapBackToTabTags() {
        assertEquals(VideoDetailNavigationMapper.COMMENTS_TAB_TAG,
                VideoDetailNavigationMapper.getTabTag(
                        R.id.video_detail_navigation_comments));
        assertEquals(VideoDetailNavigationMapper.RELATED_TAB_TAG,
                VideoDetailNavigationMapper.getTabTag(
                        R.id.video_detail_navigation_related));
        assertEquals(VideoDetailNavigationMapper.DESCRIPTION_TAB_TAG,
                VideoDetailNavigationMapper.getTabTag(
                        R.id.video_detail_navigation_description));
    }

    @Test
    public void unknownDestinationsFailClosed() {
        assertEquals(VideoDetailNavigationMapper.NO_NAVIGATION_ITEM_ID,
                VideoDetailNavigationMapper.getNavigationItemId("UNKNOWN"));
        assertEquals(VideoDetailNavigationMapper.NO_NAVIGATION_ITEM_ID,
                VideoDetailNavigationMapper.getNavigationItemId(null));
        assertNull(VideoDetailNavigationMapper.getTabTag(Integer.MAX_VALUE));
    }
}
