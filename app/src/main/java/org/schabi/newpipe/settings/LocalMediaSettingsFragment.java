package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.local.media.LocalMediaExclusionStore;

public final class LocalMediaSettingsFragment extends BasePreferenceFragment {
    private LocalMediaExclusionStore exclusionStore;
    private Preference ignoredStatusPreference;
    private Preference clearIgnoredPreference;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();
        exclusionStore = new LocalMediaExclusionStore(requireContext());
        ignoredStatusPreference = requirePreference(R.string.local_media_ignored_status_key);
        clearIgnoredPreference = requirePreference(R.string.local_media_clear_ignored_key);
        clearIgnoredPreference.setOnPreferenceClickListener(preference -> {
            confirmClearIgnoredMedia();
            return true;
        });
        updateIgnoredState();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateIgnoredState();
    }

    private void confirmClearIgnoredMedia() {
        if (exclusionStore.ignoredCount() == 0) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.local_media_clear_ignored_title)
                .setMessage(R.string.local_media_clear_ignored_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear, (dialog, which) -> {
                    exclusionStore.clear();
                    updateIgnoredState();
                })
                .show();
    }

    private void updateIgnoredState() {
        final int count = exclusionStore.ignoredCount();
        ignoredStatusPreference.setSummary(count == 0
                ? getString(R.string.local_media_ignored_none)
                : getResources().getQuantityString(
                        R.plurals.local_media_ignored_count,
                        count,
                        count));
        clearIgnoredPreference.setEnabled(count > 0);
    }
}
