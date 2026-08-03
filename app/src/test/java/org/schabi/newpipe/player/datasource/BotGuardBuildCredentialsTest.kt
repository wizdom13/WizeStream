package org.schabi.newpipe.player.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BotGuardBuildCredentialsTest {
    @Test
    fun acceptsConfiguredCredentials() {
        assertNull(botGuardBuildCredentialsError("api-key", "request-key"))
    }

    @Test
    fun reportsBothMissingCredentialNames() {
        assertEquals(
            "BotGuard build credentials missing: configure " +
                "WIZESTREAM_BOTGUARD_GOOGLE_API_KEY and WIZESTREAM_BOTGUARD_REQUEST_KEY",
            botGuardBuildCredentialsError("", " ")
        )
    }

    @Test
    fun reportsOnlyTheMissingCredentialName() {
        assertEquals(
            "BotGuard build credentials missing: configure WIZESTREAM_BOTGUARD_REQUEST_KEY",
            botGuardBuildCredentialsError("api-key", "")
        )
    }
}
