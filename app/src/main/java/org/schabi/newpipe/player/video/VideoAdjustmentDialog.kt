/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.video

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogVideoAdjustmentsBinding

object VideoAdjustmentDialog {
    @JvmStatic
    fun show(context: Context, controller: VideoAdjustmentController): AlertDialog {
        // Inflate with the dialog theme so sliders and switches follow every app palette.
        val builder = MaterialAlertDialogBuilder(context)
        val binding = DialogVideoAdjustmentsBinding.inflate(LayoutInflater.from(builder.context))
        val handler = Handler(Looper.getMainLooper())
        var rendering = false

        fun readControls() = VideoAdjustmentState(
            enabled = binding.adjustmentsEnabled.isChecked,
            brightness = binding.brightnessSlider.value.toInt(),
            contrast = binding.contrastSlider.value.toInt(),
            saturation = binding.saturationSlider.value.toInt(),
            remember = binding.adjustmentsRemember.isChecked
        )

        fun updateLabels() {
            binding.brightnessLabel.text = context.getString(
                R.string.video_adjustments_value,
                context.getString(R.string.video_adjustments_brightness),
                binding.brightnessSlider.value.toInt()
            )
            binding.contrastLabel.text = context.getString(
                R.string.video_adjustments_value,
                context.getString(R.string.video_adjustments_contrast),
                binding.contrastSlider.value.toInt()
            )
            binding.saturationLabel.text = context.getString(
                R.string.video_adjustments_value,
                context.getString(R.string.video_adjustments_saturation),
                binding.saturationSlider.value.toInt()
            )
        }

        var pending = false
        val apply = Runnable {
            pending = false
            controller.update(readControls())
        }
        val sliders = listOf(binding.brightnessSlider, binding.contrastSlider, binding.saturationSlider)
        val render = Runnable {
            rendering = true
            val state = controller.state
            binding.adjustmentsEnabled.isChecked = state.enabled
            binding.adjustmentsRemember.isChecked = state.remember
            binding.brightnessSlider.value = state.brightness.toFloat()
            binding.contrastSlider.value = state.contrast.toFloat()
            binding.saturationSlider.value = state.saturation.toFloat()
            sliders.forEach { it.isEnabled = state.enabled }
            binding.adjustmentsStatus.setText(
                when {
                    controller.failed -> R.string.video_adjustments_failed
                    !state.enabled -> R.string.video_adjustments_disabled
                    controller.hdr -> R.string.video_adjustments_hdr
                    else -> R.string.video_adjustments_active
                }
            )
            updateLabels()
            rendering = false
        }
        binding.adjustmentsEnabled.setOnCheckedChangeListener { _, _ ->
            if (!rendering) {
                pending = false
                handler.removeCallbacks(apply)
                apply.run()
            }
        }
        binding.adjustmentsRemember.setOnCheckedChangeListener { _, _ ->
            if (!rendering) {
                pending = false
                handler.removeCallbacks(apply)
                apply.run()
            }
        }
        sliders.forEach { slider ->
            slider.setLabelFormatter { context.getString(R.string.video_adjustments_percent, it.toInt()) }
            slider.addOnChangeListener { _, _, fromUser ->
                if (fromUser && !rendering) {
                    updateLabels()
                    // Coalesce rapid touch events without postponing preview until dragging ends.
                    if (!pending) {
                        pending = true
                        handler.postDelayed(apply, 50)
                    }
                }
            }
            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit

                override fun onStopTrackingTouch(slider: Slider) {
                    pending = false
                    handler.removeCallbacks(apply)
                    apply.run()
                }
            })
        }
        controller.addListener(render)
        render.run()
        val dialog = builder.setTitle(R.string.video_adjustments)
            .setView(binding.root)
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.reset, null)
            .create()
        dialog.setOnDismissListener {
            pending = false
            handler.removeCallbacks(apply)
            controller.removeListener(render)
            controller.update(readControls())
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            pending = false
            handler.removeCallbacks(apply)
            controller.update(readControls().reset())
            render.run()
        }
        return dialog
    }
}
