package org.schabi.newpipe.update

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

object UpdateInstallHelper {
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    @JvmStatic
    fun canRequestPackageInstalls(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    @JvmStatic
    fun createInstallPermissionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    @JvmStatic
    fun installApk(
        context: Context,
        apkPath: String,
        expectedSha256: String,
        expectedVersion: String
    ): Boolean {
        if (!canRequestPackageInstalls(context)) {
            return false
        }
        val apkFile = File(apkPath)
        try {
            UpdateApkVerifier.verify(context, apkFile, expectedSha256, expectedVersion)
        } catch (e: IOException) {
            apkFile.delete()
            return false
        } catch (e: RuntimeException) {
            apkFile.delete()
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                clipData = ClipData.newRawUri("WizeStream update", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
