package org.schabi.newpipe.extractor.services.youtube.invidious

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class InvidiousImageUrlsTest {
    private val base = "https://instance.example"

    @Before fun setup() = InvidiousBackend.configure(true, base)

    @After fun reset() = InvidiousBackend.configure(false, "")

    @Test fun webpAndMaxresUseTheInstancesSupportedJpegProxy() {
        val api = InvidiousApi()
        assertEquals("$base/vi/video/hqdefault.jpg?x=1", api.image("https://i.ytimg.com/vi_webp/video/hqdefault.webp?x=1"))
        assertEquals("$base/vi/video/maxres.jpg", api.image("//i.ytimg.com/vi/video/maxresdefault.jpg"))
        assertEquals("$base/vi/video/maxres.jpg", api.image("/vi_webp/video/maxresdefault.webp"))
        assertEquals("$base/ggpht/avatar", api.image("https://yt3.ggpht.com/avatar"))
        assertEquals("$base/vi/video/hqdefault.jpg", InvidiousBackend.routeImage("https://i.ytimg.com/vi_webp/video/hqdefault.webp", base))
    }

    @Test fun onlyExplicitlyTrustedExternalImagesAreAccepted() {
        val api = InvidiousApi()
        val image = "https://media.example/image.jpg"
        assertEquals("", api.image(image))
        InvidiousMediaOrigins.configure(base, "https://media.example")
        assertEquals(image, api.image(image))
        for (url in listOf("http://media.example/image.jpg", "https://media.example.evil/image.jpg", "https://user@media.example/image.jpg", "https://youtube.com/image.jpg")) {
            assertEquals("", api.image(url))
        }
        InvidiousBackend.configure(true, "https://other.example")
        assertEquals("", api.image(image))
    }

    @Test fun malformedThumbnailDoesNotDiscardOtherCandidates() {
        val images = JsonArray()
        images.add(JsonObject().apply { put("url", "https://broken host/image") })
        images.add(
            JsonObject().apply {
                put("url", "/vi/video/hqdefault.jpg")
                put("height", 360)
            }
        )
        val parsed = InvidiousApi().images(images)
        assertEquals(1, parsed.size)
        assertEquals("$base/vi/video/hqdefault.jpg", parsed[0].url)
    }
}
