/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding

/** Sound and fullscreen remain beside one another without opening the optional tools row. */
object ChannelMuteControls {
    @JvmStatic
    fun bind(binding: PlayerBinding, mute: View.OnClickListener?, fullscreen: View.OnClickListener?) {
        binding.channelMuteButton.setOnClickListener(mute)
        binding.popupMuteButton.setOnClickListener(mute)
        binding.popupFullscreenButton.setOnClickListener(fullscreen)
        if (mute == null) restorePopupTools(binding)
    }

    @JvmStatic
    fun show(binding: PlayerBinding, popup: Boolean) {
        binding.channelMuteButton.visibility = if (popup) View.GONE else View.VISIBLE
        binding.popupPlaybackActions.visibility = if (popup) View.VISIBLE else View.GONE
        binding.popupToolsScroller.visibility = if (popup) View.VISIBLE else View.GONE
        if (popup) {
            binding.fullScreenButton.visibility = View.GONE
            if (binding.topControls.parent !== binding.popupToolsScroller) {
                binding.popupToolsScroller.tag = PopupLayoutState(
                    binding.topControls.layoutParams,
                    binding.playbackSeekBar.nextFocusUpId
                )
                (binding.topControls.parent as ViewGroup).removeView(binding.topControls)
                binding.popupToolsScroller.addView(
                    binding.topControls,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
                binding.playbackSeekBar.nextFocusUpId = R.id.popupFullscreenButton
            }
        } else {
            restorePopupTools(binding)
        }
    }

    @JvmStatic
    fun update(binding: PlayerBinding, muted: Boolean, hasChannelIdentity: Boolean) {
        val context = binding.root.context
        val label = when {
            hasChannelIdentity && muted -> R.string.channel_playback_unmute
            hasChannelIdentity -> R.string.channel_playback_mute
            muted -> R.string.unmute
            else -> R.string.mute
        }
        listOf(binding.channelMuteButton, binding.popupMuteButton).forEach {
            it.setImageDrawable(AppCompatResources.getDrawable(context, if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up))
            it.contentDescription = context.getString(label)
            it.isActivated = muted
        }
        binding.switchMute.contentDescription = context.getString(if (muted) R.string.unmute else R.string.mute)
        binding.switchMute.isActivated = muted
    }

    private fun restorePopupTools(binding: PlayerBinding) {
        val state = binding.popupToolsScroller.tag as? PopupLayoutState ?: return
        binding.popupToolsScroller.removeView(binding.topControls)
        binding.playbackWindowRoot.addView(binding.topControls, 0, state.params)
        binding.playbackSeekBar.nextFocusUpId = state.seekUp
        binding.popupToolsScroller.tag = null
    }

    private data class PopupLayoutState(val params: ViewGroup.LayoutParams, val seekUp: Int)
}
