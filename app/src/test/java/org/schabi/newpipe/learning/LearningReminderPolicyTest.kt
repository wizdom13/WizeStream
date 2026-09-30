package org.schabi.newpipe.learning

import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningReminderPolicyTest {
    @Test
    fun reminderWaitsForLocalTimeAndCanOnlyBeSentOncePerDay() {
        val morning = LocalDateTime.parse("2026-09-30T09:00:00")
        assertFalse(LearningReminderPolicy.isDue(morning.minusMinutes(1), 540, null))
        assertTrue(LearningReminderPolicy.isDue(morning, 540, null))
        assertFalse(LearningReminderPolicy.isDue(morning.plusHours(8), 540, "2026-09-30"))
        assertFalse(LearningReminderPolicy.isDue(morning.plusDays(1).minusMinutes(1), 540, "2026-09-30"))
        assertTrue(LearningReminderPolicy.isDue(morning.plusDays(1), 540, "2026-09-30"))
    }

    @Test
    fun delayedWorkStillDeliversThatDaysReminder() {
        assertTrue(LearningReminderPolicy.isDue(LocalDateTime.parse("2026-09-30T14:25:00"), 540, "2026-09-29"))
    }
}
