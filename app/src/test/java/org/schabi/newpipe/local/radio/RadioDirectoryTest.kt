package org.schabi.newpipe.local.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class RadioDirectoryTest {
    private val mirror = "https://de1.api.radio-browser.info"

    @Test
    fun unfilteredSearchIsWorldwide() {
        val url = RadioDirectory.searchUrl(mirror, RadioFilters())

        listOf("name", "country", "countrycode", "language", "tag").forEach {
            assertNull(url.queryParameter(it))
        }
    }

    @Test
    fun countryAndLanguageFiltersAreOptionalAndIndependent() {
        val url = RadioDirectory.searchUrl(mirror, RadioFilters(country = " jp ", language = " Japanese "))

        assertEquals("JP", url.queryParameter("countrycode"))
        assertEquals("Japanese", url.queryParameter("language"))
        assertNull(url.queryParameter("country"))
        assertEquals(
            "Brazil",
            RadioDirectory.searchUrl(mirror, RadioFilters(country = "Brazil")).queryParameter("country")
        )
    }

    @Test
    fun parseKeepsHttpsStationsAndDetectsHls() {
        val stations = RadioDirectory.parse(
            """
            [
              {"name":"Station","country":"Japan","url":"http://example.org/stream",
               "url_resolved":"https://example.org/live.m3u8","favicon":"https://example.org/logo.png"},
              {"name":"Duplicate","url":"https://example.org/live.m3u8"},
              {"name":"Cleartext","url":"http://example.org/audio"},
              {"name":"Credentials","url":"https://name:password@example.org/audio"}
            ]
            """.trimIndent()
        )

        assertEquals(1, stations.size)
        assertEquals("Japan", stations.single().details)
        assertTrue(stations.single().hls)
        assertEquals("https://example.org/logo.png", stations.single().logoUrl)
        assertEquals(stations.single(), RadioStation.fromJson(stations.single().json()))
    }
}
