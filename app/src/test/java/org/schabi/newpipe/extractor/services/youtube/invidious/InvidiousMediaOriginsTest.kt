package org.schabi.newpipe.extractor.services.youtube.invidious

import java.io.IOException
import java.net.URI
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InvidiousMediaOriginsTest {
    @After fun reset() = InvidiousBackend.configure(false, "")

    @Test fun mediaTrustIsExplicitAndBoundToTheSelectedInstance() {
        val instance = URI("https://instance.example/latest_version?id=video")
        val companion = URI("https://media.example/videoplayback?token=private")
        InvidiousBackend.configure(true, "https://instance.example")
        assertThrows(IOException::class.java) { InvidiousBackend.checkRedirect(instance, companion) }
        InvidiousMediaOrigins.configure("https://instance.example", "https://media.example")
        InvidiousBackend.checkRedirect(instance, companion)
        InvidiousBackend.checkInstanceRequest(companion)
        org.junit.Assert.assertEquals(companion.toString(), InvidiousApi().localUrl(companion.toString()))
        InvidiousBackend.checkRedirect(companion, URI("https://media.example/segment"))
        assertThrows(IOException::class.java) { InvidiousBackend.checkRedirect(companion, URI("https://unexpected.example/media")) }
        InvidiousBackend.configure(true, "https://other.example")
        assertThrows(IOException::class.java) { InvidiousBackend.checkInstanceRequest(companion) }
    }

    @Test fun googleHostsCredentialsDowngradesAndSuffixImpersonationStayBlocked() {
        InvidiousBackend.configure(true, "https://instance.example")
        InvidiousMediaOrigins.configure("https://instance.example", "https://media.example")
        for (url in listOf("https://r1.googlevideo.com", "https://youtube.com", "http://media.example", "https://user@media.example", "https://media.example.evil.example", "https://media.example:444")) {
            assertThrows(IOException::class.java) { InvidiousBackend.checkInstanceRequest(URI(url)) }
            assertThrows(IOException::class.java) { InvidiousBackend.checkRedirect(URI("https://media.example"), URI(url)) }
        }
        for (url in listOf("https://r1.googlevideo.com", "https://youtube.com", "http://media.example", "https://user@media.example", "https://media.example/path", "https://media.example?token=secret")) {
            assertThrows(IllegalArgumentException::class.java) { InvidiousMediaOrigins.parse(url) }
        }
    }

    @Test fun acceptsSixAndSixteenDistinctServersButRejectsSeventeen() {
        val servers = (1..17).map { "https://media$it.example" }
        for (count in listOf(6, 16)) {
            val text = servers.take(count).joinToString("\n")
            org.junit.Assert.assertEquals(count, InvidiousMediaOrigins.parse(text).size)
            InvidiousBackend.configure(true, "https://instance.example")
            InvidiousMediaOrigins.configure("https://instance.example", text)
            servers.take(count).forEach { assertTrue(InvidiousMediaOrigins.allows(URI("$it/videoplayback"))) }
            assertFalse(InvidiousMediaOrigins.allows(URI(servers[count])))
        }
        org.junit.Assert.assertEquals(16, InvidiousMediaOrigins.parse((servers.take(16) + servers.first()).joinToString("\n")).size)
        assertThrows(IllegalArgumentException::class.java) { InvidiousMediaOrigins.parse(servers.joinToString("\n")) }
        InvidiousMediaOrigins.configure("https://instance.example", servers.joinToString("\n"))
        assertFalse(InvidiousMediaOrigins.allows(URI(servers.first())))
        assertThrows(IllegalArgumentException::class.java) { InvidiousMediaOrigins.parse(" ".repeat(4097)) }
    }

    @Test fun diagnosticsOnlyExposeTheOriginAndMalformedRestoresFailClosed() {
        val message = InvidiousMediaOrigins.blockedMessage(URI("https://user:secret@media.example/private?token=secret#secret"))
        assertTrue(message.contains("https://media.example"))
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("private"))
        InvidiousBackend.configure(true, "https://instance.example")
        InvidiousMediaOrigins.configure("https://instance.example", "https://media.example\ninvalid")
        assertFalse(InvidiousMediaOrigins.allows(URI("https://media.example")))
    }
}
