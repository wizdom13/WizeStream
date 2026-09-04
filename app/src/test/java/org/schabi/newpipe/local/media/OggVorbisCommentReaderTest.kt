/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.media

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OggVorbisCommentReaderTest {
    @Test
    fun `reads title artist and album from Vorbis comments`() {
        val metadata = OggVorbisCommentReader.read(
            ByteArrayInputStream(
                oggPage(
                    vorbisCommentPacket(
                        "TITLE=Hell on Earth",
                        "ARTIST=Mobb Deep",
                        "ALBUM=Hell on Earth"
                    )
                )
            )
        )

        requireNotNull(metadata)
        assertEquals("Hell on Earth", metadata.title)
        assertEquals("Mobb Deep", metadata.artist)
        assertEquals("Hell on Earth", metadata.album)
    }

    @Test
    fun `uses album artist when track artist is absent`() {
        val metadata = OggVorbisCommentReader.read(
            ByteArrayInputStream(
                oggPage(vorbisCommentPacket("TITLE=Song", "ALBUMARTIST=Various Artists"))
            )
        )

        requireNotNull(metadata)
        assertEquals("Various Artists", metadata.artist)
    }

    @Test
    fun `reads Opus tags stored in an Ogg container`() {
        val metadata = OggVorbisCommentReader.read(
            ByteArrayInputStream(
                oggPage(commentPacket("OpusTags", "TITLE=Spoken lesson", "ARTIST=Teacher"))
            )
        )

        requireNotNull(metadata)
        assertEquals("Spoken lesson", metadata.title)
        assertEquals("Teacher", metadata.artist)
    }

    @Test
    fun `rejects input that is not an Ogg stream`() {
        assertNull(
            OggVorbisCommentReader.read(
                ByteArrayInputStream("not an ogg stream".toByteArray())
            )
        )
    }

    private fun vorbisCommentPacket(vararg comments: String): ByteArray {
        return commentPacket("\u0003vorbis", *comments)
    }

    private fun commentPacket(header: String, vararg comments: String): ByteArray {
        return ByteArrayOutputStream().apply {
            write(header.toByteArray(StandardCharsets.US_ASCII))
            writeLength(4)
            write("test".toByteArray(StandardCharsets.UTF_8))
            writeLength(comments.size)
            comments.forEach { comment ->
                val bytes = comment.toByteArray(StandardCharsets.UTF_8)
                writeLength(bytes.size)
                write(bytes)
            }
        }.toByteArray()
    }

    private fun oggPage(packet: ByteArray): ByteArray {
        require(packet.size < 255)
        return ByteArrayOutputStream().apply {
            write("OggS".toByteArray(StandardCharsets.US_ASCII))
            write(byteArrayOf(0, 0))
            write(ByteArray(20))
            write(byteArrayOf(1, packet.size.toByte()))
            write(packet)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLength(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
        write(value ushr 16 and 0xff)
        write(value ushr 24 and 0xff)
    }
}
