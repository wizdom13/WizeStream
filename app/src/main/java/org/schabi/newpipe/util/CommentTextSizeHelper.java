package org.schabi.newpipe.util;

import android.content.Context;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

public final class CommentTextSizeHelper {
    static final int DEFAULT_COMMENT_TEXT_SIZE_SP = 14;

    private CommentTextSizeHelper() { }

    public static void applyCommentTextSize(final TextView textView) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                getCommentTextSizeSp(textView.getContext()));
    }

    public static int getCommentTextSizeSp(final Context context) {
        final String value = PreferenceManager.getDefaultSharedPreferences(context).getString(
                context.getString(R.string.comment_text_size_key),
                context.getString(R.string.comment_text_size_medium_key));
        return parseCommentTextSize(value);
    }

    static int parseCommentTextSize(final String value) {
        if (value == null) {
            return DEFAULT_COMMENT_TEXT_SIZE_SP;
        }

        try {
            final int size = Integer.parseInt(value);
            if (size == 12 || size == 14 || size == 16 || size == 18) {
                return size;
            }
        } catch (final NumberFormatException ignored) {
            // Fall back to the default when a restored or synchronized preference is malformed.
        }
        return DEFAULT_COMMENT_TEXT_SIZE_SP;
    }
}
