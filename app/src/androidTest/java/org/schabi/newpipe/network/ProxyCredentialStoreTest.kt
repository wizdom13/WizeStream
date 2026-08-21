package org.schabi.newpipe.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProxyCredentialStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun passwordRoundTripsThroughKeystoreAndCanBeCleared() {
        val store = ProxyCredentialStore(context)
        store.clearPassword()
        try {
            assertTrue(store.savePassword("pāssword-123"))
            assertTrue(store.hasPassword())
            assertEquals("pāssword-123", store.readPassword())
            assertTrue(store.clearPassword())
            assertFalse(store.hasPassword())
            assertNull(store.readPassword())
        } finally {
            store.clearPassword()
        }
    }
}
