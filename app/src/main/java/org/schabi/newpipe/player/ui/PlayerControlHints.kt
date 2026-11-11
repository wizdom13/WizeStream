/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.color.MaterialColors
import org.schabi.newpipe.R

/** Keep help inside the player window; native tooltip windows can fail in DeX. */
internal object PlayerControlHints {
    @JvmStatic
    fun install(root: View) {
        val host = root as? ViewGroup ?: return
        installChildren(host, host)
    }

    private fun installChildren(view: View, host: ViewGroup) {
        if (view.isClickable && !view.isLongClickable && !view.contentDescription.isNullOrBlank()) {
            view.setOnLongClickListener { control ->
                val label = control.contentDescription
                if (label.isNullOrBlank() || !control.isAttachedToWindow) {
                    false
                } else {
                    show(host, label)
                }
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) installChildren(view.getChildAt(index), host)
        }
    }

    private fun show(host: ViewGroup, label: CharSequence): Boolean {
        var hint = host.findViewById<HintView>(R.id.player_control_hint)
        if (hint == null) {
            val margin = (16 * host.resources.displayMetrics.density).toInt()
            val params = when (host) {
                is RelativeLayout -> RelativeLayout.LayoutParams(-2, -2).apply {
                    addRule(RelativeLayout.CENTER_HORIZONTAL)
                    addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    setMargins(margin, margin, margin, margin * 5)
                }

                is FrameLayout -> FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    setMargins(margin, margin, margin, margin * 5)
                }

                else -> return false
            }
            hint = HintView(host.context)
            host.addView(hint, params)
        }
        hint.showLabel(label)
        return true
    }

    private class HintView(context: Context) : AppCompatTextView(context) {
        private val hide = Runnable { visibility = GONE }

        init {
            id = R.id.player_control_hint
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
            background = GradientDrawable().apply {
                cornerRadius = padding.toFloat()
                setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.DKGRAY))
            }
            setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.WHITE))
            gravity = Gravity.CENTER
            textSize = 14f
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
            isClickable = false
            isFocusable = false
        }

        fun showLabel(label: CharSequence) {
            removeCallbacks(hide)
            text = label
            visibility = VISIBLE
            bringToFront()
            postDelayed(hide, 2500)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(hide)
            visibility = GONE
            super.onDetachedFromWindow()
        }
    }
}
