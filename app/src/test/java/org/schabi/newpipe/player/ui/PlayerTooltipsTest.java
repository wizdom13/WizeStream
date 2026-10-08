package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33})
public class PlayerTooltipsTest {
    @Test
    public void disablesNestedTooltipsWithoutRemovingPlayerActions() {
        try (ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class)
                .setup()) {
            final FrameLayout root = new FrameLayout(activity.get());
            final FrameLayout controls = new FrameLayout(activity.get());
            final ImageButton fullscreen = new ImageButton(activity.get());
            final AtomicInteger actions = new AtomicInteger();
            androidx.appcompat.widget.TooltipCompat.setTooltipText(fullscreen, "Fullscreen");
            fullscreen.setContentDescription("Fullscreen");
            fullscreen.setFocusable(true);
            fullscreen.setOnClickListener(view -> actions.incrementAndGet());
            fullscreen.setOnLongClickListener(view -> {
                actions.addAndGet(10);
                return true;
            });
            controls.addView(fullscreen);
            root.addView(controls);
            activity.get().setContentView(root);

            PlayerTooltips.disable(root);

            if (android.os.Build.VERSION.SDK_INT >= 26) {
                assertNull(fullscreen.getTooltipText());
            }
            assertEquals("Fullscreen", fullscreen.getContentDescription());
            assertTrue(fullscreen.isFocusable());
            assertEquals(View.VISIBLE, fullscreen.getVisibility());
            fullscreen.performClick();
            fullscreen.performLongClick();
            assertEquals(11, actions.get());
        }
    }
}
