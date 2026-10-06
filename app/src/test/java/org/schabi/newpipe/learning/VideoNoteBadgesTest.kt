package org.schabi.newpipe.learning

import android.app.Activity
import android.widget.FrameLayout
import io.reactivex.rxjava3.processors.PublishProcessor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.database.learning.model.VideoNoteStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class VideoNoteBadgesTest {
    @Test
    fun windowDetachmentDisposesObserverAndReattachmentReloadsIt() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val rows = PublishProcessor.create<List<VideoNoteStream>>()
        val badges = VideoNoteBadges(activity, Runnable { }, rows)
        badges.watch(root)
        assertFalse(rows.hasSubscribers())
        activity.setContentView(root)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(rows.hasSubscribers())
        activity.setContentView(FrameLayout(activity))
        assertFalse(rows.hasSubscribers())
        activity.setContentView(root)
        assertTrue(rows.hasSubscribers())
        badges.unwatch(root)
        assertFalse(rows.hasSubscribers())
        controller.pause().stop().destroy()
    }
}
