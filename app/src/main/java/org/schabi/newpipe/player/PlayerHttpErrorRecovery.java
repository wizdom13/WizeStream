package org.schabi.newpipe.player;

import static org.schabi.newpipe.extractor.ServiceList.YouTube;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Utility methods for deciding whether a player media URL failure can be retried safely. */
final class PlayerHttpErrorRecovery {
    private static final String REQUEST_DIAGNOSTIC_PREFIX =
            "YouTube media request diagnostic: ";

    private PlayerHttpErrorRecovery() {
    }

    static final class RecoveryGuard {
        static final int MAX_ATTEMPTS = 3;
        static final long RESET_AFTER_MILLIS = TimeUnit.MINUTES.toMillis(10);
        private static final long[] RETRY_DELAYS_MILLIS = {0, 1_000, 3_000};

        @NonNull
        private final LongSupplier elapsedRealtimeSupplier;
        @Nullable
        private String currentRecoveryKey;
        private int attemptCount;
        private long lastAttemptAtMillis;

        RecoveryGuard() {
            this(SystemClock::elapsedRealtime);
        }

        RecoveryGuard(@NonNull final LongSupplier elapsedRealtimeSupplier) {
            this.elapsedRealtimeSupplier = elapsedRealtimeSupplier;
        }

        void reset() {
            currentRecoveryKey = null;
            attemptCount = 0;
            lastAttemptAtMillis = 0;
        }

        @Nullable
        RecoveryAttempt acquireAttempt(@NonNull final String recoveryKey) {
            final long now = elapsedRealtimeSupplier.getAsLong();
            if (attemptCount > 0
                    && now - lastAttemptAtMillis >= RESET_AFTER_MILLIS) {
                reset();
            }

            if (!recoveryKey.equals(currentRecoveryKey)) {
                currentRecoveryKey = recoveryKey;
                attemptCount = 0;
            }
            if (attemptCount >= MAX_ATTEMPTS) {
                return null;
            }

            final RecoveryAttempt attempt = new RecoveryAttempt(attemptCount + 1,
                    RETRY_DELAYS_MILLIS[attemptCount]);
            attemptCount++;
            lastAttemptAtMillis = now;
            return attempt;
        }
    }

    static final class RecoveryAttempt {
        private final int number;
        private final long delayMillis;

        RecoveryAttempt(final int number, final long delayMillis) {
            this.number = number;
            this.delayMillis = delayMillis;
        }

        int getNumber() {
            return number;
        }

        long getDelayMillis() {
            return delayMillis;
        }
    }

    static boolean isRecoverableYouTubeMediaUrlFailure(@NonNull final Throwable error,
                                                       @Nullable final PlayQueueItem item) {
        return item != null
                && isYouTubeService(item.getServiceId())
                && isRecoverableMediaUrlFailure(error);
    }

    static boolean isRecoverableMediaUrlFailure(@NonNull final Throwable error) {
        return isRecoverableStatusCode(findInvalidResponseCode(error))
                || hasUnknownHostCause(error);
    }

    static boolean isYouTubeService(final int serviceId) {
        return serviceId == YouTube.getServiceId();
    }

    static boolean isRecoverableStatusCode(@Nullable final Integer responseCode) {
        if (responseCode == null) {
            return false;
        }
        return responseCode == 403 || responseCode == 404 || responseCode == 410;
    }

    @Nullable
    static Integer findInvalidResponseCode(@NonNull final Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) current).responseCode;
            }
            current = current.getCause();
        }
        return null;
    }

    static boolean hasUnknownHostCause(@NonNull final Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnknownHostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Nullable
    static String findSafeRequestDiagnostic(@NonNull final Throwable error) {
        Throwable current = error;
        while (current != null) {
            final String message = current.getMessage();
            if (message != null && message.startsWith(REQUEST_DIAGNOSTIC_PREFIX)) {
                return message.substring(REQUEST_DIAGNOSTIC_PREFIX.length());
            }
            current = current.getCause();
        }
        return null;
    }

    @Nullable
    static String buildSafeErrorContext(@NonNull final Throwable error) {
        final Integer responseCode = findInvalidResponseCode(error);
        final String requestDiagnostic = findSafeRequestDiagnostic(error);
        if (responseCode == null && requestDiagnostic == null) {
            return null;
        }

        final StringBuilder context = new StringBuilder();
        if (responseCode != null) {
            context.append("status=").append(responseCode);
        }
        if (requestDiagnostic != null) {
            if (context.length() > 0) {
                context.append(", ");
            }
            context.append(requestDiagnostic);
        }
        return context.toString();
    }
}
