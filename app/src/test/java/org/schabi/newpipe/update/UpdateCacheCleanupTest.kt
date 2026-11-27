package org.schabi.newpipe.update

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.impl.WorkManagerImpl
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@Suppress("DEPRECATION") // Exercise the same archive-info API as the cleanup code.
class UpdateCacheCleanupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val archivePackageManager = mock(PackageManager::class.java)
    private val workManager = mock(WorkManagerImpl::class.java)
    private val now = 1_800_000_000_000L
    private lateinit var context: Context
    private lateinit var directory: File

    @Before
    fun setUp() {
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getCacheDir(): File = temporaryFolder.root
            override fun getPackageManager(): PackageManager = archivePackageManager
        }
        directory = File(context.cacheDir, UpdateCacheCleanup.DIRECTORY).apply { mkdirs() }
        context.getSharedPreferences(UpdateCacheCleanup.INSTALL_LEASES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun obsoleteAndAlreadyInstalledApksAreRemovedButNewerPendingReleaseIsRetained() {
        val obsolete = apk(99)
        val installed = apk(100)
        val pending = apk(101)
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        UpdateDownloadNotifications.completed(context, workId(obsolete), "0.99.0")
        UpdateDownloadNotifications.completed(context, workId(pending), "1.01.0")

        assertEquals(2, clean())
        assertFalse(obsolete.exists())
        assertFalse(installed.exists())
        assertTrue(pending.exists())
        assertEquals(1, notifications.allNotifications.size)
        val intent = shadowOf(notifications.allNotifications.single().contentIntent).savedIntent
        assertEquals(workId(pending).toString(), intent.getStringExtra(UpdateDownloadNotifications.EXTRA_WORK_ID))
    }

    @Test
    @Config(sdk = [23])
    fun pruningUsesTheSameVersionPolicyOnAndroidSix() {
        val obsolete = apk(99)
        val pending = apk(101)
        assertEquals(1, clean())
        assertFalse(obsolete.exists())
        assertTrue(pending.exists())
    }

    @Test
    fun queuedRunningAndBlockedDownloadsAreNeverPruned() {
        val active = listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
            .map { apk(99, state = it) }
        val finished = apk(99, state = WorkInfo.State.SUCCEEDED)
        assertEquals(1, clean())
        assertTrue(active.all(File::exists))
        assertFalse(finished.exists())
    }

    @Test
    fun prunedWorkRecordsAreAllowedButOtherWorkerRecordsArePreserved() {
        val oldRecord = apk(99)
        work(oldRecord, null)
        val unrelated = apk(99)
        work(unrelated, WorkInfo.State.SUCCEEDED, tags = setOf("another.worker"))
        assertEquals(1, clean())
        assertFalse(oldRecord.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun recentFilesAndUnreliableTimestampsArePreserved() {
        val recent = apk(99, age = UpdateCacheCleanup.RECENT_FILE_GRACE - 1)
        val boundary = apk(99, age = UpdateCacheCleanup.RECENT_FILE_GRACE)
        val future = apk(99, age = -1)
        val unknown = apk(99).apply { assertTrue(setLastModified(0)) }
        assertEquals(1, clean())
        assertFalse(boundary.exists())
        assertTrue(listOf(recent, future, unknown).all(File::exists))
    }

    @Test
    fun installerLeaseSurvivesAnotherContextAndExpiresOnlyAfterItsGracePeriod() {
        val obsolete = apk(99)
        assertTrue(UpdateCacheCleanup.protectForInstallation(context, obsolete, now))
        val restored = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getCacheDir(): File = temporaryFolder.root
            override fun getPackageManager(): PackageManager = archivePackageManager
        }
        assertEquals(0, UpdateCacheCleanup.clean(restored, workManager, 100, now + UpdateCacheCleanup.INSTALL_GRACE - 1))
        assertTrue(obsolete.exists())
        assertEquals(1, UpdateCacheCleanup.clean(restored, workManager, 100, now + UpdateCacheCleanup.INSTALL_GRACE))
        assertFalse(obsolete.exists())
        assertTrue(context.getSharedPreferences(UpdateCacheCleanup.INSTALL_LEASES, Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun installationStartingDuringArchiveInspectionProtectsTheFile() {
        val obsolete = apk(99)
        doAnswer {
            assertTrue(UpdateCacheCleanup.protectForInstallation(context, obsolete, now))
            archive(99)
        }.`when`(archivePackageManager).getPackageArchiveInfo(obsolete.absolutePath, 0)
        assertEquals(0, clean())
        assertTrue(obsolete.exists())
    }

    @Test
    fun changedFilesAreNotDeletedUsingStaleMetadata() {
        val obsolete = apk(99)
        doAnswer {
            obsolete.appendText("changed")
            archive(99)
        }.`when`(archivePackageManager).getPackageArchiveInfo(obsolete.absolutePath, 0)
        assertEquals(0, clean())
        assertTrue(obsolete.exists())
    }

    @Test
    fun unknownMetadataWrongPackagesAndWorkLookupFailuresPreserveFiles() {
        val unknown = apk(99)
        `when`(archivePackageManager.getPackageArchiveInfo(unknown.absolutePath, 0)).thenReturn(null)
        val foreign = apk(99)
        `when`(archivePackageManager.getPackageArchiveInfo(foreign.absolutePath, 0)).thenReturn(archive(99).apply { packageName = "another.app" })
        val invalidVersion = apk(0)
        val failedLookup = apk(99)
        `when`(workManager.getWorkInfoById(workId(failedLookup)).get()).thenThrow(ExecutionException(IllegalStateException("Unavailable")))
        assertEquals(0, clean())
        assertTrue(listOf(unknown, foreign, invalidVersion, failedLookup).all(File::exists))
    }

    @Test
    fun unrelatedFilesDirectoriesAndSymlinksAreLeftUntouched() {
        val unrelated = File(directory, "manual.apk").apply { writeText("unrelated") }
        val nested = File(directory, "nested").apply { mkdirs() }
        val nestedApk = File(nested, "${UUID.randomUUID()}-update.apk").apply { writeText("nested") }
        val outside = File(temporaryFolder.root, "outside.apk").apply { writeText("outside") }
        val link = File(directory, "${UUID.randomUUID()}-update.apk")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertEquals(0, clean())
        assertTrue(listOf(unrelated, nestedApk, outside, link).all(File::exists))
        assertEquals("outside", outside.readText())
    }

    @Test
    fun aSymlinkedUpdateDirectoryIsNotTraversed() {
        val external = temporaryFolder.newFolder("external")
        assertTrue(directory.delete())
        Files.createSymbolicLink(directory.toPath(), external.toPath())
        val file = File(external, "${UUID.randomUUID()}-update.apk").apply { writeText("outside") }
        assertEquals(0, clean())
        assertTrue(file.exists())
    }

    @Test
    @SuppressLint("RestrictedApi") // A delegate avoids actually scheduling maintenance in JVM tests.
    fun maintenanceIsUniqueAndDelayedAwayFromStartup() {
        val previous = WorkManagerImpl.getInstance()
        WorkManagerImpl.setDelegate(workManager)
        try {
            UpdateCacheCleanup.schedule(context)
            val enqueue = mockingDetails(workManager).invocations.single { it.method.name == "enqueueUniqueWork" }
            assertEquals(UpdateCacheCleanup.WORK_NAME, enqueue.arguments[0])
            assertEquals(ExistingWorkPolicy.KEEP, enqueue.arguments[1])
            val argument = enqueue.arguments[2]
            val request = if (argument is List<*>) argument.single() as OneTimeWorkRequest else argument as OneTimeWorkRequest
            assertEquals(UpdateCacheCleanupWorker::class.java.name, request.workSpec.workerClassName)
            assertEquals(TimeUnit.MINUTES.toMillis(1), request.workSpec.initialDelay)
        } finally {
            WorkManagerImpl.setDelegate(previous)
        }
    }

    private fun clean(): Int = UpdateCacheCleanup.clean(context, workManager, 100, now)

    private fun apk(version: Int, state: WorkInfo.State = WorkInfo.State.SUCCEEDED, age: Long = TimeUnit.DAYS.toMillis(4)): File {
        val file = File(directory, "${UUID.randomUUID()}-wizestream.apk").apply {
            writeText("APK fixture $version")
            assertTrue(setLastModified(now - age))
        }
        `when`(archivePackageManager.getPackageArchiveInfo(file.absolutePath, 0)).thenReturn(archive(version))
        work(file, state)
        return file
    }

    private fun archive(version: Int): PackageInfo = PackageInfo().apply {
        packageName = context.packageName
        versionCode = version
    }

    @Suppress("UNCHECKED_CAST")
    private fun work(file: File, state: WorkInfo.State?, tags: Set<String> = setOf(UpdateDownloadWorker::class.java.name)) {
        val info = state?.let {
            mock(WorkInfo::class.java).also { work ->
                `when`(work.state).thenReturn(state)
                `when`(work.tags).thenReturn(tags)
            }
        }
        val future = mock(ListenableFuture::class.java) as ListenableFuture<WorkInfo?>
        `when`(future.get()).thenReturn(info)
        `when`(workManager.getWorkInfoById(workId(file))).thenReturn(future)
    }

    private fun workId(file: File): UUID = UUID.fromString(file.name.take(36))
}
