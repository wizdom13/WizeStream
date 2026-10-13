package org.schabi.newpipe.views;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import org.schabi.newpipe.R;

/** Rounds only the collapsed sheet's upper corners without replacing its fragment container. */
public final class MiniPlayerCorners {
    private final View view;
    private final ViewOutlineProvider originalProvider;
    private final boolean originalClip;
    private boolean enabled;
    private float expansion = 1f;
    private final View.OnLayoutChangeListener layoutListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    v.invalidateOutline();
    private final ViewOutlineProvider provider = new ViewOutlineProvider() {
        @Override
        public void getOutline(final View target, final Outline outline) {
            if (target.getWidth() <= 0 || target.getHeight() <= 0) {
                outline.setEmpty();
                return;
            }
            final float radius = enabled ? Math.min(
                    target.getResources().getDimension(R.dimen.mini_player_corner_radius)
                            * (1f - expansion),
                    Math.min(target.getWidth(), target.getHeight()) / 2f) : 0f;
            // Extend the lower rounded corners below the view: the visible bottom stays square.
            // A round-rect outline supports hardware clipping on all supported Android versions.
            outline.setRoundRect(0, 0, target.getWidth(),
                    target.getHeight() + (int) Math.ceil(radius), radius);
        }
    };

    public MiniPlayerCorners(final View view, final boolean enabled) {
        this.view = view;
        this.enabled = enabled;
        originalProvider = view.getOutlineProvider();
        originalClip = view.getClipToOutline();
        view.setOutlineProvider(provider);
        view.addOnLayoutChangeListener(layoutListener);
        updateClip();
    }

    public void setEnabled(final boolean value) {
        enabled = value;
        updateClip();
    }

    public void setExpansionFraction(final float value) {
        expansion = Float.isNaN(value) ? 1f : Math.max(0f, Math.min(1f, value));
        updateClip();
    }

    private void updateClip() {
        view.setClipToOutline(enabled && expansion < 1f);
        view.invalidateOutline();
    }

    public void release() {
        view.removeOnLayoutChangeListener(layoutListener);
        if (view.getOutlineProvider() == provider) {
            view.setOutlineProvider(originalProvider);
            view.setClipToOutline(originalClip);
            view.invalidateOutline();
        }
    }
}
