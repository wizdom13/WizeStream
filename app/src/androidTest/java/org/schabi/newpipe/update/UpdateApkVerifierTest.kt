package org.schabi.newpipe.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.BuildConfig

@RunWith(AndroidJUnit4::class)
class UpdateApkVerifierTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val installedApk = File(context.applicationInfo.sourceDir)
    private val installedApkSha256 by lazy {
        UpdateChecksum.calculateSha256(installedApk)
    }

    @Test
    fun acceptsInstalledApkWithMatchingIdentityAndChecksum() {
        UpdateApkVerifier.verify(
            context,
            installedApk,
            installedApkSha256,
            BuildConfig.VERSION_NAME
        )
    }

    @Test(expected = IOException::class)
    fun rejectsInstalledApkWithWrongChecksum() {
        UpdateApkVerifier.verify(
            context,
            installedApk,
            "00".repeat(32),
            BuildConfig.VERSION_NAME
        )
    }

    @Test(expected = IOException::class)
    fun rejectsInstalledApkWithWrongVersion() {
        UpdateApkVerifier.verify(
            context,
            installedApk,
            installedApkSha256,
            "v999.0.0"
        )
    }

    @Test(expected = IOException::class)
    fun rejectsApkWithWrongPackage() {
        val instrumentationApk = File(instrumentation.context.applicationInfo.sourceDir)

        UpdateApkVerifier.verify(
            context,
            instrumentationApk,
            UpdateChecksum.calculateSha256(instrumentationApk),
            BuildConfig.VERSION_NAME
        )
    }
}
