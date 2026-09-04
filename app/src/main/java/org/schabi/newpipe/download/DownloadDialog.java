package org.schabi.newpipe.download;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DownloadDialogBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.AudioTrackAdapter;
import org.schabi.newpipe.util.AudioTrackAdapter.AudioTracksWrapper;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.SecondaryStreamHelper;
import org.schabi.newpipe.util.SimpleOnSeekBarChangeListener;
import org.schabi.newpipe.util.StreamItemAdapter;
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import us.shandian.giga.postprocessing.Mp3OutputOptions;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder;

public class DownloadDialog extends DialogFragment
        implements RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "DialogFragment";
    private static final boolean DEBUG = MainActivity.DEBUG;

    @State
    StreamInfo currentInfo;
    @State
    StreamInfoWrapper<VideoStream> wrappedVideoStreams;
    @State
    StreamInfoWrapper<SubtitlesStream> wrappedSubtitleStreams;
    @State
    AudioTracksWrapper wrappedAudioTracks;
    @State
    int selectedAudioTrackIndex;
    @State
    int selectedVideoIndex; // set in the constructor
    @State
    int selectedAudioIndex = 0; // default to the first item
    @State
    int selectedSubtitleIndex = 0; // default to the first item
    @State
    int selectedAudioOutputIndex = 0;
    @State
    int selectedMp3BitrateIndex = 1;
    @State
    int muxedAudioFallbackVideoIndex = -1;

    private static final int AUDIO_OUTPUT_ORIGINAL = 0;
    private static final int AUDIO_OUTPUT_MP3 = 1;
    private static final int[] MP3_BITRATES = {128, 192, 256, 320};

    private StoredDirectoryHelper mainStorageAudio = null;
    private StoredDirectoryHelper mainStorageVideo = null;
    private DownloadManager downloadManager = null;
    private MenuItem okButton = null;
    private Context context = null;
    private boolean askForSavePath;

    private AudioTrackAdapter audioTrackAdapter;
    private StreamItemAdapter<AudioStream, Stream> audioStreamsAdapter;
    private StreamItemAdapter<VideoStream, AudioStream> videoStreamsAdapter;
    private StreamItemAdapter<SubtitlesStream, Stream> subtitleStreamsAdapter;

    private final CompositeDisposable disposables = new CompositeDisposable();

    private DownloadDialogBinding dialogBinding;

    private SharedPreferences prefs;

    // Preserve output metadata while an external picker is open.
    @State
    String filenameTmp;
    @State
    String mimeTmp;

    private final ActivityResultLauncher<Intent> requestDownloadSaveAsLauncher =
            registerForActivityResult(
                    new StartActivityForResult(), this::requestDownloadSaveAsResult);
    private final ActivityResultLauncher<Intent> requestDownloadPickAudioFolderLauncher =
            registerForActivityResult(
                    new StartActivityForResult(), this::requestDownloadPickAudioFolderResult);
    private final ActivityResultLauncher<Intent> requestDownloadPickVideoFolderLauncher =
            registerForActivityResult(
                    new StartActivityForResult(), this::requestDownloadPickVideoFolderResult);

    /*//////////////////////////////////////////////////////////////////////////
    // Instance creation
    //////////////////////////////////////////////////////////////////////////*/

    public DownloadDialog() {
        // Just an empty default no-arg ctor to keep Fragment.instantiate() happy
        // otherwise InstantiationException will be thrown when fragment is recreated
        // TODO: Maybe use a custom FragmentFactory instead?
    }

    /**
     * Create a new download dialog with the video, audio and subtitle streams from the provided
     * stream info. Video streams and video-only streams will be put into a single list menu,
     * sorted according to their resolution and the default video resolution will be selected.
     *
     * @param context the context to use just to obtain preferences and strings (will not be stored)
     * @param info    the info from which to obtain downloadable streams and other info (e.g. title)
     */
    public DownloadDialog(@NonNull final Context context, @NonNull final StreamInfo info) {
        this.currentInfo = info;
        final DownloadStreamCatalog catalog = DownloadStreamCatalogFactory.create(context, info);
        this.wrappedAudioTracks = catalog.getAudioTracks();
        this.wrappedVideoStreams = catalog.getVideoStreams();
        this.wrappedSubtitleStreams = catalog.getSubtitleStreams();
        this.selectedAudioTrackIndex = catalog.getSelectedAudioTrackIndex();
        this.selectedVideoIndex = catalog.getSelectedVideoIndex();
        this.muxedAudioFallbackVideoIndex = catalog.getMuxedAudioFallbackVideoIndex();
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Android lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }

        if (!PermissionHelper.checkStoragePermissions(getActivity(),
                PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
            dismiss();
            return;
        }

        // context will remain null if dismiss() was called above, allowing to check whether the
        // dialog is being dismissed in onViewCreated()
        context = getContext();

        setStyle(STYLE_NO_TITLE, ThemeHelper.getDialogTheme(context));
        Bridge.restoreInstanceState(this, savedInstanceState);

        this.audioTrackAdapter = new AudioTrackAdapter(wrappedAudioTracks);
        this.subtitleStreamsAdapter = new StreamItemAdapter<>(wrappedSubtitleStreams);
        updateSecondaryStreams();

        final Intent intent = new Intent(context, DownloadManagerService.class);
        context.startService(intent);

        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName cname, final IBinder service) {
                final DownloadManagerBinder mgr = (DownloadManagerBinder) service;

                mainStorageAudio = mgr.getMainStorageAudio();
                mainStorageVideo = mgr.getMainStorageVideo();
                downloadManager = mgr.getDownloadManager();
                askForSavePath = mgr.askForSavePath();

                okButton.setEnabled(true);

                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                // nothing to do
            }
        }, Context.BIND_AUTO_CREATE);
    }

    /**
     * Update the displayed video streams based on the selected audio track.
     */
    private void updateSecondaryStreams() {
        final DownloadStreamAdapters adapters = DownloadStreamAdapterFactory.create(
                context, wrappedAudioTracks, selectedAudioTrackIndex, wrappedVideoStreams,
                muxedAudioFallbackVideoIndex, DEBUG);
        this.videoStreamsAdapter = adapters.getVideoStreams();
        this.audioStreamsAdapter = adapters.getAudioStreams();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreateView() called with: "
                    + "inflater = [" + inflater + "], container = [" + container + "], "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }
        return inflater.inflate(R.layout.download_dialog, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dialogBinding = DownloadDialogBinding.bind(view);
        if (context == null) {
            return; // the dialog is being dismissed, see the call to dismiss() in onCreate()
        }

        dialogBinding.fileName.setText(FilenameUtils.createFilename(getContext(),
                currentInfo.getName()));
        selectedAudioIndex = ListHelper.getDefaultAudioFormat(getContext(),
                getWrappedAudioStreams().getStreamsList());

        selectedSubtitleIndex = getSubtitleIndexBy(subtitleStreamsAdapter.getAll());

        dialogBinding.qualitySpinner.setOnItemSelectedListener(this);
        dialogBinding.audioStreamSpinner.setOnItemSelectedListener(this);
        dialogBinding.audioTrackSpinner.setOnItemSelectedListener(this);
        dialogBinding.audioOutputFormatSpinner.setOnItemSelectedListener(this);
        dialogBinding.mp3BitrateSpinner.setOnItemSelectedListener(this);
        dialogBinding.videoAudioGroup.setOnCheckedChangeListener(this);

        final ArrayAdapter<CharSequence> outputFormatAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.audio_output_format_entries,
                android.R.layout.simple_spinner_item);
        outputFormatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.audioOutputFormatSpinner.setAdapter(outputFormatAdapter);
        dialogBinding.audioOutputFormatSpinner.setSelection(selectedAudioOutputIndex);

        final ArrayAdapter<CharSequence> bitrateAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.mp3_bitrate_entries,
                android.R.layout.simple_spinner_item);
        bitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.mp3BitrateSpinner.setAdapter(bitrateAdapter);
        dialogBinding.mp3BitrateSpinner.setSelection(selectedMp3BitrateIndex);

        initToolbar(dialogBinding.toolbarLayout.toolbar);
        setupDownloadOptions();

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        final int threads = prefs.getInt(getString(R.string.default_download_threads), 3);
        dialogBinding.threadsCount.setText(String.valueOf(threads));
        dialogBinding.threads.setProgress(threads - 1);
        dialogBinding.threads.setOnSeekBarChangeListener(new SimpleOnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@NonNull final SeekBar seekbar,
                                          final int progress,
                                          final boolean fromUser) {
                final int newProgress = progress + 1;
                prefs.edit().putInt(getString(R.string.default_download_threads), newProgress)
                        .apply();
                dialogBinding.threadsCount.setText(String.valueOf(newProgress));
            }
        });

        fetchStreamsSize();
    }

    private void initToolbar(final Toolbar toolbar) {
        if (DEBUG) {
            Log.d(TAG, "initToolbar() called with: toolbar = [" + toolbar + "]");
        }

        toolbar.setTitle(R.string.download_dialog_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.inflateMenu(R.menu.dialog_url);
        toolbar.setNavigationOnClickListener(v -> dismiss());
        toolbar.setNavigationContentDescription(R.string.cancel);

        okButton = toolbar.getMenu().findItem(R.id.okay);
        okButton.setEnabled(false); // disable until the download service connection is done

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.okay) {
                prepareSelectedDownload();
                return true;
            }
            return false;
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    @Override
    public void onDestroyView() {
        dialogBinding = null;
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Video, audio and subtitle spinners
    //////////////////////////////////////////////////////////////////////////*/

    private void fetchStreamsSize() {
        disposables.clear();
        disposables.add(StreamInfoWrapper.fetchMoreInfoForWrapper(wrappedVideoStreams)
                .subscribe(result -> {
                    if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId()
                            == R.id.video_button) {
                        setupVideoSpinner();
                    }
                }, throwable -> ErrorUtil.showSnackbar(context,
                        new ErrorInfo(throwable, UserAction.DOWNLOAD_OPEN_DIALOG,
                                "Downloading video stream size", currentInfo))));
        disposables.add(StreamInfoWrapper.fetchMoreInfoForWrapper(getWrappedAudioStreams())
                .subscribe(result -> {
                    if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId()
                            == R.id.audio_button) {
                        setupAudioSpinner();
                    }
                }, throwable -> ErrorUtil.showSnackbar(context,
                        new ErrorInfo(throwable, UserAction.DOWNLOAD_OPEN_DIALOG,
                                "Downloading audio stream size", currentInfo))));
        disposables.add(StreamInfoWrapper.fetchMoreInfoForWrapper(wrappedSubtitleStreams)
                .subscribe(result -> {
                    if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId()
                            == R.id.subtitle_button) {
                        setupSubtitleSpinner();
                    }
                }, throwable -> ErrorUtil.showSnackbar(context,
                        new ErrorInfo(throwable, UserAction.DOWNLOAD_OPEN_DIALOG,
                                "Downloading subtitle stream size", currentInfo))));
    }

    private void setupAudioTrackSpinner() {
        if (getContext() == null) {
            return;
        }

        dialogBinding.audioTrackSpinner.setAdapter(audioTrackAdapter);
        dialogBinding.audioTrackSpinner.setSelection(selectedAudioTrackIndex);
    }

    private void setupAudioSpinner() {
        if (getContext() == null) {
            return;
        }

        dialogBinding.qualitySpinner.setVisibility(View.GONE);
        setRadioButtonsState(true);
        dialogBinding.audioStreamSpinner.setAdapter(audioStreamsAdapter);
        dialogBinding.audioStreamSpinner.setSelection(selectedAudioIndex);
        dialogBinding.audioStreamSpinner.setVisibility(View.VISIBLE);
        dialogBinding.audioTrackSpinner.setVisibility(
                wrappedAudioTracks.size() > 1 ? View.VISIBLE : View.GONE);
        dialogBinding.audioTrackPresentInVideoText.setText(
                R.string.audio_extracted_from_video_notice);
        dialogBinding.audioTrackPresentInVideoText.setVisibility(
                hasMuxedAudioFallback() ? View.VISIBLE : View.GONE);
        dialogBinding.audioOutputFormatLabel.setVisibility(View.VISIBLE);
        dialogBinding.audioOutputFormatSpinner.setVisibility(View.VISIBLE);
        updateMp3BitrateVisibility();
    }

    private void setupVideoSpinner() {
        if (getContext() == null) {
            return;
        }

        dialogBinding.qualitySpinner.setAdapter(videoStreamsAdapter);
        dialogBinding.qualitySpinner.setSelection(selectedVideoIndex);
        dialogBinding.qualitySpinner.setVisibility(View.VISIBLE);
        setRadioButtonsState(true);
        dialogBinding.audioStreamSpinner.setVisibility(View.GONE);
        hideAudioOutputOptions();
        onVideoStreamSelected();
    }

    private void onVideoStreamSelected() {
        final boolean isVideoOnly = videoStreamsAdapter.getItem(selectedVideoIndex).isVideoOnly();

        dialogBinding.audioTrackPresentInVideoText.setText(R.string.audio_track_present_in_video);
        dialogBinding.audioTrackSpinner.setVisibility(
                isVideoOnly && wrappedAudioTracks.size() > 1 ? View.VISIBLE : View.GONE);
        dialogBinding.audioTrackPresentInVideoText.setVisibility(
                !isVideoOnly && wrappedAudioTracks.size() > 1 ? View.VISIBLE : View.GONE);
    }

    private void setupSubtitleSpinner() {
        if (getContext() == null) {
            return;
        }

        dialogBinding.qualitySpinner.setAdapter(subtitleStreamsAdapter);
        dialogBinding.qualitySpinner.setSelection(selectedSubtitleIndex);
        dialogBinding.qualitySpinner.setVisibility(View.VISIBLE);
        setRadioButtonsState(true);
        dialogBinding.audioStreamSpinner.setVisibility(View.GONE);
        hideAudioOutputOptions();
        dialogBinding.audioTrackSpinner.setVisibility(View.GONE);
        dialogBinding.audioTrackPresentInVideoText.setVisibility(View.GONE);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Activity results
    //////////////////////////////////////////////////////////////////////////*/

    private void requestDownloadPickAudioFolderResult(final ActivityResult result) {
        newPickerResultHandler().handleFolder(result,
                getString(R.string.download_path_audio_key), DownloadManager.TAG_AUDIO,
                filenameTmp, mimeTmp);
    }

    private void requestDownloadPickVideoFolderResult(final ActivityResult result) {
        newPickerResultHandler().handleFolder(result,
                getString(R.string.download_path_video_key), DownloadManager.TAG_VIDEO,
                filenameTmp, mimeTmp);
    }

    private void requestDownloadSaveAsResult(@NonNull final ActivityResult result) {
        newPickerResultHandler().handleSaveAs(result);
    }

    private DownloadPickerResultHandler newPickerResultHandler() {
        return new DownloadPickerResultHandler(requireContext(), downloadManager,
                this::continueSelectedDownload, this::showFailedDialog);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Listeners
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCheckedChanged(final RadioGroup group, @IdRes final int checkedId) {
        if (DEBUG) {
            Log.d(TAG, "onCheckedChanged() called with: "
                    + "group = [" + group + "], checkedId = [" + checkedId + "]");
        }
        boolean flag = true;

        if (checkedId == R.id.audio_button) {
            setupAudioSpinner();
        } else if (checkedId == R.id.video_button) {
            setupVideoSpinner();
        } else if (checkedId == R.id.subtitle_button) {
            setupSubtitleSpinner();
            flag = false;
        }

        dialogBinding.threads.setEnabled(flag);
    }

    @Override
    public void onItemSelected(final AdapterView<?> parent,
                               final View view,
                               final int position,
                               final long id) {
        if (DEBUG) {
            Log.d(TAG, "onItemSelected() called with: "
                    + "parent = [" + parent + "], view = [" + view + "], "
                    + "position = [" + position + "], id = [" + id + "]");
        }

        final int parentId = parent.getId();
        if (parentId == R.id.quality_spinner) {
            final int checkedRadioButtonId = dialogBinding.videoAudioGroup
                    .getCheckedRadioButtonId();
            if (checkedRadioButtonId == R.id.video_button) {
                selectedVideoIndex = position;
                onVideoStreamSelected();
            } else if (checkedRadioButtonId == R.id.subtitle_button) {
                selectedSubtitleIndex = position;
            }
            onItemSelectedSetFileName();
        } else if (parentId == R.id.audio_track_spinner) {
            final boolean trackChanged = selectedAudioTrackIndex != position;
            selectedAudioTrackIndex = position;
            if (trackChanged) {
                updateSecondaryStreams();
                fetchStreamsSize();
            }
        } else if (parentId == R.id.audio_stream_spinner) {
            selectedAudioIndex = position;
            updateMp3BitrateVisibility();
        } else if (parentId == R.id.audio_output_format_spinner) {
            selectedAudioOutputIndex = position;
            updateMp3BitrateVisibility();
        } else if (parentId == R.id.mp3_bitrate_spinner) {
            selectedMp3BitrateIndex = position;
        }
    }

    private void onItemSelectedSetFileName() {
        final String fileName = FilenameUtils.createFilename(getContext(), currentInfo.getName());
        final String prevFileName = Optional.ofNullable(dialogBinding.fileName.getText())
                .map(Object::toString)
                .orElse("");

        if (prevFileName.isEmpty()
                || prevFileName.equals(fileName)
                || prevFileName.startsWith(getString(R.string.caption_file_name, fileName, ""))) {
            // only update the file name field if it was not edited by the user

            final int radioButtonId = dialogBinding.videoAudioGroup
                    .getCheckedRadioButtonId();
            if (radioButtonId == R.id.audio_button || radioButtonId == R.id.video_button) {
                if (!prevFileName.equals(fileName)) {
                    // since the user might have switched between audio and video, the correct
                    // text might already be in place, so avoid resetting the cursor position
                    dialogBinding.fileName.setText(fileName);
                }
            } else if (radioButtonId == R.id.subtitle_button) {
                final String setSubtitleLanguageCode = subtitleStreamsAdapter
                        .getItem(selectedSubtitleIndex).getLanguageTag();
                // this will reset the cursor position, which is bad UX, but it can't be avoided
                dialogBinding.fileName.setText(getString(
                        R.string.caption_file_name, fileName, setSubtitleLanguageCode));
            }
        }
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent) {
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Download
    //////////////////////////////////////////////////////////////////////////*/

    protected void setupDownloadOptions() {
        setRadioButtonsState(false);
        setupAudioTrackSpinner();

        final boolean isVideoStreamsAvailable = videoStreamsAdapter.getCount() > 0;
        final boolean isAudioStreamsAvailable = audioStreamsAdapter.getCount() > 0;
        final boolean isSubtitleStreamsAvailable = subtitleStreamsAdapter.getCount() > 0;

        dialogBinding.audioButton.setVisibility(isAudioStreamsAvailable ? View.VISIBLE
                : View.GONE);
        dialogBinding.videoButton.setVisibility(isVideoStreamsAvailable ? View.VISIBLE
                : View.GONE);
        dialogBinding.subtitleButton.setVisibility(isSubtitleStreamsAvailable
                ? View.VISIBLE : View.GONE);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final String defaultMedia = prefs.getString(getString(R.string.last_used_download_type),
                getString(R.string.last_download_type_video_key));

        if (isVideoStreamsAvailable
                && (defaultMedia.equals(getString(R.string.last_download_type_video_key)))) {
            dialogBinding.videoButton.setChecked(true);
            setupVideoSpinner();
        } else if (isAudioStreamsAvailable
                && (defaultMedia.equals(getString(R.string.last_download_type_audio_key)))) {
            dialogBinding.audioButton.setChecked(true);
            setupAudioSpinner();
        } else if (isSubtitleStreamsAvailable
                && (defaultMedia.equals(getString(R.string.last_download_type_subtitle_key)))) {
            dialogBinding.subtitleButton.setChecked(true);
            setupSubtitleSpinner();
        } else if (isVideoStreamsAvailable) {
            dialogBinding.videoButton.setChecked(true);
            setupVideoSpinner();
        } else if (isAudioStreamsAvailable) {
            dialogBinding.audioButton.setChecked(true);
            setupAudioSpinner();
        } else if (isSubtitleStreamsAvailable) {
            dialogBinding.subtitleButton.setChecked(true);
            setupSubtitleSpinner();
        } else {
            Toast.makeText(getContext(), R.string.no_streams_available_download,
                    Toast.LENGTH_SHORT).show();
            dismiss();
        }
    }

    private void setRadioButtonsState(final boolean enabled) {
        dialogBinding.audioButton.setEnabled(enabled);
        dialogBinding.videoButton.setEnabled(enabled);
        dialogBinding.subtitleButton.setEnabled(enabled);
    }

    private void hideAudioOutputOptions() {
        dialogBinding.audioOutputFormatLabel.setVisibility(View.GONE);
        dialogBinding.audioOutputFormatSpinner.setVisibility(View.GONE);
        dialogBinding.mp3BitrateLabel.setVisibility(View.GONE);
        dialogBinding.mp3BitrateSpinner.setVisibility(View.GONE);
    }

    private void updateMp3BitrateVisibility() {
        if (dialogBinding == null) {
            return;
        }
        final boolean visible = selectedAudioOutputIndex == AUDIO_OUTPUT_MP3
                && audioStreamsAdapter != null
                && selectedAudioIndex >= 0
                && selectedAudioIndex < audioStreamsAdapter.getCount()
                && audioStreamsAdapter.getItem(selectedAudioIndex).getFormat() != MediaFormat.MP3;
        dialogBinding.mp3BitrateLabel.setVisibility(visible ? View.VISIBLE : View.GONE);
        dialogBinding.mp3BitrateSpinner.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private boolean isMp3OutputSelected() {
        return selectedAudioOutputIndex == AUDIO_OUTPUT_MP3;
    }

    private int getSelectedMp3Bitrate() {
        if (selectedMp3BitrateIndex < 0 || selectedMp3BitrateIndex >= MP3_BITRATES.length) {
            return Mp3OutputOptions.DEFAULT_BITRATE_KBPS;
        }
        return MP3_BITRATES[selectedMp3BitrateIndex];
    }

    private StreamInfoWrapper<AudioStream> getWrappedAudioStreams() {
        if (selectedAudioTrackIndex < 0 || selectedAudioTrackIndex >= wrappedAudioTracks.size()) {
            return StreamInfoWrapper.empty();
        }
        return wrappedAudioTracks.getTracksList().get(selectedAudioTrackIndex);
    }

    private boolean hasMuxedAudioFallback() {
        return muxedAudioFallbackVideoIndex >= 0
                && muxedAudioFallbackVideoIndex < wrappedVideoStreams.getStreamsList().size();
    }

    @Nullable
    private VideoStream getMuxedAudioFallbackSource() {
        return hasMuxedAudioFallback()
                ? wrappedVideoStreams.getStreamsList().get(muxedAudioFallbackVideoIndex) : null;
    }

    private int getSubtitleIndexBy(@NonNull final List<SubtitlesStream> streams) {
        final Localization preferredLocalization = NewPipe.getPreferredLocalization();

        int candidate = 0;
        for (int i = 0; i < streams.size(); i++) {
            final Locale streamLocale = streams.get(i).getLocale();

            final boolean languageEquals = streamLocale.getLanguage() != null
                    && preferredLocalization.getLanguageCode() != null
                    && streamLocale.getLanguage()
                    .equals(new Locale(preferredLocalization.getLanguageCode()).getLanguage());
            final boolean countryEquals = streamLocale.getCountry() != null
                    && streamLocale.getCountry().equals(preferredLocalization.getCountryCode());

            if (languageEquals) {
                if (countryEquals) {
                    return i;
                }

                candidate = i;
            }
        }

        return candidate;
    }

    @NonNull
    private String getNameEditText() {
        final String str = Objects.requireNonNull(dialogBinding.fileName.getText()).toString()
                .trim();

        return FilenameUtils.createFilename(context, str.isEmpty() ? currentInfo.getName() : str);
    }

    private void showFailedDialog(@StringRes final int msg) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.general_error)
                .setMessage(msg)
                .setNegativeButton(getString(R.string.ok), null)
                .show();
    }

    private void launchSaveAsPicker(@NonNull final String filename,
                                    @Nullable final String mimeType,
                                    @Nullable final Uri initialPath) {
        NoFileManagerSafeGuard.launchSafe(requestDownloadSaveAsLauncher,
                StoredFileHelper.getNewPicker(context, filename, mimeType, initialPath), TAG,
                context);
    }

    private void prepareSelectedDownload() {
        final StoredDirectoryHelper mainStorage;
        final DownloadOutputPlan outputPlan;
        final String selectedMediaType;

        // first, build the filename and get the output folder (if possible)
        // later, run a very very very large file checking logic

        final String baseFilename = getNameEditText();

        final int checkedRadioButtonId = dialogBinding.videoAudioGroup.getCheckedRadioButtonId();
        if (checkedRadioButtonId == R.id.audio_button) {
            selectedMediaType = getString(R.string.last_download_type_audio_key);
            mainStorage = mainStorageAudio;
            outputPlan = DownloadOutputPlanFactory.forAudio(
                    baseFilename,
                    audioStreamsAdapter.getItem(selectedAudioIndex).getFormat(),
                    getWrappedAudioStreams().getSizeInBytes(selectedAudioIndex),
                    currentInfo.getDuration(),
                    isMp3OutputSelected(),
                    getSelectedMp3Bitrate());
        } else if (checkedRadioButtonId == R.id.video_button) {
            selectedMediaType = getString(R.string.last_download_type_video_key);
            mainStorage = mainStorageVideo;
            outputPlan = DownloadOutputPlanFactory.forVideo(
                    baseFilename,
                    videoStreamsAdapter.getItem(selectedVideoIndex).getFormat(),
                    wrappedVideoStreams.getSizeInBytes(selectedVideoIndex));
        } else if (checkedRadioButtonId == R.id.subtitle_button) {
            selectedMediaType = getString(R.string.last_download_type_subtitle_key);
            mainStorage = mainStorageVideo; // subtitle & video files go together
            outputPlan = DownloadOutputPlanFactory.forSubtitle(
                    baseFilename,
                    subtitleStreamsAdapter.getItem(selectedSubtitleIndex).getFormat(),
                    wrappedSubtitleStreams.getSizeInBytes(selectedSubtitleIndex));
        } else {
            throw new RuntimeException("No stream selected");
        }

        filenameTmp = outputPlan.getFilename();
        mimeTmp = outputPlan.getMimeType();

        final boolean usedConfiguredStorage = new DownloadDestinationCoordinator(
                requireContext(), this::launchSaveAsPicker,
                requestDownloadPickAudioFolderLauncher, requestDownloadPickVideoFolderLauncher,
                this::checkSelectedDownload)
                .prepare(checkedRadioButtonId == R.id.audio_button, mainStorage, outputPlan,
                        askForSavePath);
        if (!usedConfiguredStorage) {
            return;
        }

        // remember the last media type downloaded by the user
        prefs.edit().putString(getString(R.string.last_used_download_type), selectedMediaType)
                .apply();
    }

    private void checkSelectedDownload(final StoredDirectoryHelper mainStorage,
                                       final Uri targetFile,
                                       final String filename,
                                       final String mime) {
        new DownloadStorageCoordinator(requireContext(), downloadManager,
                this::continueSelectedDownload, this::showFailedDialog)
                .check(mainStorage, targetFile, filename, mime);
    }

    private void continueSelectedDownload(@NonNull final StoredFileHelper storage) {
        if (!storage.canWrite()) {
            showFailedDialog(R.string.permission_denied);
            return;
        }

        // check if the selected file has to be overwritten, by simply checking its length
        try {
            if (storage.length() > 0) {
                storage.truncate();
            }
        } catch (final IOException e) {
            Log.e(TAG, "Failed to truncate the file: " + storage.getUri().toString(), e);
            showFailedDialog(R.string.overwrite_failed);
            return;
        }

        final DownloadMissionRequest request;
        final int threads = dialogBinding.threads.getProgress() + 1;

        // more download logic: select muxer, subtitle converter, etc.
        final int checkedRadioButtonId = dialogBinding.videoAudioGroup.getCheckedRadioButtonId();
        if (checkedRadioButtonId == R.id.audio_button) {
            final AudioStream selectedStream = audioStreamsAdapter.getItem(selectedAudioIndex);
            final VideoStream muxedAudioFallbackSource = getMuxedAudioFallbackSource();
            request = DownloadMissionRequestFactory.forAudio(selectedStream,
                    muxedAudioFallbackSource, isMp3OutputSelected(), getSelectedMp3Bitrate(),
                    threads);
        } else if (checkedRadioButtonId == R.id.video_button) {
            final VideoStream selectedStream = videoStreamsAdapter.getItem(selectedVideoIndex);
            final SecondaryStreamHelper<AudioStream> secondary = videoStreamsAdapter
                    .getAllSecondary()
                    .get(wrappedVideoStreams.getStreamsList().indexOf(selectedStream));
            request = DownloadMissionRequestFactory.forVideo(
                    selectedStream,
                    secondary == null ? null : secondary.getStream(),
                    wrappedVideoStreams.getSizeInBytes(selectedStream),
                    secondary == null ? 0 : secondary.getSizeInBytes(),
                    threads);
        } else if (checkedRadioButtonId == R.id.subtitle_button) {
            request = DownloadMissionRequestFactory.forSubtitle(
                    subtitleStreamsAdapter.getItem(selectedSubtitleIndex));
        } else {
            return;
        }

        DownloadManagerService.startMission(context, request.getUrls(), storage,
                request.getKind(), request.getThreads(), currentInfo,
                request.getPostprocessingName(), request.getPostprocessingArguments(),
                request.getNearLength(), request.getRecoveryInfo());

        Toast.makeText(context, getString(R.string.download_has_started),
                Toast.LENGTH_SHORT).show();

        dismiss();
    }
}
