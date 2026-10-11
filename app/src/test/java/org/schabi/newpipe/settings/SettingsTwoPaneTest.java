package org.schabi.newpipe.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.view.View;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;
import org.schabi.newpipe.App;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingsTwoPaneTest {
    private MockedStatic<App> appInstance;

    @Before
    public void provideApplicationForDeviceDetection() {
        // JVM tests use a plain Application to avoid starting services and workers.
        final App app = mock(App.class);
        when(app.getPackageManager()).thenReturn(
                RuntimeEnvironment.getApplication().getPackageManager());
        appInstance = mockStatic(App.class);
        appInstance.when(App::getInstance).thenReturn(app);
    }

    @After
    public void releaseApplicationStub() {
        appInstance.close();
    }

    @Test
    @Config(qualifiers = "w1000dp-h700dp")
    public void wideWindowKeepsCategoriesWhileChangingSettings() {
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup().visible()) {
            final SettingsActivity activity = controller.get();
            final var manager = activity.getSupportFragmentManager();
            manager.executePendingTransactions();
            final MainSettingsFragment categories = (MainSettingsFragment)
                    manager.findFragmentById(R.id.settings_navigation_holder);
            assertNotNull(categories);
            assertTrue(manager.findFragmentById(R.id.settings_fragment_holder)
                    instanceof VideoAudioSettingsFragment);
            final androidx.preference.Preference destination =
                    new androidx.preference.Preference(activity);
            destination.setFragment(AppearanceSettingsFragment.class.getName());
            assertTrue(activity.onPreferenceStartFragment(categories, destination));
            manager.executePendingTransactions();
            assertTrue(manager.findFragmentById(R.id.settings_fragment_holder)
                    instanceof AppearanceSettingsFragment);
            assertEquals(categories, manager.findFragmentById(R.id.settings_navigation_holder));
            assertEquals(0, manager.getBackStackEntryCount());
        }
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    public void narrowWindowRetainsSinglePaneNavigation() {
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup().visible()) {
            final SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertEquals(View.GONE,
                    activity.findViewById(R.id.settings_navigation_holder).getVisibility());
            assertTrue(activity.getSupportFragmentManager()
                    .findFragmentById(R.id.settings_fragment_holder)
                    instanceof MainSettingsFragment);
        }
    }
}
