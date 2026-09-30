package org.schabi.newpipe.learning

import java.time.LocalDateTime

object LearningReminderPolicy {
    fun isDue(now: LocalDateTime, minuteOfDay: Int, lastDate: String?): Boolean = now.hour * 60 + now.minute >= minuteOfDay.coerceIn(0, 1439) &&
        lastDate != now.toLocalDate().toString()
}
