/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContentProvider
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowToast
import org.schabi.newpipe.R
import org.schabi.newpipe.player.playqueue.PlayQueueItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class LocalMediaExternalPlaybackTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val content = "content://media/external/video/media/23"
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        ShadowToast.reset()
        // Robolectric gives each test new storage directories while AndroidX caches roots.
        // Attaching the real providers rebuilds that cache for this application's storage.
        Robolectric.setupContentProvider(LocalMediaFileProvider::class.java)
        Robolectric.setupContentProvider(FileProvider::class.java)
    }

    @After
    fun tearDown() {
        files.forEach(File::delete)
    }

    @Test
    fun audioAndVideoUseTheirOwnPreferencesAndDefaultsRemainInternal() {
        val audio = item(video = false)
        val video = item(video = true)
        assertFalse(LocalMediaExternalPlayback.playIfEnabled(context, audio))
        assertFalse(LocalMediaExternalPlayback.playIfEnabled(context, video))
        enable(audio = true)
        assertTrue(LocalMediaExternalPlayback.playIfEnabled(context, audio))
        assertEquals("audio/mpeg", launchedView().type)
        assertFalse(LocalMediaExternalPlayback.playIfEnabled(context, video))
        enable(audio = false, video = true)
        assertFalse(LocalMediaExternalPlayback.playIfEnabled(context, audio))
        assertTrue(LocalMediaExternalPlayback.playIfEnabled(context, video))
        assertEquals("video/mp4", launchedView().type)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun contentUriHasAnExactReadGrantAndKeepsItsMetadata() {
        enable(video = true)
        assertTrue(LocalMediaExternalPlayback.playIfEnabled(context, item()))
        val chooser = shadowOf(context).nextStartedActivity
        val view = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(Uri.parse(content), view.data)
        assertEquals(view.data, view.clipData!!.getItemAt(0).uri)
        assertEquals(view.data, chooser.clipData!!.getItemAt(0).uri)
        assertEquals("On-device title", view.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("On-device artist", view.getStringExtra("artist"))
        listOf(chooser, view).forEach { intent ->
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    @Test
    fun directFilesUseTheMediaProviderAndTheUpdaterRemainsRestricted() {
        val media = File.createTempFile("external-playback", ".mp4", Environment.getExternalStorageDirectory())
            .also {
                files += it
                it.writeText("local fixture")
            }
        enable(video = true)
        assertTrue(LocalMediaExternalPlayback.playIfEnabled(context, item(Uri.fromFile(media).toString())))
        val uri = launchedView().data!!
        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".localmedia", uri.authority)
        assertNotEquals(Uri.fromFile(media), uri)
        assertEquals("local fixture", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, context.packageName + ".provider", media)
        }
    }

    @Test
    fun privateAppFilesAndMissingFilesCannotBeSharedAsLocalMedia() {
        enable(video = true)
        val privateFile = File.createTempFile("private", ".mp4", context.filesDir).also { files += it }
        val missing = File(Environment.getExternalStorageDirectory(), "missing-video-1769.mp4")
        for (file in listOf(privateFile, missing)) {
            assertTrue(LocalMediaExternalPlayback.playIfEnabled(context, item(Uri.fromFile(file).toString())))
            assertNull(shadowOf(context).nextStartedActivity)
            assertEquals(context.getString(R.string.invalid_file), ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    fun unknownMimeFallsBackToTheLocalMediaKind() {
        val uri = Uri.parse("content://local-playback-test/no-extension")
        val provider = mock(ContentProvider::class.java)
        `when`(provider.getType(uri)).thenReturn(null)
        ShadowContentResolver.registerProviderInternal(uri.authority, provider)
        val audio = item(uri.toString(), video = false, mime = "application/octet-stream")
        val video = item(uri.toString(), mime = null)
        assertEquals("audio/*", LocalMediaExternalPlayback.chooser(context, audio).getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.type)
        assertEquals("video/*", LocalMediaExternalPlayback.chooser(context, video).getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.type)
    }

    @Test
    fun deniedMimeLookupDoesNotLaunchAnUnreadableFile() {
        val uri = Uri.parse("content://local-playback-test/revoked")
        val provider = mock(ContentProvider::class.java)
        `when`(provider.getType(uri)).thenThrow(SecurityException("Permission revoked"))
        ShadowContentResolver.registerProviderInternal(uri.authority, provider)
        enable(video = true)

        assertTrue(LocalMediaExternalPlayback.playIfEnabled(context, item(uri.toString(), mime = null)))

        assertNull(shadowOf(context).nextStartedActivity)
        assertEquals(context.getString(R.string.invalid_file), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun explicitAudioActionUsesAudioPreferenceWithoutMislabelingTheFile() {
        enable(audio = true)
        assertTrue(LocalMediaExternalPlayback.playIfEnabled(context, item(), audioOnly = true))
        assertEquals("video/mp4", launchedView().type)
    }

    @Test
    fun launchFailuresAreVisibleAndDoNotFallThroughToAnotherPlayer() {
        enable(video = true)
        val cases = listOf(
            SecurityException("revoked") to R.string.invalid_file,
            ActivityNotFoundException("no player") to R.string.no_app_to_open_intent
        )
        for ((error, expected) in cases) {
            val failing = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) = throw error
            }
            assertTrue(LocalMediaExternalPlayback.playIfEnabled(failing, item()))
            assertEquals(context.getString(expected), ShadowToast.getTextOfLatestToast())
            assertNull(shadowOf(context).nextStartedActivity)
        }
    }

    @Test
    fun remoteAndNonFileSourcesCannotEnterTheLocalLauncher() {
        enable(audio = true, video = true)
        assertFalse(LocalMediaExternalPlayback.playIfEnabled(context, null))
        val radio = PlayQueueItem.radioStation("Station", "https://example.invalid/radio", "", false, null)
        assertFalse(LocalMediaExternalPlayback.playIfEnabled(context, radio))
        assertNull(shadowOf(context).nextStartedActivity)
        assertTrue(LocalMediaExternalPlayback.playIfEnabled(context, item("https://example.invalid/video.mp4")))
        assertNull(shadowOf(context).nextStartedActivity)
        assertEquals(context.getString(R.string.invalid_file), ShadowToast.getTextOfLatestToast())
    }

    private fun launchedView(): Intent = shadowOf(context).nextStartedActivity
        .getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!

    private fun enable(audio: Boolean = false, video: Boolean = false) {
        preferences.edit()
            .putBoolean(context.getString(R.string.use_external_audio_player_key), audio)
            .putBoolean(context.getString(R.string.use_external_video_player_key), video)
            .commit()
    }

    private fun item(uri: String = content, video: Boolean = true, mime: String? = if (video) "video/mp4" else "audio/mpeg"): PlayQueueItem = PlayQueueItem.localMedia("On-device title", uri, 300, "On-device artist", null, null, mime, 23, video, null)
}
