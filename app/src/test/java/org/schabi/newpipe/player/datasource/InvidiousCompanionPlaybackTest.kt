package org.schabi.newpipe.player.datasource

import android.app.Application
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousMediaOrigins
import org.schabi.newpipe.network.AppProxySelector

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class InvidiousCompanionPlaybackTest {
    @After fun reset() = InvidiousBackend.configure(false, "")

    @Test fun trustedRedirectPreservesRangeButDoesNotForwardInstanceCredentials() {
        InvidiousBackend.configure(true, "https://instance.example")
        InvidiousMediaOrigins.configure("https://instance.example", "https://media.example")
        val initial = URL("https://instance.example/latest_version?id=video")
        val redirected = URL("https://media.example/videoplayback?token=media")
        val first = mock(HttpURLConnection::class.java)
        val second = mock(HttpURLConnection::class.java)
        `when`(first.responseCode).thenReturn(302)
        `when`(first.getHeaderField("Location")).thenReturn(redirected.toString())
        `when`(second.responseCode).thenReturn(206)
        `when`(second.responseMessage).thenReturn("Partial Content")
        `when`(second.getHeaderField("Content-Length")).thenReturn("3")
        `when`(second.inputStream).thenReturn(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        mockStatic(AppProxySelector::class.java).use { connections ->
            connections.`when`<HttpURLConnection> { AppProxySelector.openConnection(initial) }.thenReturn(first)
            connections.`when`<HttpURLConnection> { AppProxySelector.openConnection(redirected) }.thenReturn(second)
            val source = YoutubeHttpDataSource.Factory().createDataSource()
            val spec = DataSpec.Builder().setUri(initial.toString()).setPosition(100).setLength(3)
                .setHttpRequestHeaders(mapOf("Authorization" to "secret", "Cookie" to "session=secret")).build()
            try {
                assertEquals(3L, source.open(spec))
                assertEquals(3, source.read(ByteArray(3), 0, 3))
                verify(first).setRequestProperty("Authorization", "secret")
                verify(second, never()).setRequestProperty("Authorization", "secret")
                verify(second, never()).setRequestProperty("Cookie", "session=secret")
                verify(second).setRequestProperty("Range", "bytes=100-102")
                verify(second).requestMethod = "GET"
            } finally {
                source.close()
            }
        }
    }
}
