package org.schabi.newpipe.player.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDomPoTokenRequestTest {
    private val context = LocalDomPoTokenContext(
        visitorData = "visitor-test",
        clientName = "WEB",
        clientVersion = "2.test",
        userAgent = "test-user-agent"
    )

    @Test
    fun parsesInlineAttestationChallenge() {
        val response = """{"bgChallenge":{"program":"program","globalName":"global","interpreterJavascript":{"privateDoNotAccessOrElseSafeScriptWrappedValue":"script"}}}"""

        assertEquals(
            SabrAttChallengeData("program", "global", "script", null),
            parseSabrAttChallengeData(response)
        )
    }

    @Test
    fun resolvesProtocolRelativeAttestationInterpreterUrl() {
        val response = """{"bgChallenge":{"program":"program","globalName":"global","interpreterUrl":{"privateDoNotAccessOrElseTrustedResourceUrlWrappedValue":"//example.test/interpreter.js"}}}"""

        assertEquals(
            SabrAttChallengeData(
                "program",
                "global",
                null,
                "https://example.test/interpreter.js"
            ),
            parseSabrAttChallengeData(response)
        )
    }

    @Test
    fun cacheIdentityDoesNotCrossClientContexts() {
        assertNotEquals(
            context.cacheIdentity,
            context.copy(clientName = "MWEB").cacheIdentity
        )
        assertNotEquals(
            context.cacheIdentity,
            context.copy(clientVersion = "3.test").cacheIdentity
        )
        assertNotEquals(
            context.cacheIdentity,
            context.copy(visitorData = "other-visitor").cacheIdentity
        )
        assertNotEquals(
            context.cacheIdentity,
            context.copy(userAgent = "different-user-agent").cacheIdentity
        )
    }
}
