package org.schabi.newpipe.player.ui;

import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.widget.PopupMenu;

/** Opens player menus only while their anchor belongs to a visible window. */
final class PlayerPopupMenu {
    private PlayerPopupMenu() {
    }

    static View.OnAttachStateChangeListener detachListener(final Runnable onDetach) {
        return new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(final View view) {
            }

            @Override
            public void onViewDetachedFromWindow(final View view) {
                onDetach.run();
            }
        };
    }

    static void dismissAll(final PopupMenu... menus) {
        for (final PopupMenu menu : menus) {
            if (menu != null) {
                menu.dismiss();
            }
        }
    }

    static boolean show(final PopupMenu menu, final View anchor) {
        if (!anchor.isAttachedToWindow() || !anchor.isShown()
                || anchor.getWindowToken() == null) {
            return false;
        }
        try {
            menu.show();
            return true;
        } catch (final WindowManager.BadTokenException exception) {
            // The window can disappear between checking the anchor and opening the popup.
            menu.dismiss();
            return false;
        }
    }
}
