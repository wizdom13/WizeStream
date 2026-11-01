package org.schabi.newpipe.extractor.services.bandcamp

import com.grack.nanojson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class BandcampSearchTest {
    private val oldDownloader = NewPipe.getDownloader()
    private val oldLocalization = NewPipe.getPreferredLocalization()
    private val oldCountry = NewPipe.getPreferredContentCountry()
    private val requests = mutableListOf<Request>()
    private var response = ""

    @Before
    fun setUp() {
        NewPipe.init(object : Downloader() {
            override fun executeAsync(request: Request, callback: AsyncCallback): CancellableCall = throw UnsupportedOperationException()

            override fun execute(request: Request): Response {
                requests += request
                return Response(200, "OK", emptyMap(), response, response.toByteArray(), request.url())
            }
        })
    }

    @After
    fun tearDown() {
        NewPipe.init(oldDownloader, oldLocalization, oldCountry)
    }

    @Test
    fun initialSearchAndPaginationSendCompatibilityCookieAndExtractResults() {
        response = """
            <ul><li class="searchresult">
              <div class="art"><img src="https://example.com/art.jpg"></div>
              <div class="result-info"><div class="itemtype">ARTIST</div>
                <div class="heading">Fixture artist</div>
                <div class="itemurl">https://fixture.bandcamp.com</div>
                <div class="subhead">Music</div>
              </div>
            </li></ul>
            <ul class="pagelist"><li><span>1</span></li><li><a>2</a></li></ul>
        """.trimIndent()
        val extractor = ServiceList.Bandcamp.getSearchExtractor("fixture")
        extractor.fetchPage()
        val first = extractor.initialPage
        assertEquals("Fixture artist", first.items.single().name)
        assertTrue(first.errors.isEmpty())
        val next = first.nextPage
        assertTrue(next.url.endsWith("page=2"))
        assertEquals("Fixture artist", extractor.getPage(Page(next.url)).items.single().name)
        assertEquals(2, requests.size)
        for (request in requests) {
            assertEquals("GET", request.httpMethod())
            assertEquals(listOf("identity"), request.headers()["Cookie"])
        }
    }

    @Test
    fun suggestionsUseJsonPostAndPreserveUnicodeAndQuotes() {
        response = """{"auto":{"results":[{"name":"Artist"},{"name":"Album"},{"name":"Artist"}]}}"""
        val query = "quoted \"music\"\nالعربية"
        assertEquals(listOf("Artist", "Album"), ServiceList.Bandcamp.suggestionExtractor.suggestionList(query))
        val request = requests.single()
        assertEquals("POST", request.httpMethod())
        assertEquals("https://bandcamp.com/api/bcsearch_public_api/1/autocomplete_elastic", request.url())
        assertEquals(listOf("application/json"), request.headers()["Content-Type"])
        assertFalse(request.headers().containsKey("Cookie"))
        val body = JsonParser.`object`().from(String(request.dataToSend()!!, Charsets.UTF_8))
        assertEquals(query, body.getString("search_text"))
        assertTrue(body.containsKey("fan_id"))
        assertEquals(null, body["fan_id"])
        assertFalse(body.getBoolean("full_page"))
        assertEquals("", body.getString("search_filter"))
    }

    @Test
    fun malformedOrEmptySuggestionsReturnAnEmptyList() {
        for (body in listOf("not JSON", "{}", "{\"auto\":{\"results\":[]}}")) {
            response = body
            assertTrue(ServiceList.Bandcamp.suggestionExtractor.suggestionList("fixture").isEmpty())
        }
    }
}
