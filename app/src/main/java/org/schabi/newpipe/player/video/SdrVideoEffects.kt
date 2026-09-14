/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.video

import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbMatrix

internal object SdrVideoEffects {
    fun create(state: VideoAdjustmentState): List<Effect> {
        val safe = state.clamped()
        return listOf(
            SdrMatrix(Brightness(safe.brightness / 100f)),
            SdrMatrix(Contrast(safe.contrast / 100f)),
            SdrEffect(HslAdjustment.Builder().adjustSaturation(safe.saturation - 100f).build())
        )
    }

    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    // Check the actual GL input, not an asynchronous format/UI callback: the next queue item
    // or an adaptive stream can change to HDR before the app receives that callback.
    internal class SdrMatrix(private val sdr: RgbMatrix) : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = if (useHdr) identity else sdr.getMatrix(presentationTimeUs, false)
    }

    internal class SdrEffect(private val sdr: GlEffect) : GlEffect {
        internal fun forInput(useHdr: Boolean): GlEffect = if (useHdr) {
            RgbMatrix { _, _ -> identity }
        } else {
            sdr
        }

        override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = forInput(useHdr).toGlShaderProgram(context, useHdr)
    }
}
