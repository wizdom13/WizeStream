/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class LocalMediaAccess(
    val canReadAudio: Boolean,
    val canReadVideo: Boolean
) {
    val hasAnyAccess: Boolean
        get() = canReadAudio || canReadVideo
}

object LocalMediaPermissionPolicy {
    fun requiredPermissions(sdk: Int = Build.VERSION.SDK_INT): Array<String> = when {
        sdk >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO
        )

        sdk >= 23 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> emptyArray()
    }

    fun access(
        context: Context,
        sdk: Int = Build.VERSION.SDK_INT
    ): LocalMediaAccess = when {
        sdk < 23 -> LocalMediaAccess(canReadAudio = true, canReadVideo = true)
        sdk < 33 -> {
            val granted = isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            LocalMediaAccess(canReadAudio = granted, canReadVideo = granted)
        }

        else -> LocalMediaAccess(
            canReadAudio = isGranted(context, Manifest.permission.READ_MEDIA_AUDIO),
            canReadVideo = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
        )
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
