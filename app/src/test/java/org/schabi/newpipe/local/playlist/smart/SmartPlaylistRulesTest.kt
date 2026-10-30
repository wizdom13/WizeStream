/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist.smart

import android.app.Application
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SmartPlaylistRulesTest {
    @Test fun roundTripAndDeletionRemainProfileScoped() {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        val first = SmartPlaylistRules(context, "00000000-0000-0000-0000-000000000000")
        val second = SmartPlaylistRules(context, "00000000-0000-0000-0000-000000000001")
        val rule = SmartPlaylistRule(name = "Lessons", watch = 2, maxMinutes = 20, channels = listOf(7), learningOnly = true)
        first.save(rule)
        second.save(rule.copy(name = "Other"))
        assertEquals(rule, first.read().single())
        first.delete(rule.id)
        assertEquals(emptyList<SmartPlaylistRule>(), first.read())
        assertEquals("Other", second.read().single().name)
    }

    @Test fun invalidDurationAndWatchStateAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { SmartPlaylistRule.parse(SmartPlaylistRule(name = "Invalid", maxMinutes = -1).json()) }
        assertThrows(IllegalArgumentException::class.java) { SmartPlaylistRule.parse(SmartPlaylistRule(name = "Invalid", watch = 7).json()) }
    }
}
