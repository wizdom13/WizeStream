package org.schabi.newpipe.learning

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reactivex.rxjava3.processors.PublishProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.database.learning.model.VideoNoteStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class VideoNoteBadgesViewTest {
    @Test
    fun indicatorsFollowReactiveNotesAndClearRecycledRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rows = PublishProcessor.create<List<VideoNoteStream>>()
        var changes = 0
        val badges = VideoNoteBadges(context, Runnable { changes++ }, rows)
        lateinit var root: FrameLayout
        lateinit var badge: ImageButton
        val url = "https://example.com/video"
        val video = StreamInfoItem(0, url, "Lesson", StreamType.VIDEO_STREAM)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            instrumentation.runOnMainSync {
                root = FrameLayout(context).apply { id = R.id.itemThumbnailContainer }
                badges.attach()
            }
            assertTrue(rows.hasSubscribers())
            rows.onNext(listOf(VideoNoteStream(42, 0, url)))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(1, changes)
                badges.bindOnline(root, video)
                badge = root.findViewById(R.id.itemVideoNoteBadge)
                assertEquals(View.VISIBLE, badge.visibility)
                assertTrue(badge.performClick())
                assertEquals(1, changes)

                badges.bindOnline(root, StreamInfoItem(1, url, "Other service", StreamType.VIDEO_STREAM))
                assertEquals(View.GONE, badge.visibility)
                assertFalse(badge.hasOnClickListeners())
                badges.bindOnline(root, video)
                assertEquals(1, root.childCount)
                assertEquals(View.VISIBLE, badge.visibility)
            }

            rows.onNext(emptyList())
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                badges.bindOnline(root, video)
                assertEquals(View.GONE, badge.visibility)
                badges.detach()
                assertFalse(rows.hasSubscribers())
                badges.attach()
            }
            rows.onNext(emptyList())
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(3, changes)
                badges.bindOnline(root, video)
                assertEquals(View.GONE, badge.visibility)
            }
        } finally {
            instrumentation.runOnMainSync { badges.detach() }
        }
    }

    @Test
    fun unannotatedVideosDoNotCreateAnOverlayAndSharedAttachmentsStaySubscribed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rows = PublishProcessor.create<List<VideoNoteStream>>()
        val badges = VideoNoteBadges(context, Runnable { }, rows)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = FrameLayout(context).apply { id = R.id.itemThumbnailContainer }
            badges.bindOnline(root, StreamInfoItem(0, "https://example.com/video", "Title", StreamType.VIDEO_STREAM))
            assertNull(root.findViewById<View>(R.id.itemVideoNoteBadge))
            badges.attach()
            badges.attach()
            badges.detach()
            assertTrue(rows.hasSubscribers())
            badges.detach()
            assertFalse(rows.hasSubscribers())
        }
    }
}
