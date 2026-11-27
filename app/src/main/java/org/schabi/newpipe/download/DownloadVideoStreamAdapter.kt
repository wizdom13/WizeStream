/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.collection.SparseArrayCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.SecondaryStreamHelper
import org.schabi.newpipe.util.StreamItemAdapter
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper

/** Keeps source metadata unchanged while naming the file the manual downloader will produce. */
internal class DownloadVideoStreamAdapter(
    streams: StreamInfoWrapper<VideoStream>,
    secondaryStreams: SparseArrayCompat<SecondaryStreamHelper<AudioStream>>
) : StreamItemAdapter<VideoStream, AudioStream>(streams, secondaryStreams) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = super.getView(position, convertView, parent).also {
        labelHls(it, (parent as Spinner).selectedItemPosition)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = super.getDropDownView(position, convertView, parent).also { labelHls(it, position) }

    private fun labelHls(view: View, position: Int) {
        val stream = getItem(position)
        if (!DownloadSourcePolicy.isHlsVideo(stream)) return
        val resolution = stream.resolution.trim().takeIf { RESOLUTION.matches(it) }
        view.findViewById<TextView>(R.id.stream_quality).text = if (resolution == null) {
            view.context.getString(R.string.download_hls_best_available)
        } else {
            view.context.getString(R.string.download_hls_quality, resolution)
        }
        view.findViewById<TextView>(R.id.stream_format_name).text = MediaFormat.MPEG_4.getName()
        view.findViewById<View>(R.id.stream_size).visibility = View.GONE
    }

    private companion object {
        val RESOLUTION = Regex("[1-9][0-9]{1,4}p(?:[0-9]{1,3})?")
    }
}
