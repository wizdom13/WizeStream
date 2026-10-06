package org.schabi.newpipe.network;

import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Check every redirect before OkHttp can resolve or connect to its destination. */
public final class InvidiousInterceptor implements Interceptor {
    @Override
    public Response intercept(final Chain chain) throws IOException {
        Request request = chain.request();
        if (!InvidiousBackend.isEnabled()) {
            return chain.proceed(request);
        }
        final String routed = InvidiousBackend.isYoutubeImageHost(request.url().host())
                ? InvidiousBackend.routeImage(request.url().toString(), InvidiousBackend
                        .getInstance())
                : request.url().toString();
        if (!routed.equals(request.url().toString())) {
            request = request.newBuilder().url(routed).removeHeader("Cookie")
                    .removeHeader("Authorization").removeHeader("Origin").removeHeader("Referer")
                            .build();
        }
        for (int redirects = 0; redirects <= 20; redirects++) {
            InvidiousBackend.checkRequest(request.url().uri());
            final Response response = chain.proceed(request);
            final int code = response.code();
            final String location = response.header("Location");
            if (location == null || !(code == 301 || code == 302 || code == 303
                    || code == 307 || code == 308)) {
                return response;
            }
            final HttpUrl target = request.url().resolve(location);
            response.close();
            if (target == null) {
                throw new IOException("Invalid redirect URL");
            }
            InvidiousBackend.checkRedirect(request.url().uri(), target.uri());
            final Request.Builder redirected = request.newBuilder().url(target);
            if (!InvidiousBackend.sameOrigin(request.url().uri(), target.uri())) {
                redirected.removeHeader("Authorization").removeHeader("Cookie");
            }
            if (code != 307 && code != 308 && !request.method().equals("HEAD")) {
                redirected.method("GET", null).removeHeader("Content-Type")
                        .removeHeader("Content-Length").removeHeader("Transfer-Encoding");
            }
            request = redirected.build();
        }
        throw new IOException("Too many redirects");
    }
}
