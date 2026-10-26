package org.schabi.newpipe.player

import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveQualityControllerTest {
    private val qualities = listOf(1080, 720, 480, 360).map {
        LiveQualityController.Option(it, it * 16 / 9, 30, "H264", false)
    }

    @Test
    fun choosesDefaultWithoutExceedingMeteredLimit() {
        assertEquals(720, LiveQualityController.chooseDefault(qualities, "720p", null)?.height)
        assertEquals(480, LiveQualityController.chooseDefault(qualities, "1080p", "480p")?.height)
        assertEquals(480, LiveQualityController.chooseDefault(qualities, "best", "480p")?.height)
        assertEquals(1080, LiveQualityController.chooseDefault(qualities, "best", null)?.height)
    }

    @Test
    fun missingDefaultFallsBackButNeverExceedsDataLimit() {
        assertEquals(480, LiveQualityController.chooseDefault(qualities, "600p", null)?.height)
        assertEquals(360, LiveQualityController.chooseDefault(qualities, "144p", null)?.height)
        assertNull(LiveQualityController.chooseDefault(qualities, "720p", "144p"))
        assertNull(LiveQualityController.chooseDefault(emptyList(), "720p", null))
        assertEquals(720, LiveQualityController.resolutionHeight("720p60"))
        assertNull(LiveQualityController.resolutionHeight("auto"))
    }

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
    fun displaysActualResolutionWithoutInventingUnknownSizes() {
        assertNull(LiveQualityController.resolutionFromFormat(null))
        assertNull(LiveQualityController.resolutionFromFormat(Format.Builder().build()))
        assertEquals(
            "720p",
            LiveQualityController.resolutionFromFormat(
                Format.Builder().setHeight(720).setFrameRate(30f).build()
            )
        )
        assertEquals(
            "1080p60",
            LiveQualityController.resolutionFromFormat(
                Format.Builder().setHeight(1080).setFrameRate(59.94f).build()
            )
        )
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
