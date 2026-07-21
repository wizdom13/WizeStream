package org.schabi.newpipe.fragments.detail;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;

final class VideoDetailNavigationMapper {
    static final int NO_NAVIGATION_ITEM_ID = 0;

    static final String COMMENTS_TAB_TAG = "COMMENTS";
    static final String RELATED_TAB_TAG = "NEXT VIDEO";
    static final String DESCRIPTION_TAB_TAG = "DESCRIPTION TAB";

    private VideoDetailNavigationMapper() { }

    @IdRes
    static int getNavigationItemId(@Nullable final String tabTag) {
        if (COMMENTS_TAB_TAG.equals(tabTag)) {
            return R.id.video_detail_navigation_comments;
        } else if (RELATED_TAB_TAG.equals(tabTag)) {
            return R.id.video_detail_navigation_related;
        } else if (DESCRIPTION_TAB_TAG.equals(tabTag)) {
            return R.id.video_detail_navigation_description;
        }
        return NO_NAVIGATION_ITEM_ID;
    }

    @Nullable
    static String getTabTag(@IdRes final int navigationItemId) {
        if (navigationItemId == R.id.video_detail_navigation_comments) {
            return COMMENTS_TAB_TAG;
        } else if (navigationItemId == R.id.video_detail_navigation_related) {
            return RELATED_TAB_TAG;
        } else if (navigationItemId == R.id.video_detail_navigation_description) {
            return DESCRIPTION_TAB_TAG;
        }
        return null;
    }
}
