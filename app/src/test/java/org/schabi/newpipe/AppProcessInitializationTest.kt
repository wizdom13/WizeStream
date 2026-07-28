package org.schabi.newpipe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProcessInitializationTest {
    @Test
    fun `full initialization runs in the main process`() {
        assertTrue(App.shouldInitializeFullApp(false, false))
    }

    @Test
    fun `full initialization is skipped in the ACRA sender process`() {
        assertFalse(App.shouldInitializeFullApp(true, false))
    }

    @Test
    fun `full initialization is skipped in a phoenix process`() {
        assertFalse(App.shouldInitializeFullApp(false, true))
    }

    @Test
    fun `full initialization is skipped when both process flags are set`() {
        assertFalse(App.shouldInitializeFullApp(true, true))
    }
}
