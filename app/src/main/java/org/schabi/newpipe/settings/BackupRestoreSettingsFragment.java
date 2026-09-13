package org.schabi.newpipe.settings;

import static org.schabi.newpipe.extractor.utils.Utils.isBlank;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.subscription.SubscriptionsImportExportHelper;
import org.schabi.newpipe.settings.export.BackupFileLocator;
import org.schabi.newpipe.settings.export.ImportExportManager;
import org.schabi.newpipe.settings.export.NewPipeCompatibleExportManager;
import org.schabi.newpipe.settings.export.NewPipeDataMigrationManager;
import org.schabi.newpipe.settings.export.ScheduledBackupWorker;
import org.schabi.newpipe.settings.export.TakeoutImportWorker;
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupRestoreSettingsFragment extends BasePreferenceFragment {

    private static final String ZIP_MIME_TYPE = "application/zip";

    private enum MigrationOption {
        SUBSCRIPTIONS,
        HISTORY,
        PLAYLISTS,
        SETTINGS,
        SPONSOR_BLOCK
    }

    private final SimpleDateFormat exportDateFormat =
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    private final ActivityResultLauncher<String[]> requestTakeoutImport =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) {
                    return;
                }
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    TakeoutImportWorker.enqueue(requireContext(), uri);
                    Toast.makeText(requireContext(), R.string.takeout_import_queued,
                            Toast.LENGTH_LONG).show();
                } catch (final Exception error) {
                    showErrorSnackbar(error, "Opening Takeout export");
                }
            });
    private ImportExportManager manager;
    private String importExportDataPathKey;
    private final ActivityResultLauncher<Intent> requestImportPathLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    this::requestImportPathResult);
    private final ActivityResultLauncher<Intent> requestExportPathLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    this::requestExportPathResult);
    private final ActivityResultLauncher<Intent> requestCompatibleExportPathLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    this::requestCompatibleExportPathResult);
    private final ActivityResultLauncher<Intent> requestMigrationPathLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    this::requestMigrationPathResult);
    private SubscriptionsImportExportHelper importExportHelper;
    private NewPipeDataMigrationManager migrationManager;
    private NewPipeCompatibleExportManager compatibleExportManager;
    private final SharedPreferences.OnSharedPreferenceChangeListener backupStatusListener =
            (preferences, key) -> {
                if (isAdded() && getView() != null
                        && (ScheduledBackupWorker.LAST_SUCCESS_KEY.equals(key)
                        || ScheduledBackupWorker.FAILED_KEY.equals(key))) {
                    updateAutomaticBackupSummary();
                }
            };
    private final ActivityResultLauncher<Uri> requestBackupDirectory =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) {
                    return;
                }
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    defaultPreferences.edit()
                            .putString(ScheduledBackupWorker.DIRECTORY_KEY, uri.toString()).apply();
                    ScheduledBackupWorker.schedule(requireContext());
                    updateAutomaticBackupSummary();
                } catch (final SecurityException error) {
                    showErrorSnackbar(error, "Selecting automatic backup folder");
                }
            });


    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        importExportHelper = new SubscriptionsImportExportHelper(this);
    }

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        manager = new ImportExportManager(new BackupFileLocator(requireContext()));
        migrationManager = new NewPipeDataMigrationManager(requireContext());
        compatibleExportManager = new NewPipeCompatibleExportManager(requireContext());

        importExportDataPathKey = getString(R.string.import_export_data_path);

        addPreferencesFromResourceRegistry();
        requirePreference(R.string.takeout_import_key).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.takeout_import_title)
                    .setMessage(R.string.takeout_import_help)
                    .setPositiveButton(R.string.ok, (dialog, which) ->
                            NoFileManagerSafeGuard.launchSafe(requestTakeoutImport,
                                    new String[]{"*/*"}, TAG, requireContext()))
                    .setNegativeButton(R.string.cancel, null).show();
            return true;
        });
        androidx.work.WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData("takeout-import").observe(this, work -> {
                    if (isAdded() && !work.isEmpty()) {
                        final boolean pending = work.stream()
                                .anyMatch(info -> !info.getState().isFinished());
                        requirePreference(R.string.takeout_import_status_key).setSummary(pending
                                ? getString(R.string.takeout_import_queued)
                                : defaultPreferences.getString(TakeoutImportWorker.STATUS,
                                        getString(R.string.takeout_import_idle)));
                    }
                });
        requirePreference(R.string.automatic_backup_directory_key)
                .setOnPreferenceClickListener(preference -> {
                    NoFileManagerSafeGuard.launchSafe(requestBackupDirectory,
                            null, TAG, requireContext());
                    return true;
                });
        requirePreference(R.string.automatic_backup_interval_key)
                .setOnPreferenceChangeListener((preference, value) -> {
                    if (!"off".equals(value) && defaultPreferences.getString(
                            ScheduledBackupWorker.DIRECTORY_KEY, "").isEmpty()) {
                        Toast.makeText(requireContext(), R.string.automatic_backup_choose_folder,
                                Toast.LENGTH_LONG).show();
                        NoFileManagerSafeGuard.launchSafe(requestBackupDirectory,
                                null, TAG, requireContext());
                        return false;
                    }
                    defaultPreferences.edit().putString(ScheduledBackupWorker.INTERVAL_KEY,
                            (String) value).apply();
                    ScheduledBackupWorker.schedule(requireContext());
                    return true;
                });

        final Preference importDataPreference = requirePreference(R.string.import_data);
        importDataPreference.setOnPreferenceClickListener((Preference p) -> {
            NoFileManagerSafeGuard.launchSafe(
                    requestImportPathLauncher,
                    StoredFileHelper.getSystemPicker(requireContext(),
                            ZIP_MIME_TYPE, getImportExportDataUri()),
                    TAG,
                    getContext()
            );

            return true;
        });

        final Preference importCompatiblePreference =
                requirePreference(R.string.import_compatible_data_key);
        importCompatiblePreference.setOnPreferenceClickListener(preference -> {
            NoFileManagerSafeGuard.launchSafe(
                    requestMigrationPathLauncher,
                    StoredFileHelper.getSystemPicker(requireContext(),
                            ZIP_MIME_TYPE, getImportExportDataUri()),
                    TAG,
                    getContext()
            );
            return true;
        });

        final Preference exportDataPreference = requirePreference(R.string.export_data);
        exportDataPreference.setOnPreferenceClickListener((final Preference p) -> {
            NoFileManagerSafeGuard.launchSafe(
                    requestExportPathLauncher,
                    StoredFileHelper.getNewSystemPicker(requireContext(),
                            "WizeStreamData-" + exportDateFormat.format(new Date()) + ".zip",
                            ZIP_MIME_TYPE, getImportExportDataUri()),
                    TAG,
                    getContext()
            );

            return true;
        });

        final Preference exportCompatiblePreference =
                requirePreference(R.string.export_compatible_data_key);
        exportCompatiblePreference.setOnPreferenceClickListener(preference -> {
            NoFileManagerSafeGuard.launchSafe(
                    requestCompatibleExportPathLauncher,
                    StoredFileHelper.getNewSystemPicker(requireContext(),
                            "NewPipeData-" + exportDateFormat.format(new Date()) + ".zip",
                            ZIP_MIME_TYPE, getImportExportDataUri()),
                    TAG,
                    getContext()
            );
            return true;
        });

        final Preference resetSettings = requirePreference(R.string.reset_settings);
        // Resets all settings by deleting shared preference and restarting the app
        // A dialogue will pop up to confirm if user intends to reset all settings
        resetSettings.setOnPreferenceClickListener(preference -> {
            // Show Alert Dialogue
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.reset_all_settings)
                    .setCancelable(true)
                    .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                        // Clears the default SharedPreferences and applies Material defaults.
                        WizeStreamDefaultPreferences.applyBundledDefaults(
                                requireContext(), true);
                        // Restarts the app
                        if (getActivity() == null) {
                            return;
                        }
                        NavigationHelper.restartApp(getActivity());
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                    })
                    .show();
            return true;
        });

        final Preference exportSubsPreference =
                requirePreference(R.string.export_subscriptions_key);
        exportSubsPreference.setOnPreferenceClickListener(reference -> {
            importExportHelper.onExportSelected();
            return true;
        });

        final Preference importSubsPreference =
                requirePreference(R.string.import_subscriptions_key);
        importSubsPreference.setOnPreferenceClickListener(preference -> {
            importExportHelper.onImportPreviousSelected();
            return true;
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        defaultPreferences.registerOnSharedPreferenceChangeListener(backupStatusListener);
        updateAutomaticBackupSummary();
        requirePreference(R.string.takeout_import_status_key).setSummary(
                defaultPreferences.getString(TakeoutImportWorker.STATUS,
                        getString(R.string.takeout_import_idle)));
    }

    @Override
    public void onPause() {
        defaultPreferences.unregisterOnSharedPreferenceChangeListener(backupStatusListener);
        super.onPause();
    }

    private void updateAutomaticBackupSummary() {
        final Preference directory = requirePreference(R.string.automatic_backup_directory_key);
        final String uri = defaultPreferences.getString(ScheduledBackupWorker.DIRECTORY_KEY, "");
        directory.setSummary(uri.isEmpty() ? getString(R.string.automatic_backup_choose_folder)
                : Uri.decode(uri));
        final Preference status = requirePreference(R.string.automatic_backup_status_key);
        final long timestamp = defaultPreferences.getLong(
                ScheduledBackupWorker.LAST_SUCCESS_KEY, 0);
        status.setSummary(defaultPreferences.getBoolean(ScheduledBackupWorker.FAILED_KEY, false)
                ? getString(R.string.automatic_backup_failed)
                : timestamp == 0 ? getString(R.string.automatic_backup_waiting)
                : getString(R.string.automatic_backup_last_success,
                        java.text.DateFormat.getDateTimeInstance().format(new Date(timestamp))));
    }

    private void requestExportPathResult(final ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            // will be saved only on success
            final Uri lastExportDataUri = result.getData().getData();

            final StoredFileHelper file = new StoredFileHelper(
                    requireContext(), result.getData().getData(), ZIP_MIME_TYPE);

            exportDatabase(file, lastExportDataUri);
        }
    }

    private void requestCompatibleExportPathResult(final ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            final Uri exportDataUri = result.getData().getData();
            final StoredFileHelper file = new StoredFileHelper(
                    requireContext(), exportDataUri, ZIP_MIME_TYPE);
            exportNewPipeCompatibleData(file, exportDataUri);
        }
    }

    private void requestImportPathResult(final ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            // will be saved only on success
            final Uri lastImportDataUri = result.getData().getData();

            final StoredFileHelper file = new StoredFileHelper(
                    requireContext(), result.getData().getData(), ZIP_MIME_TYPE);

            showImportConfirmation(file, lastImportDataUri);
        }
    }

    private void requestMigrationPathResult(final ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }
        final Uri importDataUri = result.getData().getData();
        final StoredFileHelper file = new StoredFileHelper(
                requireContext(), importDataUri, ZIP_MIME_TYPE);
        inspectMigrationBackup(file, importDataUri);
    }

    private void inspectMigrationBackup(final StoredFileHelper file, final Uri importDataUri) {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Path stagedDatabase = null;
            try {
                manager.ensureDbDirectoryExists();
                stagedDatabase = manager.stageMigrationDb(file);
                if (stagedDatabase == null) {
                    throw new IOException("The backup does not contain a SQLite database");
                }
                final NewPipeDataMigrationManager.Preview databasePreview =
                        migrationManager.inspect(stagedDatabase);
                final boolean isPipePipe = databasePreview.getSourceApp()
                        == NewPipeDataMigrationManager.SourceApp.PIPEPIPE;
                final Map<String, ?> sourcePreferences = manager.readMigrationPrefs(
                        file, isPipePipe);
                final NewPipeDataMigrationManager.Preview preview = sourcePreferences.isEmpty()
                        ? databasePreview
                        : migrationManager.inspect(stagedDatabase, sourcePreferences);
                final Path readyDatabase = stagedDatabase;
                final Map<String, ?> readyPreferences = sourcePreferences;
                stagedDatabase = null;
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() ->
                            showMigrationConfirmation(readyDatabase, importDataUri,
                                    readyPreferences, preview));
                } else {
                    manager.discardStagedDb(readyDatabase);
                }
            } catch (final Exception e) {
                final Path failedDatabase = stagedDatabase;
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), R.string.migration_invalid_backup,
                                Toast.LENGTH_LONG).show();
                        if (failedDatabase != null) {
                            manager.discardStagedDb(failedDatabase);
                        }
                    });
                } else if (failedDatabase != null) {
                    manager.discardStagedDb(failedDatabase);
                }
            } finally {
                executor.shutdown();
            }
        });
    }

    private void showMigrationConfirmation(
            final Path stagedDatabase,
            final Uri importDataUri,
            final Map<String, ?> sourcePreferences,
            final NewPipeDataMigrationManager.Preview preview) {
        if (!preview.getHasImportableData()) {
            manager.discardStagedDb(stagedDatabase);
            Toast.makeText(requireContext(), R.string.migration_invalid_backup,
                    Toast.LENGTH_LONG).show();
            return;
        }

        final List<String> optionLabels = new ArrayList<>();
        final List<MigrationOption> optionTypes = new ArrayList<>();
        if (preview.getHasSubscriptions()) {
            optionLabels.add(getString(
                    R.string.migration_subscriptions_option,
                    preview.getSubscriptions()));
            optionTypes.add(MigrationOption.SUBSCRIPTIONS);
        }
        if (preview.getHasHistory()) {
            optionLabels.add(getString(
                    R.string.migration_history_option,
                    preview.getHistoryItems(),
                    preview.getProgressItems()));
            optionTypes.add(MigrationOption.HISTORY);
        }
        if (preview.getHasPlaylists()) {
            optionLabels.add(getString(
                    R.string.migration_playlists_option,
                    preview.getPlaylists(),
                    preview.getPlaylistItems()));
            optionTypes.add(MigrationOption.PLAYLISTS);
        }
        if (preview.getHasCompatibleSettings()) {
            optionLabels.add(getString(
                    R.string.migration_settings_option,
                    preview.getCompatibleSettings()));
            optionTypes.add(MigrationOption.SETTINGS);
        }
        if (preview.getHasSponsorBlockSettings()) {
            optionLabels.add(getString(
                    R.string.migration_sponsor_block_option,
                    preview.getSponsorBlockSettings()));
            optionTypes.add(MigrationOption.SPONSOR_BLOCK);
        }
        final boolean[] checkedItems = new boolean[optionLabels.size()];
        java.util.Arrays.fill(checkedItems, true);

        final androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.migration_choose_data_title)
                        .setMultiChoiceItems(
                                optionLabels.toArray(new String[0]),
                                checkedItems,
                                (ignored, which, isChecked) -> checkedItems[which] = isChecked)
                        .setNegativeButton(R.string.cancel, (ignored, which) ->
                                manager.discardStagedDb(stagedDatabase))
                        .setPositiveButton(R.string.migration_import_button, null)
                        .create();
        dialog.setOnCancelListener(ignored -> manager.discardStagedDb(stagedDatabase));
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    boolean importSubscriptions = false;
                    boolean importHistory = false;
                    boolean importPlaylists = false;
                    boolean importSettings = false;
                    boolean importSponsorBlock = false;
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (!checkedItems[i]) {
                            continue;
                        }
                        switch (optionTypes.get(i)) {
                            case SUBSCRIPTIONS:
                                importSubscriptions = true;
                                break;
                            case HISTORY:
                                importHistory = true;
                                break;
                            case PLAYLISTS:
                                importPlaylists = true;
                                break;
                            case SETTINGS:
                                importSettings = true;
                                break;
                            case SPONSOR_BLOCK:
                                importSponsorBlock = true;
                                break;
                            default:
                                break;
                        }
                    }
                    if (!importSubscriptions && !importHistory && !importPlaylists
                            && !importSettings && !importSponsorBlock) {
                        Toast.makeText(requireContext(), R.string.migration_nothing_selected,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    importMigratedData(
                            stagedDatabase,
                            importDataUri,
                            sourcePreferences,
                            new NewPipeDataMigrationManager.Selection(
                                    importHistory,
                                    importPlaylists,
                                    importSettings,
                                    importSponsorBlock,
                                    importSubscriptions));
                }));
        dialog.show();
    }

    private void importMigratedData(
            final Path stagedDatabase,
            final Uri importDataUri,
            final Map<String, ?> sourcePreferences,
            final NewPipeDataMigrationManager.Selection selection) {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                final NewPipeDataMigrationManager.Result migrationResult =
                        migrationManager.importData(
                                stagedDatabase, selection, sourcePreferences);
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        saveLastImportExportDataUri(importDataUri);
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.migration_complete_title)
                                .setMessage(getString(
                                        R.string.migration_complete_message,
                                        migrationResult.getHistoryItems(),
                                        migrationResult.getProgressItems(),
                                        migrationResult.getPlaylists(),
                                        migrationResult.getPlaylistItems(),
                                        migrationResult.getSubscriptions(),
                                        migrationResult.getCompatibleSettings(),
                                        migrationResult.getSponsorBlockSettings(),
                                        migrationResult.getSkippedItems()))
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    });
                }
            } catch (final Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() ->
                            showErrorSnackbar(e, "Migrating NewPipe data"));
                }
            } finally {
                manager.discardStagedDb(stagedDatabase);
                executor.shutdown();
            }
        });
    }

    private void exportDatabase(final StoredFileHelper file, final Uri exportDataUri) {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            //checkpoint before export
            executor.submit(NewPipeDatabase::checkpoint).get();

            final SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(requireContext());
            manager.exportDatabase(preferences, file);

            saveLastImportExportDataUri(exportDataUri); // save export path only on success
            Snackbar.make(getListView(), getString(R.string.backup_exported_with_file,
                    file.getName()), Snackbar.LENGTH_LONG).show();
        } catch (final Exception e) {
            showErrorSnackbar(e, "Exporting database and settings");
        }
    }

    private void exportNewPipeCompatibleData(final StoredFileHelper file,
                                             final Uri exportDataUri) {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                NewPipeDatabase.checkpoint();
                final NewPipeCompatibleExportManager.ExportResult exportResult =
                        compatibleExportManager.export(file);
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        saveLastImportExportDataUri(exportDataUri);
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.export_compatible_data_complete_title)
                                .setMessage(getString(
                                        R.string.export_compatible_data_complete_message,
                                        exportResult.getSubscriptions(),
                                        exportResult.getHistoryItems(),
                                        exportResult.getProgressItems(),
                                        exportResult.getSkippedItems()))
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    });
                }
            } catch (final Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() ->
                            showErrorSnackbar(e, "Exporting NewPipe-compatible data"));
                }
            } finally {
                executor.shutdown();
            }
        });
    }

    private void showImportConfirmation(final StoredFileHelper file, final Uri importDataUri) {
        try {
            final ImportExportManager.BackupContents contents = manager.inspectBackup(file);
            if (!contents.getHasRecognizableBackupData()) {
                Toast.makeText(requireContext(), R.string.backup_invalid_or_empty,
                                Toast.LENGTH_LONG)
                        .show();
                return;
            }
            if (contents.getSource() != ImportExportManager.BackupSource.WIZESTREAM) {
                new MaterialAlertDialogBuilder(requireActivity())
                        .setTitle(R.string.backup_incompatible_title)
                        .setMessage(R.string.backup_incompatible_message)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                return;
            }

            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.import_full_backup_title)
                    .setMessage(getString(R.string.override_current_data) + "\n\n"
                            + getString(R.string.import_full_backup_detected_contents,
                            describeBackupContents(contents)))
                    .setPositiveButton(R.string.import_full_backup_button, (d, id) ->
                            chooseSettingsImport(file, importDataUri, contents))
                    .setNegativeButton(R.string.cancel, (d, id) -> d.cancel())
                    .show();
        } catch (final Exception e) {
            Toast.makeText(requireContext(), R.string.no_valid_zip_file, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private String describeBackupContents(final ImportExportManager.BackupContents contents) {
        final StringBuilder builder = new StringBuilder();
        appendBackupContent(builder, contents.getHasDatabase(), R.string.backup_contents_database);
        appendBackupContent(builder, contents.getHasJsonPreferences(),
                R.string.backup_contents_json_preferences);
        appendBackupContent(builder, contents.getHasSerializedPreferences(),
                R.string.backup_contents_legacy_preferences);
        appendBackupContent(builder, contents.getHasManifest(), R.string.backup_contents_manifest);
        return builder.length() == 0
                ? getString(R.string.backup_contents_none) : builder.toString();
    }

    private void appendBackupContent(final StringBuilder builder, final boolean shouldAppend,
                                     final int stringRes) {
        if (!shouldAppend) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(getString(stringRes));
    }

    private void chooseSettingsImport(final StoredFileHelper file, final Uri importDataUri,
                                      final ImportExportManager.BackupContents contents) {
        final boolean hasJsonPrefs = contents.getHasJsonPreferences();
        if (!hasJsonPrefs && !contents.getHasSerializedPreferences()) {
            importDatabase(file, importDataUri, contents, false);
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.import_settings)
                .setMessage(hasJsonPrefs ? null : requireContext()
                        .getString(R.string.import_settings_vulnerable_format))
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    importDatabase(file, importDataUri, contents, false);
                })
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    importDatabase(file, importDataUri, contents, true);
                })
                .show();
    }

    private void importDatabase(final StoredFileHelper file, final Uri importDataUri,
                                final ImportExportManager.BackupContents contents,
                                final boolean importSettings) {
        final Activity activity = requireActivity();
        final Context context = activity.getApplicationContext();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            final SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(context);
            final Map<String, ?> previousPreferences = new HashMap<>(preferences.getAll());
            ImportExportManager.DatabaseRollback databaseRollback = null;
            Path stagedDatabase = null;

            try {
                manager.ensureDbDirectoryExists();

                final Map<String, ?> importedPreferences;
                if (!importSettings) {
                    importedPreferences = null;
                } else if (contents.getHasJsonPreferences()) {
                    importedPreferences = manager.readJsonPrefs(file);
                } else {
                    importedPreferences = manager.readSerializedPrefs(file);
                }

                if (contents.getHasDatabase()) {
                    stagedDatabase = manager.stageDb(file);
                    if (stagedDatabase == null) {
                        throw new IOException("The backup database could not be staged");
                    }
                    NewPipeDatabase.validateImportDatabase(
                            context, stagedDatabase.getFileName().toString());
                    try {
                        NewPipeDatabase.checkpoint();
                    } catch (final IllegalStateException ignored) {
                        // The database has not been opened during this process.
                    }
                    NewPipeDatabase.close();
                    databaseRollback = manager.replaceDb(stagedDatabase);
                }

                if (importedPreferences != null) {
                    manager.replacePreferences(preferences, importedPreferences);
                    cleanImport(context, preferences);
                }

                if (databaseRollback != null) {
                    manager.finishDbReplacement(databaseRollback);
                }
                activity.runOnUiThread(() -> {
                    if (isAdded()) {
                        finishImport(importDataUri);
                    }
                });
            } catch (final Exception e) {
                try {
                    manager.replacePreferences(preferences, previousPreferences);
                    if (databaseRollback != null) {
                        manager.rollbackDb(databaseRollback);
                    }
                } catch (final Exception rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                activity.runOnUiThread(() -> {
                    if (isAdded()) {
                        showErrorSnackbar(e, "Importing database and settings");
                    }
                });
            } finally {
                if (stagedDatabase != null) {
                    manager.discardStagedDb(stagedDatabase);
                }
                executor.shutdown();
            }
        });
    }

    /**
     * Remove settings that are not supposed to be imported on different devices
     * and reset them to default values.
     * @param context the context used for the import
     * @param prefs the preferences used while running the import
     */
    private void cleanImport(@NonNull final Context context,
                             @NonNull final SharedPreferences prefs) {
        // Check if media tunnelling needs to be disabled automatically,
        // if it was disabled automatically in the imported preferences.
        final String tunnelingKey = context.getString(R.string.disable_media_tunneling_key);
        final String automaticTunnelingKey =
                context.getString(R.string.disabled_media_tunneling_automatically_key);
        // R.string.disable_media_tunneling_key should always be true
        // if R.string.disabled_media_tunneling_automatically_key equals 1,
        // but we double check here just to be sure and to avoid regressions
        // caused by possible later modification of the media tunneling functionality.
        // R.string.disabled_media_tunneling_automatically_key == 0:
        //     automatic value overridden by user in settings
        // R.string.disabled_media_tunneling_automatically_key == -1: not set
        final boolean wasMediaTunnelingDisabledAutomatically =
                prefs.getInt(automaticTunnelingKey, -1) == 1
                        && prefs.getBoolean(tunnelingKey, false);
        if (wasMediaTunnelingDisabledAutomatically) {
            prefs.edit()
                    .putInt(automaticTunnelingKey, -1)
                    .putBoolean(tunnelingKey, false)
                    .apply();
            NewPipeSettings.setMediaTunneling(context);
        }
    }

    /**
     * Save import path and restart app.
     *
     * @param importDataUri The import path to save
     */
    private void finishImport(final Uri importDataUri) {
        // save import path only on success
        saveLastImportExportDataUri(importDataUri);
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.backup_import_succeeded_restart)
                .setCancelable(false)
                .setPositiveButton(R.string.restart, (dialog, which) ->
                        NavigationHelper.restartApp(requireActivity()))
                .show();
    }

    private Uri getImportExportDataUri() {
        final String path = defaultPreferences.getString(importExportDataPathKey, null);
        return isBlank(path) ? null : Uri.parse(path);
    }

    private void saveLastImportExportDataUri(final Uri importExportDataUri) {
        final SharedPreferences.Editor editor = defaultPreferences.edit()
                .putString(importExportDataPathKey, importExportDataUri.toString());
        editor.apply();
    }

    private void showErrorSnackbar(final Throwable e, final String request) {
        ErrorUtil.showSnackbar(this, new ErrorInfo(e, UserAction.DATABASE_IMPORT_EXPORT, request));
    }

    private void createErrorNotification(final Throwable e, final String request) {
        ErrorUtil.createNotification(
                requireContext(),
                new ErrorInfo(e, UserAction.DATABASE_IMPORT_EXPORT, request)
        );
    }
}
