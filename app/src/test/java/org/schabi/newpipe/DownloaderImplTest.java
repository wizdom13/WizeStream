/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.schabi.newpipe.error.ReCaptchaActivity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;

public class DownloaderImplTest {
    @Test
    public void storedCookiesAreRestrictedToSecureYoutubeHosts() {
        final DownloaderImpl downloader = DownloaderImpl.init(null);
        downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, "VISITOR_INFO1_LIVE=test");
        downloader.setCookie(DownloaderImpl.YOUTUBE_RESTRICTED_MODE_COOKIE_KEY, "PREF=f2=8000000");
        for (final String url : new String[]{"https://youtube.com/watch?v=test",
                "https://www.youtube.com/", "https://music.youtube.com/"}) {
            assertEquals("PREF=f2=8000000; VISITOR_INFO1_LIVE=test", downloader.getCookies(url));
        }
        for (final String url : new String[]{"https://youtube.com.example.org/",
                "https://notyoutube.com/", "https://example.org/youtube.com",
                "https://youtube.com@example.org/", "http://www.youtube.com/",
                "https://www.bilibili.com/", "not a URL"}) {
            assertEquals("", downloader.getCookies(url));
        }
    }

    @Test
    public void eachNetworkHopSelectsCookiesForItsOwnDestination() throws IOException {
        final DownloaderImpl downloader = DownloaderImpl.init(null);
        downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, "VISITOR_INFO1_LIVE=test");
        assertEquals("VISITOR_INFO1_LIVE=test", interceptedCookie(downloader,
                new Request.Builder().url("https://www.youtube.com/").build()));
        assertNull(interceptedCookie(downloader,
                new Request.Builder().url("https://example.org/").build()));
        assertEquals("service=value", interceptedCookie(downloader,
                new Request.Builder().url("https://www.bilibili.com/")
                        .header("Cookie", "service=value").build()));
    }

    private String interceptedCookie(final DownloaderImpl downloader, final Request request)
            throws IOException {
        final Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(request);
        downloader.getClient().networkInterceptors().get(0).intercept(chain);
        final ArgumentCaptor<Request> captured = ArgumentCaptor.forClass(Request.class);
        verify(chain).proceed(captured.capture());
        return captured.getValue().header("Cookie");
    }

    @Test
    public void parsedUrlsAreReusedAndBounded() {
        final DownloaderImpl downloader = DownloaderImpl.init(null);
        final String repeatedUrl = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false";

        assertSame(downloader.parseUrl(repeatedUrl), downloader.parseUrl(repeatedUrl));

        final Object firstUrl = downloader.parseUrl("https://example.com/0");
        for (int i = 1; i <= DownloaderImpl.PARSED_URL_CACHE_SIZE; i++) {
            downloader.parseUrl("https://example.com/" + i);
        }
        assertNotSame(firstUrl, downloader.parseUrl("https://example.com/0"));
    }

    @Test
    public void postWithoutDataUsesEmptyRequestBody() throws IOException {
        final RequestBody requestBody = DownloaderImpl.buildRequestBody("POST", null);

        assertNotNull(requestBody);
        assertEquals(0, requestBody.contentLength());
        final Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);
        assertEquals(0, buffer.size());
    }

    @Test
    public void postWithDataPreservesRequestBody() throws IOException {
        final byte[] payload = "ticket-request".getBytes(StandardCharsets.UTF_8);
        final RequestBody requestBody = DownloaderImpl.buildRequestBody("POST", payload);

        assertNotNull(requestBody);
        assertEquals(payload.length, requestBody.contentLength());
        final Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);
        assertArrayEquals(payload, buffer.readByteArray());
    }

    @Test
    public void methodsWithoutDataRemainBodyless() {
        assertNull(DownloaderImpl.buildRequestBody("GET", null));
        assertNull(DownloaderImpl.buildRequestBody("HEAD", null));
        assertNull(DownloaderImpl.buildRequestBody("OPTIONS", null));
    }
}
