package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.profiles.ProfileIcon;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.profiles.ProfilePolicy;
import org.schabi.newpipe.profiles.ProfileRecord;

import java.util.List;

public final class ProfilesSettingsFragment extends BasePreferenceFragment {
    private static final String KEY_ACTIVE = "profile_active";
    private static final String KEY_NAME = "profile_name";
    private static final String KEY_DESCRIPTION = "profile_description";
    private static final String KEY_ICON = "profile_icon";
    private static final String KEY_CREATE = "profile_create";
    private static final String KEY_DELETE = "profile_delete";

    private ListPreference activePreference;
    private EditTextPreference namePreference;
    private EditTextPreference descriptionPreference;
    private ListPreference iconPreference;
    private Preference deletePreference;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        activePreference = findPreference(KEY_ACTIVE);
        namePreference = findPreference(KEY_NAME);
        descriptionPreference = findPreference(KEY_DESCRIPTION);
        iconPreference = findPreference(KEY_ICON);
        deletePreference = findPreference(KEY_DELETE);
        final Preference createPreference = findPreference(KEY_CREATE);

        configureNonPersistentPreferences();
        configureIconPreference();
        bindListeners(createPreference);
        refreshPreferences();
    }

    private void configureNonPersistentPreferences() {
        activePreference.setPersistent(false);
        namePreference.setPersistent(false);
        descriptionPreference.setPersistent(false);
        iconPreference.setPersistent(false);

        namePreference.setOnBindEditTextListener(editText -> editText.setFilters(
                new InputFilter[] {new InputFilter.LengthFilter(ProfilePolicy.MAX_NAME_LENGTH)}));
        descriptionPreference.setOnBindEditTextListener(editText -> editText.setFilters(
                new InputFilter[] {
                    new InputFilter.LengthFilter(ProfilePolicy.MAX_DESCRIPTION_LENGTH)
                }));
    }

    private void configureIconPreference() {
        final ProfileIcon[] icons = ProfileIcon.values();
        final CharSequence[] entries = new CharSequence[icons.length];
        final CharSequence[] values = new CharSequence[icons.length];
        for (int i = 0; i < icons.length; i++) {
            entries[i] = getString(iconLabel(icons[i]));
            values[i] = icons[i].getKey();
        }
        iconPreference.setEntries(entries);
        iconPreference.setEntryValues(values);
    }

    private void bindListeners(@NonNull final Preference createPreference) {
        activePreference.setOnPreferenceChangeListener((preference, value) -> {
            final String profileId = String.valueOf(value);
            if (!ProfileManager.setActiveProfile(requireContext(), profileId)) {
                return false;
            }
            refreshPreferences();
            final ProfileRecord active = ProfileManager.getActiveProfile(requireContext());
            Toast.makeText(
                    requireContext(),
                    getString(
                            R.string.profile_switched,
                            ProfileManager.getDisplayName(requireContext(), active)),
                    Toast.LENGTH_SHORT
            ).show();
            return true;
        });

        namePreference.setOnPreferenceChangeListener((preference, value) ->
                updateCurrentProfile(String.valueOf(value), null, null));

        descriptionPreference.setOnPreferenceChangeListener((preference, value) ->
                updateCurrentProfile(null, String.valueOf(value), null));

        iconPreference.setOnPreferenceChangeListener((preference, value) ->
                updateCurrentProfile(null, null, String.valueOf(value)));

        createPreference.setOnPreferenceClickListener(preference -> {
            showCreateProfileDialog();
            return true;
        });

        deletePreference.setOnPreferenceClickListener(preference -> {
            showDeleteProfileDialog();
            return true;
        });
    }

    private boolean updateCurrentProfile(final String name,
                                         final String description,
                                         final String iconKey) {
        final ProfileRecord current = ProfileManager.getActiveProfile(requireContext());
        final String nextName = name == null ? current.getName() : name;
        final String nextDescription = description == null
                ? current.getDescription() : description;
        final String nextIcon = iconKey == null ? current.getIconKey() : iconKey;
        final boolean updated = ProfileManager.updateProfile(
                requireContext(),
                current.getId(),
                nextName,
                nextDescription,
                nextIcon
        );
        if (!updated) {
            Toast.makeText(
                    requireContext(),
                    R.string.profile_name_invalid,
                    Toast.LENGTH_SHORT
            ).show();
            return false;
        }
        refreshPreferences();
        return true;
    }

    private void showCreateProfileDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint(R.string.profile_create_hint);
        input.setSingleLine(true);
        input.setFilters(
                new InputFilter[] {
                    new InputFilter.LengthFilter(ProfilePolicy.MAX_NAME_LENGTH)
                });
        final int horizontalPadding = (int) (24 * getResources().getDisplayMetrics().density);
        input.setPadding(horizontalPadding, input.getPaddingTop(),
                horizontalPadding, input.getPaddingBottom());

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_create_dialog_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, null);

        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
                    final ProfileRecord created = ProfileManager.createProfile(
                            requireContext(),
                            input.getText().toString(),
                            "",
                            ProfileIcon.PERSON.getKey()
                    );
                    if (created == null) {
                        input.setError(getString(R.string.profile_name_invalid));
                        return;
                    }
                    dialog.dismiss();
                    refreshPreferences();
                    Toast.makeText(
                            requireContext(),
                            R.string.profile_created,
                            Toast.LENGTH_SHORT
                    ).show();
                }));
        dialog.show();
    }

    private void showDeleteProfileDialog() {
        final ProfileRecord current = ProfileManager.getActiveProfile(requireContext());
        if (current.getId().equals(ProfileManager.DEFAULT_PROFILE_ID)) {
            Toast.makeText(
                    requireContext(),
                    R.string.profile_delete_default_unavailable,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        final String displayName = ProfileManager.getDisplayName(requireContext(), current);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.profile_delete_confirmation_title, displayName))
                .setMessage(R.string.profile_delete_confirmation_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (ProfileManager.deleteProfile(requireContext(), current.getId())) {
                        refreshPreferences();
                        Toast.makeText(
                                requireContext(),
                                R.string.profile_deleted,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .show();
    }

    private void refreshPreferences() {
        final List<ProfileRecord> profiles = ProfileManager.getProfiles(requireContext());
        final CharSequence[] entries = new CharSequence[profiles.size()];
        final CharSequence[] values = new CharSequence[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            entries[i] = ProfileManager.getDisplayName(requireContext(), profiles.get(i));
            values[i] = profiles.get(i).getId();
        }

        final ProfileRecord active = ProfileManager.getActiveProfile(requireContext());
        activePreference.setEntries(entries);
        activePreference.setEntryValues(values);
        activePreference.setValue(active.getId());
        activePreference.setSummary(ProfileManager.getDisplayName(requireContext(), active));

        namePreference.setText(ProfileManager.getDisplayName(requireContext(), active));
        descriptionPreference.setText(active.getDescription());
        descriptionPreference.setSummary(active.getDescription().trim().isEmpty()
                ? getString(R.string.profile_description_summary)
                : active.getDescription());

        iconPreference.setValue(active.getIconKey());
        iconPreference.setSummary(iconPreference.getEntry());

        deletePreference.setEnabled(!active.getId().equals(ProfileManager.DEFAULT_PROFILE_ID));
    }

    private int iconLabel(@NonNull final ProfileIcon icon) {
        switch (icon) {
            case WORK:
                return R.string.profile_icon_work;
            case STUDY:
                return R.string.profile_icon_study;
            case ENTERTAINMENT:
                return R.string.profile_icon_entertainment;
            case MUSIC:
                return R.string.profile_icon_music;
            case FAVORITES:
                return R.string.profile_icon_favorites;
            case PERSON:
            default:
                return R.string.profile_icon_person;
        }
    }
}
