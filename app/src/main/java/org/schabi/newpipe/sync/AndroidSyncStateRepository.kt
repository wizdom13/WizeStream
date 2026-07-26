/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.crypto.unmarshalPrivateKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidSyncStateRepository(context: Context) : SyncStateRepository {
    private val stateFile = AtomicFile(context.noBackupFilesDir.resolve(STATE_FILE_NAME))

    override fun loadOrCreateIdentity(): DeviceIdentity {
        return synchronized(STATE_LOCK) {
            val existingState = readState()
            if (existingState != null) {
                return@synchronized try {
                    DeviceIdentity(
                        unmarshalPrivateKey(
                            Base64.getDecoder().decode(existingState.privateKey)
                        )
                    )
                } catch (error: Exception) {
                    throw PairingException("The saved device identity is invalid", error)
                }
            }

            val identity = DeviceIdentity(generateKeyPair(KeyType.ED25519).first)
            writeState(
                PersistedSyncState(
                    privateKey = Base64.getEncoder()
                        .encodeToString(identity.privateKey.bytes())
                )
            )
            identity
        }
    }

    override fun getTrustedPeers(): List<TrustedPeer> {
        return synchronized(STATE_LOCK) {
            loadOrCreateIdentity()
            requireNotNull(readState()).trustedPeers.sortedBy {
                it.deviceName.lowercase()
            }
        }
    }

    fun hasTrustedPeers(): Boolean {
        return synchronized(STATE_LOCK) {
            readState()?.trustedPeers?.isNotEmpty() == true
        }
    }

    override fun getListenPort(): Int? {
        return synchronized(STATE_LOCK) {
            loadOrCreateIdentity()
            requireNotNull(readState()).listenPort
        }
    }

    override fun saveListenPort(port: Int) {
        if (port !in MIN_LISTEN_PORT..MAX_LISTEN_PORT) {
            throw PairingException("The synchronization listener selected an invalid TCP port")
        }
        synchronized(STATE_LOCK) {
            loadOrCreateIdentity()
            val state = requireNotNull(readState())
            if (state.listenPort != port) {
                writeState(state.copy(listenPort = port))
            }
        }
    }

    override fun saveTrustedPeer(peer: TrustedPeer) {
        synchronized(STATE_LOCK) {
            loadOrCreateIdentity()
            val state = requireNotNull(readState())
            val existingPeer = state.trustedPeers.firstOrNull {
                it.peerId == peer.peerId
            }
            val savedPeer = peer.copy(
                lastSyncAtEpochMillis = existingPeer?.lastSyncAtEpochMillis,
                lastSyncError = existingPeer?.lastSyncError
            )
            val peers = state.trustedPeers
                .filterNot { it.peerId == peer.peerId }
                .plus(savedPeer)
                .takeLast(MAX_TRUSTED_PEERS)
            writeState(state.copy(trustedPeers = peers))
        }
    }

    override fun updateTrustedPeerSyncStatus(
        peerId: String,
        syncedAtEpochMillis: Long?,
        error: String?
    ) {
        synchronized(STATE_LOCK) {
            loadOrCreateIdentity()
            val state = requireNotNull(readState())
            if (state.trustedPeers.none { it.peerId == peerId }) {
                return
            }
            val peers = state.trustedPeers.map { peer ->
                if (peer.peerId != peerId) {
                    peer
                } else {
                    peer.copy(
                        lastSyncAtEpochMillis = syncedAtEpochMillis
                            ?: peer.lastSyncAtEpochMillis,
                        lastSyncError = error?.take(MAX_SYNC_ERROR_LENGTH)
                    )
                }
            }
            writeState(state.copy(trustedPeers = peers))
        }
    }

    override fun clearTrustedPeers() {
        synchronized(STATE_LOCK) {
            loadOrCreateIdentity()
            val state = requireNotNull(readState())
            writeState(state.copy(trustedPeers = emptyList()))
        }
    }

    private fun readState(): PersistedSyncState? {
        if (!stateFile.baseFile.exists()) {
            return null
        }
        val encrypted = try {
            stateFile.readFully()
        } catch (error: Exception) {
            throw PairingException("Could not read the secure synchronization state", error)
        }
        if (encrypted.size > MAX_STATE_FILE_BYTES) {
            throw PairingException("The secure synchronization state is too large")
        }

        return try {
            val input = DataInputStream(ByteArrayInputStream(encrypted))
            val fileVersion = input.readUnsignedByte()
            if (fileVersion != STATE_FILE_VERSION) {
                throw PairingException(
                    "Unsupported secure synchronization state version: $fileVersion"
                )
            }
            val ivLength = input.readUnsignedByte()
            if (ivLength !in MIN_GCM_IV_BYTES..MAX_GCM_IV_BYTES) {
                throw PairingException("The secure synchronization state has an invalid IV")
            }
            val iv = ByteArray(ivLength)
            input.readFully(iv)
            val ciphertext = input.readBytes()
            if (ciphertext.isEmpty()) {
                throw PairingException("The secure synchronization state is empty")
            }

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey(), GCMParameterSpec(128, iv))
            val state = JSON.decodeFromString<PersistedSyncState>(
                cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            )
            if (state.version != SYNC_PROTOCOL_VERSION) {
                throw PairingException(
                    "Unsupported synchronization state version: ${state.version}"
                )
            }
            if (state.listenPort != null &&
                state.listenPort !in MIN_LISTEN_PORT..MAX_LISTEN_PORT
            ) {
                throw PairingException(
                    "The secure synchronization state has an invalid TCP port"
                )
            }
            state
        } catch (error: PairingException) {
            throw error
        } catch (error: Exception) {
            throw PairingException(
                "Could not decrypt the secure synchronization state",
                error
            )
        }
    }

    private fun writeState(state: PersistedSyncState) {
        val plaintext = JSON.encodeToString(state).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey())
        val ciphertext = cipher.doFinal(plaintext)

        val outputBytes = ByteArrayOutputStream()
        DataOutputStream(outputBytes).use { output ->
            output.writeByte(STATE_FILE_VERSION)
            output.writeByte(cipher.iv.size)
            output.write(cipher.iv)
            output.write(ciphertext)
        }

        val stream = stateFile.startWrite()
        try {
            stream.write(outputBytes.toByteArray())
            stateFile.finishWrite(stream)
        } catch (error: Exception) {
            stateFile.failWrite(stream)
            throw PairingException("Could not save the secure synchronization state", error)
        }
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    @Serializable
    private data class PersistedSyncState(
        val version: Int = SYNC_PROTOCOL_VERSION,
        val privateKey: String,
        val trustedPeers: List<TrustedPeer> = emptyList(),
        val listenPort: Int? = null
    )

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "wizestream_device_sync_state_v1"
        private const val STATE_FILE_NAME = "device_sync_state_v1"
        private const val STATE_FILE_VERSION = 1
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MIN_GCM_IV_BYTES = 12
        private const val MAX_GCM_IV_BYTES = 16
        private const val MAX_STATE_FILE_BYTES = 1024 * 1024
        private const val MAX_TRUSTED_PEERS = 32
        private const val MAX_SYNC_ERROR_LENGTH = 512
        private const val MIN_LISTEN_PORT = 1
        private const val MAX_LISTEN_PORT = 65_535
        private val STATE_LOCK = Any()
        private val JSON = Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }
    }
}
