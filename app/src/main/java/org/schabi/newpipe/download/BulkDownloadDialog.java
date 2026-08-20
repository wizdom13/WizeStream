package org.schabi.newpipe.download;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.BulkDownloadDialogBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.PermissionHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder;

/** Queues a list of streams using the user's default video or audio quality. */
public final class BulkDownloadDialog extends DialogFragment {
    private static final String ARG_ITEMS = "bulk_download_items";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ArrayList<BulkDownloadItem> items = new ArrayList<>();
    private BulkDownloadDialogBinding binding;
    private AlertDialog dialog;
    private StoredDirectoryHelper videoDirectory;
    private StoredDirectoryHelper audioDirectory;
    private boolean askForSavePath;
    private boolean serviceReady;
    private boolean running;

    @NonNull
    public static BulkDownloadDialog newInstance(@NonNull final List<BulkDownloadItem> items) {
        final BulkDownloadDialog dialog = new BulkDownloadDialog();
        final Bundle arguments = new Bundle();
        arguments.putSerializable(ARG_ITEMS, new ArrayList<>(items));
        dialog.setArguments(arguments);
        return dialog;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Serializable value = requireArguments().getSerializable(ARG_ITEMS);
        if (value instanceof ArrayList<?>) {
            items = (ArrayList<BulkDownloadItem>) value;
        }
        if (!PermissionHelper.checkStoragePermissions(getActivity(),
                PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
            dismiss();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        binding = BulkDownloadDialogBinding.inflate(LayoutInflater.from(requireContext()));
        binding.bulkDownloadSummary.setText(getResources().getQuantityString(
                R.plurals.bulk_download_summary, items.size(), items.size()));
        binding.bulkDownloadNumberFiles.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);

        final String lastType = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(getString(R.string.last_used_download_type),
                        getString(R.string.last_download_type_video_key));
        binding.bulkDownloadMediaType.check(lastType.equals(
                getString(R.string.last_download_type_audio_key))
                ? R.id.bulk_download_audio : R.id.bulk_download_video);

        dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.bulk_download_title)
                .setView(binding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.download, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(serviceReady);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> startBulkDownload());
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        bindDownloadService();
    }

    private void bindDownloadService() {
        final Context context = requireContext();
        final Intent intent = new Intent(context, DownloadManagerService.class);
        context.startService(intent);
        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                final DownloadManagerBinder binder = (DownloadManagerBinder) service;
                videoDirectory = binder.getMainStorageVideo();
                audioDirectory = binder.getMainStorageAudio();
                askForSavePath = binder.askForSavePath();
                serviceReady = true;
                if (dialog != null) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                serviceReady = false;
            }
        }, Context.BIND_AUTO_CREATE);
    }

    private void startBulkDownload() {
        if (running || !serviceReady || items.isEmpty()) {
            return;
        }
        final BulkDownloadMissionFactory.MediaType mediaType =
                binding.bulkDownloadMediaType.getCheckedRadioButtonId()
                        == R.id.bulk_download_audio
                        ? BulkDownloadMissionFactory.MediaType.AUDIO
                        : BulkDownloadMissionFactory.MediaType.VIDEO;
        final StoredDirectoryHelper directory = mediaType
                == BulkDownloadMissionFactory.MediaType.AUDIO ? audioDirectory : videoDirectory;

        if (askForSavePath) {
            showInlineError(R.string.bulk_download_ask_path_error);
            return;
        }
        if (directory == null
                || directory.isDirect() == NewPipeSettings.useStorageAccessFramework(
                        requireContext())
                || directory.isInvalidSafStorage()) {
            showInlineError(R.string.bulk_download_folder_error);
            return;
        }

        final String selectedMediaType = mediaType == BulkDownloadMissionFactory.MediaType.AUDIO
                ? getString(R.string.last_download_type_audio_key)
                : getString(R.string.last_download_type_video_key);
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putString(getString(R.string.last_used_download_type), selectedMediaType)
                .apply();

        running = true;
        setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        binding.bulkDownloadMediaType.setEnabled(false);
        binding.bulkDownloadVideo.setEnabled(false);
        binding.bulkDownloadAudio.setEnabled(false);
        binding.bulkDownloadNumberFiles.setEnabled(false);
        binding.bulkDownloadError.setVisibility(View.GONE);
        binding.bulkDownloadProgressGroup.setVisibility(View.VISIBLE);
        binding.bulkDownloadProgress.setMax(items.size());
        updateProgress(0);

        final int threads = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getInt(getString(R.string.default_download_threads), 3);
        final boolean numberFiles = binding.bulkDownloadNumberFiles.isChecked();
        final int total = items.size();
        final Context appContext = requireContext().getApplicationContext();
        final int[] successes = {0};
        final List<String> failures = new ArrayList<>();

        disposables.add(Observable.range(0, total)
                .concatMapSingle(index -> {
                    final BulkDownloadItem item = items.get(index);
                    return ExtractorHelper.getStreamInfo(item.getServiceId(), item.getUrl(), false)
                            .subscribeOn(Schedulers.io())
                            .map(info -> {
                                BulkDownloadMissionFactory.enqueue(appContext, directory,
                                        info, mediaType, threads, index + 1, total, numberFiles);
                                return BatchResult.success(item);
                            })
                            .onErrorReturn(error -> BatchResult.failure(item, error));
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.error == null) {
                        successes[0]++;
                    } else {
                        failures.add(result.item.getTitle());
                    }
                    updateProgress(successes[0] + failures.size());
                }, error -> finishWithUnexpectedError(error),
                        () -> finishBatch(successes[0], failures)));
    }

    private void updateProgress(final int completed) {
        if (binding == null) {
            return;
        }
        binding.bulkDownloadProgress.setProgress(completed);
        binding.bulkDownloadProgressText.setText(getString(
                R.string.bulk_download_progress, completed, items.size()));
    }

    private void finishBatch(final int successes, @NonNull final List<String> failures) {
        if (!isAdded()) {
            return;
        }
        if (failures.isEmpty()) {
            Toast.makeText(requireContext(), getResources().getQuantityString(
                    R.plurals.bulk_download_queued, successes, successes),
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(), getString(R.string.bulk_download_partial_result,
                    successes, failures.size()), Toast.LENGTH_LONG).show();
            ErrorUtil.createNotification(requireContext(), new ErrorInfo(
                    new IllegalStateException("Failed items: " + String.join(", ", failures)),
                    UserAction.DOWNLOAD_FAILED, "Bulk download"));
        }
        dismissAllowingStateLoss();
    }

    private void finishWithUnexpectedError(@NonNull final Throwable error) {
        if (!isAdded()) {
            return;
        }
        ErrorUtil.showSnackbar(requireActivity(), new ErrorInfo(
                error, UserAction.DOWNLOAD_FAILED, "Bulk download"));
        dismissAllowingStateLoss();
    }

    private void showInlineError(final int message) {
        binding.bulkDownloadError.setText(message);
        binding.bulkDownloadError.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        dialog = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        disposables.dispose();
        super.onDestroy();
    }

    private static final class BatchResult {
        @NonNull
        private final BulkDownloadItem item;
        private final Throwable error;

        private BatchResult(@NonNull final BulkDownloadItem item, final Throwable error) {
            this.item = item;
            this.error = error;
        }

        @NonNull
        private static BatchResult success(@NonNull final BulkDownloadItem item) {
            return new BatchResult(item, null);
        }

        @NonNull
        private static BatchResult failure(@NonNull final BulkDownloadItem item,
                                           @NonNull final Throwable error) {
            return new BatchResult(item, error);
        }
    }
}
