package org.schabi.newpipe.player.ui;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.TooltipCompat;

/** Prevent native tooltip windows from using the embedded player's window token in DeX. */
final class PlayerTooltips {
    private PlayerTooltips() {
    }

    static void disable(final View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        // Clearing the tooltip also cancels any pending native hover-tooltip callback.
        // Keep content descriptions, focus, click, and long-click actions intact.
        TooltipCompat.setTooltipText(view, null);
        view.setOnHoverListener((hovered, event) -> {
            TooltipCompat.setTooltipText(hovered, null);
            return false;
        });
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                disable(group.getChildAt(index));
            }
        }
    }
}
