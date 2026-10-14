package org.schabi.newpipe.local

import androidx.preference.PreferenceManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListSeparatorsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val key = "list_separators_test_playlist"
    private val items: List<LocalItem> = (1L..20L).map { id ->
        mock(PlaylistStreamEntry::class.java).also { `when`(it.streamId).thenReturn(id) }
    }

    @Test
    fun migratesLegacyHeadingsAndKeepsThemIndependentOfVideoIdentity() {
        preferences.edit().remove("${key}_positions")
            .putString(
                key,
                JSONObject().put("stream:1:0", "A")
                    .put("stream:6:0", "B").put("stream:11:0", "C")
                    .put("stream:16:0", "D").toString()
            ).commit()
        val separators = ListSeparators(context, "test", "playlist")
        assertEquals(emptyMap<Int, String>(), separators.labels(emptyList()))
        assertEquals(mapOf(0 to "A", 6 to "B", 12 to "C", 18 to "D"), separators.labels(items))
        assertEquals(separators.labels(items), separators.labels(items.reversed()))
        separators.move(items, 3, 16)
        assertEquals(mapOf(0 to "A", 5 to "B", 11 to "C", 18 to "D"), separators.labels(items))
    }

    @Test
    fun headingsCanMoveIndependentlyAndBecomeConsecutive() {
        preferences.edit().putString(
            "${key}_positions",
            JSONObject().put("5", "A").put("11", "B").toString()
        ).commit()
        val separators = ListSeparators(context, "test", "playlist")
        separators.move(items, 11, 6)
        assertEquals(mapOf(5 to "A", 6 to "B"), separators.labels(items))
        separators.move(items, 5, 22 - 1)
        assertEquals(mapOf(5 to "B", 21 to "A"), separators.labels(items))
        assertEquals(separators.labels(items), ListSeparators(context, "test", "playlist").labels(items))
    }

    @Test
    fun deletingAndUndoingVideoRowsRetainsTheHeading() {
        preferences.edit().putString("${key}_positions", JSONObject().put("6", "A").toString()).commit()
        val separators = ListSeparators(context, "test", "playlist")
        separators.removeRow(items, 2)
        assertEquals(mapOf(5 to "A"), separators.labels(items.drop(1)))
        separators.insertRow(items.drop(1), 2)
        assertEquals(mapOf(6 to "A"), separators.labels(items))
    }
}
