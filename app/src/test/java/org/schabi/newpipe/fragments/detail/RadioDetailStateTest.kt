/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.detail

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.evernote.android.state.StateSaver
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.Duration
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.spy
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.download.DownloadedCopyRepository
import org.schabi.newpipe.error.ErrorPanelHelper
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.DirectMediaNavigation
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h800dp-port")
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
class RadioDetailStateTest {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private lateinit var binding: FragmentVideoDetailBinding
    private lateinit var fragment: VideoDetailFragment
    private lateinit var sheet: BottomSheetBehavior<FrameLayout>
    private lateinit var errors: ErrorPanelHelper
    private lateinit var imageLoader: ImageLoader
    private val imageRequests = mutableListOf<String>()
    private val stackField = VideoDetailFragment::class.java.getDeclaredField("stack").apply { isAccessible = true }
    private var previousStack: Any? = null
    private val station = PlayQueueItem.radioStation(
        "Station",
        "https://radio.example/live?token=current",
        "Country · Language",
        true,
        "https://radio.example/logo.png"
    )

    @Before
    fun setUp() {
        previousStack = stackField.get(null)
        stackField.set(null, LinkedList<StackItem>())
        assertTrue(PlayerHolder.getInstance().player.isEmpty)
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java)
        activity = activityController.get()
        activity.setTheme(R.style.DarkTheme)
        activityController.setup()
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        InfoCache.getInstance().clearCache()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        imageLoader = ImageLoader.Builder(activity)
            .mainCoroutineContext(Dispatchers.Unconfined)
            .coroutineContext(Dispatchers.Unconfined)
            .components {
                add(
                    Interceptor { chain ->
                        imageRequests += chain.request.data.toString()
                        SuccessResult(bitmap.asImage(), chain.request)
                    }
                )
            }
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
    }

    @After
    fun tearDown() {
        try {
            if (::errors.isInitialized) errors.dispose()
            if (::fragment.isInitialized) {
                fieldValue(fragment, "currentWorker")?.let { (it as io.reactivex.rxjava3.disposables.Disposable).dispose() }
                fragment.playQueue?.dispose()
            }
            activityController.pause().stop().destroy()
            imageLoader.shutdown()
            SingletonImageLoader.reset()
            RxJavaPlugins.reset()
            InfoCache.getInstance().clearCache()
        } finally {
            stackField.set(null, previousStack)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun restoredStationLoadsWithoutAPlayerAndPreservesHlsArtworkAndIdentity() {
        val original = VideoDetailFragment.getInstance(station.serviceId, station.url, station.title, queue(station))
        val saved = save(original)
        val restored = VideoDetailFragment()
        StateSaver.restoreInstanceState(restored, saved)
        assertNull(restored.playQueue)
        bind(restored)

        mockStatic(ExtractorHelper::class.java).use { extractor ->
            fragment.doInitialLoadLogic()
            extractor.verifyNoInteractions()
        }
        assertStation()
        assertTrue(imageRequests.contains(station.localThumbnailUrl))
        assertEquals(View.GONE, binding.viewPager.visibility)
        assertEquals(View.GONE, binding.detailControlsDownload.visibility)
        assertFalse(fragment.isErrorPanelVisible)
        assertEquals(BottomSheetBehavior.STATE_COLLAPSED, sheet.state)
        assertFalse(sheet.isDraggable)
        assertFalse(binding.overlayMetadataLayout.isClickable)
    }

    @Test
    fun delayedBindingAndEveryRetryKeepRadioOutOfTheExtractor() {
        bind(VideoDetailFragment.getInstance(station.serviceId, station.url, station.title, null))
        val connected = queue(station)
        mockStatic(ExtractorHelper::class.java).use { extractor ->
            fragment.doInitialLoadLogic()
            fragment.onQueueUpdate(connected)
            assertStation()
            fragment.onMetadataUpdate(LocalMediaItemTag.of(station), connected)
            errors.showTextError("Previous failure")
            fragment.reloadContent()
            fragment.startLoading(false)
            VideoDetailFragment::class.java.getDeclaredMethod("startLoading", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(fragment, true, true)
            VideoDetailFragment::class.java.getDeclaredMethod("runWorker", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(fragment, true, true)
            extractor.verifyNoInteractions()
        }
        assertSame(connected, fragment.playQueue)
        assertStation()
        assertFalse(fragment.isErrorPanelVisible)
    }

    @Test
    fun selectingRadioWithoutAQueueBuildsUsableNavigationHistory() {
        bind(VideoDetailFragment.getInstance(0, "https://video.example/previous", "Previous", null))
        mockStatic(ExtractorHelper::class.java).use { extractor ->
            fragment.selectAndLoadVideo(station.serviceId, station.url, station.title, null)
            extractor.verifyNoInteractions()
        }
        val stack = stackField.get(null) as LinkedList<*>
        val selected = stack.peek() as StackItem
        assertTrue(selected.playQueue.item!!.isRadio)
        assertEquals(station.url, selected.playQueue.item!!.url)
        assertEquals(BottomSheetBehavior.STATE_COLLAPSED, sheet.state)
    }

    @Test
    fun videoRadioVideoTransitionsClearSavedRadioStateAndRestoreExpansion() {
        bind(VideoDetailFragment.getInstance(station.serviceId, station.url, station.title, queue(station)))
        mockStatic(ExtractorHelper::class.java).use { extractor ->
            fragment.doInitialLoadLogic()
            extractor.verifyNoInteractions()
            val url = "https://video.example/next"
            val title = "Next video"
            val video = SinglePlayQueue(StreamInfoItem(0, url, title, StreamType.VIDEO_STREAM))
            val network = Single.never<StreamInfo>()
            extractor.`when`<Single<StreamInfo>> { ExtractorHelper.getStreamInfo(0, url, false) }.thenReturn(network)
            mockStatic(DownloadedCopyRepository::class.java).use { copies ->
                copies.`when`<Single<StreamInfo>> {
                    DownloadedCopyRepository.streamInfoOrNetwork(activity, 0, url, title, network)
                }.thenReturn(network)
                fragment.selectAndLoadVideo(0, url, title, video)
                extractor.verify { ExtractorHelper.getStreamInfo(0, url, false) }
            }
            assertNull(fragment.currentLocalItem)
            assertSame(video, fragment.playQueue)
            assertTrue(sheet.isDraggable)
            assertTrue(binding.overlayMetadataLayout.isClickable)
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, DirectMediaDetailState.sheetState(BottomSheetBehavior.STATE_EXPANDED, false))

            val restoredVideo = VideoDetailFragment()
            StateSaver.restoreInstanceState(restoredVideo, save(fragment))
            assertNull(restoredVideo.currentLocalItem)
            assertEquals(0, restoredVideo.serviceId)
            fragment.selectAndLoadVideo(station.serviceId, station.url, station.title, queue(station))
            assertStation()
            assertFalse(sheet.isDraggable)
        }
    }

    @Test
    fun missingLocalDescriptorsAndUnknownNegativeIdsNeverReachAnExtractor() {
        bind(VideoDetailFragment.getInstance(-99, "https://unknown.example/live", "Unknown", null))
        mockStatic(ExtractorHelper::class.java).use { extractor ->
            for ((id, url) in listOf(-99 to "https://unknown.example/live", -1 to "content://media/audio/42", -2 to "http://radio.example/live")) {
                fragment.setInitialData(id, url, "Unavailable", null)
                fragment.doInitialLoadLogic()
                fragment.reloadContent()
                assertTrue(fragment.isErrorPanelVisible)
                assertNull(fragment.currentLocalItem)
            }
            extractor.verifyNoInteractions()
        }
    }

    @Test
    fun loadingBeforeTheViewExistsCancelsTheOldWorkerWithoutLosingTheStation() {
        val unbound = VideoDetailFragment.getInstance(station.serviceId, station.url, station.title, queue(station))
        val worker = Disposable.empty()
        val loading = AtomicBoolean(true)
        field(unbound, "currentWorker", worker)
        field(unbound, "isLoading", loading)
        mockStatic(ExtractorHelper::class.java).use { extractor ->
            unbound.startLoading(false)
            extractor.verifyNoInteractions()
        }
        assertTrue(worker.isDisposed)
        assertFalse(loading.get())
        assertEquals(station.url, unbound.currentLocalItem!!.url)
    }

    @Test
    fun savedStatePreservesExistingLocalAndLiveTvDescriptorsToo() {
        val local = PlayQueueItem.localMedia("Local", "content://media/audio/42", 120, "Artist", "Album", "Folder", "audio/flac", 42, false, "content://art/42")
        val tv = PlayQueueItem.liveTv("TV", "https://tv.example/live", "Group", "application/x-mpegURL", "https://tv.example/logo.png")
        for (item in listOf(local, tv)) {
            val original = VideoDetailFragment.getInstance(item.serviceId, item.url, item.title, queue(item))
            val restored = VideoDetailFragment()
            StateSaver.restoreInstanceState(restored, save(original))
            val recovered = DirectMediaNavigation.resolveQueue(restored.serviceId, restored.url, restored.title, restored.playQueue, restored.currentLocalItem, null)!!.item!!
            assertEquals(item.sourceType, recovered.sourceType)
            assertEquals(item.url, recovered.url)
            assertEquals(item.mimeType, recovered.mimeType)
            assertEquals(item.localThumbnailUrl, recovered.localThumbnailUrl)
            assertEquals(item.album, recovered.album)
            assertEquals(item.localMediaId, recovered.localMediaId)
        }
    }

    private fun bind(value: VideoDetailFragment) {
        fragment = spy(value)
        doReturn(activity).`when`(fragment).requireContext()
        doReturn(activity).`when`(fragment).requireActivity()
        doReturn(activity.resources).`when`(fragment).resources
        field(fragment, "activity", activity)
        binding = FragmentVideoDetailBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        activityController.visible()
        field(fragment, "binding", binding)
        field(fragment, "detailNavigation", binding.root.findViewById<View>(R.id.detail_navigation))
        field(fragment, "pageAdapter", TabAdapter(activity.supportFragmentManager))
        sheet = BottomSheetBehavior<FrameLayout>().apply { state = BottomSheetBehavior.STATE_EXPANDED }
        field(fragment, "bottomSheetBehavior", sheet)
        errors = ErrorPanelHelper(fragment, binding.root, Runnable { fragment.reloadContent() })
        field(fragment, "errorPanelHelper", errors)
        fragment.setAutoPlay(false)
    }

    private fun assertStation() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        val item = requireNotNull(fragment.playQueue?.item)
        assertTrue(item.isRadio)
        assertEquals(station.url, item.url)
        assertEquals(station.title, item.title)
        assertEquals(station.uploader, item.uploader)
        assertEquals(station.mimeType, item.mimeType)
        assertEquals(station.localThumbnailUrl, item.localThumbnailUrl)
        assertEquals(station.title, binding.detailVideoTitleView.text.toString())
        assertEquals(station.title, binding.overlayTitleTextView.text.toString())
    }

    private fun queue(item: PlayQueueItem) = LocalMediaPlayQueue(listOf(item), 0)

    private fun save(value: VideoDetailFragment): Bundle {
        val state = Bundle()
        StateSaver.saveInstanceState(value, state)
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(state)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(PlayQueueItem::class.java.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    private fun field(target: Any, name: String, value: Any?) = findField(target, name).set(target, value)

    private fun fieldValue(target: Any, name: String): Any? = findField(target, name).get(target)

    private fun findField(target: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                return type.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        error("Missing field: $name")
    }
}
