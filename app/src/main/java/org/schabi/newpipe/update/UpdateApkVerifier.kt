package org.schabi.newpipe.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

object UpdateApkVerifier {
    @Throws(IOException::class)
    fun verify(
        context: Context,
        apkFile: File,
        expectedSha256: String,
        expectedVersion: String
    ) {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            throw IOException("Downloaded APK is missing or empty")
        }
        if (!UpdateChecksum.matches(apkFile, expectedSha256)) {
            throw IOException("Downloaded APK checksum mismatch")
        }

        val packageManager = context.packageManager
        val archiveInfo = getArchivePackageInfo(packageManager, apkFile)
            ?: throw IOException("Downloaded file is not a valid APK")
        if (archiveInfo.packageName != context.packageName) {
            throw IOException("Downloaded APK package does not match the installed app")
        }
        if (!versionsMatch(expectedVersion, archiveInfo.versionName.orEmpty())) {
            throw IOException("Downloaded APK version does not match the selected release")
        }

        val installedInfo = try {
            getInstalledPackageInfo(packageManager, context.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            throw IOException("Could not verify the installed app certificate", e)
        }
        val installedSigners = signerFingerprints(installedInfo)
        val archiveSigners = signerFingerprints(archiveInfo)
        if (installedSigners.isEmpty() || archiveSigners != installedSigners) {
            throw IOException("Downloaded APK signing certificate does not match the installed app")
        }
    }

    private fun versionsMatch(expected: String, actual: String): Boolean {
        if (expected.isBlank() || actual.isBlank()) {
            return false
        }
        return normalizeVersion(expected) == normalizeVersion(actual)
    }

    private fun normalizeVersion(version: String): String {
        val trimmed = version.trim()
        return if (trimmed.startsWith("v", ignoreCase = true)) trimmed.substring(1) else trimmed
    }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(
        packageManager: PackageManager,
        apkFile: File
    ): PackageInfo? {
        val flags = signingCertificateFlags()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }
    }

    @Suppress("DEPRECATION")
    @Throws(PackageManager.NameNotFoundException::class)
    private fun getInstalledPackageInfo(
        packageManager: PackageManager,
        packageName: String
    ): PackageInfo {
        val flags = signingCertificateFlags()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            packageManager.getPackageInfo(packageName, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
    }

    @Suppress("DEPRECATION")
    private fun signerFingerprints(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            packageInfo.signatures.orEmpty()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            digest.joinToString(separator = "") { byte ->
                "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
            }
        }
    }
}
