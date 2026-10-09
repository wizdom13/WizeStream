package org.schabi.newpipe.player.ui;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.widget.ListPopupWindow;
import androidx.appcompat.widget.PopupMenu;

import org.schabi.newpipe.R;

import java.util.ArrayList;
import java.util.List;

/** Flat player choices avoid AppCompat's cascading-menu anchor cleanup in desktop windows. */
final class PlayerPopupMenu {
    private ListPopupWindow popup;

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

    void dismiss() {
        if (popup != null) {
            final ListPopupWindow previous = popup;
            popup = null;
            previous.dismiss();
        }
    }

    boolean show(final PopupMenu menu, final View anchor, final Runnable onDismiss) {
        dismiss();
        if (menu == null || !anchor.isAttachedToWindow() || !anchor.isShown()
                || anchor.getWindowToken() == null) {
            return false;
        }
        final List<MenuItem> items = new ArrayList<>();
        for (int index = 0; index < menu.getMenu().size(); index++) {
            final MenuItem item = menu.getMenu().getItem(index);
            if (item.isVisible()) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            return false;
        }
        final Context context = new ContextThemeWrapper(anchor.getContext(),
                R.style.PlayerOverlayPopupMenu);
        final ArrayAdapter<MenuItem> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, items) {
            @Override
            public boolean areAllItemsEnabled() {
                return false;
            }

            @Override
            public boolean isEnabled(final int position) {
                return getItem(position).isEnabled();
            }

            @Override
            public View getView(final int position, final View recycled, final ViewGroup parent) {
                final TextView row = (TextView) super.getView(position, recycled, parent);
                row.setText(getItem(position).getTitle());
                row.setEnabled(isEnabled(position));
                return row;
            }
        };
        final ListPopupWindow choices = new ListPopupWindow(context);
        choices.setAnchorView(anchor);
        choices.setModal(true);
        choices.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        choices.setAdapter(adapter);
        final int available = anchor.getRootView().getWidth();
        final int minimum = (int) (180 * context.getResources().getDisplayMetrics().density);
        int width = minimum;
        for (int index = 0; index < adapter.getCount(); index++) {
            final View row = adapter.getView(index, null, new android.widget.FrameLayout(context));
            row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            width = Math.max(width, row.getMeasuredWidth());
        }
        choices.setContentWidth(available > 0 ? Math.min(width, available) : width);
        choices.setOnDismissListener(() -> {
            if (popup == choices) {
                popup = null;
            }
            onDismiss.run();
        });
        choices.setOnItemClickListener((parent, view, position, id) -> {
            final MenuItem selected = items.get(position);
            if (selected.isEnabled()) {
                choices.dismiss();
                menu.getMenu().performIdentifierAction(selected.getItemId(),
                        Menu.FLAG_PERFORM_NO_CLOSE);
            }
        });
        popup = choices;
        try {
            choices.show();
            return true;
        } catch (final WindowManager.BadTokenException exception) {
            // A desktop or popup-player window can disappear after the anchor check.
            dismiss();
            return false;
        }
    }
}
