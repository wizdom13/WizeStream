package org.schabi.newpipe.views;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FocusOverlayViewTest {
    @Test
    public void playbackContainerHasNoOutlineButItsControlsAndThumbnailDo() {
        try (ActivityController<Activity> activity =
                     Robolectric.buildActivity(Activity.class).setup().visible()) {
            final FrameLayout container = new FrameLayout(activity.get());
            final View surface = new View(activity.get());
            surface.setId(R.id.surfaceView);
            container.addView(surface, new FrameLayout.LayoutParams(200, 100));
            final View button = new View(activity.get());
            container.addView(button, new FrameLayout.LayoutParams(48, 48));
            activity.get().setContentView(container);
            container.layout(10, 20, 310, 220);
            surface.layout(0, 0, 200, 100);
            button.layout(0, 0, 48, 48);
            final FocusOverlayView overlay = new FocusOverlayView(activity.get());
            overlay.setBounds(0, 0, 1000, 1000);
            overlay.setCurrentFocus(container);
            overlay.onTouchModeChanged(false);
            final Canvas playbackCanvas = mock(Canvas.class);
            overlay.draw(playbackCanvas);
            verify(playbackCanvas, never()).drawRect(any(Rect.class), any(Paint.class));

            overlay.setCurrentFocus(button);
            overlay.onTouchModeChanged(false);
            final Canvas controlsCanvas = mock(Canvas.class);
            overlay.draw(controlsCanvas);
            verify(controlsCanvas).drawRect(any(Rect.class), any(Paint.class));

            surface.setVisibility(View.GONE);
            overlay.setCurrentFocus(container);
            overlay.onTouchModeChanged(false);
            final Canvas thumbnailCanvas = mock(Canvas.class);
            overlay.draw(thumbnailCanvas);
            verify(thumbnailCanvas).drawRect(any(Rect.class), any(Paint.class));
        }
    }
}
