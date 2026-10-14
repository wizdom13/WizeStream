package org.schabi.newpipe.util;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Typeface;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.schabi.newpipe.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MediaTextWeightTest {
    @Test
    public void togglingBoldPreservesItalicAndRestoresOriginalTypeface() {
        final var context = RuntimeEnvironment.getApplication();
        final var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String key = context.getString(R.string.bold_media_text_key);
        final TextView view = new TextView(context);
        final Typeface original = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC);
        view.setTypeface(original);
        preferences.edit().putBoolean(key, false).commit();
        MediaTextWeight.apply(view);
        assertSame(original, view.getTypeface());
        preferences.edit().putBoolean(key, true).commit();
        MediaTextWeight.apply(view);
        assertTrue(view.getTypeface().isBold());
        assertTrue(view.getTypeface().isItalic());
        preferences.edit().putBoolean(key, false).commit();
        MediaTextWeight.apply(view);
        assertSame(original, view.getTypeface());
    }
}
