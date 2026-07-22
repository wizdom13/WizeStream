package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DialogSleepTimerBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.util.Localization;

import java.util.concurrent.TimeUnit;

/**
 * Builds the shared sleep timer dialog used by the video player and play queue activity.
 */
public final class SleepTimerDialog {
    private static final int MAX_CUSTOM_MINUTES = 1_440;
    private static final long STATUS_UPDATE_INTERVAL_MILLIS = 1_000L;

    private SleepTimerDialog() {
    }

    public static void show(@NonNull final AppCompatActivity activity,
                            @NonNull final Player player) {
        final DialogSleepTimerBinding binding = DialogSleepTimerBinding.inflate(
                activity.getLayoutInflater());
        selectCurrentOption(binding, player);
        binding.fadeOut.setChecked(player.isSleepTimerActive()
                && player.isSleepTimerFadeOutEnabled());
        binding.timerOptions.setOnCheckedChangeListener((group, checkedId) ->
                updateCustomDurationVisibility(binding, checkedId));
        updateCustomDurationVisibility(binding, binding.timerOptions.getCheckedRadioButtonId());

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.sleep_timer)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.start, null)
                .setNegativeButton(R.string.cancel, null);
        if (player.isSleepTimerActive()) {
            builder.setNeutralButton(R.string.sleep_timer_turn_off, null);
        }

        final AlertDialog dialog = builder.create();
        final Handler statusHandler = new Handler(Looper.getMainLooper());
        final Runnable statusUpdater = new Runnable() {
            @Override
            public void run() {
                updateActiveStatus(binding, player);
                if (dialog.isShowing()) {
                    statusHandler.postDelayed(this, STATUS_UPDATE_INTERVAL_MILLIS);
                }
            }
        };

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (startSelectedTimer(binding, player)) {
                    dialog.dismiss();
                }
            });
            if (player.isSleepTimerActive()) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                    player.cancelSleepTimer();
                    dialog.dismiss();
                });
            }
            statusHandler.post(statusUpdater);
        });
        dialog.setOnDismissListener(ignored -> statusHandler.removeCallbacks(statusUpdater));
        dialog.show();
    }

    private static void selectCurrentOption(@NonNull final DialogSleepTimerBinding binding,
                                            @NonNull final Player player) {
        switch (player.getSleepTimerMode()) {
            case END_OF_CURRENT:
                binding.timerEndCurrent.setChecked(true);
                break;
            case END_OF_QUEUE:
                binding.timerEndQueue.setChecked(true);
                break;
            case DURATION:
                selectDurationOption(binding, player.getSleepTimerRemainingMillis());
                break;
            case NONE:
            default:
                binding.timer30Minutes.setChecked(true);
                break;
        }
    }

    private static void selectDurationOption(@NonNull final DialogSleepTimerBinding binding,
                                             final long remainingMillis) {
        final long roundedMinutes = Math.max(1L,
                (remainingMillis + TimeUnit.MINUTES.toMillis(1) - 1L)
                        / TimeUnit.MINUTES.toMillis(1));
        if (roundedMinutes == 15L) {
            binding.timer15Minutes.setChecked(true);
        } else if (roundedMinutes == 30L) {
            binding.timer30Minutes.setChecked(true);
        } else if (roundedMinutes == 45L) {
            binding.timer45Minutes.setChecked(true);
        } else if (roundedMinutes == 60L) {
            binding.timer60Minutes.setChecked(true);
        } else {
            binding.timerCustom.setChecked(true);
            binding.customMinutes.setText(String.valueOf(Math.min(roundedMinutes,
                    MAX_CUSTOM_MINUTES)));
        }
    }

    private static void updateCustomDurationVisibility(
            @NonNull final DialogSleepTimerBinding binding,
            final int checkedId) {
        final boolean customSelected = checkedId == R.id.timerCustom;
        binding.customMinutesLayout.setVisibility(customSelected ? View.VISIBLE : View.GONE);
        if (!customSelected) {
            binding.customMinutesLayout.setError(null);
        }
    }

    private static boolean startSelectedTimer(@NonNull final DialogSleepTimerBinding binding,
                                              @NonNull final Player player) {
        final boolean fadeOut = binding.fadeOut.isChecked();
        final int checkedId = binding.timerOptions.getCheckedRadioButtonId();
        if (checkedId == R.id.timer15Minutes) {
            player.startSleepTimer(TimeUnit.MINUTES.toMillis(15), fadeOut);
        } else if (checkedId == R.id.timer30Minutes) {
            player.startSleepTimer(TimeUnit.MINUTES.toMillis(30), fadeOut);
        } else if (checkedId == R.id.timer45Minutes) {
            player.startSleepTimer(TimeUnit.MINUTES.toMillis(45), fadeOut);
        } else if (checkedId == R.id.timer60Minutes) {
            player.startSleepTimer(TimeUnit.MINUTES.toMillis(60), fadeOut);
        } else if (checkedId == R.id.timerEndCurrent) {
            if (!player.startSleepTimerAtEndOfCurrent(fadeOut)) {
                showUnavailableMessage(binding);
                return false;
            }
        } else if (checkedId == R.id.timerEndQueue) {
            if (!player.startSleepTimerAtEndOfQueue(fadeOut)) {
                showUnavailableMessage(binding);
                return false;
            }
        } else if (checkedId == R.id.timerCustom) {
            final Integer minutes = parseCustomMinutes(binding);
            if (minutes == null) {
                return false;
            }
            player.startSleepTimer(TimeUnit.MINUTES.toMillis(minutes), fadeOut);
        } else {
            return false;
        }
        return true;
    }

    private static void showUnavailableMessage(@NonNull final DialogSleepTimerBinding binding) {
        Toast.makeText(binding.getRoot().getContext(), R.string.sleep_timer_unavailable,
                Toast.LENGTH_SHORT).show();
    }

    private static Integer parseCustomMinutes(@NonNull final DialogSleepTimerBinding binding) {
        try {
            final int minutes = Integer.parseInt(String.valueOf(binding.customMinutes.getText()));
            if (minutes >= 1 && minutes <= MAX_CUSTOM_MINUTES) {
                binding.customMinutesLayout.setError(null);
                return minutes;
            }
        } catch (final NumberFormatException ignored) {
            // The shared validation message below covers empty and non-numeric input.
        }
        binding.customMinutesLayout.setError(
                binding.getRoot().getContext().getString(R.string.sleep_timer_invalid_duration));
        return null;
    }

    private static void updateActiveStatus(@NonNull final DialogSleepTimerBinding binding,
                                           @NonNull final Player player) {
        if (!player.isSleepTimerActive()) {
            binding.activeStatus.setVisibility(View.GONE);
            return;
        }
        binding.activeStatus.setText(binding.getRoot().getContext().getString(
                R.string.sleep_timer_active_status,
                getStatusText(binding.getRoot().getContext(), player.getSleepTimerMode(),
                        player.getSleepTimerRemainingMillis())));
        binding.activeStatus.setVisibility(View.VISIBLE);
    }

    @NonNull
    public static String getStatusText(@NonNull final Context context,
                                       @NonNull final SleepTimer.Mode mode,
                                       final long remainingMillis) {
        final String remaining = remainingMillis == SleepTimer.REMAINING_TIME_UNSET
                ? null : Localization.getDurationString((remainingMillis + 999L) / 1_000L);
        if (mode == SleepTimer.Mode.END_OF_CURRENT) {
            return remaining == null
                    ? context.getString(R.string.sleep_timer_end_current_status)
                    : context.getString(R.string.sleep_timer_end_current_remaining, remaining);
        } else if (mode == SleepTimer.Mode.END_OF_QUEUE) {
            return remaining == null
                    ? context.getString(R.string.sleep_timer_end_queue_status)
                    : context.getString(R.string.sleep_timer_end_queue_remaining, remaining);
        } else if (mode == SleepTimer.Mode.DURATION
                && remainingMillis != SleepTimer.REMAINING_TIME_UNSET) {
            return context.getString(R.string.sleep_timer_remaining, remaining);
        }
        return context.getString(R.string.sleep_timer);
    }
}
