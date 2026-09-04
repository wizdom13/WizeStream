/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper

internal fun interface DownloadAudioStreamsProvider {
    fun get(): StreamInfoWrapper<AudioStream>
}

internal fun interface DownloadStreamSizeLoadedListener {
    fun onLoaded(option: DownloadMediaOption)
}

internal fun interface DownloadStreamSizeErrorListener {
    fun onError(option: DownloadMediaOption, throwable: Throwable)
}

internal fun interface DownloadStreamInfoFetcher {
    fun fetch(streams: StreamInfoWrapper<out Stream>): Single<Boolean>
}

/** Owns the asynchronous stream metadata requests and their cancellation. */
internal class DownloadStreamSizeLoader @JvmOverloads constructor(
    private val videoStreams: StreamInfoWrapper<VideoStream>,
    private val audioStreamsProvider: DownloadAudioStreamsProvider,
    private val subtitleStreams: StreamInfoWrapper<SubtitlesStream>,
    private val loadedListener: DownloadStreamSizeLoadedListener,
    private val errorListener: DownloadStreamSizeErrorListener,
    private val fetcher: DownloadStreamInfoFetcher = DownloadStreamInfoFetcher(::fetchStreamInfo)
) {
    private val disposables = CompositeDisposable()

    fun refresh() {
        disposables.clear()
        load(DownloadMediaOption.VIDEO, videoStreams)
        load(DownloadMediaOption.AUDIO, audioStreamsProvider.get())
        load(DownloadMediaOption.SUBTITLE, subtitleStreams)
    }

    fun clear() {
        disposables.clear()
    }

    private fun <T : Stream> load(
        option: DownloadMediaOption,
        streams: StreamInfoWrapper<T>
    ) {
        disposables.add(
            fetcher.fetch(streams)
                .subscribe(
                    { loadedListener.onLoaded(option) },
                    { errorListener.onError(option, it) }
                )
        )
    }

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun fetchStreamInfo(streams: StreamInfoWrapper<out Stream>): Single<Boolean> {
            return StreamInfoWrapper.fetchMoreInfoForWrapper(
                streams as StreamInfoWrapper<Stream>
            )
        }
    }
}
