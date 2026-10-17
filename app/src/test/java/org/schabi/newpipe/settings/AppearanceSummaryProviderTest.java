package org.schabi.newpipe.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class AppearanceSummaryProviderTest {
    private ActivityController<AppCompatActivity> controller;
    private AppCompatActivity activity;
    private MockedStatic<DeviceUtils> deviceUtils;

    @Before
    public void setUp() {
        // Isolate device classification from the real application's singleton bootstrap.
        deviceUtils = mockStatic(DeviceUtils.class);
        controller = Robolectric.buildActivity(AppCompatActivity.class);
        activity = controller.get();
        activity.setTheme(R.style.EyeComfortSettingsTheme);
        controller.setup();
    }

    @After
    public void tearDown() {
        try {
            controller.pause().stop().destroy();
        } finally {
            deviceUtils.close();
        }
    }

    @Test
    public void eyeComfortCanOpenAndRecreateAppearanceWithoutSummaryConflict() {
        selectTheme(activity.getString(R.string.eye_comfort_theme_key));
        assertEyeComfortSummary(openAppearance());
        controller.recreate();
        activity = controller.get();
        assertEyeComfortSummary((AppearanceSettingsFragment)
                activity.getSupportFragmentManager().findFragmentByTag("appearance"));
    }

    @Test
    public void otherThemesKeepTheSelectedColorSummaryProvider() {
        selectTheme(activity.getString(R.string.default_theme_value));
        final AppearanceSettingsFragment fragment = openAppearance();
        final ListPreference color = fragment.findPreference(
                activity.getString(R.string.theme_color_key));
        assertTrue(color.isEnabled());
        assertNotNull(color.getSummaryProvider());
        color.setValueIndex(0);
        assertEquals(color.getEntry(), color.getSummary());
    }

    private void selectTheme(final String theme) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putString(activity.getString(R.string.theme_key), theme).commit();
    }

    private AppearanceSettingsFragment openAppearance() {
        final AppearanceSettingsFragment fragment = new AppearanceSettingsFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "appearance").commitNow();
        return fragment;
    }

    private void assertEyeComfortSummary(final AppearanceSettingsFragment fragment) {
        assertNotNull(fragment);
        final ListPreference color = fragment.findPreference(
                activity.getString(R.string.theme_color_key));
        assertFalse(color.isEnabled());
        assertNull(color.getSummaryProvider());
        assertEquals(activity.getString(R.string.eye_comfort_theme_summary), color.getSummary());
    }
}
