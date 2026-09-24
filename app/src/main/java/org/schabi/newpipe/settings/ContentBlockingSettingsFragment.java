package org.schabi.newpipe.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.AiSListRepository;
import org.schabi.newpipe.util.AiSListSyncWorker;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.ContentBlockingHelper.Entry;
import org.schabi.newpipe.util.ServiceHelper;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class ContentBlockingSettingsFragment extends BasePreferenceFragment {
    private Preference blockedChannelsPreference;
    private Preference blockedVideosPreference;
    private EditTextPreference blockedKeywordsPreference;
    private SwitchPreferenceCompat aiSListEnabledPreference;
    private Preference aiSListUpdatePreference;
    private Preference aiSListStatusPreference;
    private Preference aiSListWarnBehaviorPreference;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        blockedKeywordsPreference = requirePreference(R.string.blocked_keywords_key);
        blockedChannelsPreference = requirePreference(R.string.manage_blocked_channels_key);
        blockedVideosPreference = requirePreference(R.string.manage_blocked_videos_key);
        aiSListEnabledPreference = requirePreference(R.string.aislist_enabled_key);
        aiSListUpdatePreference = requirePreference(R.string.aislist_update_key);
        aiSListStatusPreference = requirePreference(R.string.aislist_status_key);
        aiSListWarnBehaviorPreference = requirePreference(R.string.aislist_warn_behavior_key);

        requirePreference(R.string.hide_members_only_videos_key)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    ServiceHelper.setHideMembersOnlyVideos((Boolean) newValue);
                    return true;
                });
        blockedKeywordsPreference.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editText.setMinLines(4);
        });
        blockedKeywordsPreference.setOnPreferenceChangeListener((preference, value) -> {
            final String proposed = value == null ? "" : value.toString();
            final String sanitized = ContentBlockingHelper.sanitizeKeywords(proposed);
            preference.setSummary(keywordSummary(sanitized));
            if (!sanitized.equals(proposed)) {
                blockedKeywordsPreference.setText(sanitized);
                return false;
            }
            return true;
        });
        blockedChannelsPreference.setOnPreferenceClickListener(preference -> {
            showEntries(false);
            return true;
        });
        blockedVideosPreference.setOnPreferenceClickListener(preference -> {
            showEntries(true);
            return true;
        });
        aiSListEnabledPreference.setOnPreferenceChangeListener((preference, value) -> {
            final boolean enabled = Boolean.TRUE.equals(value);
            aiSListUpdatePreference.setEnabled(enabled);
            aiSListWarnBehaviorPreference.setEnabled(enabled);
            final UUID workId = AiSListSyncWorker.setEnabled(requireContext(), enabled);
            if (workId != null) {
                observeAiSListUpdate(workId);
            }
            return true;
        });
        aiSListUpdatePreference.setOnPreferenceClickListener(preference -> {
            observeAiSListUpdate(AiSListSyncWorker.enqueueImmediateSync(requireContext()));
            return true;
        });
        requirePreference(R.string.aislist_about_key)
                .setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(AiSListRepository.PROJECT_URL)));
                    return true;
                });
        requirePreference(R.string.clear_blocked_content_key)
                .setOnPreferenceClickListener(preference -> {
                    confirmClearAll();
                    return true;
                });
        updateSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummaries();
    }

    private void showEntries(final boolean videos) {
        final List<Entry> entries = videos
                ? ContentBlockingHelper.getBlockedVideos(requireContext())
                : ContentBlockingHelper.getBlockedChannels(requireContext());
        if (entries.isEmpty()) {
            Toast.makeText(requireContext(), R.string.blocked_content_empty,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] labels = entries.stream().map(Entry::getLabel).toArray(String[]::new);
        final boolean[] checked = new boolean[entries.size()];
        java.util.Arrays.fill(checked, true);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(videos ? R.string.manage_blocked_videos_title
                        : R.string.manage_blocked_channels_title)
                .setMultiChoiceItems(labels, checked,
                        (ignored, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.clear, null)
                .setPositiveButton(R.string.ok, (ignored, which) -> {
                    final List<Entry> kept = new ArrayList<>();
                    for (int index = 0; index < entries.size(); index++) {
                        if (checked[index]) {
                            kept.add(entries.get(index));
                        }
                    }
                    saveEntries(videos, kept);
                })
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(view -> {
                    saveEntries(videos, List.of());
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void observeAiSListUpdate(@NonNull final UUID workId) {
        aiSListUpdatePreference.setEnabled(false);
        aiSListUpdatePreference.setSummary(R.string.aislist_updating);
        WorkManager.getInstance(requireContext())
                .getWorkInfoByIdLiveData(workId)
                .observe(this, workInfo -> {
                    if (workInfo == null || !workInfo.getState().isFinished()) {
                        return;
                    }
                    aiSListUpdatePreference.setEnabled(aiSListEnabledPreference.isChecked());
                    aiSListUpdatePreference.setSummary(R.string.aislist_update_summary);
                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        final int blockCount = workInfo.getOutputData()
                                .getInt(AiSListSyncWorker.OUTPUT_BLOCK_COUNT, 0);
                        final int warnCount = workInfo.getOutputData()
                                .getInt(AiSListSyncWorker.OUTPUT_WARN_COUNT, 0);
                        if (blockCount > 0 || warnCount > 0) {
                            Toast.makeText(
                                    requireContext(),
                                    getString(
                                            R.string.aislist_updated,
                                            blockCount,
                                            warnCount),
                                    Toast.LENGTH_SHORT)
                                    .show();
                        }
                    } else {
                        Toast.makeText(
                                requireContext(),
                                R.string.aislist_update_failed,
                                Toast.LENGTH_SHORT)
                                .show();
                    }
                    updateAiSListSummary();
                });
    }

    private void updateAiSListSummary() {
        if (aiSListStatusPreference == null) {
            return;
        }
        final boolean enabled = aiSListEnabledPreference.isChecked();
        aiSListUpdatePreference.setEnabled(enabled);
        aiSListWarnBehaviorPreference.setEnabled(enabled);
        final AiSListRepository.SyncStatus status =
                AiSListRepository.status(requireContext());
        if (status.getCount() <= 0 || status.getUpdatedAtMillis() <= 0) {
            aiSListStatusPreference.setSummary(R.string.aislist_not_downloaded);
            return;
        }
        final String updatedAt = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT)
                .format(new Date(status.getUpdatedAtMillis()));
        aiSListStatusPreference.setSummary(
                getString(
                        R.string.aislist_status_format,
                        status.getBlockCount(),
                        status.getWarnCount(),
                        updatedAt));
    }

    private void saveEntries(final boolean videos, @NonNull final List<Entry> entries) {
        if (videos) {
            ContentBlockingHelper.saveBlockedVideos(requireContext(), entries);
        } else {
            ContentBlockingHelper.saveBlockedChannels(requireContext(), entries);
        }
        updateSummaries();
    }

    private void confirmClearAll() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_blocked_content_title)
                .setMessage(R.string.clear_blocked_content_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear, (dialog, which) -> {
                    ContentBlockingHelper.clearAll(requireContext());
                    blockedKeywordsPreference.setText("");
                    updateSummaries();
                })
                .show();
    }

    private void updateSummaries() {
        if (blockedChannelsPreference == null) {
            return;
        }
        final int channelCount = ContentBlockingHelper.getBlockedChannels(requireContext()).size();
        final int videoCount = ContentBlockingHelper.getBlockedVideos(requireContext()).size();
        blockedChannelsPreference.setSummary(getResources().getQuantityString(
                R.plurals.blocked_channels_count, channelCount, channelCount));
        blockedVideosPreference.setSummary(getResources().getQuantityString(
                R.plurals.blocked_videos_count, videoCount, videoCount));
        blockedKeywordsPreference.setSummary(keywordSummary(
                blockedKeywordsPreference.getText()));
        updateAiSListSummary();
    }

    @NonNull
    private String keywordSummary(@Nullable final String value) {
        final String sanitized = ContentBlockingHelper.sanitizeKeywords(value);
        final int count = sanitized.isEmpty() ? 0 : sanitized.split("\\n").length;
        return getResources().getQuantityString(R.plurals.blocked_keywords_count, count, count);
    }
}
