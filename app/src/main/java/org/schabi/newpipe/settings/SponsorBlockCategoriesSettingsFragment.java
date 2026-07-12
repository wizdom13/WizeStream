package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import java.util.Objects;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryConfig;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryRepository;
import org.schabi.newpipe.util.ServiceHelper;

public class SponsorBlockCategoriesSettingsFragment extends BasePreferenceFragment {
    private boolean suppressRefresh;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
            this::onPreferenceChanged;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        addPreferencesFromResourceRegistry();
        setupPresetPreference("sponsor_block_activate_all", true);
        setupPresetPreference("sponsor_block_deactivate_all", false);
        requirePreferenceByKey("sponsor_block_reset_defaults")
                .setOnPreferenceClickListener(preference -> {
                    confirmReset();
                    return true;
                });

        for (final SponsorBlockCategoryConfig category : SponsorBlockCategoryConfig.ALL) {
            final SponsorBlockCategoryPreference preference =
                    new SponsorBlockCategoryPreference(requireContext());
            preference.setCategory(category);
            preference.setOnConfigureClickListener(this::openCategorySettings);
            getPreferenceScreen().addPreference(preference);
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SponsorBlockCategoryRepository.migrateBehaviorOnce(requireContext());
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

    @NonNull
    private Preference requirePreferenceByKey(@NonNull final String key) {
        return Objects.requireNonNull(findPreference(key));
    }

    private void setupPresetPreference(@NonNull final String key, final boolean enabled) {
        requirePreferenceByKey(key).setOnPreferenceClickListener(preference -> {
            bulkSetAll(enabled);
            return true;
        });
    }

    private void openCategorySettings(@NonNull final SponsorBlockCategoryConfig category) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.settings_fragment_holder,
                        SponsorBlockCategorySettingsFragment.newInstance(category.id))
                .addToBackStack(null)
                .commit();
    }

    private void bulkSetAll(final boolean enabled) {
        suppressRefresh = true;
        try {
            SponsorBlockCategoryRepository.setAllEnabled(requireContext(), enabled);
            updateCategoryRows();
        } finally {
            suppressRefresh = false;
        }
        ServiceHelper.initServices(requireContext());
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sponsor_block_reset_categories_dialog_title)
                .setMessage(R.string.sponsor_block_reset_categories_dialog_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reset, (dialog, which) -> {
                    suppressRefresh = true;
                    try {
                        SponsorBlockCategoryRepository.resetDefaults(requireContext());
                        updateCategoryRows();
                    } finally {
                        suppressRefresh = false;
                    }
                    ServiceHelper.initServices(requireContext());
                })
                .show();
    }

    private void updateCategoryRows() {
        for (final SponsorBlockCategoryConfig category : SponsorBlockCategoryConfig.ALL) {
            final Preference preference = findPreference(getString(category.enabledKeyResId));
            if (preference instanceof SponsorBlockCategoryPreference) {
                ((SponsorBlockCategoryPreference) preference).setChecked(
                        SponsorBlockCategoryRepository.isEnabled(requireContext(), category));
            }
        }
    }

    private void onPreferenceChanged(@NonNull final SharedPreferences preferences,
                                     @Nullable final String key) {
        if (!suppressRefresh && key != null
                && key.startsWith("sponsor_block_category_")) {
            ServiceHelper.initServices(requireContext());
        }
    }
}
