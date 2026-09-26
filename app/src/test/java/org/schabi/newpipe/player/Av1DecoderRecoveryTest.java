package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.PlaybackException;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

public class Av1DecoderRecoveryTest {
    @Test
    public void decoderInitRecoveryAppliesOnlyToAv1VideoStreams() throws Exception {
        final VideoStream av1 = new VideoStream.Builder()
                .setId("398")
                .setContent("https://media.example.com/video", true)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setResolution("720p")
                .setCodec("av01.0.05M.08")
                .setFps(30)
                .setIsVideoOnly(true)
                .setItagItem(ItagItem.getItag(398))
                .build();
        final VideoStream avc = new VideoStream.Builder()
                .setId("136")
                .setContent("https://media.example.com/video", true)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setResolution("720p")
                .setCodec("avc1.4d401f")
                .setFps(30)
                .setIsVideoOnly(true)
                .setItagItem(ItagItem.getItag(136))
                .build();

        assertTrue(PlayerHttpErrorRecovery.isRecoverableAv1DecoderInitFailure(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, av1));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableAv1DecoderInitFailure(
                PlaybackException.ERROR_CODE_TIMEOUT, av1));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableAv1DecoderInitFailure(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, avc));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableAv1DecoderInitFailure(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, null));
    }

    @Test
    public void decoderRecoveryGuardAllowsOnlyOneAttemptPerVideo() {
        final PlayerHttpErrorRecovery.OneShotRecoveryGuard guard =
                new PlayerHttpErrorRecovery.OneShotRecoveryGuard();

        assertTrue(guard.acquire("youtube:https://example.com/watch?v=one"));
        assertFalse(guard.acquire("youtube:https://example.com/watch?v=one"));
        assertTrue(guard.acquire("youtube:https://example.com/watch?v=two"));
        assertFalse(guard.acquire("youtube:https://example.com/watch?v=two"));

        guard.reset();
        assertTrue(guard.acquire("youtube:https://example.com/watch?v=two"));
    }
}
