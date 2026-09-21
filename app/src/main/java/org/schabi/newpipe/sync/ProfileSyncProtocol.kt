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

internal typealias ProfileSyncRequestHandler =
    (PeerId, ProfileSyncRequest, ProfileSyncProtocolController) -> Unit

internal class ProfileSyncProtocolBinding(
    requestHandler: ProfileSyncRequestHandler
) : StrictProtocolBinding<ProfileSyncProtocolController>(
    PROFILE_SYNC_PROTOCOL_ID,
    ProfileSyncProtocol(requestHandler)
)

internal class ProfileSyncProtocol(
    private val requestHandler: ProfileSyncRequestHandler
) : ProtocolHandler<ProfileSyncProtocolController>(
    MAX_PROTOCOL_BYTES,
    MAX_PROTOCOL_BYTES
) {
    override fun onStartInitiator(
        stream: Stream
    ): CompletableFuture<ProfileSyncProtocolController> {
        return start(stream, true)
    }

    override fun onStartResponder(
        stream: Stream
    ): CompletableFuture<ProfileSyncProtocolController> {
        return start(stream, false)
    }

    private fun start(
        stream: Stream,
        initiator: Boolean
    ): CompletableFuture<ProfileSyncProtocolController> {
        val ready = CompletableFuture<Void>()
        val controller = ProfileSyncProtocolController(
            stream,
            initiator,
            requestHandler,
            ready
        )
        stream.pushHandler(controller)
        return ready.thenApply { controller }
    }

    companion object {
        private const val MAX_PROTOCOL_BYTES = 512 * 1024L
    }
}

internal class ProfileSyncProtocolController(
    private val stream: Stream,
    private val initiator: Boolean,
    private val requestHandler: ProfileSyncRequestHandler,
    private val ready: CompletableFuture<Void>
) : ProtocolMessageHandler<ByteBuf> {
    val response = CompletableFuture<ProfileSyncResponse>()
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
                    throw ProfileSyncException(
                        "The profile synchronization message has an invalid length"
                    )
                }
                val totalFrameLength = FRAME_LENGTH_BYTES + frameLength
                if (pendingBytes.size < totalFrameLength) {
                    return
                }
                if (handledFrame) {
                    throw ProfileSyncException(
                        "The profile synchronization stream sent an unexpected extra message"
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

    fun sendRequest(request: ProfileSyncRequest) {
        check(initiator) { "Only the stream initiator can send a profile sync request" }
        send(ProfileSyncCodec.encodeRequest(request))
    }

    fun sendResponse(response: ProfileSyncResponse) {
        check(!initiator) { "Only the stream responder can send a profile sync response" }
        send(ProfileSyncCodec.encodeResponse(response))
    }

    fun close() {
        stream.close()
    }

    override fun onClosed(stream: Stream) {
        if (initiator && !response.isDone) {
            response.completeExceptionally(
                ProfileSyncException("The profile synchronization connection closed")
            )
        }
    }

    override fun onException(cause: Throwable?) {
        if (initiator && !response.isDone) {
            response.completeExceptionally(
                cause ?: ProfileSyncException("The profile synchronization connection failed")
            )
        }
    }

    private fun handleFrame(remotePeerId: PeerId, frame: ByteArray) {
        val value = frame.toString(Charsets.UTF_8)
        if (initiator) {
            response.complete(ProfileSyncCodec.decodeResponse(value))
        } else {
            requestHandler(
                remotePeerId,
                ProfileSyncCodec.decodeRequest(value),
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
                ProfileSyncResponse(
                    accepted = false,
                    error = "Malformed profile synchronization request"
                )
            )
        }
    }

    private fun send(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FRAME_BYTES) {
            throw ProfileSyncException("The profile synchronization message is too large")
        }
        val frame = ByteBuffer.allocate(FRAME_LENGTH_BYTES + bytes.size)
            .putInt(bytes.size)
            .put(bytes)
            .array()
        stream.writeAndFlush(frame.toByteBuf())
    }

    companion object {
        private const val FRAME_LENGTH_BYTES = Int.SIZE_BYTES
        private const val MAX_FRAME_BYTES = 256 * 1024
    }
}

private object ProfileSyncCodec {
    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encodeRequest(request: ProfileSyncRequest): String {
        return json.encodeToString(request)
    }

    fun decodeRequest(value: String): ProfileSyncRequest {
        return try {
            json.decodeFromString(value)
        } catch (error: Exception) {
            throw ProfileSyncException(
                "The profile synchronization request is malformed",
                error
            )
        }
    }

    fun encodeResponse(response: ProfileSyncResponse): String {
        return json.encodeToString(response)
    }

    fun decodeResponse(value: String): ProfileSyncResponse {
        return try {
            json.decodeFromString(value)
        } catch (error: Exception) {
            throw ProfileSyncException(
                "The profile synchronization response is malformed",
                error
            )
        }
    }
}
