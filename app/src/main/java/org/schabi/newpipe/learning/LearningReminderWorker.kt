package org.schabi.newpipe.learning

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class LearningReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try {
        LearningReminders.sendDueReminders(applicationContext)
        Result.success()
    } catch (error: Exception) {
        Log.e("LearningReminderWorker", "Could not check learning reminders", error)
        Result.retry()
    }
}
