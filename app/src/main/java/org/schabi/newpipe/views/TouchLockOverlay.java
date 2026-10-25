package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Shields playback input while allowing the child unlock button to receive clicks. */
public final class TouchLockOverlay extends FrameLayout {
    public TouchLockOverlay(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_APP_SWITCH:
                return false;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                dispatchConfirmKeyEvent(event);
                return true;
            default:
                // Consume both halves of navigation, playback and custom shortcut presses.
                return true;
        }
    }

    private void dispatchConfirmKeyEvent(final KeyEvent event) {
        if (event.isCanceled()) {
            // Older Android buttons activate on canceled releases too.
            final View focused = findFocus();
            if (focused != null) {
                focused.cancelLongPress();
                focused.setPressed(false);
            }
            return;
        }
        // Android 6 only recognizes Center and Enter as button confirmation keys.
        final KeyEvent confirmEvent = event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER
                ? new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
                        KeyEvent.KEYCODE_ENTER, event.getRepeatCount(), event.getMetaState(),
                        event.getDeviceId(), event.getScanCode(), event.getFlags(),
                        event.getSource())
                : event;
        super.dispatchKeyEvent(confirmEvent);
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent event) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        super.dispatchTouchEvent(event);
        return true;
    }
}
