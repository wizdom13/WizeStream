package org.schabi.newpipe.settings.appearance

import android.content.pm.PackageManager as PM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconsTest {
    @Test fun alwaysEnablesTheDestinationBeforeRemovingTheOldEntry() {
        val states = LauncherIcons.variants.associateWith { PM.COMPONENT_ENABLED_STATE_DEFAULT }.toMutableMap()
        LauncherIcons.apply("blue", { states.getValue(it) }, { key, state ->
            states[key] = state
            assertTrue(states.any { (k, v) -> v == PM.COMPONENT_ENABLED_STATE_ENABLED || k == "default" && v == PM.COMPONENT_ENABLED_STATE_DEFAULT })
        })
        assertEquals(listOf("blue"), states.filterValues { it == PM.COMPONENT_ENABLED_STATE_ENABLED }.keys.toList())
        assertEquals(PM.COMPONENT_ENABLED_STATE_DISABLED, states["default"])
        LauncherIcons.apply("default", { states.getValue(it) }, { key, value -> states[key] = value })
        assertEquals(PM.COMPONENT_ENABLED_STATE_ENABLED, states["default"])
        assertEquals(PM.COMPONENT_ENABLED_STATE_DISABLED, states["blue"])
    }

    @Test fun rejectsUnknownChoicesAndRestoresStatesAfterFailure() {
        val states = LauncherIcons.variants.associateWith { PM.COMPONENT_ENABLED_STATE_DEFAULT }.toMutableMap()
        val original = states.toMap()
        var fail = true
        assertThrows(SecurityException::class.java) {
            LauncherIcons.apply("green", { states.getValue(it) }, { key, value ->
                if (key == "blue" && fail) {
                    fail = false
                    throw SecurityException()
                }
                states[key] = value
            })
        }
        assertEquals(original, states)
        assertThrows(IllegalArgumentException::class.java) { LauncherIcons.apply("external", { error("Should not read") }, { _, _ -> error("Should not write") }) }
    }
}
