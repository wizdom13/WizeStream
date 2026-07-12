package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryRepository;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

public class SponsorBlockSettingsFragment extends BasePreferenceFragment {
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
            this::onPreferenceChanged;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        addPreferencesFromResourceRegistry();
        SponsorBlockCategoryRepository.migrateBehaviorOnce(requireContext());

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
    public void onStart() {
        super.onStart();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    public void onStop() {
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        super.onStop();
    }

    private void onPreferenceChanged(@NonNull final SharedPreferences preferences,
                                     @Nullable final String key) {
        if (key != null && key.startsWith("sponsor_block_")) {
            ServiceHelper.initServices(requireContext());
        }
    }
}
