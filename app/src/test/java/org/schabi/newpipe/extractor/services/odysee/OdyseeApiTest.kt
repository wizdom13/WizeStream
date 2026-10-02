package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs

class OdyseeApiTest {
    private val oldDownloader = NewPipe.getDownloader()
    private val oldLocalization = NewPipe.getPreferredLocalization()
    private val oldCountry = NewPipe.getPreferredContentCountry()
    private var respond: (JsonObject) -> String = { "{\"result\":{}}" }
    private val requests = mutableListOf<JsonObject>()

    @Before
    fun setUp() {
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                assertEquals(OdyseeConstants.SDK_PROXY, request.url())
                val body = JsonParser.`object`().from(String(request.dataToSend()!!, Charsets.UTF_8))
                requests += body
                val result = respond(body)
                return Response(200, "OK", emptyMap(), result, result.toByteArray(), request.url())
            }
        })
    }

    @After
    fun tearDown() {
        NewPipe.init(oldDownloader, oldLocalization, oldCountry)
    }

    @Test
    fun serializesNestedParametersAndEscapedSearchTextAsJson() {
        val text = "quoted \"video\"\nالعربية"
        OdyseeApi.rpc(
            "claim_search",
            JsonObject().apply {
                put("text", text)
                put("page", 2)
                put("has_source", true)
                put("claim_type", listOf("stream"))
            }
        )
        val request = requests.single()
        assertEquals("2.0", request.getString("jsonrpc"))
        assertEquals("claim_search", request.getString("method"))
        val params = request.getObject("params")
        assertEquals(text, params.getString("text"))
        assertEquals(2, params.getInt("page"))
        assertTrue(params.getBoolean("has_source"))
        assertEquals("stream", params.getArray("claim_type").getString(0))
    }

    @Test
    fun reportsRpcErrorsAndMissingClaimsAsExtractionErrors() {
        for (response in listOf("{\"error\":{\"message\":\"Not found\"}}", "{}", "not JSON")) {
            respond = { response }
            assertThrows(ExtractionException::class.java) { OdyseeApi.rpc("resolve", JsonObject()) }
        }
        respond = { "{\"result\":{}}" }
        assertThrows(ExtractionException::class.java) { OdyseeApi.resolveClaim("lbry://@channel#abc") }
    }

    @Test
    fun channelVideosAndFreshExtractorContinuationUseTheAppFeedContract() {
        respond = { request ->
            val params = request.getObject("params")
            val result = if (request.getString("method") == "resolve") {
                JsonObject().apply {
                    put(
                        params.getArray("urls").getString(0),
                        JsonObject().apply {
                            put("claim_id", "abc")
                            put("name", "@channel")
                            put("value", JsonObject().apply { put("title", "Channel") })
                        }
                    )
                }
            } else {
                assertEquals("abc", params.getArray("channel_ids").getString(0))
                val count = if (params.getInt("page") == 1) OdyseeConstants.PAGE_SIZE else 1
                JsonObject().apply {
                    put(
                        "items",
                        (0 until count).map { index ->
                            JsonObject().apply {
                                put("name", "video-$index")
                                put("canonical_url", "lbry://video-$index#def")
                                put("timestamp", 1790000000)
                                put(
                                    "value",
                                    JsonObject().apply {
                                        put("title", "Video $index")
                                        put("video", JsonObject().apply { put("duration", 120) })
                                    }
                                )
                            }
                        }
                    )
                }
            }
            JsonWriter.string(JsonObject().apply { put("result", result) })
        }
        val channel = ChannelInfo.getInfo(ServiceList.Odysee, "https://odysee.com/@channel:abc")
        val tab = channel.tabs.single()
        assertEquals(ChannelTabs.VIDEOS, tab.contentFilters.single().name)
        val first = ChannelTabInfo.getInfo(ServiceList.Odysee, tab)
        assertEquals(20, first.relatedItems.size)
        assertTrue(first.errors.isEmpty())
        // ChannelTabInfo.getMoreItems creates a new extractor without fetching the first page.
        val second = ChannelTabInfo.getMoreItems(ServiceList.Odysee, tab, first.nextPage)
        assertEquals(1, second.items.size)
        assertFalse(second.hasNextPage())
        assertTrue(second.errors.isEmpty())
        assertEquals(listOf(1, 2), requests.filter { it.getString("method") == "claim_search" }.map { it.getObject("params").getInt("page") })
    }
}
