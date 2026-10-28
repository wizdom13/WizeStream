package org.schabi.newpipe.themes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeExtensionsTest {
    private fun png(width: Int = 16, height: Int = 16): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun recipe(): JSONObject = JSONObject()
        .put("format", "wizestream-theme").put("version", 2).put("name", "Glass record").put("author", "Creator")
        .put("glass", JSONObject().put("blur", 8).put("tint", 80).put("radius", 16))
        .put("assets", JSONObject().put("art", png()))
        .put("skin", "art")
        .put("icons", JSONObject().put("play", "art").put("pause", "art"))
        .put("player", JSONObject().put("layout", "vinyl").put("artwork", "art").put("background", "art"))

    @Test
    fun roundTripPreservesEmbeddedAssetsAndSettings() {
        val pack = ThemePack.parse(recipe().toString())
        assertEquals(pack, ThemePack.parse(pack.encode()))
        assertEquals(16, pack.assets.getValue("art").bitmap.width)
        assertEquals("art", pack.icons["play"])
        val context = RuntimeEnvironment.getApplication()
        ThemePacks.apply(context, pack)
        assertEquals(pack, ThemePacks.export(context))
        ThemePacks.restore(context)
        assertNull(ThemePacks.active(context))
    }

    @Test
    fun rejectsMissingReferencesWrongTypesUnknownSlotsAndUnsafeAssets() {
        val invalid = listOf(
            recipe().put("skin", "missing"),
            recipe().put("skin", "https://example.com/art.png"),
            recipe().put("version", 1),
            recipe().put("glass", "yes"),
            recipe().put("glass", JSONObject().put("tint", 0)),
            recipe().put("glass", JSONObject().put("blur", 17)),
            recipe().put("glass", JSONObject().put("radius", "12")),
            recipe().put("glass", JSONObject().put("css", "anything")),
            recipe().put("player", JSONObject().put("layout", "html")),
            recipe().put("icons", JSONObject().put("unknown", "art")),
            recipe().put("assets", JSONObject().put("../art", png())),
            recipe().put("assets", JSONObject().put("art", "data:image/png;base64," + png())),
            recipe().put("assets", JSONObject().put("art", Base64.encodeToString("<svg/>".toByteArray(), Base64.NO_WRAP))),
            recipe().put("assets", JSONObject().put("art", png(257, 16))),
            recipe().put("assets", JSONObject().put("art", png(1025, 1))),
            recipe().put("assets", JSONObject().put("art", png(1024, 1024)).put("other", png()))
        )
        invalid.forEach { assertThrows(Exception::class.java) { ThemePack.parse(it.toString()) } }
        val duplicate = recipe().toString().replace("\"blur\":8", "\"blur\":8,\"blur\":9")
        assertThrows(Exception::class.java) { ThemePack.parse(duplicate) }
        val tooMany = recipe().put("assets", JSONObject().apply { repeat(7) { put("asset$it", png()) } })
        assertThrows(Exception::class.java) { ThemePack.parse(tooMany.toString()) }
    }

    @Test
    fun draftUsesSmallTokenAndSurvivesRecreationWithoutTrustingPaths() {
        val context = RuntimeEnvironment.getApplication()
        val pack = ThemePack.parse(recipe().toString())
        val id = ThemeDraftStore.save(context, null, pack.encode())!!
        assertTrue(id.length < 80)
        assertEquals(pack, ThemeDraftStore.read(context, id))
        assertNull(ThemeDraftStore.read(context, "../../preferences"))
        ThemeDraftStore.remove(context, id)
        assertNull(ThemeDraftStore.read(context, id))
    }

    @Test
    fun nativeLayoutsHandleSmallAndLandscapeBoundsWithoutReplacingControls() {
        val context = RuntimeEnvironment.getApplication()
        for (layout in ThemeExtensions.layouts) {
            val pack = ThemePack.parse(recipe().put("player", JSONObject().put("layout", layout).put("artwork", "art")).toString())
            val view = ThemePlayerView(context).apply { this.pack = pack }
            for ((w, h) in listOf(1 to 1, 360 to 200, 200 to 360)) {
                view.layout(0, 0, w, h)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                bitmap.recycle()
            }
            assertFalse(view.isClickable)
            assertFalse(view.isFocusable)
        }
    }

    @Test
    fun boundedBlurPreservesUniformBackgroundAndDimensions() {
        val bitmap = Bitmap.createBitmap(40, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        GlassBackdrop.blur(bitmap, 16)
        assertEquals(Color.RED, bitmap.getPixel(10, 10))
        assertEquals(40, bitmap.width)
        assertEquals(24, bitmap.height)
    }
}
