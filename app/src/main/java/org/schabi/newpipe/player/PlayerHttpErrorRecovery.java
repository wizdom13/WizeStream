package org.schabi.newpipe.player;

import static org.schabi.newpipe.extractor.ServiceList.YouTube;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRedirectException;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/** Utility methods for deciding whether a player media URL failure can be retried safely. */
final class PlayerHttpErrorRecovery {
    private PlayerHttpErrorRecovery() {
    }

    static final class RecoveryGuard {
        private static final long RESET_AFTER_MILLIS = TimeUnit.MINUTES.toMillis(10);

        @Nullable
        private String currentRecoveryKey;
        @Nullable
        private String lastRetriedRecoveryKey;
        private long lastRetryAtMillis;

        void reset() {
            currentRecoveryKey = null;
            lastRetriedRecoveryKey = null;
            lastRetryAtMillis = 0;
        }

        boolean canRetry(@NonNull final String recoveryKey) {
            final long now = System.currentTimeMillis();
            if (now - lastRetryAtMillis > RESET_AFTER_MILLIS) {
                reset();
            }

            if (!recoveryKey.equals(currentRecoveryKey)) {
                currentRecoveryKey = recoveryKey;
                lastRetriedRecoveryKey = null;
            }
            if (recoveryKey.equals(lastRetriedRecoveryKey)) {
                return false;
            }
            lastRetriedRecoveryKey = recoveryKey;
            lastRetryAtMillis = now;
            return true;
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
                || hasUnknownHostCause(error)
                || hasSabrRedirectCause(error);
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

    static boolean hasSabrRedirectCause(@NonNull final Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SabrRedirectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
