package org.schabi.newpipe.settings

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.settings.InvidiousInstanceDirectory.ResponseFailure

class InvidiousInstanceDirectoryTest {
    @Test
    fun loadRetainsHttpStatusAndRequestsOnlyTheOfficialDirectory() {
        for (status in listOf(403, 429, 503)) {
            val requests = mutableListOf<String>()
            val error = assertThrows(ResponseFailure.Http::class.java) {
                InvidiousInstanceDirectory.load(downloader(status, "[]", requests = requests))
            }
            assertEquals(status, error.status)
            assertEquals(listOf("https://api.invidious.io/instances.json"), requests)
        }
    }

    @Test
    fun loadDistinguishesInvalidResponsesFromAnEmptyDirectory() {
        val responses = listOf(
            Triple("<html>Network sign-in</html>", "Text/HTML; charset=UTF-8", ResponseFailure.Reason.HTML),
            Triple("not JSON", "application/json", ResponseFailure.Reason.INVALID_JSON),
            Triple(" ".repeat(512_001), "application/json", ResponseFailure.Reason.TOO_LARGE)
        )
        for ((body, type, reason) in responses) {
            val error = assertThrows(ResponseFailure.Invalid::class.java) {
                InvidiousInstanceDirectory.load(downloader(200, body, type))
            }
            assertEquals(reason, error.reason)
        }
        assertTrue(InvidiousInstanceDirectory.load(downloader(200, "[]")).isEmpty())
    }

    @Test
    fun loadPreservesTransportFailuresForDiagnosis() {
        val cause = IOException("transport failed")
        val downloader = object : Downloader() {
            override fun execute(request: Request): Response = throw cause

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw AssertionError("Unexpected async directory request")
        }
        assertSame(cause, assertThrows(IOException::class.java) { InvidiousInstanceDirectory.load(downloader) })
    }

    @Test
    fun keepsOnlyHealthyOfficialHttpsApiInstances() {
        val entries = InvidiousInstanceDirectory.parse(
            """
            [
              ["good.example", {
                "flag": "🇩🇪",
                "region": "de",
                "api": true,
                "type": "https",
                "uri": "HTTPS://GOOD.EXAMPLE/",
                "monitor": {
                  "uptime": 99.5,
                  "down": false,
                  "enabled": true,
                  "published": true,
                  "last_status": 200,
                  "ssl": {"valid": true}
                }
              }],
              ["no-api.example", {
                "api": false,
                "type": "https",
                "uri": "https://no-api.example",
                "monitor": {
                  "uptime": 100,
                  "down": false,
                  "enabled": true,
                  "published": true,
                  "last_status": 200,
                  "ssl": {"valid": true}
                }
              }],
              ["down.example", {
                "api": true,
                "type": "https",
                "uri": "https://down.example",
                "monitor": {
                  "uptime": 99,
                  "down": true,
                  "enabled": true,
                  "published": true,
                  "last_status": 503,
                  "ssl": {"valid": true}
                }
              }],
              ["low-uptime.example", {
                "api": true,
                "type": "https",
                "uri": "https://low-uptime.example",
                "monitor": {
                  "uptime": 89.9,
                  "down": false,
                  "enabled": true,
                  "published": true,
                  "last_status": 200,
                  "ssl": {"valid": true}
                }
              }],
              ["bad-tls.example", {
                "api": true,
                "type": "https",
                "uri": "https://bad-tls.example",
                "monitor": {
                  "uptime": 99,
                  "down": false,
                  "enabled": true,
                  "published": true,
                  "last_status": 200,
                  "ssl": {"valid": false}
                }
              }]
            ]
            """.trimIndent()
        )

        assertEquals(1, entries.size)
        assertEquals("https://good.example", entries.single().url)
        assertEquals("good.example", entries.single().host)
        assertEquals("🇩🇪", entries.single().flag)
        assertEquals("DE", entries.single().region)
        assertEquals(99.5, entries.single().uptime, 0.0)
    }

    private fun downloader(status: Int, body: String, type: String = "application/json", requests: MutableList<String> = mutableListOf()) = object : Downloader() {
        override fun execute(request: Request): Response {
            requests += request.url()
            return Response(status, "", mapOf("Content-Type" to listOf(type)), body, body.toByteArray(), request.url())
        }

        override fun executeAsync(request: Request, callback: AsyncCallback) = throw AssertionError("Unexpected async directory request")
    }
}
