/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingSecurityTest {
    private val instant = Instant.parse("2026-07-25T10:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)

    @Test
    fun `signed invitation round trips through pairing URI`() {
        val identity = newIdentity()
        val security = PairingSecurity(clock)
        val invitation = security.createInvitation(
            identity,
            "Test tablet",
            listOf(address(identity, 32451))
        )

        val decoded = security.decodeAndVerifyInvitation(
            security.encodeInvitation(invitation)
        )

        assertEquals(invitation, decoded)
    }

    @Test
    fun `tampered invitation signature is rejected`() {
        val identity = newIdentity()
        val security = PairingSecurity(clock)
        val invitation = security.createInvitation(
            identity,
            "Test tablet",
            listOf(address(identity, 32451))
        )

        assertThrows(PairingException::class.java) {
            security.verifyInvitation(
                invitation.copy(signature = invitation.signature.reversed())
            )
        }
    }

    @Test
    fun `invitation public key must match PeerID`() {
        val identity = newIdentity()
        val otherIdentity = newIdentity()
        val security = PairingSecurity(clock)
        val invitation = security.createInvitation(
            identity,
            "Test tablet",
            listOf(address(identity, 32451))
        )
        val otherInvitation = security.createInvitation(
            otherIdentity,
            "Other device",
            listOf(address(otherIdentity, 32452))
        )

        assertThrows(PairingException::class.java) {
            security.verifyInvitation(
                invitation.copy(publicKey = otherInvitation.publicKey)
            )
        }
    }

    @Test
    fun `expired invitation is rejected`() {
        val identity = newIdentity()
        val issuer = PairingSecurity(clock)
        val invitation = issuer.createInvitation(
            identity,
            "Test tablet",
            listOf(address(identity, 32451))
        )
        val verifier = PairingSecurity(
            Clock.offset(clock, java.time.Duration.ofMinutes(6))
        )

        assertThrows(PairingException::class.java) {
            verifier.verifyInvitation(invitation)
        }
    }

    @Test
    fun `pairing request consumes invitation token only once`() {
        val inviter = newIdentity()
        val requester = newIdentity()
        val inviterSecurity = PairingSecurity(clock)
        val requesterSecurity = PairingSecurity(clock)
        val invitation = inviterSecurity.createInvitation(
            inviter,
            "Test tablet",
            listOf(address(inviter, 32451))
        )
        val request = requesterSecurity.createPairingRequest(
            requester,
            "Test phone",
            listOf(address(requester, 32452)),
            invitation.token
        )

        val peer = inviterSecurity.verifyAndConsumePairingRequest(
            request,
            requester.peerId
        )
        assertEquals(requester.peerId.toBase58(), peer.peerId)

        assertThrows(PairingException::class.java) {
            inviterSecurity.verifyAndConsumePairingRequest(request, requester.peerId)
        }
    }

    @Test
    fun `pairing request must match authenticated Noise peer`() {
        val inviter = newIdentity()
        val requester = newIdentity()
        val differentPeer = newIdentity()
        val inviterSecurity = PairingSecurity(clock)
        val invitation = inviterSecurity.createInvitation(
            inviter,
            "Test tablet",
            listOf(address(inviter, 32451))
        )
        val request = PairingSecurity(clock).createPairingRequest(
            requester,
            "Test phone",
            listOf(address(requester, 32452)),
            invitation.token
        )

        assertThrows(PairingException::class.java) {
            inviterSecurity.verifyAndConsumePairingRequest(request, differentPeer.peerId)
        }
    }

    private fun newIdentity(): DeviceIdentity {
        return DeviceIdentity(generateKeyPair(KeyType.ED25519).first)
    }

    private fun address(identity: DeviceIdentity, port: Int): String {
        return "/ip4/127.0.0.1/tcp/$port/p2p/${identity.peerId.toBase58()}"
    }
}
