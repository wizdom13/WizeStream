package org.schabi.newpipe.player.pip

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.view.PixelCopy
import android.view.View
import androidx.media3.common.Player
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.util.NavigationHelper

@SdkSuppress(minSdkVersion = 26)
class NativePipPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun portraitVideoRemainsVisibleAcrossPipEntryExitAndFullscreenEntry() {
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val pipKey = context.getString(R.string.native_pip_key)
        val previousPip = prefs.all[pipKey] as? Boolean
        val starPromptKey = "github_star_prompt_completed"
        val previousStarPrompt = prefs.all[starPromptKey] as? Boolean
        val changelog = context.getSharedPreferences("changelog", Context.MODE_PRIVATE)
        val previousSeenRelease = changelog.all["last_seen_release"] as? Int
        // Launcher return must exercise playback, not the first-run release-notes dialog.
        changelog.edit().putInt("last_seen_release", Int.MAX_VALUE).commit()
        val automation = instrumentation.uiAutomation
        val previousFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        prefs.edit().putBoolean(pipKey, false).putBoolean(starPromptKey, true).commit()
        // Original, solid-purple 9:16 H.264 fixture; no network or extractor dependency.
        val video = File(context.cacheDir, "native-pip-portrait.mp4")
        val encoded = instrumentation.context.assets.open("native-pip/portrait.mp4.base64").use { it.readBytes() }
        video.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
        try {
            onActivity(scenario) { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val item = PlayQueueItem.localMedia("Vertical PiP test", video.toURI().toString(), 8, "", "", "", "video/mp4", -1, true, null)
                NavigationHelper.playOnMainPlayer(activity, LocalMediaPlayQueue(listOf(item), 0))
            }
            waitFor(scenario, "video playback") { activity ->
                val player = PlayerHolder.getInstance().player.orElse(null)
                player?.getExoPlayer()?.videoSize?.height == 160 && detail(activity)?.isNativePipEligible == true
            }
            onActivity(scenario) {
                PlayerHolder.getInstance().player.get().exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            }
            assertVisibleFrame("native-pip-before")
            for (fullscreen in listOf(false, true)) {
                var expandedSize = 0
                onActivity(scenario) { activity ->
                    expandedSize = detail(activity)!!.requireView().let { maxOf(it.width, it.height) }
                    if (fullscreen) {
                        playerUi().toggleFullscreenWithOrientation()
                    }
                }
                waitFor(scenario, "fullscreen state") { activity ->
                    val root = detail(activity)!!.requireView()
                    val surface = playerUi().binding.surfaceView
                    playerUi().isFullscreen == fullscreen &&
                        !root.isLayoutRequested && !surface.isLayoutRequested &&
                        (
                            !fullscreen || root.height > root.width &&
                                root.findViewById<View>(R.id.detail_thumbnail_image_view).height == root.height &&
                                activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                            )
                }
                // A configuration callback precedes the actual rotation/window transition.
                instrumentation.uiAutomation.waitForIdle(500, 5000)
                onActivity(scenario) { activity ->
                    // Also verify a paused video keeps its decoded frame after entering PiP.
                    PlayerHolder.getInstance().player.get().exoPlayer.playWhenReady = !fullscreen
                    assertTrue(NativePipController(activity).enterPictureInPicture())
                }
                waitFor(scenario, "PiP surface inside resized viewport") { activity ->
                    val fragment = detail(activity) ?: return@waitFor false
                    val root = fragment.requireView()
                    val surface = playerUi().binding.surfaceView
                    val visible = Rect()
                    activity.isInPictureInPictureMode && root.height in 1 until expandedSize &&
                        root.findViewById<View>(R.id.detail_thumbnail_image_view).height == root.height &&
                        surface.getLocalVisibleRect(visible) && visible.height() == surface.height &&
                        surface.height <= root.height && surface.width <= root.width &&
                        PlayerHolder.getInstance().player.get().exoPlayer.videoSize.width == 90
                }
                assertVisibleFrame("native-pip-${if (fullscreen) "paused-fullscreen" else "playing"}", inPip = true)
                onActivity(scenario) {
                    val engine = PlayerHolder.getInstance().player.get().exoPlayer
                    assertEquals(!fullscreen, engine.playWhenReady)
                    assertEquals(90, engine.videoSize.width)
                    assertTrue(playerUi().isFullscreen)
                }
                returnToApp()
                waitFor(scenario, "return from PiP") { activity ->
                    !activity.isInPictureInPictureMode && activity.hasWindowFocus() && playerUi().isFullscreen == fullscreen
                }
                instrumentation.uiAutomation.waitForIdle(500, 5000)
                // Resume callbacks may show paused controls again, dimming the fixture. Dismiss
                // them at capture time; PiP itself must pass without this extra dismissal.
                assertVisibleFrame("native-pip-restored-$fullscreen") {
                    onActivity(scenario) { playerUi().hideControls(0, 0) }
                }
                onActivity(scenario) { assertFalse(it.isInPictureInPictureMode) }
            }
        } finally {
            returnToApp()
            waitFor(scenario, "PiP cleanup") { !it.isInPictureInPictureMode }
            onActivity(scenario) { activity ->
                PlayerHolder.getInstance().stopService()
                activity.finish()
            }
            scenario.close()
            automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
            prefs.edit().apply { previousPip?.let { putBoolean(pipKey, it) } ?: remove(pipKey) }.commit()
            prefs.edit().apply { previousStarPrompt?.let { putBoolean(starPromptKey, it) } ?: remove(starPromptKey) }.commit()
            changelog.edit().apply {
                previousSeenRelease?.let { putInt("last_seen_release", it) } ?: remove("last_seen_release")
            }.commit()
            video.delete()
        }
    }

    private fun detail(activity: MainActivity): VideoDetailFragment? = activity.supportFragmentManager.findFragmentById(R.id.fragment_player_holder) as? VideoDetailFragment

    private fun playerUi(): MainPlayerUi = PlayerHolder.getInstance().player.get().UIs().get(MainPlayerUi::class.java).get()

    private fun returnToApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    private fun assertVisibleFrame(name: String, inPip: Boolean = false, beforeCapture: () -> Unit = {}) {
        // Check the composed display, not only a decoder callback or a valid Surface handle.
        // A decoded frame outside the PiP viewport must fail this assertion.
        var purplePixels = 0
        repeat(60) {
            beforeCapture()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val bounds = Rect(0, 0, bitmap.width, bitmap.height)
            if (inPip) {
                bounds.setEmpty()
                instrumentation.uiAutomation.windows.firstOrNull { it.isInPictureInPictureMode }?.getBoundsInScreen(bounds)
                if (!bounds.intersect(0, 0, bitmap.width, bitmap.height)) bounds.setEmpty()
                if (!bounds.isEmpty) assertTrue("Portrait video must use a portrait PiP window", bounds.height() > bounds.width())
            }
            purplePixels = 0
            for (y in bounds.top until bounds.bottom step 4) {
                for (x in bounds.left until bounds.right step 4) {
                    val color = bitmap.getPixel(x, y)
                    if (Color.red(color) in 150..200 && Color.green(color) in 10..55 && Color.blue(color) in 200..245) purplePixels++
                }
            }
            if (purplePixels > 150 || it == 59) savePreview(bitmap, name)
            bitmap.recycle()
            if (purplePixels > 150) return
            Thread.sleep(100)
        }
        assertTrue("Expected visible purple video in $name, found $purplePixels samples; ${frameDiagnostic()}", purplePixels > 150)
    }

    private fun frameDiagnostic(): String {
        val done = CountDownLatch(1)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        var diagnostic = ""
        Handler(Looper.getMainLooper()).post {
            try {
                val ui = playerUi()
                val binding = ui.binding
                diagnostic = "controls=${binding.playbackControlRoot.visibility}/${binding.playbackControlRoot.alpha}, shadow=${binding.playbackControlsShadow.visibility}/${binding.playbackControlsShadow.alpha}, activity=${ui.parentActivity.orElse(null)?.javaClass?.name}"
                PixelCopy.request(binding.surfaceView, bitmap, { result ->
                    diagnostic += ", copy=$result, pixel=${Integer.toHexString(bitmap.getPixel(8, 8))}"
                    done.countDown()
                }, Handler(Looper.getMainLooper()))
            } catch (error: Throwable) {
                diagnostic += ", error=$error"
                done.countDown()
            }
        }
        if (done.await(5, TimeUnit.SECONDS)) bitmap.recycle()
        return diagnostic
    }

    private fun savePreview(bitmap: Bitmap, name: String) {
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val target = "/sdcard/Download/wizestream-player-layout-previews"
        listOf("mkdir -p $target", "cp ${file.absolutePath} $target/").forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    private fun waitFor(scenario: ActivityScenario<MainActivity>, state: String, condition: (MainActivity) -> Boolean) {
        repeat(200) {
            var ready = false
            onActivity(scenario) { ready = condition(it) }
            if (ready) return
            Thread.sleep(50)
        }
        var diagnostic = ""
        onActivity(scenario) { activity ->
            val player = PlayerHolder.getInstance().player.orElse(null)
            val root = detail(activity)?.view
            val surface = player?.UIs()?.get(MainPlayerUi::class.java)?.orElse(null)?.binding?.surfaceView
            diagnostic = "pip=${activity.isInPictureInPictureMode}, viewport=${root?.width}x${root?.height}, surface=${surface?.width}x${surface?.height}, video=${player?.getExoPlayer()?.videoSize}, presentation=${player?.playbackPresentationMode}"
        }
        throw AssertionError("Timed out waiting for $state: $diagnostic")
    }

    private fun onActivity(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        Handler(Looper.getMainLooper()).post {
            try {
                scenario.onActivity(action)
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                done.countDown()
            }
        }
        assertTrue("Main-thread action timed out", done.await(10, TimeUnit.SECONDS))
        failure.get()?.let { throw it }
    }
}
