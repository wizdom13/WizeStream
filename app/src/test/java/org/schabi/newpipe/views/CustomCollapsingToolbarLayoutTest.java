package org.schabi.newpipe.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.schabi.newpipe.R;

@RunWith(RobolectricTestRunner.class)
public class CustomCollapsingToolbarLayoutTest {
    @Test
    public void activeVideoPlayerRendersAboveScrollingDetailContent() {
        final Context context = ApplicationProvider.getApplicationContext();
        final View root = LayoutInflater.from(context)
                .inflate(R.layout.fragment_video_detail, null, false);
        final View thumbnailLayer = root.findViewById(R.id.detail_thumbnail_root_layout);
        final FrameLayout playerPlaceholder = root.findViewById(R.id.player_placeholder);

        assertEquals(0.0f, thumbnailLayer.getTranslationZ(), 0.0f);

        final View playerView = new View(context);
        playerPlaceholder.addView(playerView);
        assertTrue(thumbnailLayer.getTranslationZ() > 0.0f);

        playerPlaceholder.removeView(playerView);
        assertEquals(0.0f, thumbnailLayer.getTranslationZ(), 0.0f);
    }
}
