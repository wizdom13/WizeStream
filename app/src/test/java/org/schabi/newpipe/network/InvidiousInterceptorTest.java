package org.schabi.newpipe.network;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

public class InvidiousInterceptorTest {
    @Before
    public void enable() {
        InvidiousBackend.configure(true, "https://example.org");
    }
    @After
    public void disable() {
        InvidiousBackend.configure(false, "");
    }

    private Response response(final Request request, final int code, final String location) {
        final Response.Builder builder = new Response.Builder().request(request)
                .protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .body(ResponseBody.create("", (okhttp3.MediaType) null));
        if (location != null) {
            builder.header("Location", location);
        }
        return builder.build();
    }

    @Test
    public void rejectsDirectRequestsBeforeCallingTheTransport() throws Exception {
        final Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(new Request.Builder()
                .url("https://rr1.googlevideo.com/videoplayback").build());
        assertThrows(IOException.class, () -> new InvidiousInterceptor().intercept(chain));
        verify(chain, never()).proceed(any());
    }

    @Test
    public void rewritesStoredImagesAndDropsDirectServiceHeaders() throws Exception {
        final Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(new Request.Builder()
                .url("https://i.ytimg.com/vi/abc/hqdefault.jpg")
                .header("Cookie", "private-cookie").header("Authorization", "private-token")
                .header("Referer", "https://www.youtube.com/").build());
        when(chain.proceed(any())).thenAnswer(call -> {
            final Request request = call.getArgument(0);
            assertEquals("https://example.org/vi/abc/hqdefault.jpg", request.url().toString());
            assertNull(request.header("Cookie"));
            assertNull(request.header("Authorization"));
            assertNull(request.header("Referer"));
            return response(request, 200, null);
        });
        new InvidiousInterceptor().intercept(chain).close();
        verify(chain, times(1)).proceed(any());
    }

    @Test
    public void checksRedirectsBeforeTheNextConnection() throws Exception {
        for (final String destination : new String[]{"https://rr1.googlevideo.com/videoplayback",
                "https://www.youtube.com/watch?v=x", "https://different.example.org/videoplayback",
                "http://example.org/videoplayback"}) {
            final Interceptor.Chain chain = mock(Interceptor.Chain.class);
            final Request request = new Request.Builder().url("https://example.org/videoplayback")
                    .build();
            when(chain.request()).thenReturn(request);
            when(chain.proceed(any())).thenReturn(response(request, 302, destination));
            assertThrows(IOException.class, () -> new InvidiousInterceptor().intercept(chain));
            verify(chain, times(1)).proceed(any());
        }
    }

    @Test
    public void followsRelativeLocalRedirectsAndPreservesRangeRequests() throws Exception {
        final Interceptor.Chain chain = mock(Interceptor.Chain.class);
        final Request request = new Request.Builder().url("https://example.org/videoplayback?old")
                .header("Range", "bytes=100-").build();
        when(chain.request()).thenReturn(request);
        when(chain.proceed(any())).thenAnswer(call -> {
            final Request target = call.getArgument(0);
            assertEquals("bytes=100-", target.header("Range"));
            return response(target, target.url().query().equals("old") ? 302 : 200,
                    target.url().query().equals("old") ? "/videoplayback?new" : null);
        });
        final Response result = new InvidiousInterceptor().intercept(chain);
        assertEquals("https://example.org/videoplayback?new", result.request().url().toString());
        result.close();
        verify(chain, times(2)).proceed(any());
    }

    @Test
    public void disabledModePreservesDirectRequests() throws Exception {
        InvidiousBackend.configure(false, "");
        final Interceptor.Chain chain = mock(Interceptor.Chain.class);
        final Request request = new Request.Builder().url("https://www.youtube.com/").build();
        when(chain.request()).thenReturn(request);
        when(chain.proceed(request)).thenReturn(response(request, 200, null));
        new InvidiousInterceptor().intercept(chain).close();
        verify(chain).proceed(request);
    }
}
