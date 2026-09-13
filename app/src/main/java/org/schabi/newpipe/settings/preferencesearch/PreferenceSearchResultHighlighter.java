package org.schabi.newpipe.settings.preferencesearch;

import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;

public final class PreferenceSearchResultHighlighter {
    public static final String ARG_KEY = "settings_search_highlight_key";

    private PreferenceSearchResultHighlighter() {
    }

    /**
     * Scroll to and highlight a preference after its fragment has created its view.
     * @param key the preference key to highlight
     * @param fragment the destination fragment with its view ready
     */
    public static void highlight(final String key, final PreferenceFragmentCompat fragment) {
        final View root = fragment.getView();
        if (root == null) {
            return;
        }
        root.post(() -> {
            if (fragment.getView() != root || !fragment.isAdded()) {
                return;
            }
            final Preference preference = fragment.findPreference(key);
            if (preference == null || !preference.isVisible()) {
                return;
            }
            final RecyclerView list = fragment.getListView();
            final RecyclerView.Adapter<?> adapter = list.getAdapter();
            if (!(adapter instanceof PreferenceGroup.PreferencePositionCallback)) {
                return;
            }
            final int position = ((PreferenceGroup.PreferencePositionCallback) adapter)
                    .getPreferenceAdapterPosition(preference);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            list.scrollToPosition(position);
            list.postDelayed(() -> {
                if (fragment.getView() != root || !fragment.isAdded()) {
                    return;
                }
                final RecyclerView.ViewHolder holder =
                        list.findViewHolderForAdapterPosition(position);
                if (holder != null && holder.itemView.getBackground() instanceof RippleDrawable) {
                    final RippleDrawable ripple = (RippleDrawable) holder.itemView.getBackground();
                    ripple.setHotspot(holder.itemView.getWidth() / 2f,
                            holder.itemView.getHeight() / 2f);
                    ripple.setState(new int[]{android.R.attr.state_pressed,
                            android.R.attr.state_enabled});
                    list.postDelayed(() -> ripple.setState(new int[]{}), 1000);
                } else {
                    highlightFallback(fragment, preference);
                }
            }, 200);
        });
    }

    private static void highlightFallback(final PreferenceFragmentCompat fragment,
                                          final Preference preference) {
        final TypedArray colors = fragment.requireContext().obtainStyledAttributes(
                new int[]{android.R.attr.textColorPrimary});
        final int color = colors.getColor(0, 0);
        colors.recycle();
        final Drawable icon = AppCompatResources.getDrawable(fragment.requireContext(),
                R.drawable.ic_play_arrow);
        if (icon == null) {
            return;
        }
        final Drawable oldIcon = preference.getIcon();
        final boolean oldSpaceReserved = preference.isIconSpaceReserved();
        icon.mutate().setTint(color);
        preference.setIcon(icon);
        fragment.scrollToPreference(preference);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            preference.setIcon(oldIcon);
            preference.setIconSpaceReserved(oldSpaceReserved);
        }, 1000);
    }
}
