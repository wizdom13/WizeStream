package org.schabi.newpipe;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Records extraction request timing without logging signed URLs, cookies or request bodies. */
final class YoutubeRequestTimingInterceptor implements Interceptor {
    @NonNull
    @Override
    public Response intercept(@NonNull final Chain chain) throws IOException {
        final Request request = chain.request();
        final String host = request.url().host();
        final String path = request.url().encodedPath();
        if (!(host.equals("youtube.com") || host.endsWith(".youtube.com"))
                || !path.startsWith("/youtubei/v1/")) {
            return chain.proceed(request);
        }
        final long startMs = SystemClock.elapsedRealtime();
        String result = "failed";
        try {
            final Response response = chain.proceed(request);
            result = "HTTP " + response.code();
            return response;
        } catch (final IOException error) {
            result = error.getClass().getSimpleName();
            throw error;
        } finally {
            Log.d("YouTubeRequestTiming", path + " client="
                    + request.header("X-YouTube-Client-Name") + " result=" + result
                    + " headersAfterMs=" + (SystemClock.elapsedRealtime() - startMs));
        }
    }
}
