/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.app.Application
import android.os.Looper
import android.view.View
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.SparseArrayCompat
import androidx.preference.PreferenceManager
import com.livefront.bridge.Bridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HlsDownloadDialogTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
            .putBoolean(context.getString(R.string.storage_use_saf), true)
            .commit()
    }

    @Test
    fun catalogOffersHlsVideoWithoutInventingExtractedAudio() {
        val hls = video(DeliveryMethod.HLS, "Auto")
        val catalog = DownloadStreamCatalogFactory.create(context, info(hls))
        assertSame(hls, catalog.videoStreams.streamsList.single())
        assertEquals(0, catalog.audioTracks.size())
        assertEquals(-1, catalog.muxedAudioFallbackVideoIndex)

        val progressive = video(DeliveryMethod.PROGRESSIVE_HTTP, "720p")
        val preferred = DownloadStreamCatalogFactory.create(context, info(hls, progressive))
        assertEquals(listOf(progressive), preferred.videoStreams.streamsList)
    }

    @Test
    fun qualityRowsNameAdvertisedResolutionAndUnknownHlsWithoutChangingSource() {
        val host = Robolectric.buildActivity(AppCompatActivity::class.java)
        val activity = host.get().apply { setTheme(R.style.LightTheme) }
        host.setup()
        try {
            val auto = video(DeliveryMethod.HLS, "Auto")
            val hls = video(DeliveryMethod.HLS, "720p")
            val progressive = video(DeliveryMethod.PROGRESSIVE_HTTP, "1080p")
            val adapter = DownloadVideoStreamAdapter(
                StreamInfoWrapper(listOf(auto, hls, progressive), activity),
                SparseArrayCompat()
            )
            val parent = Spinner(activity)
            val row = adapter.getDropDownView(0, null, parent)
            assertEquals(activity.getString(R.string.download_hls_best_available), row.findViewById<TextView>(R.id.stream_quality).text)
            assertEquals(MediaFormat.MPEG_4.getName(), row.findViewById<TextView>(R.id.stream_format_name).text)
            adapter.getDropDownView(1, row, parent)
            assertEquals(activity.getString(R.string.download_hls_quality, "720p"), row.findViewById<TextView>(R.id.stream_quality).text)
            adapter.getDropDownView(2, row, parent)
            assertEquals("1080p", row.findViewById<TextView>(R.id.stream_quality).text)
            assertEquals("Auto", auto.resolution)
            assertEquals(DeliveryMethod.HLS, auto.deliveryMethod)
        } finally {
            host.pause().stop().destroy()
        }
    }

    @Test
    fun hlsSelectionDropsRestoredRangesAndCompanionsAndHidesUnsupportedControls() = withDialog(info(video(DeliveryMethod.HLS, "Auto"))) { dialog, root ->
        assertTrue(root.findViewById<RadioButton>(R.id.video_button).isChecked)
        assertEquals(View.GONE, root.findViewById<View>(R.id.download_segments).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.threads_layout).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.companion_caption).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.audio_output_format_spinner).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.mp3_bitrate_spinner).visibility)
        assertFalse(root.findViewById<View>(R.id.threads).isEnabled)
        assertEquals("", dialog.selectedSegments)
        assertEquals(-1, dialog.companionCaptionIndex)
    }

    @Test
    fun selectingIndependentProgressiveAudioRestoresItsDownloadControls() {
        val source = info(video(DeliveryMethod.HLS, "720p")).apply {
            audioStreams = listOf(
                AudioStream.Builder().setId("audio")
                    .setContent("https://example.org/audio.m4a", true)
                    .setMediaFormat(MediaFormat.M4A)
                    .setAverageBitrate(192)
                    .build()
            )
            subtitles = listOf(
                SubtitlesStream.Builder().setContent("https://example.org/caption.srt", true)
                    .setMediaFormat(MediaFormat.SRT).setLanguageCode("en").setAutoGenerated(false).build()
            )
        }
        withDialog(source) { _, root ->
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.subtitle_button).visibility)
            root.findViewById<RadioButton>(R.id.audio_button).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.threads_layout).visibility)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.download_segments).visibility)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.audio_output_format_spinner).visibility)
            assertTrue(root.findViewById<View>(R.id.threads).isEnabled)
        }
    }

    private fun withDialog(source: StreamInfo, test: (DownloadDialog, View) -> Unit) {
        mockStatic(Bridge::class.java).use {
            mockConstruction(DownloadServiceConnector::class.java).use {
                mockConstruction(DownloadStreamSizeLoader::class.java).use {
                    val host = Robolectric.buildActivity(AppCompatActivity::class.java)
                    val activity = host.get().apply { setTheme(R.style.LightTheme) }
                    host.setup()
                    try {
                        mockStatic(PermissionHelper::class.java).use { permissions ->
                            // Exercise the real dialog with storage access already granted;
                            // device/permission discovery requires the full production App.
                            permissions.`when`<Boolean> {
                                PermissionHelper.checkStoragePermissions(activity, PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)
                            }.thenReturn(true)
                            val dialog = DownloadDialog(activity, source).apply {
                                selectedSegments = "0:1000"
                                companionCaptionIndex = 0
                                selectedAudioOutputIndex = 1
                            }
                            dialog.showNow(activity.supportFragmentManager, "download-fixture")
                            shadowOf(Looper.getMainLooper()).idle()
                            test(dialog, dialog.requireView())
                            dialog.dismissNow()
                        }
                    } finally {
                        host.pause().stop().destroy()
                    }
                }
            }
        }
    }

    private fun info(vararg streams: VideoStream) = StreamInfo(0, "fixture", "https://example.org/watch", "Fixture").apply {
        streamType = StreamType.VIDEO_STREAM
        duration = 120
        videoStreams = streams.toList()
    }

    private fun video(delivery: DeliveryMethod, resolution: String) = VideoStream.Builder()
        .setId("$delivery-$resolution")
        .setContent("https://example.org/$delivery-$resolution", true)
        .setMediaFormat(MediaFormat.MPEG_4)
        .setDeliveryMethod(delivery)
        .setResolution(resolution)
        .setIsVideoOnly(false)
        .build()
}
