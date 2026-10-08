package org.schabi.newpipe.player.helper

import android.net.Uri
import androidx.media3.common.ParserException
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import java.io.IOException
import java.io.InputStream
import org.schabi.newpipe.player.datasource.InvidiousMediaResponse
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** Explains unusable instance manifests without changing their live timing or segment URLs. */
class InvidiousDashManifestParser : DashManifestParser() {
    override fun parse(uri: Uri, inputStream: InputStream): DashManifest {
        val manifest = try {
            super.parse(uri, inputStream)
        } catch (error: ParserException) {
            throw invalidManifest(uri, error)
        } catch (error: IllegalArgumentException) {
            throw invalidManifest(uri, error)
        }
        val hasRepresentations = (0 until manifest.periodCount).any { index ->
            manifest.getPeriod(index).adaptationSets.any { it.representations.isNotEmpty() }
        }
        if (!hasRepresentations) {
            throw invalidManifest(uri, null)
        }
        return manifest
    }

    override fun parseMediaPresentationDescription(parser: XmlPullParser, documentBaseUri: Uri): DashManifest {
        return super.parseMediaPresentationDescription(CompleteXmlParser(parser), documentBaseUri)
    }

    // Media3's nested element loops expect closing tags. Some XML parsers return END_DOCUMENT
    // indefinitely for a truncated response, so reject that event before a loop can stall.
    private class CompleteXmlParser(private val parser: XmlPullParser) : XmlPullParser by parser {
        override fun next(): Int = requireElement(parser.next())

        override fun nextToken(): Int = requireElement(parser.nextToken())

        private fun requireElement(event: Int): Int {
            if (event == XmlPullParser.END_DOCUMENT) {
                throw XmlPullParserException("Incomplete Invidious DASH manifest")
            }
            return event
        }
    }

    private fun invalidManifest(uri: Uri, cause: Throwable?) = InvidiousMediaResponse.InvalidResponseException(
        IOException("The Invidious instance returned an unusable DASH manifest", cause),
        DataSpec.Builder().setUri(uri).build()
    )
}
