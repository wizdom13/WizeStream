package org.schabi.newpipe.util;

import android.graphics.Typeface;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

/** Preserves each layout's original weight when the optional bold setting is disabled. */
public final class MediaTextWeight {
    private MediaTextWeight() { }

    public static void apply(final TextView view) {
        Typeface original = (Typeface) view.getTag(R.id.media_original_typeface);
        if (original == null) {
            original = view.getTypeface() == null ? Typeface.DEFAULT : view.getTypeface();
            view.setTag(R.id.media_original_typeface, original);
        }
        final boolean bold = PreferenceManager.getDefaultSharedPreferences(view.getContext())
                .getBoolean(view.getContext().getString(R.string.bold_media_text_key), false);
        if (bold) {
            view.setTypeface(Typeface.create(original,
                    original.isItalic() ? Typeface.BOLD_ITALIC : Typeface.BOLD));
        } else {
            view.setTypeface(original);
        }
    }
}
