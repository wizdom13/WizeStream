package org.schabi.newpipe.util.image

import coil3.request.allowHardware
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CoilHelperSoftwareImagesTest {
    @After
    fun restoreImages() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @Test
    @Config(sdk = [29])
    fun android10RequestsSoftwareBitmapsWithAnIsolatedMemoryCache() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        val url = "https://example.com/avatar.jpg"
        val request = CoilHelper.getImageRequest(RuntimeEnvironment.getApplication(), url, 0).build()
        assertFalse(request.allowHardware)
        assertEquals("wizestream-software-backdrop:$url", request.memoryCacheKey)
        assertEquals(url, request.diskCacheKey)
    }

    @Test
    @Config(sdk = [28, 30])
    fun ordinaryThemesOnOtherAndroidVersionsKeepHardwareDecoding() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        val url = "https://example.com/avatar.jpg"
        val request = CoilHelper.getImageRequest(RuntimeEnvironment.getApplication(), url, 0).build()
        assertTrue(request.allowHardware)
        assertEquals(url, request.memoryCacheKey)
    }
}
