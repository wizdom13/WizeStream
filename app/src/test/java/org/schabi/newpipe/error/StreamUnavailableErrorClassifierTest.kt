package org.schabi.newpipe.error

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ExtractionException

class StreamUnavailableErrorClassifierTest {
    @Test
    fun recognizesKnownTakedownReasonsInsideExtractionErrors() {
        assertTrue(
            StreamUnavailableErrorClassifier.isKnownUnavailable(
                ExtractionException("This video has been removed due to a copyright claim")
            )
        )
        assertTrue(
            StreamUnavailableErrorClassifier.isKnownUnavailable(
                ExtractionException("Video unavailable: removed for Community Guidelines")
            )
        )
    }

    @Test
    fun ordinaryParserFailuresRemainParserErrors() {
        assertFalse(
            StreamUnavailableErrorClassifier.isKnownUnavailable(
                ExtractionException("Could not parse player response")
            )
        )
    }
}
