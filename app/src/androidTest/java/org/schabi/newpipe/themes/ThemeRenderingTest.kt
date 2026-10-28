package org.schabi.newpipe.themes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.ParcelFileDescriptor
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageButton
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class ThemeRenderingTest {
    @Test
    fun nativeTemplatesRenderOnDeviceAndIconsKeepTheirActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = ThemePacks.active(context)
        val pack = context.assets.open("themes/glass-record.json").use(ThemePack::read)
        val folder = File(context.getExternalFilesDir(null), "player-layout-previews").apply { mkdirs() }
        try {
            ThemePacks.apply(context, pack)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val binding = org.schabi.newpipe.databinding.PlayerBinding.inflate(android.view.LayoutInflater.from(ContextThemeWrapper(context, R.style.DarkTheme)))
                ThemePlayerView.updatePresentation(binding, true)
                assertEquals(View.VISIBLE, binding.themePlayer.visibility)
                assertEquals(View.GONE, binding.audioVisualizer.visibility)
                ThemePlayerView.updatePresentation(binding, false)
                assertEquals(View.GONE, binding.themePlayer.visibility)
                assertEquals(View.GONE, binding.audioVisualizer.visibility)
                val button = ImageButton(context).apply { contentDescription = "Play" }
                var clicks = 0
                button.setOnClickListener { clicks++ }
                for (slot in listOf("play", "pause", "replay")) {
                    ThemeIcons.apply(button, slot)
                    button.performClick()
                }
                assertEquals(3, clicks)
                assertEquals("Play", button.contentDescription)
                for ((mode, style) in listOf("light" to R.style.LightTheme, "dark" to R.style.DarkTheme)) {
                    val palette = ContextThemeWrapper(context, style)
                    for (layout in listOf("vinyl", "pocket", "cassette")) {
                        val view = ThemePlayerView(palette).apply { this.pack = pack.copy(player = pack.player.copy(layout = layout)) }
                        for ((width, height) in listOf(720 to 420, 420 to 720)) {
                            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                            view.layout(0, 0, width, height)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            view.draw(Canvas(bitmap))
                            assertTrue(bitmap.getPixel(width / 2, height / 2) != 0)
                            assertFalse(view.isClickable)
                            File(folder, "theme-$layout-$mode-$width.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bitmap.recycle()
                        }
                    }
                }
            }
            val destination = "/sdcard/Download/wizestream-player-layout-previews"
            val commands = listOf("mkdir -p $destination") + folder.listFiles().orEmpty().filter { it.name.startsWith("theme-") }.map { "cp ${it.absolutePath} $destination/" }
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            commands.forEach { command ->
                ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
            }
        } finally {
            if (original == null) ThemePacks.restore(context) else ThemePacks.apply(context, original)
        }
    }
}
