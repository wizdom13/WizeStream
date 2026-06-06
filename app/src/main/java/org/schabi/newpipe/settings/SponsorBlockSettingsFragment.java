package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

public class SponsorBlockSettingsFragment extends BasePreferenceFragment {
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
            this::onPreferenceChanged;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        addPreferencesFromResourceRegistry();

        requirePreference(R.string.sponsor_block_home_page_key).setOnPreferenceClickListener(
                preference -> {
                    ShareUtils.openUrlInApp(requireContext(),
                            getString(R.string.sponsor_block_homepage_url));
                    return true;
                });
        requirePreference(R.string.sponsor_block_privacy_key).setOnPreferenceClickListener(
                preference -> {
                    ShareUtils.openUrlInApp(requireContext(),
                            getString(R.string.sponsor_block_privacy_policy_url));
                    return true;
                });
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    public void onDestroy() {
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        super.onDestroy();
    }

    private void onPreferenceChanged(@NonNull final SharedPreferences preferences,
                                     @Nullable final String key) {
        if (key != null && key.startsWith("sponsor_block_")) {
            ServiceHelper.initServices(requireContext());
        }
    }
}
