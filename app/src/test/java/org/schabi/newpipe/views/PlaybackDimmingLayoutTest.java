package org.schabi.newpipe.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlaybackDimmingLayoutTest {
    private ActivityController<Activity> activity;
    private PlaybackDimmingLayout layout;

    @Before
    public void setup() {
        activity = Robolectric.buildActivity(Activity.class);
        activity.get().setTheme(R.style.LightTheme);
        activity.setup().visible();
        layout = (PlaybackDimmingLayout) LayoutInflater.from(activity.get())
                .inflate(R.layout.player, null);
        activity.get().setContentView(layout);
        activity.windowFocusChanged(true);
    }

    @After
    public void teardown() {
        activity.pause().stop().destroy();
    }

    @Test
    public void dimsOnlyWhenEnabledAndRestoresBrightnessWhenWoken() {
        final float brightness = activity.get().getWindow().getAttributes().screenBrightness;
        idle(60);
        assertFalse(layout.isDimmed());
        layout.configure(true, 60_000);
        idle(59);
        assertFalse(layout.isDimmed());
        idle(1);
        assertTrue(layout.isDimmed());
        assertEquals(0.01f, activity.get().getWindow().getAttributes().screenBrightness, 0.001f);
        assertTrue(layout.wakeOnInteraction());
        assertFalse(layout.isDimmed());
        assertEquals(brightness,
                activity.get().getWindow().getAttributes().screenBrightness, 0.001f);
        assertFalse(layout.wakeOnInteraction());
    }

    @Test
    public void interactionRestartsDelayAndFirstTouchOnlyWakes() {
        layout.configure(true, 60_000);
        idle(50);
        layout.wakeOnInteraction();
        idle(50);
        assertFalse(layout.isDimmed());
        idle(10);
        assertTrue(layout.isDimmed());
        final MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1, 1, 0);
        final MotionEvent up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 1, 1, 0);
        assertTrue(layout.dispatchTouchEvent(down));
        assertTrue(layout.dispatchTouchEvent(up));
        assertFalse(layout.isDimmed());
        down.recycle();
        up.recycle();
    }

    @Test
    public void remoteWakeConsumesTheEntireFirstKeyPress() {
        layout.configure(true, 60_000);
        idle(60);
        assertTrue(layout.isDimmed());
        assertTrue(layout.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)));
        assertFalse(layout.isDimmed());
        assertTrue(layout.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)));
    }

    @Test
    public void pauseExitAndFocusLossWakeAndCancelDimming() {
        layout.configure(true, 60_000);
        idle(60);
        assertTrue(layout.isDimmed());
        activity.windowFocusChanged(false);
        assertFalse(layout.isDimmed());
        idle(120);
        assertFalse(layout.isDimmed());
        activity.windowFocusChanged(true);
        idle(60);
        assertTrue(layout.isDimmed());
        layout.configure(false, 60_000);
        assertFalse(layout.isDimmed());
        idle(120);
        assertFalse(layout.isDimmed());
    }

    private void idle(final int seconds) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds));
    }
}
