/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Shows a theme-aware splash frame after the platform splash and fades it out
 * once the activity has produced its first draw.
 */
public final class ThemedSplashOverlay extends FrameLayout {
    private static final long FADE_DURATION_MILLIS = 180L;
    private static boolean shownInProcess;

    public ThemedSplashOverlay(@NonNull final Context context) {
        super(context);
    }

    public ThemedSplashOverlay(@NonNull final Context context,
                               @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public ThemedSplashOverlay(@NonNull final Context context,
                               @Nullable final AttributeSet attrs,
                               final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (shownInProcess) {
            setVisibility(View.GONE);
            return;
        }
        shownInProcess = true;

        final ViewTreeObserver observer = getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (getViewTreeObserver().isAlive()) {
                    getViewTreeObserver().removeOnPreDrawListener(this);
                }
                postOnAnimation(() -> animate()
                        .alpha(0f)
                        .setDuration(FADE_DURATION_MILLIS)
                        .withEndAction(() -> {
                            setVisibility(View.GONE);
                            setAlpha(1f);
                        })
                        .start());
                return true;
            }
        });
    }
}
