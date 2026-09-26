package org.schabi.newpipe.player

import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveQualityControllerTest {
    @Test
    fun formatsLiveQualityLabelsFromManifestFormats() {
        val format = Format.Builder()
            .setWidth(1920)
            .setHeight(1080)
            .setFrameRate(60f)
            .setCodecs("av01.0.08M.08")
            .build()

        val option = LiveQualityController.optionFromFormat(format)

        assertEquals("AV1 1080p60", option?.label)
    }

    @Test
    fun ignoresFormatsWithoutVideoHeight() {
        val format = Format.Builder()
            .setCodecs("avc1.640028")
            .build()

        assertNull(LiveQualityController.optionFromFormat(format))
    }

    @Test
    fun normalizesCommonCodecFamilies() {
        assertEquals("AV1", LiveQualityController.codecName("av01.0.08M.08"))
        assertEquals("VP9", LiveQualityController.codecName("vp09.00.51.08"))
        assertEquals("H264", LiveQualityController.codecName("avc1.640028"))
        assertEquals("HEVC", LiveQualityController.codecName("hvc1.1.6.L120"))
    }
}
