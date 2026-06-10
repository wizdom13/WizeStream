package org.schabi.newpipe;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.util.InfoCache;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public final class DownloaderImpl extends Downloader {
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE_KEY =
            "youtube_restricted_mode_key";
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE = "PREF=f2=8000000";
    public static final String YOUTUBE_DOMAIN = "youtube.com";

    private static DownloaderImpl instance;
    private final Map<String, String> mCookies;
    private final OkHttpClient client;

    private DownloaderImpl(final OkHttpClient.Builder builder) {
        this.client = builder
                .addInterceptor(chain -> {
                    final okhttp3.Request originalRequest = chain.request();
                    if (originalRequest.header("User-Agent") != null) {
                        return chain.proceed(originalRequest);
                    }

                    return chain.proceed(originalRequest.newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .build());
                })
                .readTimeout(30, TimeUnit.SECONDS)
//                .cache(new Cache(new File(context.getExternalCacheDir(), "okhttp"),
//                        16 * 1024 * 1024))
                .build();
        this.mCookies = new HashMap<>();
    }

    @NonNull
    public OkHttpClient getClient() {
        return client;
    }

    /**
     * It's recommended to call exactly once in the entire lifetime of the application.
     *
     * @param builder if null, default builder will be used
     * @return a new instance of {@link DownloaderImpl}
     */
    public static DownloaderImpl init(@Nullable final OkHttpClient.Builder builder) {
        instance = new DownloaderImpl(
                builder != null ? builder : new OkHttpClient.Builder());
        return instance;
    }

    public static DownloaderImpl getInstance() {
        return instance;
    }

    public String getCookies(final String url) {
        final String youtubeCookie = url.contains(YOUTUBE_DOMAIN)
                ? getCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY) : null;

        // Recaptcha cookie is always added TODO: not sure if this is necessary
        return Stream.of(youtubeCookie, getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY))
                .filter(Objects::nonNull)
                .flatMap(cookies -> Arrays.stream(cookies.split("; *")))
                .distinct()
                .collect(Collectors.joining("; "));
    }

    public String getCookie(final String key) {
        return mCookies.get(key);
    }

    public void setCookie(final String key, final String cookie) {
        mCookies.put(key, cookie);
    }

    public void removeCookie(final String key) {
        mCookies.remove(key);
    }

    public void updateYoutubeRestrictedModeCookies(final Context context) {
        final String restrictedModeEnabledKey =
                context.getString(R.string.youtube_restricted_mode_enabled);
        final boolean restrictedModeEnabled = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(restrictedModeEnabledKey, false);
        updateYoutubeRestrictedModeCookies(restrictedModeEnabled);
    }

    public void updateYoutubeRestrictedModeCookies(final boolean youtubeRestrictedModeEnabled) {
        if (youtubeRestrictedModeEnabled) {
            setCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY,
                    YOUTUBE_RESTRICTED_MODE_COOKIE);
        } else {
            removeCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY);
        }
        InfoCache.getInstance().clearCache();
    }

    /**
     * Get the size of the content that the url is pointing by firing a HEAD request.
     *
     * @param url an url pointing to the content
     * @return the size of the content, in bytes
     */
    public long getContentLength(final String url) throws IOException {
        try {
            final Response response = head(url);
            return Long.parseLong(response.getHeader("Content-Length"));
        } catch (final NumberFormatException e) {
            throw new IOException("Invalid content length", e);
        } catch (final ReCaptchaException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Response execute(@NonNull final Request request)
            throws IOException, ReCaptchaException {
        final okhttp3.Request okHttpRequest = buildRequest(request);
        try (okhttp3.Response response = client.newCall(okHttpRequest).execute()) {
            return buildExtractorResponse(response, request.url());
        }
    }

    @Override
    public CancellableCall executeAsync(@NonNull final Request request,
                                        final AsyncCallback callback)
            throws IOException, ReCaptchaException {
        final Call call = client.newCall(buildRequest(request));
        final CancellableCall cancellableCall = new CancellableCall(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull final Call call, @NonNull final IOException e) {
                cancellableCall.setFinished();
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull final Call call,
                                   @NonNull final okhttp3.Response response) {
                try (response) {
                    final Response extractorResponse = buildExtractorResponse(
                            response, request.url());
                    cancellableCall.setFinished();
                    callback.onSuccess(extractorResponse);
                } catch (final IOException | ExtractionException e) {
                    cancellableCall.setFinished();
                    callback.onError(e);
                }
            }
        });
        return cancellableCall;
    }

    @NonNull
    private okhttp3.Request buildRequest(@NonNull final Request request) {
        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        RequestBody requestBody = null;
        if (dataToSend != null) {
            requestBody = RequestBody.create(dataToSend);
        }

        final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)
                .addHeader("User-Agent", USER_AGENT);

        final String cookies = getCookies(url);
        if (!cookies.isEmpty()) {
            requestBuilder.addHeader("Cookie", cookies);
        }

        headers.forEach((headerName, headerValueList) -> {
            requestBuilder.removeHeader(headerName);
            headerValueList.forEach(headerValue ->
                    requestBuilder.addHeader(headerName, headerValue));
        });
        return requestBuilder.build();
    }

    @NonNull
    private static Response buildExtractorResponse(@NonNull final okhttp3.Response response,
                                                   @NonNull final String originalUrl)
            throws IOException, ReCaptchaException {
        if (response.code() == 429) {
            throw new ReCaptchaException("reCaptcha Challenge requested", originalUrl);
        }

        byte[] responseBodyBytes = new byte[0];
        String responseBodyToReturn = null;
        try (ResponseBody body = response.body()) {
            if (body != null) {
                responseBodyBytes = body.bytes();
                responseBodyToReturn = new String(responseBodyBytes, responseCharset(body));
            }
        }

        final String latestUrl = response.request().url().toString();
        return new Response(
                response.code(),
                response.message(),
                response.headers().toMultimap(),
                responseBodyToReturn,
                responseBodyBytes,
                latestUrl);
    }

    @NonNull
    private static Charset responseCharset(@NonNull final ResponseBody body) {
        final MediaType contentType = body.contentType();
        return contentType == null
                ? StandardCharsets.UTF_8 : contentType.charset(StandardCharsets.UTF_8);
    }

}
