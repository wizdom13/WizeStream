package org.schabi.newpipe.player.datasource

import com.grack.nanojson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSessionPolicy
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrStreamProtectionStatus

class QuickJsSabrSessionPolicyCompatibilityTest {
    @Test
    fun stalePoTokenRefreshStateIsIgnored() {
        val state = SabrSessionPolicy.State(7, 2, 1)
        val input = QuickJsSabrPolicyCompatibility.stateJson(state)
        val scripted = JsonObject().apply {
            this["requestNumber"] = 99
            this["redirectCount"] = 3
            this["poTokenRefreshes"] = 99
            this["reloads"] = 99
        }

        assertFalse(input.has("poTokenRefreshes"))
        assertEquals(
            SabrSessionPolicy.State(7, 3, 1),
            QuickJsSabrPolicyCompatibility.nextState(state, scripted)
        )
    }

    @Test
    fun attestationStatusesAlwaysUseBuiltinPolicy() {
        assertTrue(
            QuickJsSabrPolicyCompatibility.requiresBuiltinPolicy(
                SabrStreamProtectionStatus.ATTESTATION_PENDING
            )
        )
        assertTrue(
            QuickJsSabrPolicyCompatibility.requiresBuiltinPolicy(
                SabrStreamProtectionStatus.ATTESTATION_REQUIRED
            )
        )
        assertFalse(QuickJsSabrPolicyCompatibility.requiresBuiltinPolicy(1))
    }
}
