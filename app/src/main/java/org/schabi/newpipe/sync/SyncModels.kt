/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.PeerId
import io.libp2p.core.crypto.PrivKey
import kotlinx.serialization.Serializable

internal const val SYNC_PROTOCOL_ID = "/wizestream/sync/1.0.0"
internal const val SYNC_PROTOCOL_VERSION = 1
internal const val MAX_DEVICE_NAME_LENGTH = 64
internal const val MAX_PAIRING_ADDRESSES = 8
internal const val MAX_MULTIADDRESS_LENGTH = 256

data class DeviceIdentity(
    val privateKey: PrivKey
) {
    val peerId: PeerId = PeerId.fromPubKey(privateKey.publicKey())
}

@Serializable
data class TrustedPeer(
    val peerId: String,
    val publicKey: String,
    val deviceName: String,
    val addresses: List<String>,
    val pairedAtEpochMillis: Long,
    val lastSyncAtEpochMillis: Long? = null,
    val lastSyncError: String? = null
)

@Serializable
data class PairingInvitation(
    val version: Int,
    val peerId: String,
    val publicKey: String,
    val deviceName: String,
    val addresses: List<String>,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val token: String,
    val signature: String
)

@Serializable
internal data class PairingRequest(
    val version: Int,
    val peerId: String,
    val publicKey: String,
    val deviceName: String,
    val addresses: List<String>,
    val issuedAtEpochMillis: Long,
    val invitationToken: String,
    val signature: String
)

@Serializable
internal data class PairingResponse(
    val accepted: Boolean,
    val error: String? = null
)

@Serializable
internal data class SyncWireMessage(
    val type: String,
    val pairingRequest: PairingRequest? = null,
    val pairingResponse: PairingResponse? = null
) {
    init {
        require(
            listOfNotNull(pairingRequest, pairingResponse).size == 1
        ) {
            "A sync message must contain exactly one payload"
        }
    }

    companion object {
        const val TYPE_PAIRING_REQUEST = "pairing_request"
        const val TYPE_PAIRING_RESPONSE = "pairing_response"

        fun pairingRequest(request: PairingRequest) = SyncWireMessage(
            type = TYPE_PAIRING_REQUEST,
            pairingRequest = request
        )

        fun pairingResponse(response: PairingResponse) = SyncWireMessage(
            type = TYPE_PAIRING_RESPONSE,
            pairingResponse = response
        )
    }
}

interface SyncStateRepository {
    fun loadOrCreateIdentity(): DeviceIdentity

    fun getTrustedPeers(): List<TrustedPeer>

    fun getListenPort(): Int? = null

    fun saveListenPort(port: Int) = Unit

    fun saveTrustedPeer(peer: TrustedPeer)

    fun updateTrustedPeerSyncStatus(
        peerId: String,
        syncedAtEpochMillis: Long?,
        error: String?
    )

    fun clearTrustedPeers()
}

class PairingException(message: String, cause: Throwable? = null) : Exception(message, cause)
