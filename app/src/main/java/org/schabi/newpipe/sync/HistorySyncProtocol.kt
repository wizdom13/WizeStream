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

internal typealias HistorySyncRequestHandler =
    (PeerId, HistorySyncRequest, HistorySyncProtocolController) -> Unit

internal class HistorySyncProtocolBinding(
    requestHandler: HistorySyncRequestHandler
) : StrictProtocolBinding<HistorySyncProtocolController>(
    HISTORY_SYNC_PROTOCOL_ID,
    HistorySyncProtocol(requestHandler)
)

internal class HistorySyncProtocol(
    private val requestHandler: HistorySyncRequestHandler
) : ProtocolHandler<HistorySyncProtocolController>(
    MAX_PROTOCOL_BYTES,
    MAX_PROTOCOL_BYTES
) {
    override fun onStartInitiator(
        stream: Stream
    ): CompletableFuture<HistorySyncProtocolController> {
        return start(stream, true)
    }

    override fun onStartResponder(
        stream: Stream
    ): CompletableFuture<HistorySyncProtocolController> {
        return start(stream, false)
    }

    private fun start(
        stream: Stream,
        initiator: Boolean
    ): CompletableFuture<HistorySyncProtocolController> {
        val ready = CompletableFuture<Void>()
        val controller = HistorySyncProtocolController(
            stream,
            initiator,
            requestHandler,
            ready
        )
        stream.pushHandler(controller)
        return ready.thenApply { controller }
    }

    companion object {
        private const val MAX_PROTOCOL_BYTES = 2L * 1024 * 1024
    }
}

internal class HistorySyncProtocolController(
    private val stream: Stream,
    private val initiator: Boolean,
    private val requestHandler: HistorySyncRequestHandler,
    private val ready: CompletableFuture<Void>
) : ProtocolMessageHandler<ByteBuf> {
    val response = CompletableFuture<HistorySyncResponse>()
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
                    throw HistorySyncException(
                        "The history synchronization message has an invalid length"
                    )
                }
                val totalFrameLength = FRAME_LENGTH_BYTES + frameLength
                if (pendingBytes.size < totalFrameLength) {
                    return
                }
                if (handledFrame) {
                    throw HistorySyncException(
                        "The history synchronization stream sent an unexpected extra message"
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

    fun sendRequest(request: HistorySyncRequest) {
        check(initiator) { "Only the stream initiator can send a history request" }
        send(HistorySyncCodec.encodeRequest(request))
    }

    fun sendResponse(response: HistorySyncResponse) {
        check(!initiator) { "Only the stream responder can send a history response" }
        send(HistorySyncCodec.encodeResponse(response))
    }

    fun close() {
        stream.close()
    }

    override fun onClosed(stream: Stream) {
        if (initiator && !response.isDone) {
            response.completeExceptionally(
                HistorySyncException("The history synchronization connection closed")
            )
        }
    }

    override fun onException(cause: Throwable?) {
        if (initiator && !response.isDone) {
            response.completeExceptionally(
                cause ?: HistorySyncException(
                    "The history synchronization connection failed"
                )
            )
        }
    }

    private fun handleFrame(remotePeerId: PeerId, frame: ByteArray) {
        val value = frame.toString(Charsets.UTF_8)
        if (initiator) {
            response.complete(HistorySyncCodec.decodeResponse(value))
        } else {
            requestHandler(
                remotePeerId,
                HistorySyncCodec.decodeRequest(value),
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
                HistorySyncResponse(
                    accepted = false,
                    category = HistorySyncCategory.WATCH,
                    error = "Malformed history synchronization request"
                )
            )
        }
    }

    private fun send(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FRAME_BYTES) {
            throw HistorySyncException("The history synchronization message is too large")
        }
        val frame = ByteBuffer.allocate(FRAME_LENGTH_BYTES + bytes.size)
            .putInt(bytes.size)
            .put(bytes)
            .array()
        stream.writeAndFlush(frame.toByteBuf())
    }

    companion object {
        private const val FRAME_LENGTH_BYTES = Int.SIZE_BYTES
        private const val MAX_FRAME_BYTES = 1024 * 1024
    }
}

private object HistorySyncCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encodeRequest(request: HistorySyncRequest): String {
        return json.encodeToString(request)
    }

    fun decodeRequest(value: String): HistorySyncRequest {
        return try {
            json.decodeFromString(value)
        } catch (error: Exception) {
            throw HistorySyncException(
                "The history synchronization request is malformed",
                error
            )
        }
    }

    fun encodeResponse(response: HistorySyncResponse): String {
        return json.encodeToString(response)
    }

    fun decodeResponse(value: String): HistorySyncResponse {
        return try {
            json.decodeFromString(value)
        } catch (error: Exception) {
            throw HistorySyncException(
                "The history synchronization response is malformed",
                error
            )
        }
    }
}
