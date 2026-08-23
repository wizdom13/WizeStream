package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * A {@link ViewPager} that allows enabling or disabling horizontal swipe navigation via touch.
 */
public class SwipeControllableViewPager extends ViewPager {

    private boolean swipeEnabled = true;

    public SwipeControllableViewPager(@NonNull final Context context) {
        super(context);
    }

    public SwipeControllableViewPager(@NonNull final Context context,
                                      @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Enables or disables horizontal swipe paging.
     *
     * @param swipeEnabled whether swiping is enabled
     */
    public void setSwipeEnabled(final boolean swipeEnabled) {
        this.swipeEnabled = swipeEnabled;
    }

    /**
     * @return whether horizontal swipe paging is currently enabled
     */
    public boolean isSwipeEnabled() {
        return swipeEnabled;
    }

    @Override
    public boolean canScrollHorizontally(final int direction) {
        if (!swipeEnabled) {
            return false;
        }
        return super.canScrollHorizontally(direction);
    }

    @Override
    protected boolean canScroll(final View v, final boolean checkV, final int dx,
                                final int x, final int y) {
        if (!swipeEnabled) {
            return false;
        }
        return super.canScroll(v, checkV, dx, x, y);
    }

    @Override
    public boolean executeKeyEvent(@NonNull final KeyEvent event) {
        if (!swipeEnabled) {
            return false;
        }
        return super.executeKeyEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        if (!swipeEnabled) {
            return false;
        }
        try {
            return super.onInterceptTouchEvent(ev);
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        if (!swipeEnabled) {
            return false;
        }
        try {
            return super.onTouchEvent(ev);
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }
}
