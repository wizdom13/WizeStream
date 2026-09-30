package org.schabi.newpipe.player.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.fragments.list.comments.CommentRepliesFragment
import org.schabi.newpipe.fragments.list.comments.CommentsFragment
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.NavigationHelper

class FullscreenCommentsSidebarIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun commentsAndRepliesStayOutOfNavigationAndOneRemoteBackRestoresTheSameVideo() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldSort = prefs.all["comment_sort_newest"] as? Boolean
        prefs.edit().putBoolean("comment_sort_newest", false).commit()
        val comment = cacheComments()
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        try {
            lateinit var binding: PlayerBinding
            var navigationChanges = 0
            var navigationCount = 0
            onActivity(scenario) { activity ->
                binding = attachPlayer(activity)
                navigationCount = activity.supportFragmentManager.backStackEntryCount
                activity.supportFragmentManager.addOnBackStackChangedListener { navigationChanges++ }
                FullscreenCommentsSidebar.show(activity, 0, URL, "Sidebar test")
                activity.supportFragmentManager.executePendingTransactions()
            }
            waitFor(scenario) { activity ->
                sidebar(activity)?.dialog?.window?.decorView?.hasWindowFocus() == true &&
                    binding.playerVideoContent.width in 1 until binding.root.width
            }
            onActivity(scenario) { activity ->
                val sidebar = requireNotNull(sidebar(activity))
                assertTrue(sidebar.childFragmentManager.findFragmentById(R.id.fullscreenCommentsContent) is CommentsFragment)
                assertEquals(navigationCount, activity.supportFragmentManager.backStackEntryCount)
                assertEquals(0, navigationChanges)
                assertTrue(binding.playerVideoContent.width < binding.root.width)
                assertTrue(binding.surfaceView.width <= binding.playerVideoContent.width)
                NavigationHelper.openCommentRepliesFragment(activity, comment)
                sidebar.childFragmentManager.executePendingTransactions()
                assertTrue(sidebar.childFragmentManager.findFragmentById(R.id.fullscreenCommentsContent) is CommentRepliesFragment)
                assertEquals(navigationCount, activity.supportFragmentManager.backStackEntryCount)
                sidebar.requireView().findViewById<View>(R.id.fullscreenCommentsBack).performClick()
                sidebar.childFragmentManager.executePendingTransactions()
                assertTrue(sidebar.childFragmentManager.findFragmentById(R.id.fullscreenCommentsContent) is CommentsFragment)
            }
            savePreview()
            val surface = binding.surfaceView
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor(scenario) { activity -> sidebar(activity) == null }
            onActivity(scenario) { activity ->
                assertFalse(activity.isFinishing)
                assertEquals(0, (binding.playerVideoContent.layoutParams as ViewGroup.MarginLayoutParams).marginEnd)
                assertSame(surface, binding.surfaceView)
                assertEquals(navigationCount, activity.supportFragmentManager.backStackEntryCount)
                assertEquals(0, navigationChanges)
                FullscreenCommentsSidebar.show(activity, 0, URL, "Sidebar test")
                activity.supportFragmentManager.executePendingTransactions()
                assertNotNull(sidebar(activity)?.childFragmentManager?.findFragmentById(R.id.fullscreenCommentsContent)?.view)
            }
            scenario.recreate()
            onActivity(scenario) { activity -> binding = attachPlayer(activity) }
            waitFor(scenario) { activity ->
                sidebar(activity)?.dialog?.window?.decorView?.hasWindowFocus() == true &&
                    binding.playerVideoContent.width in 1 until binding.root.width
            }
            onActivity(scenario) { activity ->
                assertNotNull(sidebar(activity)?.childFragmentManager?.findFragmentById(R.id.fullscreenCommentsContent)?.view)
                NavigationHelper.openCommentRepliesFragment(activity, comment)
                sidebar(activity)!!.childFragmentManager.executePendingTransactions()
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor(scenario) { activity -> sidebar(activity) == null }
            onActivity(scenario) { activity ->
                assertFalse(activity.isFinishing)
                assertEquals(0, (binding.playerVideoContent.layoutParams as ViewGroup.MarginLayoutParams).marginEnd)
            }
        } finally {
            onActivity(scenario) { it.finish() }
            scenario.close()
            InfoCache.getInstance().removeInfo(0, URL, InfoCache.Type.COMMENTS)
            prefs.edit().apply { oldSort?.let { putBoolean("comment_sort_newest", it) } ?: remove("comment_sort_newest") }.commit()
        }
    }

    private fun attachPlayer(activity: AboutActivity): PlayerBinding = PlayerBinding.inflate(activity.layoutInflater).also {
        it.loadingPanel.visibility = View.GONE
        it.surfaceForeground.setBackgroundColor(0xff204050.toInt())
        it.playbackControlRoot.visibility = View.VISIBLE
        it.titleTextView.text = "Fullscreen video keeps its place"
        it.channelTextView.text = "Comments stay beside the video"
        activity.setContentView(it.root)
    }

    private fun sidebar(activity: AboutActivity): FullscreenCommentsDialog? = activity.supportFragmentManager.findFragmentByTag(FullscreenCommentsSidebar.TAG) as? FullscreenCommentsDialog

    private fun cacheComments(): CommentsInfoItem {
        val comment = CommentsInfoItem(0, URL, "Test comment").apply {
            commentId = "sidebar-comment"
            commentText = "The video should remain fullscreen while these comments are open."
            uploaderName = "WizeStream tester"
            uploaderUrl = ""
            uploaderAvatarUrl = ""
            textualUploadDate = "today"
            likeCount = 2
        }
        val constructor = CommentsInfo::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType, ListLinkHandler::class.java, String::class.java)
        constructor.isAccessible = true
        val info = constructor.newInstance(0, ServiceList.YouTube.commentsLHFactory.fromUrl(URL), "Sidebar test").apply {
            relatedItems = listOf(comment)
        }
        InfoCache.getInstance().putInfo(0, URL, info, InfoCache.Type.COMMENTS)
        return comment
    }

    private fun savePreview() {
        val image = instrumentation.uiAutomation.takeScreenshot() ?: return
        val file = File(context.getExternalFilesDir(null), "fullscreen-comments-sidebar.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val target = "/sdcard/Download/wizestream-player-layout-previews"
        listOf("mkdir -p $target", "cp ${file.absolutePath} $target/").forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    private fun waitFor(scenario: ActivityScenario<AboutActivity>, condition: (AboutActivity) -> Boolean) {
        repeat(100) {
            var ready = false
            onActivity(scenario) { ready = condition(it) }
            if (ready) return
            Thread.sleep(50)
        }
        throw AssertionError("Comments panel did not reach its expected state")
    }

    private fun onActivity(scenario: ActivityScenario<AboutActivity>, action: (AboutActivity) -> Unit) {
        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        Handler(Looper.getMainLooper()).post {
            try {
                scenario.onActivity(action)
            } catch (failure: Throwable) {
                error.set(failure)
            } finally {
                done.countDown()
            }
        }
        assertTrue("Main-thread action timed out", done.await(10, TimeUnit.SECONDS))
        error.get()?.let { throw it }
    }

    companion object {
        private const val URL = "https://www.youtube.com/watch?v=sidebar0001"
    }
}
