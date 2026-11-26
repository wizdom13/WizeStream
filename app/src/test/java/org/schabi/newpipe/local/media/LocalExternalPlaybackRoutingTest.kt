/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.eq
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.download.DownloadPlayback
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.SerializedCache
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.Mission
import us.shandian.giga.ui.adapter.MissionAdapter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalExternalPlaybackRoutingTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private val uri = "content://media/external/file/1769"

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java)
        activity = activityController.get()
        activity.setTheme(R.style.DarkTheme)
        activityController.setup()
        preferences(audio = false, video = false)
    }

    @After
    fun tearDown() {
        SerializedCache.getInstance().clear()
        activityController.pause().stop().destroy()
        framework().clearInlineMocks()
    }

    @Test
    fun ordinaryAudioTapUsesAudioPreferenceWithoutStartingTheInternalPlayerOrExtractor() {
        preferences(audio = true, video = false)
        mockStatic(ExtractorHelper::class.java).use { extractor ->
            NavigationHelper.playOnMainPlayer(activity, queue(video = false))
            assertExternal("audio/mpeg")
            extractor.verifyNoInteractions()
        }
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun ordinaryVideoTapUsesVideoPreference() {
        preferences(audio = false, video = true)
        NavigationHelper.playOnMainPlayer(context, queue(video = true), false)
        assertExternal("video/mp4")
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun downloadedAudioAndVideoUseTheActualSavedFile() {
        for (video in listOf(false, true)) {
            preferences(audio = !video, video = video)
            val storage = mock(StoredFileHelper::class.java)
            `when`(storage.uri).thenReturn(Uri.parse(uri))
            val mission = FinishedMission().apply {
                this.storage = storage
                kind = if (video) 'v' else 'a'
                title = "Downloaded media"
                mimeType = if (video) "video/mp4" else "audio/mpeg"
                source = "https://example.invalid/original-online-video"
            }
            mockStatic(ExtractorHelper::class.java).use { extractor ->
                DownloadPlayback.play(context, mission)
                assertExternal(mission.mimeType)
                extractor.verifyNoInteractions()
            }
        }
    }

    @Test
    fun explicitDownloadOpenAndShareUseTheMediaProviderForDirectFiles() {
        val file = File.createTempFile("download-external", ".mp4", Environment.getExternalStorageDirectory())
        try {
            file.writeText("download fixture")
            val storage = mock(StoredFileHelper::class.java)
            `when`(storage.uri).thenReturn(Uri.fromFile(file))
            `when`(storage.isDirect).thenReturn(true)
            `when`(storage.existsAsFile()).thenReturn(true)
            `when`(storage.type).thenReturn("video/mp4")
            val mission = FinishedMission().apply { this.storage = storage }
            val adapter = mock(MissionAdapter::class.java, CALLS_REAL_METHODS)
            field(MissionAdapter::class.java, adapter, "mContext", context)

            for ((method, action) in listOf("viewWithFileProvider" to Intent.ACTION_VIEW, "shareFile" to Intent.ACTION_SEND)) {
                MissionAdapter::class.java.getDeclaredMethod(method, Mission::class.java)
                    .apply { isAccessible = true }.invoke(adapter, mission)
                val chooser = shadowOf(context).nextStartedActivity
                assertEquals(Intent.ACTION_CHOOSER, chooser.action)
                val request = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(action, request.action)
                val uri = if (action == Intent.ACTION_VIEW) request.data!! else request.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
                assertEquals(context.packageName + ".localmedia", uri.authority)
                assertEquals("video/mp4", request.type)
                assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, request.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
                assertEquals(0, request.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                assertEquals("download fixture", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun disabledOrUnrelatedPreferenceKeepsTheInternalDetailRoute() {
        preferences(audio = false, video = true)
        NavigationHelper.playOnMainPlayer(context, queue(video = false), false)
        assertEquals(MainActivity::class.java.name, shadowOf(context).nextStartedActivity.component!!.className)
        preferences(audio = true, video = false)
        NavigationHelper.playOnMainPlayer(context, queue(video = true), false)
        assertEquals(MainActivity::class.java.name, shadowOf(context).nextStartedActivity.component!!.className)
    }

    @Test
    fun explicitQueuePlaybackAndPlayerSwitchesStayInternal() {
        preferences(audio = true, video = true)
        val group = queue(video = false).apply { requestOpenQueueOnStart() }
        NavigationHelper.playOnMainPlayer(activity, group)
        assertEquals(PlayerService::class.java.name, shadowOf(context).nextStartedService.component!!.className)
        assertNull(shadowOf(context).nextStartedActivity)

        NavigationHelper.playOnMainPlayer(context, queue(video = true), true)
        val switch = shadowOf(context).nextStartedActivity
        assertEquals(MainActivity::class.java.name, switch.component!!.className)
        assertEquals(true, switch.getBooleanExtra(VideoDetailFragment.KEY_SWITCHING_PLAYERS, false))
    }

    @Test
    fun explicitBackgroundPopupAndEnqueueActionsStayInternal() {
        preferences(audio = true, video = true)
        val queue = queue(video = true)
        NavigationHelper.playOnBackgroundPlayer(context, queue, true)
        assertNotNull(shadowOf(context).nextStartedService)
        mockStatic(PermissionHelper::class.java).use { permissions ->
            permissions.`when`<Boolean> { PermissionHelper.isPopupEnabledElseAsk(context) }.thenReturn(true)
            NavigationHelper.playOnPopupPlayer(context, queue, true)
            assertNotNull(shadowOf(context).nextStartedService)
        }
        NavigationHelper.enqueueOnPlayer(context, queue, PlayerType.AUDIO)
        assertNotNull(shadowOf(context).nextStartedService)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun programmaticLocalDetailPlaybackDoesNotEnterTheOnlineExternalDialog() {
        preferences(audio = true, video = true)
        val (fragment, holder) = detail()
        fragment.openVideoPlayer(false)
        verify(holder).startService(anyBoolean(), eq(fragment))
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun detailAudioActionOpensTheLocalFileAndAppendStillEnqueues() {
        preferences(audio = true, video = false)
        val (fragment, _) = detail()
        val method = VideoDetailFragment::class.java.getDeclaredMethod("openBackgroundPlayer", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        method.invoke(fragment, false)
        assertExternal("video/mp4")
        method.invoke(fragment, true)
        assertEquals(PlayerService::class.java.name, shadowOf(context).nextStartedService.component!!.className)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    private fun detail(): Pair<VideoDetailFragment, PlayerHolder> {
        val fragment = mock(VideoDetailFragment::class.java, CALLS_REAL_METHODS)
        doReturn(activity).`when`(fragment).requireContext()
        doReturn(activity.resources).`when`(fragment).resources
        val holder = mock(PlayerHolder::class.java)
        field(BaseFragment::class.java, fragment, "activity", activity)
        field(VideoDetailFragment::class.java, fragment, "playerHolder", holder)
        field(VideoDetailFragment::class.java, fragment, "currentLocalItem", requireNotNull(queue(video = true).item))
        return fragment to holder
    }

    private fun assertExternal(mime: String) {
        val chooser = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val view = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Uri.parse(uri), view.data)
        assertEquals(mime, view.type)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    private fun preferences(audio: Boolean, video: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
            .putBoolean(context.getString(R.string.use_external_audio_player_key), audio)
            .putBoolean(context.getString(R.string.use_external_video_player_key), video)
            .commit()
    }

    private fun queue(video: Boolean): LocalMediaPlayQueue = LocalMediaPlayQueue(
        listOf(PlayQueueItem.localMedia("Local media", uri, 300, "Artist", null, null, if (video) "video/mp4" else "audio/mpeg", 1769, video, null)),
        0
    )

    private fun field(type: Class<*>, target: Any, name: String, value: Any) {
        type.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
}
