package org.schabi.newpipe.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.TextViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.NewVersionWorker;
import org.schabi.newpipe.R;
import org.schabi.newpipe.update.WizeStreamUpdateRepository;
import org.schabi.newpipe.update.WizeStreamUpdateRepository.VersionComparison;
import org.schabi.newpipe.update.UpdateDownloadWorker;
import org.schabi.newpipe.update.UpdateInstallHelper;
import org.schabi.newpipe.util.DeviceUtils;

import java.util.Locale;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class UpdateSettingsFragment extends BasePreferenceFragment {
    private static final int CHANGELOG_PREVIEW_MAX_CHARS = 900;
    private static final int CHANGELOG_PREVIEW_MAX_LINES = 8;
    private static final float UPDATE_DIALOG_MESSAGE_MAX_HEIGHT_FRACTION = 0.43f;
    private static final int DIALOG_CONTENT_HORIZONTAL_PADDING_DP = 24;
    private static final int DIALOG_CONTENT_VERTICAL_PADDING_DP = 8;

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
        disposables.add(Single.fromCallable(() -> WizeStreamUpdateRepository.INSTANCE
                        .formatChangelog(
                                WizeStreamUpdateRepository.INSTANCE.fetchReleases(),
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
                WizeStreamUpdateRepository.INSTANCE.installedVersionSummary()));
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
        final String apkUrl = workInfo.getOutputData().getString(NewVersionWorker.OUTPUT_APK_URL);
        final String apkName = workInfo.getOutputData().getString(NewVersionWorker.OUTPUT_APK_NAME);
        final long apkSize = workInfo.getOutputData().getLong(
                NewVersionWorker.OUTPUT_APK_SIZE, -1L);
        final String changelog = workInfo.getOutputData().getString(
                NewVersionWorker.OUTPUT_CHANGELOG);
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

        showUpdateAvailableDialog(latestVersion, installedVersion, releaseUrl, apkUrl, apkName,
                apkSize, changelog);
    }

    @NonNull
    private String formatChangelogPreview(final String changelog) {
        if (changelog == null || changelog.isBlank()) {
            return getString(R.string.changelog_empty);
        }
        final String[] lines = changelog.trim().split("\\R");
        final StringBuilder preview = new StringBuilder();
        for (final String line : lines) {
            if (preview.length() >= CHANGELOG_PREVIEW_MAX_CHARS
                    || preview.toString().split("\\R", -1).length > CHANGELOG_PREVIEW_MAX_LINES) {
                break;
            }
            if (preview.length() > 0) {
                preview.append('\n');
            }
            preview.append(line);
        }
        if (preview.length() > CHANGELOG_PREVIEW_MAX_CHARS) {
            return preview.substring(0, CHANGELOG_PREVIEW_MAX_CHARS).trim() + "…";
        }
        return preview.toString();
    }

    @NonNull
    private String formatApkSize(final long bytes) {
        if (bytes < 0) {
            return "";
        }
        final String[] units = {"B", "KB", "MB", "GB"};
        double size = bytes;
        int unit = 0;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit++;
        }
        return String.format(Locale.getDefault(), "%.1f %s", size, units[unit]);
    }

    private void showUpdateAvailableDialog(final String latestVersion,
                                           final String installedVersion,
                                           final String releaseUrl,
                                           final String apkUrl,
                                           final String apkName,
                                           final long apkSize,
                                           final String changelog) {
        final StringBuilder message = new StringBuilder(getString(
                R.string.app_update_available_dialog_message, latestVersion, installedVersion));
        message.append("\n\n").append(getString(R.string.app_update_store_source_warning));
        if (apkSize >= 0) {
            message.append("\n\n").append(getString(R.string.app_update_apk_size_format,
                    formatApkSize(apkSize)));
        }
        message.append("\n\n").append(getString(R.string.app_update_changelog_preview_title))
                .append("\n").append(formatChangelogPreview(changelog));

        final boolean hasApk = apkUrl != null && !apkUrl.isBlank();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_update_available_dialog_title)
                .setView(createUpdateMessageView(message))
                .setPositiveButton(hasApk ? R.string.app_update_download_from_github
                        : R.string.app_update_open_release, (dialog, which) -> {
                            if (hasApk) {
                                startApkDownload(apkUrl, apkName, latestVersion, releaseUrl);
                            } else {
                                openReleasePage(releaseUrl);
                            }
                        })
                .setNegativeButton(R.string.app_update_later, null)
                .setNeutralButton(hasApk ? R.string.app_update_full_changelog
                        : R.string.app_update_release_page, (dialog, which) -> {
                            if (hasApk) {
                                showChangelogDialog(changelog == null ? "" : changelog);
                            } else {
                                openReleasePage(releaseUrl);
                            }
                        })
                .show();
    }

    @NonNull
    private ScrollView createUpdateMessageView(@NonNull final CharSequence message) {
        final Context context = requireContext();
        final TextView textView = new TextView(context);
        TextViewCompat.setTextAppearance(textView,
                com.google.android.material.R.style.TextAppearance_MaterialComponents_Body1);
        final int horizontalPadding = DeviceUtils.dpToPx(DIALOG_CONTENT_HORIZONTAL_PADDING_DP,
                context);
        final int verticalPadding = DeviceUtils.dpToPx(DIALOG_CONTENT_VERTICAL_PADDING_DP,
                context);
        textView.setPaddingRelative(horizontalPadding, verticalPadding,
                horizontalPadding, verticalPadding);
        textView.setText(message);

        final int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels
                * UPDATE_DIALOG_MESSAGE_MAX_HEIGHT_FRACTION);
        final ScrollView scrollView = new MaxHeightScrollView(context, maxHeight);
        scrollView.addView(textView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private void openReleasePage(final String releaseUrl) {
        final String url = releaseUrl == null || releaseUrl.isBlank()
                ? WizeStreamUpdateRepository.RELEASES_URL : releaseUrl;
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(requireContext(), R.string.app_update_no_browser,
                        Toast.LENGTH_LONG).show();
            }
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.app_update_no_browser,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startApkDownload(final String apkUrl, final String apkName,
                                  final String version, final String releaseUrl) {
        final UUID workId = UpdateDownloadWorker.enqueue(requireContext(), apkUrl, apkName,
                version);
        final ProgressBar progressBar = new ProgressBar(requireContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);

        final FrameLayout progressContainer = new FrameLayout(requireContext());
        final int horizontalPadding = DeviceUtils.dpToPx(DIALOG_CONTENT_HORIZONTAL_PADDING_DP,
                requireContext());
        final int verticalPadding = DeviceUtils.dpToPx(DIALOG_CONTENT_VERTICAL_PADDING_DP,
                requireContext());
        progressContainer.setPaddingRelative(horizontalPadding, verticalPadding,
                horizontalPadding, verticalPadding);
        progressContainer.addView(progressBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_update_download_progress_title)
                .setMessage(getString(R.string.app_update_download_progress_unknown))
                .setView(progressContainer)
                .setNegativeButton(R.string.cancel, (dialog, which) ->
                        WorkManager.getInstance(requireContext()).cancelWorkById(workId))
                .show();

        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(workId)
                .observe(this, workInfo -> {
                    if (workInfo == null) {
                        return;
                    }
                    final int percent = workInfo.getProgress().getInt(
                            UpdateDownloadWorker.PROGRESS_PERCENT, -1);
                    if (percent >= 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(percent);
                        final long downloadedBytes = workInfo.getProgress().getLong(
                                UpdateDownloadWorker.PROGRESS_BYTES_DOWNLOADED, -1L);
                        final long totalBytes = workInfo.getProgress().getLong(
                                UpdateDownloadWorker.PROGRESS_TOTAL_BYTES, -1L);
                        String message = getString(
                                R.string.app_update_download_progress_message, percent);
                        if (downloadedBytes >= 0 && totalBytes > 0) {
                            message += "\n" + getString(
                                    R.string.app_update_downloaded_size_format,
                                    formatApkSize(downloadedBytes), formatApkSize(totalBytes));
                        }
                        progressDialog.setMessage(message);
                    }
                    if (!workInfo.getState().isFinished()) {
                        return;
                    }
                    progressDialog.dismiss();
                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        showInstallPrompt(workInfo);
                    } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                        showDownloadFailedDialog(releaseUrl);
                    }
                });
    }

    private void showInstallPrompt(@NonNull final WorkInfo workInfo) {
        final String apkPath = workInfo.getOutputData().getString(
                UpdateDownloadWorker.OUTPUT_APK_PATH);
        final String version = workInfo.getOutputData().getString(
                UpdateDownloadWorker.OUTPUT_VERSION);
        if (apkPath == null || apkPath.isBlank()) {
            Toast.makeText(getContext(), R.string.app_update_download_failed, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        final int messageRes = version == null || version.isBlank()
                ? R.string.app_update_download_complete_message_generic
                : R.string.app_update_download_complete_message;
        final String message = version == null || version.isBlank()
                ? getString(messageRes)
                : getString(messageRes, version);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_update_download_complete_title)
                .setMessage(message)
                .setPositiveButton(R.string.app_update_install_now, (dialog, which) -> {
                    if (UpdateInstallHelper.canRequestPackageInstalls(requireContext())) {
                        if (!UpdateInstallHelper.installApk(requireContext(), apkPath)) {
                            Toast.makeText(getContext(), R.string.app_update_download_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        showInstallPermissionDialog();
                    }
                })
                .setNegativeButton(R.string.app_update_later, null)
                .show();
    }

    private void showInstallPermissionDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_update_install_permission_title)
                .setMessage(R.string.app_update_install_permission_message)
                .setPositiveButton(R.string.app_update_open_install_settings, (dialog, which) ->
                        UpdateInstallHelper.openInstallPermissionSettings(requireContext()))
                .setNegativeButton(R.string.app_update_later, null)
                .show();
    }

    private void showDownloadFailedDialog(final String releaseUrl) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_update_download_failed)
                .setPositiveButton(R.string.app_update_open_release, (dialog, which) ->
                        openReleasePage(releaseUrl))
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

    private static final class MaxHeightScrollView extends ScrollView {
        private final int maxHeight;

        private MaxHeightScrollView(@NonNull final Context context, final int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(final int widthMeasureSpec,
                                 final int heightMeasureSpec) {
        final int parentMode = MeasureSpec.getMode(heightMeasureSpec);
        final int parentHeight = MeasureSpec.getSize(heightMeasureSpec);

        final int heightLimit = parentMode == MeasureSpec.UNSPECIFIED
                ? maxHeight
                : Math.min(maxHeight, parentHeight);

        final int cappedMode = parentMode == MeasureSpec.EXACTLY
                && parentHeight <= maxHeight
                ? MeasureSpec.EXACTLY
                : MeasureSpec.AT_MOST;

        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(heightLimit, cappedMode));
        }
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
