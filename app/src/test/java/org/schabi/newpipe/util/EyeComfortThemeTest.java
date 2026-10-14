package org.schabi.newpipe.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigationrail.NavigationRailView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class EyeComfortThemeTest {
    @Test
    public void savedEyeComfortThemeCanInflateMainScreenAndNavigation() {
        final var context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), 0);
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.theme_key),
                        context.getString(R.string.eye_comfort_theme_key))
                .commit();
        ThemeHelper.setTheme(context);
        assertTrue(ThemeHelper.isEyeComfortSelected(context));
        assertNotNull(new BottomNavigationView(context));
        assertNotNull(new NavigationRailView(context));
        assertNotNull(LayoutInflater.from(context).inflate(R.layout.activity_main, null));
    }

    @Test
    public void settingsSecondaryTextResolvesToAColorResource() {
        final var context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                R.style.EyeComfortSettingsTheme);
        final var value = new android.util.TypedValue();
        assertTrue(context.getTheme().resolveAttribute(
                android.R.attr.textColorSecondary, value, true));
        assertTrue(value.resourceId != 0);
        assertNotNull(AppCompatResources.getColorStateList(context, value.resourceId));
        assertNotNull(new BottomNavigationView(context));
    }
}
