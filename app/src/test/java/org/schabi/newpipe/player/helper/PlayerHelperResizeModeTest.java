package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;

import androidx.media3.ui.AspectRatioFrameLayout;

import org.junit.Test;

public class PlayerHelperResizeModeTest {
    private static final String PREFERENCE_KEY = "last_resize_mode";

    @Test
    public void preservesSupportedResizeModes() {
        assertEquals(
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                PlayerHelper.sanitizeResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT));
        assertEquals(
                AspectRatioFrameLayout.RESIZE_MODE_FILL,
                PlayerHelper.sanitizeResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL));
        assertEquals(
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                PlayerHelper.sanitizeResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM));
    }

    @Test
    public void fallsBackToFitForUnsupportedResizeModes() {
        assertEquals(
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                PlayerHelper.sanitizeResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH));
        assertEquals(
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                PlayerHelper.sanitizeResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT));
        assertEquals(
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                PlayerHelper.sanitizeResizeMode(5));
    }

    @Test
    public void repairsUnsupportedStoredResizeMode() {
        final SharedPreferences preferences = mock(SharedPreferences.class);
        final SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(preferences.getInt(PREFERENCE_KEY, AspectRatioFrameLayout.RESIZE_MODE_FIT))
                .thenReturn(5);
        when(preferences.edit()).thenReturn(editor);
        when(editor.putInt(PREFERENCE_KEY, AspectRatioFrameLayout.RESIZE_MODE_FIT))
                .thenReturn(editor);

        assertEquals(
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                PlayerHelper.retrieveResizeModeFromPrefs(preferences, PREFERENCE_KEY));

        verify(editor).putInt(PREFERENCE_KEY, AspectRatioFrameLayout.RESIZE_MODE_FIT);
        verify(editor).apply();
    }

    @Test
    public void leavesSupportedStoredResizeModeUntouched() {
        final SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getInt(PREFERENCE_KEY, AspectRatioFrameLayout.RESIZE_MODE_FIT))
                .thenReturn(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

        assertEquals(
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                PlayerHelper.retrieveResizeModeFromPrefs(preferences, PREFERENCE_KEY));

        verify(preferences, never()).edit();
    }
}
