package org.schabi.newpipe.util.external_communication;

import static org.schabi.newpipe.util.external_communication.ShareUtils.tryOpenIntentInApp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;

/**
 * Util class for TubeBeam, a phone app that sends a YouTube link to SmartTube on an Android TV.
 * @see <a href="https://github.com/jagjordi/tubebeam">TubeBeam on GitHub</a>
 */
public final class TubeBeamUtils {
    private TubeBeamUtils() { }

    /**
     * @param serviceId the service of the stream
     * @return whether TubeBeam can play streams of this service (only YouTube)
     */
    public static boolean isServiceSupported(final int serviceId) {
        return serviceId == ServiceList.YouTube.getServiceId();
    }

    /**
     * @param context   the context to use
     * @param serviceId the service of the stream
     * @return whether the "Send to TV" entries should be offered: YouTube stream and TubeBeam
     *         installed
     */
    public static boolean shouldShowSendToTv(@NonNull final Context context,
                                             final int serviceId) {
        return isServiceSupported(serviceId)
                && context.getPackageManager()
                .resolveActivity(buildIntent(context, "", false), 0) != null;
    }

    /**
     * Hands the stream url to TubeBeam, which forwards it to the TV without showing the share
     * sheet.
     *
     * @param context   the context to use
     * @param streamUrl the url of the stream
     * @param enqueue   true to add the stream to SmartTube's playback queue instead of playing it
     */
    public static void sendToTv(@NonNull final Context context,
                                @NonNull final String streamUrl,
                                final boolean enqueue) {
        tryOpenIntentInApp(context, buildIntent(context, streamUrl, enqueue));
    }

    private static Intent buildIntent(@NonNull final Context context,
                                      @NonNull final String streamUrl,
                                      final boolean enqueue) {
        final String pkg = context.getString(R.string.tubebeam_package);
        final String activity = context.getString(enqueue
                ? R.string.tubebeam_queue_activity : R.string.tubebeam_send_activity);
        return new Intent(Intent.ACTION_SEND)
                .setComponent(new ComponentName(pkg, activity))
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, streamUrl)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
