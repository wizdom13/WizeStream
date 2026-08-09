/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// jvm-libp2p's CompletableFuture API is backported by coreLibraryDesugaring on Android.
@file:Suppress("NewApi")

package org.schabi.newpipe.sync

import io.libp2p.core.PeerId
import io.libp2p.core.Stream
import io.libp2p.core.multistream.StrictProtocolBinding
import io.libp2p.etc.types.toByteBuf
import io.libp2p.protocol.ProtocolHandler
import io.libp2p.protocol.ProtocolMessageHandler
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal typealias PlaylistSyncRequestHandler =
    (PeerId, PlaylistSyncRequest, PlaylistSyncProtocolController) -> Unit

internal class PlaylistSyncProtocolBinding(
    requestHandler: PlaylistSyncRequestHandler
) : StrictProtocolBinding<PlaylistSyncProtocolController>(
    PLAYLIST_SYNC_PROTOCOL_ID,
    PlaylistSyncProtocol(requestHandler)
)

internal class PlaylistSyncProtocol(
    private val requestHandler: PlaylistSyncRequestHandler
) : ProtocolHandler<PlaylistSyncProtocolController>(
    MAX_PROTOCOL_BYTES,
    MAX_PROTOCOL_BYTES
) {
    override fun onStartInitiator(
        stream: Stream
    ): CompletableFuture<PlaylistSyncProtocolController> {
        return start(stream, true)
    }

    override fun onStartResponder(
        stream: Stream
    ): CompletableFuture<PlaylistSyncProtocolController> {
        return start(stream, false)
    }

    private fun start(
        stream: Stream,
        initiator: Boolean
    ): CompletableFuture<PlaylistSyncProtocolController> {
        val ready = CompletableFuture<Void>()
        val controller = PlaylistSyncProtocolController(
            stream,
            initiator,
            requestHandler,
            ready
        )
        stream.pushHandler(controller)
        return ready.thenApply { controller }
    }

    companion object {
        private const val MAX_PROTOCOL_BYTES = 8 * 1024 * 1024L
    }
}

internal class PlaylistSyncProtocolController(
    private val stream: Stream,
    private val initiator: Boolean,
    private val requestHandler: PlaylistSyncRequestHandler,
    private val ready: CompletableFuture<Void>
) : ProtocolMessageHandler<ByteBuf> {
    val response = CompletableFuture<PlaylistSyncResponse>()
    private var pendingBytes = ByteArray(0)
    private var handledFrame = false

    override fun onActivated(stream: Stream) {
        ready.complete(null)
    }

    override fun onMessage(stream: Stream, msg: ByteBuf) {
        try {
            val received = ByteArray(msg.readableBytes())
            msg.readBytes(received)
            pendingBytes += received
            while (pendingBytes.size >= FRAME_LENGTH_BYTES) {
                val frameLength = ByteBuffer.wrap(
                    pendingBytes,
                    0,
                    FRAME_LENGTH_BYTES
                ).int
                if (frameLength !in 1..MAX_FRAME_BYTES) {
                    throw PlaylistSyncException(
                        "The playlist synchronization message has an invalid length"
                    )
                }
                val totalFrameLength = FRAME_LENGTH_BYTES + frameLength
                if (pendingBytes.size < totalFrameLength) {
                    return
                }
                if (handledFrame) {
                    throw PlaylistSyncException(
                        "The playlist synchronization stream sent an unexpected extra message"
                    )
                }
                val frame = pendingBytes.copyOfRange(
                    FRAME_LENGTH_BYTES,
                    totalFrameLength
                )
                pendingBytes = pendingBytes.copyOfRange(
                    totalFrameLength,
                    pendingBytes.size
                )
                handledFrame = true
                handleFrame(stream.remotePeerId(), frame)
            }
        } catch (error: Exception) {
            handleFailure(error)
        }
    }

    fun sendRequest(request: PlaylistSyncRequest) {
        check(initiator) { "Only the stream initiator can send a playlist sync request" }
        send(PlaylistSyncCodec.encodeRequest(request))
    }

    fun sendResponse(response: PlaylistSyncResponse) {
        check(!initiator) { "Only the stream responder can send a playlist sync response" }
        send(PlaylistSyncCodec.encodeResponse(response))
    }

    fun close() {
        stream.close()
    }

    override fun onClosed(stream: Stream) {
        if (initiator && !response.isDone) {
            response.completeExceptionally(
                PlaylistSyncException("The playlist synchronization connection closed")
            )
        }
    }

    override fun onException(cause: Throwable?) {
        if (initiator && !response.isDone) {
            response.completeExceptionally(
                cause ?: PlaylistSyncException(
                    "The playlist synchronization connection failed"
                )
            )
        }
    }

    private fun handleFrame(remotePeerId: PeerId, frame: ByteArray) {
        val value = frame.toString(Charsets.UTF_8)
        if (initiator) {
            response.complete(PlaylistSyncCodec.decodeResponse(value))
        } else {
            requestHandler(
                remotePeerId,
                PlaylistSyncCodec.decodeRequest(value),
                this
            )
        }
    }

    private fun handleFailure(error: Exception) {
        if (initiator) {
            response.completeExceptionally(error)
            return
        }
        runCatching {
            sendResponse(
                PlaylistSyncResponse(
                    accepted = false,
                    error = "Malformed playlist synchronization request"
                )
            )
        }
    }

    private fun send(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FRAME_BYTES) {
            throw PlaylistSyncException(
                "The playlist synchronization message is too large"
            )
        }
        val frame = ByteBuffer.allocate(FRAME_LENGTH_BYTES + bytes.size)
            .putInt(bytes.size)
            .put(bytes)
            .array()
        stream.writeAndFlush(frame.toByteBuf())
    }

    companion object {
        private const val FRAME_LENGTH_BYTES = Int.SIZE_BYTES
        private const val MAX_FRAME_BYTES = 4 * 1024 * 1024
    }
}

private object PlaylistSyncCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encodeRequest(request: PlaylistSyncRequest): String {
        return json.encodeToString(request)
    }

    fun decodeRequest(value: String): PlaylistSyncRequest {
        return try {
            json.decodeFromString(value)
        } catch (error: Exception) {
            throw PlaylistSyncException(
                "The playlist synchronization request is malformed",
                error
            )
        }
    }

    fun encodeResponse(response: PlaylistSyncResponse): String {
        return json.encodeToString(response)
    }

    fun decodeResponse(value: String): PlaylistSyncResponse {
        return try {
            json.decodeFromString(value)
        } catch (error: Exception) {
            throw PlaylistSyncException(
                "The playlist synchronization response is malformed",
                error
            )
        }
    }
}
