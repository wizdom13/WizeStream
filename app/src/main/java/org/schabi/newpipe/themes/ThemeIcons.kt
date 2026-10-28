package org.schabi.newpipe.themes

import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView

object ThemeIcons {
    /** Changes only artwork: listeners, labels, focus, tint and state remain native. */
    @JvmStatic
    fun apply(view: ImageView, slot: String) {
        val pack = ThemePacks.active(view.context) ?: return
        val asset = pack.icons[slot]?.let(pack.assets::get) ?: return
        view.setImageDrawable(BitmapDrawable(view.resources, asset.bitmap))
    }
}
