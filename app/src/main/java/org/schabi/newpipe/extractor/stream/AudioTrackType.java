package org.schabi.newpipe.extractor.stream;

/**
 * The semantic type of an {@link AudioStream}'s audio track.
 */
public enum AudioTrackType {
    /**
     * The video's original audio.
     */
    ORIGINAL,

    /**
     * Audio with the original voices replaced, typically in another language.
     */
    DUBBED,

    /**
     * Audio containing descriptions of visual elements for accessibility.
     */
    DESCRIPTIVE,

    /**
     * An alternate audio track that is neither original, dubbed nor descriptive.
     */
    SECONDARY
}
