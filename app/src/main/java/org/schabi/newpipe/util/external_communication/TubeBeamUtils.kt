package org.schabi.newpipe.util.external_communication

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList

/** Optional link handoff; TubeBeam owns TV configuration and ADB communication. */
object TubeBeamUtils {
    @JvmStatic
    fun shouldShowSendToTv(context: Context, serviceId: Int): Boolean = serviceId == ServiceList.YouTube.serviceId &&
        context.packageManager.resolveActivity(buildIntent(context, ""), 0)?.activityInfo?.let {
            it.exported && it.enabled && it.applicationInfo.enabled
        } == true

    @JvmStatic
    @JvmOverloads
    fun sendToTv(context: Context, streamUrl: String, positionMillis: Long? = null): Boolean {
        return try {
            context.startActivity(buildIntent(context, withPosition(streamUrl, positionMillis)))
            true
        } catch (_: ActivityNotFoundException) {
            showLaunchError(context)
        } catch (_: SecurityException) {
            showLaunchError(context)
        }
    }

    private fun showLaunchError(context: Context): Boolean {
        Toast.makeText(context, R.string.tubebeam_launch_failed, Toast.LENGTH_LONG).show()
        return false
    }

    internal fun buildIntent(context: Context, streamUrl: String): Intent = Intent(Intent.ACTION_SEND)
        .setComponent(ComponentName(context.getString(R.string.tubebeam_package), context.getString(R.string.tubebeam_send_activity)))
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, streamUrl)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    internal fun withPosition(streamUrl: String, positionMillis: Long?): String {
        if (positionMillis == null || positionMillis < 0) return streamUrl
        val uri = Uri.parse(streamUrl)
        val builder = uri.buildUpon().clearQuery()
        for (key in uri.queryParameterNames) {
            if (key !in setOf("t", "start", "time_continue")) {
                for (value in uri.getQueryParameters(key)) builder.appendQueryParameter(key, value)
            }
        }
        return builder.appendQueryParameter("t", (positionMillis / 1000).toString()).build().toString()
    }
}
