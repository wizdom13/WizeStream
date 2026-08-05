package org.schabi.newpipe.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearningNoteTimeTest {
    @Test
    fun `formats and parses minute and hour timestamps`() {
        assertEquals("2:05", LearningNoteTime.format(125_000))
        assertEquals("1:02:03", LearningNoteTime.format(3_723_000))
        assertEquals(125_000L, LearningNoteTime.parse("2:05"))
        assertEquals(3_723_000L, LearningNoteTime.parse("1:02:03"))
    }

    @Test
    fun `rejects malformed timestamps`() {
        assertNull(LearningNoteTime.parse("2"))
        assertNull(LearningNoteTime.parse("1:60"))
        assertNull(LearningNoteTime.parse("abc"))
        assertNull(LearningNoteTime.parse("1:2:60"))
    }
}
