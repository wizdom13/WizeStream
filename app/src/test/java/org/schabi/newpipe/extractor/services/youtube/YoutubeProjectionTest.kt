package org.schabi.newpipe.extractor.services.youtube

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeProjectionTest {
    @Test
    fun identifiesSphericalFormatsAcrossClientsWithoutGuessingFromStereoLayout() {
        val flat = JsonParser.`object`().from("""{"formats":[{"projectionType":"RECTANGULAR","stereoLayout":"MONO"}]}""")
        val sphere = JsonParser.`object`().from("""{"adaptiveFormats":[{"mimeType":"audio/mp4"},{"projectionType":"EQUIRECTANGULAR"}]}""")
        val mesh = JsonParser.`object`().from("""{"formats":[{"projectionType":"MESH"}]}""")
        assertFalse(YoutubeProjection.isSpherical(null, flat))
        assertTrue(YoutubeProjection.isSpherical(null, flat, sphere))
        assertTrue(YoutubeProjection.isSpherical(mesh))
        assertFalse(YoutubeProjection.isSpherical(JsonParser.`object`().from("{}")))
    }
}
