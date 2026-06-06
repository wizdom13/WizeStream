package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.util.ServiceHelper;

public class SponsorBlockCategoriesSettingsFragment extends BasePreferenceFragment {
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
            this::onPreferenceChanged;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        addPreferencesFromResourceRegistry();
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
        if (key != null && key.startsWith("sponsor_block_category_")) {
            ServiceHelper.initServices(requireContext());
        }
    }
}
