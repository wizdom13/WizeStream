package org.schabi.newpipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;

public class DownloaderImplAsyncTest {
    private static final long TEST_TIMEOUT_SECONDS = 2;

    private DownloaderImpl downloader;

    @Before
    public void setUp() {
        downloader = DownloaderImpl.init(new OkHttpClient.Builder()
                .addInterceptor(chain -> new okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create("response", null))
                        .build()));
    }

    @After
    public void tearDown() {
        downloader.getClient().dispatcher().executorService().shutdownNow();
        downloader.getClient().connectionPool().evictAll();
    }

    @Test
    public void awaitReturnsOnlyAfterSuccessCallbackCompletes() throws Exception {
        final CallbackGate callback = new CallbackGate();
        final CancellableCall call = downloader.executeAsync(testRequest(), callback);

        assertTrue(callback.started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertFalse(call.await(100, TimeUnit.MILLISECONDS));

        callback.release.countDown();

        assertTrue(call.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(0, callback.completed.getCount());
    }

    @Test
    public void awaitReturnsOnlyAfterErrorCallbackCompletes() throws Exception {
        downloader = DownloaderImpl.init(new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    throw new IOException("expected");
                }));
        final CallbackGate callback = new CallbackGate();
        final CancellableCall call = downloader.executeAsync(testRequest(), callback);

        assertTrue(callback.started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertFalse(call.await(100, TimeUnit.MILLISECONDS));

        callback.release.countDown();

        assertTrue(call.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(0, callback.completed.getCount());
    }

    private Request testRequest() {
        return Request.newBuilder().get("https://example.com/test").build();
    }

    private static final class CallbackGate implements Downloader.AsyncCallback {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);

        @Override
        public void onSuccess(final Response response) {
            awaitRelease();
        }

        @Override
        public void onError(final Exception error) {
            awaitRelease();
        }

        private void awaitRelease() {
            started.countDown();
            try {
                assertTrue(release.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (final InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            } finally {
                completed.countDown();
            }
        }
    }
}
