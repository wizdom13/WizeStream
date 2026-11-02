package org.schabi.newpipe.util.image

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CoilHelperBitmapConfigTest {
    @Test
    @Config(sdk = [29])
    fun android10UsesSoftwareBitmapsForSoftwareRenderedDetailLayers() {
        assertTrue(requiresSoftwareBitmap(RuntimeEnvironment.getApplication()))
    }

    @Test
    @Config(sdk = [28, 30])
    fun otherAndroidVersionsKeepHardwareBitmapsWithoutGlassTheme() {
        assertFalse(requiresSoftwareBitmap(RuntimeEnvironment.getApplication()))
    }
}
