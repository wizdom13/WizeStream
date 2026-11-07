package org.schabi.newpipe.player

import androidx.media3.common.C
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMedia403RecoveryPolicyTest {
    private val policy = LiveMedia403RecoveryPolicy()
    private val key = "youtube:live-one"

    @Test
    fun refreshesAfterRepeatedRejectionOfOneTrackAndSegment() {
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 0))
        assertFalse(reject(C.TRACK_TYPE_VIDEO, 6_216_000, 100))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 300))
        assertTrue(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 900))
    }

    @Test
    fun successfulRetryAndNewSegmentDoNotTriggerPrematureRefresh() {
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 0))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 300))
        policy.onLoadedSegment(key, C.TRACK_TYPE_AUDIO, 6_216_000, 400)
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 600))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_218_000, 900))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_218_000, 1_100))
        assertTrue(reject(C.TRACK_TYPE_AUDIO, 6_218_000, 1_500))
    }

    @Test
    fun successfulLoadWhileBufferingClearsTheFailedSegment() {
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 0))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 300))
        policy.onLoadedSegment(key, C.TRACK_TYPE_AUDIO, 6_216_000, 400, isReady = false)
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 600))
    }

    @Test
    fun cooldownAndBudgetPreventReloadLoopUntilPlaybackIsStable() {
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 0))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 300))
        assertTrue(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 900))

        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_218_000, 1_000))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_218_000, 1_300))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_218_000, 1_900))

        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_220_000, 16_000))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_220_000, 16_300))
        assertTrue(reject(C.TRACK_TYPE_AUDIO, 6_220_000, 16_900))

        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_222_000, 33_000))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_222_000, 33_300))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_222_000, 33_900))

        policy.onLoadedSegment(key, C.TRACK_TYPE_AUDIO, 6_224_000, 34_000)
        policy.onLoadedSegment(key, C.TRACK_TYPE_AUDIO, 6_226_000, 64_000)
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_228_000, 65_000))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_228_000, 65_300))
        assertTrue(reject(C.TRACK_TYPE_AUDIO, 6_228_000, 65_900))
    }

    @Test
    fun anotherBroadcastGetsItsOwnRecoveryBudget() {
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 0))
        assertFalse(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 300))
        assertTrue(reject(C.TRACK_TYPE_AUDIO, 6_216_000, 900))
        assertFalse(policy.onRejectedSegment("youtube:live-two", C.TRACK_TYPE_AUDIO, 0, 1_000))
        assertFalse(policy.onRejectedSegment("youtube:live-two", C.TRACK_TYPE_AUDIO, 0, 1_300))
        assertTrue(policy.onRejectedSegment("youtube:live-two", C.TRACK_TYPE_AUDIO, 0, 1_900))
    }

    private fun reject(trackType: Int, mediaStartMs: Long, nowMs: Long): Boolean {
        return policy.onRejectedSegment(key, trackType, mediaStartMs, nowMs)
    }
}
