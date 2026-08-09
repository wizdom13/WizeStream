/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// jvm-libp2p's CompletableFuture API is backported by coreLibraryDesugaring.
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

internal typealias PairingRequestHandler =
    (PeerId, PairingRequest, SyncProtocolController) -> Unit

internal class SyncProtocolBinding(
    requestHandler: PairingRequestHandler
) : StrictProtocolBinding<SyncProtocolController>(
    SYNC_PROTOCOL_ID,
    SyncProtocol(requestHandler)
)

internal class SyncProtocol(
    private val requestHandler: PairingRequestHandler
) : ProtocolHandler<SyncProtocolController>(MAX_PROTOCOL_BYTES, MAX_PROTOCOL_BYTES) {
    override fun onStartInitiator(stream: Stream): CompletableFuture<SyncProtocolController> {
        return start(stream, true)
    }

    override fun onStartResponder(stream: Stream): CompletableFuture<SyncProtocolController> {
        return start(stream, false)
    }

    private fun start(
        stream: Stream,
        initiator: Boolean
    ): CompletableFuture<SyncProtocolController> {
        val ready = CompletableFuture<Void>()
        val controller = SyncProtocolController(stream, initiator, requestHandler, ready)
        stream.pushHandler(controller)
        return ready.thenApply { controller }
    }

    companion object {
        private const val MAX_PROTOCOL_BYTES = 64 * 1024L
    }
}

internal class SyncProtocolController(
    private val stream: Stream,
    private val initiator: Boolean,
    private val requestHandler: PairingRequestHandler,
    private val ready: CompletableFuture<Void>
) : ProtocolMessageHandler<ByteBuf> {
    val response = CompletableFuture<PairingResponse>()
    private var pendingBytes = ByteArray(0)

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
                    throw PairingException("The pairing message has an invalid length")
                }
                val totalFrameLength = FRAME_LENGTH_BYTES + frameLength
                if (pendingBytes.size < totalFrameLength) {
                    return
                }
                val frame = pendingBytes.copyOfRange(FRAME_LENGTH_BYTES, totalFrameLength)
                pendingBytes = pendingBytes.copyOfRange(totalFrameLength, pendingBytes.size)
                handleFrame(stream.remotePeerId(), frame)
            }
        } catch (error: Exception) {
            if (initiator) {
                response.completeExceptionally(error)
            } else {
                sendPairingResponse(
                    PairingResponse(accepted = false, error = "Malformed pairing request")
                )
            }
        }
    }

    fun sendPairingRequest(request: PairingRequest) {
        check(initiator) { "Only the stream initiator can send a pairing request" }
        send(SyncWireMessage.pairingRequest(request))
    }

    fun sendPairingResponse(response: PairingResponse) {
        check(!initiator) { "Only the stream responder can send a pairing response" }
        send(SyncWireMessage.pairingResponse(response))
    }

    fun close() {
        stream.close()
    }

    override fun onClosed(stream: Stream) {
        if (initiator && !response.isDone) {
            response.completeExceptionally(PairingException("The pairing connection closed"))
        }
    }

    override fun onException(cause: Throwable?) {
        if (initiator && !response.isDone) {
            response.completeExceptionally(
                cause ?: PairingException("The pairing connection failed")
            )
        }
    }

    private fun handleInitiatorMessage(message: SyncWireMessage) {
        if (
            message.type != SyncWireMessage.TYPE_PAIRING_RESPONSE ||
            message.pairingResponse == null
        ) {
            throw PairingException("The remote device sent an unexpected response")
        }
        response.complete(message.pairingResponse)
    }

    private fun handleResponderMessage(remotePeerId: PeerId, message: SyncWireMessage) {
        if (
            message.type != SyncWireMessage.TYPE_PAIRING_REQUEST ||
            message.pairingRequest == null
        ) {
            throw PairingException("The remote device sent an unexpected request")
        }
        requestHandler(remotePeerId, message.pairingRequest, this)
    }

    private fun send(message: SyncWireMessage) {
        val bytes = PairingSecurity.encodeWireMessage(message).toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_FRAME_BYTES) { "The pairing message is too large" }
        val frame = ByteBuffer.allocate(FRAME_LENGTH_BYTES + bytes.size)
            .putInt(bytes.size)
            .put(bytes)
            .array()
        stream.writeAndFlush(frame.toByteBuf())
    }

    private fun handleFrame(remotePeerId: PeerId, frame: ByteArray) {
        val message = PairingSecurity.decodeWireMessage(frame.toString(Charsets.UTF_8))
        if (initiator) {
            handleInitiatorMessage(message)
        } else {
            handleResponderMessage(remotePeerId, message)
        }
    }

    companion object {
        private const val FRAME_LENGTH_BYTES = Int.SIZE_BYTES
        private const val MAX_FRAME_BYTES = 32 * 1024
    }
}
