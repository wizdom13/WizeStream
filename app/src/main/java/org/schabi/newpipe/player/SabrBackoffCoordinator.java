package org.schabi.newpipe.player;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;

/** Publishes the SABR server-wait state independently from the media notification. */
public final class SabrBackoffCoordinator {
    public static final long NO_DEADLINE = -1L;
    static final int NOTIFICATION_ID = 123790;
    private static final long UPDATE_INTERVAL_MS = 1_000L;
    private static final SabrBackoffCoordinator INSTANCE = new SabrBackoffCoordinator();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateTask = this::updateNotification;
    private Context appContext;
    private Object owner;
    private long deadlineElapsedMs = NO_DEADLINE;
    private boolean playerBuffering;
    private boolean playbackBlockedBeforeBuffering;

    private SabrBackoffCoordinator() {
    }

    @NonNull
    public static SabrBackoffCoordinator getInstance() {
        return INSTANCE;
    }

    public synchronized void begin(@NonNull final Context context,
                                   @NonNull final Object sourceOwner,
                                   final long deadlineMs) {
        begin(context, sourceOwner, deadlineMs, false);
    }

    public synchronized void beginPlaybackWait(@NonNull final Context context,
                                               @NonNull final Object sourceOwner,
                                               final long deadlineMs) {
        begin(context, sourceOwner, deadlineMs, true);
    }

    private synchronized void begin(@NonNull final Context context,
                                    @NonNull final Object sourceOwner,
                                    final long deadlineMs,
                                    final boolean blocksPlaybackBeforeBuffering) {
        if (deadlineMs <= SystemClock.elapsedRealtime()) {
            clear(context, sourceOwner);
            return;
        }
        appContext = context.getApplicationContext();
        if (owner != sourceOwner) {
            owner = sourceOwner;
            deadlineElapsedMs = deadlineMs;
            playbackBlockedBeforeBuffering = blocksPlaybackBeforeBuffering;
        } else {
            deadlineElapsedMs = Math.max(deadlineElapsedMs, deadlineMs);
            playbackBlockedBeforeBuffering |= blocksPlaybackBeforeBuffering;
        }
        handler.removeCallbacks(updateTask);
        updateNotification();
    }

    public synchronized void clear(@NonNull final Context context,
                                   @NonNull final Object sourceOwner) {
        if (owner != sourceOwner) {
            return;
        }
        owner = null;
        deadlineElapsedMs = NO_DEADLINE;
        playbackBlockedBeforeBuffering = false;
        handler.removeCallbacks(updateTask);
        NotificationManagerCompat.from(context.getApplicationContext())
                .cancel(NOTIFICATION_ID);
    }

    public synchronized void setPlayerBuffering(@NonNull final Context context,
                                                final boolean buffering) {
        appContext = context.getApplicationContext();
        playerBuffering = buffering;
        handler.removeCallbacks(updateTask);
        if (buffering || playbackBlockedBeforeBuffering) {
            updateNotification();
        } else {
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID);
        }
    }

    public synchronized long getRemainingMs() {
        return deadlineElapsedMs == NO_DEADLINE
                ? 0L : Math.max(0L, deadlineElapsedMs - SystemClock.elapsedRealtime());
    }

    public synchronized boolean isWaiting() {
        return getRemainingMs() > 0L;
    }

    static int remainingSeconds(final long remainingMs) {
        return remainingMs <= 0L ? 0 : (int) ((remainingMs + 999L) / 1_000L);
    }

    @SuppressLint("MissingPermission")
    private synchronized void updateNotification() {
        if (appContext == null || deadlineElapsedMs == NO_DEADLINE
                || (!playerBuffering && !playbackBlockedBeforeBuffering)) {
            return;
        }
        final long remainingMs = getRemainingMs();
        if (remainingMs <= 0L) {
            final Context context = appContext;
            owner = null;
            deadlineElapsedMs = NO_DEADLINE;
            playbackBlockedBeforeBuffering = false;
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
            return;
        }
        final int seconds = remainingSeconds(remainingMs);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext,
                appContext.getString(R.string.sabr_backoff_notification_channel_id))
                .setSmallIcon(R.drawable.ic_wizestream_triangle_white)
                .setContentTitle(appContext.getString(R.string.sabr_backoff_notification_title))
                .setContentText(appContext.getString(
                        R.string.sabr_backoff_notification_content, seconds))
                .setContentIntent(PendingIntent.getActivity(appContext, NOTIFICATION_ID,
                        new Intent(appContext, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build());
        handler.postDelayed(updateTask, UPDATE_INTERVAL_MS);
    }
}
