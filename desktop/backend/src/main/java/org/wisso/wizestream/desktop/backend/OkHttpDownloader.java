package org.wisso.wizestream.desktop.backend;

import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class OkHttpDownloader extends Downloader {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(30))
            .callTimeout(Duration.ofSeconds(45))
            .build();

    @Override
    public org.schabi.newpipe.extractor.downloader.Response execute(@Nonnull final Request request)
            throws IOException, ReCaptchaException {
        try (okhttp3.Response response = clientFor(request).newCall(toOkHttpRequest(request)).execute()) {
            return convert(response);
        }
    }

    @Override
    public CancellableCall executeAsync(@Nonnull final Request request, final AsyncCallback callback)
            throws IOException, ReCaptchaException {
        final Call call = clientFor(request).newCall(toOkHttpRequest(request));
        final CancellableCall cancellable = new CancellableCall(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@Nonnull final Call ignored, @Nonnull final IOException error) {
                cancellable.setFinished();
                callback.onError(error);
            }

            @Override
            public void onResponse(@Nonnull final Call ignored, @Nonnull final okhttp3.Response response) {
                try (response) {
                    callback.onSuccess(convert(response));
                } catch (final Exception error) {
                    callback.onError(error);
                } finally {
                    cancellable.setFinished();
                }
            }
        });
        return cancellable;
    }

    private OkHttpClient clientFor(final Request request) {
        if (request.followRedirects() == client.followRedirects()) return client;
        return client.newBuilder().followRedirects(request.followRedirects()).build();
    }

    private okhttp3.Request toOkHttpRequest(final Request request) {
        final okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                .url(request.url())
                .header("User-Agent", "WizeStream Desktop/0.1");
        for (final Map.Entry<String, List<String>> header : request.headers().entrySet()) {
            for (final String value : header.getValue()) builder.addHeader(header.getKey(), value);
        }
        final byte[] data = request.dataToSend();
        final boolean bodyRequired = List.of("POST", "PUT", "PATCH").contains(request.httpMethod());
        final RequestBody body = data == null && !bodyRequired
                ? null
                : RequestBody.create(data == null ? new byte[0] : data, (MediaType) null);
        builder.method(request.httpMethod(), body);
        return builder.build();
    }

    private org.schabi.newpipe.extractor.downloader.Response convert(final okhttp3.Response response)
            throws IOException {
        final byte[] body = response.body() == null ? new byte[0] : response.body().bytes();
        return new org.schabi.newpipe.extractor.downloader.Response(
                response.code(),
                response.message(),
                response.headers().toMultimap(),
                new String(body, StandardCharsets.UTF_8),
                body,
                response.request().url().toString()
        );
    }
}
