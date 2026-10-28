package org.schabi.newpipe.player

import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl

/** Give live playback room to recover from multi-second network stalls without replacing its source. */
object LiveBufferHeadroom {
    fun create(): DefaultLivePlaybackSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
        // Media3's 500 ms default recovers little headroom after a several-second interruption.
        // Keep its normal initial offset, manifest limits, stable-playback decay and seek handling.
        .setTargetLiveOffsetIncrementOnRebufferMs(5_000)
        .build()
}
