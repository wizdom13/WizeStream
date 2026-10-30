/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download.offline

import android.app.Application
import android.content.Context
import android.net.Uri
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.FinishedMission

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OfflineLibraryOwnershipTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val profile = "00000000-0000-0000-0000-000000000000"
    private val registry get() = context.getSharedPreferences("offline_library_ownership", Context.MODE_PRIVATE)

    private fun pending(): DownloadMission {
        val storage = StoredFileHelper(context, null, Uri.fromFile(temporary.newFile()), "audio")
        return mock(DownloadMission::class.java).apply {
            this.storage = storage
            source = "https://example.com/video"
            timestamp = 1000
            kind = 'a'
        }
    }

    private fun track(mission: DownloadMission) {
        val info = mock(StreamInfo::class.java)
        `when`(info.uploaderUrl).thenReturn("https://example.com/channel")
        OfflineLibrary.trackPending(context, mission, info, profile)
    }

    @Test fun manualCompletionIsNeverEnrolled() {
        registry.edit().clear().commit()
        OfflineLibrary.setRules(context, profile, RetentionRules(enabled = true))
        val finished = FinishedMission(pending())
        OfflineLibrary.completed(context, finished)
        assertFalse(registry.contains("file:${finished.syncId}"))
    }

    @Test fun exactAutomaticMissionIsTrackedButReplacementIsNot() {
        registry.edit().clear().commit()
        OfflineLibrary.setRules(context, profile, RetentionRules(enabled = true))
        val pending = pending()
        track(pending)
        val finished = FinishedMission(pending)
        OfflineLibrary.completed(context, finished)
        assertTrue(registry.contains("file:${finished.syncId}"))
        val replacement = FinishedMission(pending).apply {
            timestamp++
            syncId = UUID.randomUUID().toString()
        }
        OfflineLibrary.completed(context, replacement)
        assertFalse(registry.contains("file:${replacement.syncId}"))
    }

    @Test fun optOutBeforeCompletionPreventsEnrollment() {
        registry.edit().clear().commit()
        OfflineLibrary.setRules(context, profile, RetentionRules(enabled = true))
        val pending = pending()
        track(pending)
        OfflineLibrary.setRules(context, profile, RetentionRules())
        val finished = FinishedMission(pending)
        OfflineLibrary.completed(context, finished)
        assertFalse(registry.contains("file:${finished.syncId}"))
    }
}
