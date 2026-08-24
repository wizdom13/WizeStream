package us.shandian.giga.postprocessing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
public class M4aFromMp4DemuxerTest {
    @Test
    public void findsAudioTrackAfterVideoTrack() {
        assertEquals(1, M4aFromMp4Demuxer.findAudioTrackIndex(
                "video/avc", "audio/mp4a-latm"));
    }

    @Test
    public void returnsMinusOneWhenAudioTrackIsMissing() {
        assertEquals(-1, M4aFromMp4Demuxer.findAudioTrackIndex(
                "video/avc", "text/vtt"));
    }
}
