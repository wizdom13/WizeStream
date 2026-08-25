package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.AppBarLayout;

import org.schabi.newpipe.R;

/**
 * App bar used by channel pages to reveal a banner when it becomes available asynchronously.
 *
 * <p>The channel banner starts hidden so the header does not reserve empty space while channel
 * metadata is loading. When a real banner is shown for the first time, this app bar expands once
 * to prevent a restored/stale collapsed offset from keeping the newly loaded banner off-screen.
 * Normal user scrolling is preserved after that first reveal.</p>
 */
public final class ChannelAppBarLayout extends AppBarLayout {
    @Nullable
    private View bannerContainer;
    private boolean bannerWasVisible;

    public ChannelAppBarLayout(@NonNull final Context context) {
        super(context);
    }

    public ChannelAppBarLayout(@NonNull final Context context,
                               @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        bannerContainer = findViewById(R.id.channel_banner_container);
        bannerWasVisible = isBannerVisible();
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top,
                            final int right, final int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        final boolean bannerVisible = isBannerVisible();
        if (shouldExpandForBanner(bannerWasVisible, bannerVisible)) {
            // Run after this layout pass so AppBarLayout has already recomputed its scroll range
            // with the newly visible banner.
            post(() -> setExpanded(true, false));
        }
        bannerWasVisible = bannerVisible;
    }

    static boolean shouldExpandForBanner(final boolean wasVisible, final boolean isVisible) {
        return !wasVisible && isVisible;
    }

    private boolean isBannerVisible() {
        return bannerContainer != null
                && bannerContainer.getVisibility() == View.VISIBLE
                && bannerContainer.getHeight() > 0;
    }
}
