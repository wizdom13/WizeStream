/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.view.View
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.PlaybackPresentationMode
import org.schabi.newpipe.util.StreamTypeUtil

/** Direct audio livestreams have no video surface, including when metadata has no StreamInfo. */
internal object DirectMediaPresentation {
    @JvmStatic
    fun update(surface: View, artwork: View, type: StreamType, mode: PlaybackPresentationMode) {
        val showVideo = !StreamTypeUtil.isAudio(type) && mode.rendersVideo()
        surface.visibility = if (showVideo) View.VISIBLE else View.GONE
        artwork.visibility = if (!showVideo && !mode.allowsVisualizer()) View.VISIBLE else View.GONE
    }
}
