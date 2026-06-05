package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.NewVersionWorker;
import org.schabi.newpipe.R;
import org.schabi.newpipe.update.NewPipeMaterialUpdateRepository;
import org.schabi.newpipe.update.NewPipeMaterialUpdateRepository.VersionComparison;

import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class UpdateSettingsFragment extends BasePreferenceFragment {
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final Preference.OnPreferenceChangeListener updatePreferenceChange = (p, nVal) -> {
        final boolean checkForUpdates = (boolean) nVal;
        defaultPreferences.edit()
                .putBoolean(getString(R.string.update_app_key), checkForUpdates)
                .putBoolean(getString(R.string.update_check_consent_key), true)
                .apply();

        if (checkForUpdates) {
            NewVersionWorker.enqueueNewVersionCheckingWork(requireContext(), false);
        }
        return true;
    };

    private final Preference.OnPreferenceClickListener manualUpdateClick = preference -> {
        Toast.makeText(getContext(), R.string.checking_updates_toast, Toast.LENGTH_SHORT).show();
        final UUID workId = NewVersionWorker.enqueueNewVersionCheckingWork(requireContext(), true);
        observeManualUpdateWork(workId);
        return true;
    };

    private final Preference.OnPreferenceClickListener changelogClick = preference -> {
        Toast.makeText(getContext(), R.string.checking_updates_toast, Toast.LENGTH_SHORT).show();
        disposables.add(Single.fromCallable(() -> NewPipeMaterialUpdateRepository.INSTANCE
                        .formatChangelog(
                                NewPipeMaterialUpdateRepository.INSTANCE.fetchReleases(),
                                getString(R.string.changelog_empty)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showChangelogDialog, throwable -> {
                    final String cachedChangelog = defaultPreferences.getString(
                            getString(R.string.latest_available_changelog_key), "");
                    if (cachedChangelog != null && !cachedChangelog.isBlank()) {
                        showChangelogDialog(cachedChangelog);
                    } else {
                        Toast.makeText(getContext(), R.string.changelog_load_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                }));
        return true;
    };

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        requirePreference(R.string.update_app_key)
                .setOnPreferenceChangeListener(updatePreferenceChange);
        requirePreference(R.string.manual_update_key)
                .setOnPreferenceClickListener(manualUpdateClick);
        requirePreference(R.string.changelog_key)
                .setOnPreferenceClickListener(changelogClick);
        refreshVersionSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshVersionSummaries();
    }

    @Override
    public void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private void refreshVersionSummaries() {
        final Preference latestVersionPreference = requirePreference(
                R.string.latest_available_version_key);
        final String latestVersion = defaultPreferences.getString(
                getString(R.string.latest_available_version_value_key), "");
        if (latestVersion == null || latestVersion.isBlank()) {
            latestVersionPreference.setSummary(R.string.current_version_not_checked);
        } else {
            latestVersionPreference.setSummary(getString(
                    R.string.latest_version_summary_format, latestVersion));
        }

        requirePreference(R.string.installed_app_version_key).setSummary(getString(
                R.string.installed_version_summary_format,
                NewPipeMaterialUpdateRepository.INSTANCE.installedVersionSummary()));
    }

    private void observeManualUpdateWork(@NonNull final UUID workId) {
        WorkManager.getInstance(requireContext())
                .getWorkInfoByIdLiveData(workId)
                .observe(this, workInfo -> {
                    if (workInfo == null || !workInfo.getState().isFinished()) {
                        return;
                    }

                    refreshVersionSummaries();
                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        showManualUpdateResult(workInfo);
                    } else {
                        Toast.makeText(getContext(), R.string.app_update_check_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showManualUpdateResult(@NonNull final WorkInfo workInfo) {
        final String latestVersion = workInfo.getOutputData().getString(
                NewVersionWorker.OUTPUT_LATEST_VERSION);
        final String installedVersion = workInfo.getOutputData().getString(
                NewVersionWorker.OUTPUT_INSTALLED_VERSION);
        final String releaseUrl = workInfo.getOutputData().getString(
                NewVersionWorker.OUTPUT_RELEASE_URL);
        final String comparisonName = workInfo.getOutputData().getString(
                NewVersionWorker.OUTPUT_COMPARISON);

        if (latestVersion == null || latestVersion.isBlank()
                || installedVersion == null || installedVersion.isBlank()) {
            Toast.makeText(getContext(), R.string.app_update_check_failed, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        final VersionComparison comparison = parseComparison(comparisonName);
        if (comparison == VersionComparison.SAME_OR_OLDER) {
            Toast.makeText(getContext(), R.string.app_update_unavailable_toast,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final int messageRes = comparison == VersionComparison.NEWER
                ? R.string.app_update_available_dialog_message
                : R.string.app_update_latest_release_dialog_message;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_update_available_dialog_title)
                .setMessage(getString(messageRes, latestVersion, installedVersion))
                .setPositiveButton(R.string.app_update_open_release, (dialog, which) -> {
                    final String url = releaseUrl == null || releaseUrl.isBlank()
                            ? NewPipeMaterialUpdateRepository.RELEASES_URL : releaseUrl;
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                })
                .setNegativeButton(R.string.app_update_later, null)
                .show();
    }

    @NonNull
    private VersionComparison parseComparison(final String comparisonName) {
        if (comparisonName == null) {
            return VersionComparison.UNKNOWN;
        }
        try {
            return VersionComparison.valueOf(comparisonName);
        } catch (final IllegalArgumentException e) {
            return VersionComparison.UNKNOWN;
        }
    }

    private void showChangelogDialog(@NonNull final String changelog) {
        if (!isAdded()) {
            return;
        }

        final TextView textView = new TextView(requireContext());
        final int padding = (int) (24 * getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding, padding, padding);
        textView.setText(changelog.isBlank() ? getString(R.string.changelog_empty) : changelog);
        textView.setTextIsSelectable(true);

        final ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(textView);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.changelog_title)
                .setView(scrollView)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    public static void askForConsentToUpdateChecks(final Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.check_for_updates))
                .setMessage(context.getString(R.string.auto_update_check_description))
                .setPositiveButton(context.getString(R.string.yes), (d, w) -> {
                    d.dismiss();
                    setAutoUpdateCheckEnabled(context, true);
                })
                .setNegativeButton(R.string.no, (d, w) -> {
                    d.dismiss();
                    // set explicitly to false, since the default is true on previous versions
                    setAutoUpdateCheckEnabled(context, false);
                })
                .show();
    }

    private static void setAutoUpdateCheckEnabled(final Context context, final boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(context.getString(R.string.update_app_key), enabled)
                .putBoolean(context.getString(R.string.update_check_consent_key), true)
                .apply();
    }

    /**
     * Whether the user was asked for consent to automatically check for app updates.
     * @param context the current context
     * @return true if the user was asked for consent, false otherwise
     */
    public static boolean wasUserAskedForConsent(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.update_check_consent_key), false);
    }
}
