package org.schabi.newpipe.settings;

import android.os.Bundle;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;

public class MainSettingsFragment extends BasePreferenceFragment {
    public static final boolean DEBUG = MainActivity.DEBUG;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        findPreference("settings_search").setOnPreferenceClickListener(preference -> {
            ((SettingsActivity) requireActivity()).openSettingsSearch();
            return true;
        });

        // Hide debug preferences in RELEASE build variant
        if (!DEBUG) {
            getPreferenceScreen().removePreference(
                    requirePreference(R.string.debug_pref_screen_key));
        }
    }

}
