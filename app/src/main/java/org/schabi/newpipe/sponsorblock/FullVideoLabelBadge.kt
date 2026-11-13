/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sponsorblock

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Optional
import org.schabi.newpipe.R

/** Recycled rows cannot inherit an old disclosure. The full label is also spoken by TalkBack. */
class FullVideoLabelBadge internal constructor(
    private val container: FrameLayout,
    private val lookup: (Int, String) -> Single<Optional<FullVideoLabel>>
) {
    constructor(container: FrameLayout) : this(container, { service, url -> FullVideoLabels.get(container.context, service, url) })

    private val icon = AppCompatImageView(container.context).apply {
        id = R.id.full_video_label_badge
        setImageResource(R.drawable.ic_campaign)
        val padding = dp(3)
        setPadding(padding, padding, padding, padding)
        imageTintList = ColorStateList.valueOf(MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnSecondaryContainer, android.graphics.Color.WHITE))
        background = MaterialShapeDrawable().apply {
            setCornerSize(dp(12).toFloat())
            fillColor = ColorStateList.valueOf(MaterialColors.getColor(container, com.google.android.material.R.attr.colorSecondaryContainer, android.graphics.Color.DKGRAY))
        }
        visibility = View.GONE
    }
    private var request: Disposable? = null
    private var generation = 0
    private var identity: Pair<Int, String>? = null

    init {
        container.addView(icon, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                identity?.let { bind(it.first, it.second) }
            }

            override fun onViewDetachedFromWindow(view: View) {
                cancel()
            }
        })
    }

    fun bind(serviceId: Int, url: String) {
        clear()
        identity = serviceId to url
        val token = generation
        request = lookup(serviceId, url).observeOn(AndroidSchedulers.mainThread()).subscribe(
            { result ->
                if (generation != token || !FullVideoLabels.enabled(container.context)) return@subscribe
                val label = result.orElse(null) ?: return@subscribe
                val text = container.context.getString(label.text)
                icon.contentDescription = text
                TooltipCompat.setTooltipText(icon, text)
                icon.visibility = View.VISIBLE
            },
            { /* A provider failure must not interrupt browsing. */ }
        )
    }

    fun clear() {
        cancel()
        identity = null
        icon.visibility = View.GONE
        icon.contentDescription = null
        TooltipCompat.setTooltipText(icon, null)
    }

    private fun cancel() {
        generation++
        request?.dispose()
        request = null
    }

    private fun dp(value: Int): Int = (value * container.resources.displayMetrics.density).toInt()
}
