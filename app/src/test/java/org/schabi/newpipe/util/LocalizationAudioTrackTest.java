package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import org.junit.Test;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;

public class LocalizationAudioTrackTest {
    @Test
    public void audioTrackNameAppendsLocalizedStructuredType() {
        final Context context = mock(Context.class);
        final AudioTrackType[] types = {
                AudioTrackType.ORIGINAL,
                AudioTrackType.DUBBED,
                AudioTrackType.DESCRIPTIVE,
                AudioTrackType.SECONDARY
        };
        final int[] resourceIds = {
                R.string.audio_track_type_original,
                R.string.audio_track_type_dubbed,
                R.string.audio_track_type_descriptive,
                R.string.audio_track_type_secondary
        };
        final String[] labels = {"original", "dubbed", "descriptive", "secondary"};

        for (int i = 0; i < types.length; i++) {
            when(context.getString(resourceIds[i])).thenReturn(labels[i]);
            when(context.getString(R.string.audio_track_name, "English", labels[i]))
                    .thenReturn("English (" + labels[i] + ")");

            final AudioStream track = new AudioStream.Builder()
                    .setId("track-" + i)
                    .setContent("", true)
                    .setMediaFormat(MediaFormat.M4A)
                    .setAudioTrackName("English")
                    .setAudioTrackType(types[i])
                    .build();

            assertEquals("English (" + labels[i] + ")",
                    Localization.audioTrackName(context, track));
        }
    }

    @Test
    public void audioTrackNameDoesNotInventTypeWhenMetadataIsMissing() {
        final Context context = mock(Context.class);
        final AudioStream track = new AudioStream.Builder()
                .setId("track")
                .setContent("", true)
                .setMediaFormat(MediaFormat.M4A)
                .setAudioTrackName("English")
                .build();

        assertEquals("English", Localization.audioTrackName(context, track));
    }
}
