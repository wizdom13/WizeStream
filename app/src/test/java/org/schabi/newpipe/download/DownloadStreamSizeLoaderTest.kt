/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.SingleSubject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper

internal class DownloadStreamSizeLoaderTest {
    @Test
    fun `refresh loads every stream group in order`() {
        val video = StreamInfoWrapper<VideoStream>(emptyList(), null)
        val audio = StreamInfoWrapper<AudioStream>(emptyList(), null)
        val subtitle = StreamInfoWrapper<SubtitlesStream>(emptyList(), null)
        val requested = mutableListOf<StreamInfoWrapper<*>>()
        val loaded = mutableListOf<DownloadMediaOption>()

        val loader = DownloadStreamSizeLoader(
            video,
            DownloadAudioStreamsProvider { audio },
            subtitle,
            DownloadStreamSizeLoadedListener { loaded += it },
            DownloadStreamSizeErrorListener { _, _ -> },
            DownloadStreamInfoFetcher {
                requested += it
                Single.just(true)
            }
        )

        loader.refresh()

        assertEquals(listOf(video, audio, subtitle), requested)
        assertEquals(
            listOf(
                DownloadMediaOption.VIDEO,
                DownloadMediaOption.AUDIO,
                DownloadMediaOption.SUBTITLE
            ),
            loaded
        )
    }

    @Test
    fun `refresh obtains the current audio track wrapper`() {
        val firstAudio = StreamInfoWrapper<AudioStream>(emptyList(), null)
        val secondAudio = StreamInfoWrapper<AudioStream>(emptyList(), null)
        var currentAudio = firstAudio
        val requested = mutableListOf<StreamInfoWrapper<*>>()
        val loader = loader(
            audioProvider = DownloadAudioStreamsProvider { currentAudio },
            fetcher = DownloadStreamInfoFetcher {
                requested += it
                Single.just(true)
            }
        )

        loader.refresh()
        currentAudio = secondAudio
        loader.refresh()

        assertSame(firstAudio, requested[1])
        assertSame(secondAudio, requested[4])
    }

    @Test
    fun `clear cancels pending callbacks`() {
        val pending = SingleSubject.create<Boolean>()
        val loaded = mutableListOf<DownloadMediaOption>()
        val loader = loader(
            loaded = loaded,
            fetcher = DownloadStreamInfoFetcher { pending }
        )

        loader.refresh()
        loader.clear()
        pending.onSuccess(true)

        assertTrue(loaded.isEmpty())
    }

    private fun loader(
        audioProvider: DownloadAudioStreamsProvider = DownloadAudioStreamsProvider {
            StreamInfoWrapper(emptyList(), null)
        },
        loaded: MutableList<DownloadMediaOption> = mutableListOf(),
        fetcher: DownloadStreamInfoFetcher
    ): DownloadStreamSizeLoader {
        return DownloadStreamSizeLoader(
            StreamInfoWrapper(emptyList(), null),
            audioProvider,
            StreamInfoWrapper(emptyList(), null),
            DownloadStreamSizeLoadedListener { loaded += it },
            DownloadStreamSizeErrorListener { _, _ -> },
            fetcher
        )
    }
}
