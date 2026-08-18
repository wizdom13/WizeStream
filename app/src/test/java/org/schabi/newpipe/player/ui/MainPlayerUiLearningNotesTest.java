package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;

public class MainPlayerUiLearningNotesTest {
    @Test
    public void hidesButtonBeforeMetadataArrives() {
        assertFalse(MainPlayerUi.shouldShowLearningNoteButton(true, true, null));
    }

    @Test
    public void showsButtonForOnDemandMetadataWhenNotesAreEnabled() {
        assertTrue(MainPlayerUi.shouldShowLearningNoteButton(
                true, true, streamInfo(StreamType.VIDEO_STREAM)));
    }

    @Test
    public void hidesButtonWhenNotesAreDisabled() {
        assertFalse(MainPlayerUi.shouldShowLearningNoteButton(
                false, true, streamInfo(StreamType.VIDEO_STREAM)));
    }

    @Test
    public void hidesButtonForLiveMetadata() {
        assertFalse(MainPlayerUi.shouldShowLearningNoteButton(
                true, true, streamInfo(StreamType.LIVE_STREAM)));
    }

    @Test
    public void hidesButtonForUnmarkedContent() {
        assertFalse(MainPlayerUi.shouldShowLearningNoteButton(
                true, false, streamInfo(StreamType.VIDEO_STREAM)));
    }

    private static StreamInfo streamInfo(final StreamType streamType) {
        final StreamInfo info = new StreamInfo();
        info.setStreamType(streamType);
        return info;
    }
}
