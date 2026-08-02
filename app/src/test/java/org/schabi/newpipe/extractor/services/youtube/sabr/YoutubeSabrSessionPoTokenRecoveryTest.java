package org.schabi.newpipe.extractor.services.youtube.sabr;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonParser;
import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.Localization;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeSabrSessionPoTokenRecoveryTest {
    private static final long CONTINUATION_EDGE_MS = 60_001;

    @Test
    void exhaustedPoTokenRefreshAtContinuationBoundaryTriggersRecoverableFailure()
            throws Exception {
        final QueueDownloader downloader = new QueueDownloader(
                protectedNoMediaResponse(),
                protectedNoMediaResponse(),
                protectedNoMediaResponse());
        final AtomicInteger tokenMints = new AtomicInteger();
        final YoutubeSabrSession session = createSession(downloader, new SabrPoTokenProvider() {
            @Nullable
            @Override
            public byte[] getPoToken(@Nonnull final YoutubeSabrInfo info,
                                     @Nonnull final YoutubeSabrStreamState streamState,
                                     final boolean forceRefresh) {
                tokenMints.incrementAndGet();
                return new byte[]{(byte) (7 + tokenMints.get())};
            }

            @Nullable
            @Override
            public byte[] getPoToken(@Nonnull final YoutubeSabrInfo info,
                                     @Nonnull final YoutubeSabrStreamState streamState) {
                return getPoToken(info, streamState, false);
            }
        });
        session.getStreamState().setPlayerTimeMs(CONTINUATION_EDGE_MS);
        session.getStreamState().setPoToken(new byte[]{7});

        assertEquals(0, session.pumpOnceStreaming(Localization.DEFAULT));
        assertEquals(0, session.pumpOnceStreaming(Localization.DEFAULT));

        final SabrPoTokenRefreshException failure = assertThrows(
                SabrPoTokenRefreshException.class,
                () -> session.pumpOnceStreaming(Localization.DEFAULT));

        assertEquals(2, tokenMints.get());
        assertArrayEquals(new byte[]{9}, session.getStreamState().getRawPoToken());
        assertEquals("video", failure.getVideoId());
        assertTrue(failure.getMessage().contains("after 2 forced PO-token refreshes"));
    }

    @Nonnull
    private static YoutubeSabrSession createSession(
            @Nonnull final Downloader downloader,
            @Nonnull final SabrPoTokenProvider tokenProvider) throws Exception {
        NewPipe.init(downloader);
        final JsonArray formats = JsonParser.array().from("["
                + "{\"itag\":140,\"lastModified\":\"1\",\"mimeType\":\"audio/mp4\","
                + "\"bitrate\":128000,\"contentLength\":\"1000\","
                + "\"approxDurationMs\":\"120000\"},"
                + "{\"itag\":299,\"lastModified\":\"2\",\"mimeType\":\"video/mp4\","
                + "\"width\":1920,\"height\":1080,\"bitrate\":4000000,"
                + "\"contentLength\":\"2000\",\"approxDurationMs\":\"120000\"}]");
        final List<YoutubeSabrFormat> parsedFormats =
                YoutubeSabrFormat.fromAdaptiveFormats("video", formats);
        final YoutubeSabrInfo info = new YoutubeSabrInfo(
                YoutubeSabrClientProfile.MWEB,
                "video",
                "cpn",
                YoutubeSabrClientProfile.MWEB.getClientVersion(),
                "visitor",
                "https://example.com/sabr",
                "AA==",
                parsedFormats);
        return new YoutubeSabrSession(info, parsedFormats.get(0), parsedFormats.get(1),
                tokenProvider, null);
    }

    @Nonnull
    private static byte[] protectedNoMediaResponse() {
        final SabrProto.Writer protection = new SabrProto.Writer();
        protection.writeInt32(1, 3);
        protection.writeInt32(2, 2);
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeUmpPart(output, SabrResponseDecoder.STREAM_PROTECTION_STATUS,
                protection.toByteArray());
        return output.toByteArray();
    }

    private static void writeUmpPart(@Nonnull final ByteArrayOutputStream output,
                                     final int type,
                                     @Nonnull final byte[] payload) {
        if (type >= 128 || payload.length >= 128) {
            throw new IllegalArgumentException("Test UMP part exceeds single-byte encoding");
        }
        output.write(type);
        output.write(payload.length);
        output.write(payload, 0, payload.length);
    }

    private static final class QueueDownloader extends Downloader {
        @Nonnull
        private final Deque<byte[]> responses;

        private QueueDownloader(@Nonnull final byte[]... responses) {
            this.responses = new ArrayDeque<>();
            for (final byte[] response : responses) {
                this.responses.add(Arrays.copyOf(response, response.length));
            }
        }

        @Override
        public StreamingResponse postStreaming(
                final String url,
                @Nullable final Map<String, List<String>> headers,
                @Nullable final byte[] dataToSend,
                @Nullable final Localization localization) throws IOException {
            final byte[] body = responses.pollFirst();
            if (body == null) {
                throw new IOException("No queued SABR response");
            }
            return new StreamingResponse(
                    200,
                    Collections.singletonMap("Content-Type",
                            Collections.singletonList("application/vnd.yt-ump")),
                    new ByteArrayInputStream(body));
        }

        @Override
        public Response execute(@Nonnull final Request request)
                throws IOException, ReCaptchaException {
            throw new UnsupportedOperationException();
        }

        @Override
        public CancellableCall executeAsync(@Nonnull final Request request,
                                            final AsyncCallback callback)
                throws IOException, ReCaptchaException {
            throw new UnsupportedOperationException();
        }
    }
}
