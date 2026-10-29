package org.schabi.newpipe.sync

import android.app.Application
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DeviceSyncChargingTest {
    @Test fun chargingRestrictionPreservesNetworkAndBatteryRequirements() {
        val restricted = DeviceSyncBackgroundPolicy.constraints(true)
        assertTrue(restricted.requiresCharging())
        assertTrue(restricted.requiresBatteryNotLow())
        assertEquals(NetworkType.CONNECTED, restricted.requiredNetworkType)
        val unrestricted = DeviceSyncBackgroundPolicy.constraints(false)
        assertFalse(unrestricted.requiresCharging())
        assertTrue(unrestricted.requiresBatteryNotLow())
        assertEquals(NetworkType.CONNECTED, unrestricted.requiredNetworkType)
    }
}
