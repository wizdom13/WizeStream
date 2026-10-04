package org.schabi.newpipe.sync

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R

/** Runs the same manual sync as Settings without leaving playback or queuing repeated presses. */
object DeviceSyncShortcut {
    private val running = AtomicBoolean(false)

    fun start(activity: AppCompatActivity) {
        val context = activity.applicationContext
        val manager = DeviceSyncManager.get(context)
        if (manager.trustedPeers.isEmpty()) {
            Toast.makeText(context, R.string.device_sync_no_trusted_devices, Toast.LENGTH_SHORT).show()
            return
        }
        if (!running.compareAndSet(false, true)) return
        Toast.makeText(context, R.string.device_sync_sync_in_progress, Toast.LENGTH_SHORT).show()
        activity.lifecycleScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) { manager.sync() }
                Toast.makeText(
                    context,
                    context.getString(R.string.remote_sync_result, summary.succeeded, summary.failed),
                    Toast.LENGTH_LONG
                ).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Toast.makeText(context, R.string.device_sync_sync_failed, Toast.LENGTH_LONG).show()
            } finally {
                running.set(false)
            }
        }
    }
}
