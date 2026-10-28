package org.schabi.newpipe.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LiveBufferHeadroomTest {
    @Test fun rebufferAddsUsefulHeadroomWithinManifestLimitsAndNewItemsResetIt() {
        val control = LiveBufferHeadroom.create()
        control.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(10_000).setMaxOffsetMs(20_000).build())
        assertEquals(10_000_000L, control.targetLiveOffsetUs)
        control.notifyRebuffer()
        assertEquals(15_000_000L, control.targetLiveOffsetUs)
        repeat(8) { control.notifyRebuffer() }
        assertEquals(20_000_000L, control.targetLiveOffsetUs)
        control.setLiveConfiguration(MediaItem.LiveConfiguration.UNSET)
        assertEquals(C.TIME_UNSET, control.targetLiveOffsetUs)
        control.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(10_000).build())
        assertEquals(10_000_000L, control.targetLiveOffsetUs)
    }
}
