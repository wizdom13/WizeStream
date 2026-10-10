package org.schabi.newpipe.views;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;

/** Blanks an idle fullscreen player without changing playback or system brightness. */
public final class PlaybackDimmingLayout extends RelativeLayout {
    private final View shade;
    private final Runnable dimTask = this::dim;
    private boolean active;
    private boolean dimmed;
    private boolean swallowingTouch;
    private int swallowingKey = KeyEvent.KEYCODE_UNKNOWN;
    private long delayMillis = 60_000;
    @Nullable
    private Window dimmedWindow;
    private float previousBrightness;
    @Nullable
    private View previousFocus;

    public PlaybackDimmingLayout(@NonNull final Context context,
                                 @Nullable final AttributeSet attributes) {
        super(context, attributes);
        shade = new View(context);
        shade.setBackgroundColor(Color.BLACK);
        shade.setContentDescription(context.getString(R.string.playback_dimming_wake));
        shade.setFocusable(true);
        shade.setFocusableInTouchMode(true);
        shade.setVisibility(GONE);
        shade.setOnClickListener(view -> wakeOnInteraction());
        shade.setElevation(100);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addView(shade, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void configure(final boolean eligible, final long timeoutMillis) {
        if (active == eligible && delayMillis == timeoutMillis) {
            return;
        }
        active = eligible;
        delayMillis = Math.max(60_000, timeoutMillis);
        wake();
        schedule();
    }

    public boolean wakeOnInteraction() {
        final boolean wasDimmed = dimmed;
        wake();
        schedule();
        return wasDimmed;
    }

    private void schedule() {
        removeCallbacks(dimTask);
        if (active && isAttachedToWindow() && isShown() && hasWindowFocus()) {
            postDelayed(dimTask, delayMillis);
        }
    }

    private void dim() {
        if (!active || !isAttachedToWindow() || !isShown() || !hasWindowFocus()) {
            return;
        }
        previousFocus = findFocus();
        dimmed = true;
        shade.setVisibility(VISIBLE);
        shade.bringToFront();
        shade.requestFocus();
        Context owner = getContext();
        while (owner instanceof ContextWrapper && !(owner instanceof Activity)) {
            owner = ((ContextWrapper) owner).getBaseContext();
        }
        if (owner instanceof Activity) {
            dimmedWindow = ((Activity) owner).getWindow();
            final WindowManager.LayoutParams attributes = dimmedWindow.getAttributes();
            previousBrightness = attributes.screenBrightness;
            attributes.screenBrightness = 0.01f;
            dimmedWindow.setAttributes(attributes);
        }
    }

    private void wake() {
        removeCallbacks(dimTask);
        if (!dimmed) {
            return;
        }
        dimmed = false;
        shade.setVisibility(GONE);
        if (dimmedWindow != null) {
            final WindowManager.LayoutParams attributes = dimmedWindow.getAttributes();
            attributes.screenBrightness = previousBrightness;
            dimmedWindow.setAttributes(attributes);
            dimmedWindow = null;
        }
        if (previousFocus != null && previousFocus.isAttachedToWindow()) {
            previousFocus.requestFocus();
        }
        previousFocus = null;
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && wakeOnInteraction()) {
            swallowingKey = event.getKeyCode();
            return true;
        }
        if (event.getKeyCode() == swallowingKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                swallowingKey = KeyEvent.KEYCODE_UNKNOWN;
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            wakeOnInteraction();
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            swallowingTouch = wakeOnInteraction();
        } else if (!swallowingTouch) {
            wakeOnInteraction();
        }
        if (swallowingTouch) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                swallowingTouch = false;
                schedule();
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        wake();
        if (hasFocus) {
            schedule();
        }
    }

    @Override
    protected void onWindowVisibilityChanged(final int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // This callback can run before the constructor has initialized the shade.
        if (shade != null) {
            wake();
            if (visibility == VISIBLE) {
                schedule();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        active = false;
        wake();
        super.onDetachedFromWindow();
    }

    public boolean isDimmed() {
        return dimmed;
    }
}
