/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.preference.PreferenceManager
import java.util.Locale
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DownloadDialogBinding
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.AudioTrackAdapter
import org.schabi.newpipe.util.AudioTrackAdapter.AudioTracksWrapper
import org.schabi.newpipe.util.StreamItemAdapter
import us.shandian.giga.postprocessing.Mp3OutputOptions

internal enum class DownloadMediaOption(val sizeRequestDescription: String) {
    VIDEO("Downloading video stream size"),
    AUDIO("Downloading audio stream size"),
    SUBTITLE("Downloading subtitle stream size")
}

internal object DownloadMediaOptionPolicy {
    fun select(
        defaultMedia: String?,
        videoKey: String,
        audioKey: String,
        subtitleKey: String,
        videoAvailable: Boolean,
        audioAvailable: Boolean,
        subtitleAvailable: Boolean
    ): DownloadMediaOption? {
        return when {
            videoAvailable && defaultMedia == videoKey -> DownloadMediaOption.VIDEO
            audioAvailable && defaultMedia == audioKey -> DownloadMediaOption.AUDIO
            subtitleAvailable && defaultMedia == subtitleKey -> DownloadMediaOption.SUBTITLE
            videoAvailable -> DownloadMediaOption.VIDEO
            audioAvailable -> DownloadMediaOption.AUDIO
            subtitleAvailable -> DownloadMediaOption.SUBTITLE
            else -> null
        }
    }
}

internal object DownloadAudioOutputPolicy {
    private val mp3Bitrates = intArrayOf(128, 192, 256, 320)
    private const val MP3_OUTPUT_INDEX = 1

    @JvmStatic
    fun isMp3Output(selectedOutputIndex: Int): Boolean {
        return selectedOutputIndex == MP3_OUTPUT_INDEX
    }

    @JvmStatic
    fun bitrateForIndex(selectedBitrateIndex: Int): Int {
        return mp3Bitrates.getOrNull(selectedBitrateIndex)
            ?: Mp3OutputOptions.DEFAULT_BITRATE_KBPS
    }

    fun shouldShowBitrate(
        selectedOutputIndex: Int,
        selectedAudioIndex: Int,
        audioStreamCount: Int,
        sourceFormat: MediaFormat?
    ): Boolean {
        return isMp3Output(selectedOutputIndex) &&
            selectedAudioIndex in 0 until audioStreamCount &&
            sourceFormat != MediaFormat.MP3
    }
}

internal object DownloadSubtitleSelectionPolicy {
    @JvmStatic
    fun preferredIndex(streams: List<SubtitlesStream>): Int {
        val localization = NewPipe.getPreferredLocalization()
        return preferredIndex(streams, localization.languageCode, localization.countryCode)
    }

    internal fun preferredIndex(
        streams: List<SubtitlesStream>,
        preferredLanguageCode: String?,
        preferredCountryCode: String?
    ): Int {
        val preferredLanguage = preferredLanguageCode?.let { Locale(it).language }
        var candidate = 0
        streams.forEachIndexed { index, stream ->
            val streamLocale = stream.locale
            if (preferredLanguage != null && streamLocale.language == preferredLanguage) {
                if (streamLocale.country == preferredCountryCode) {
                    return index
                }
                candidate = index
            }
        }
        return candidate
    }
}

/** Owns download option visibility and spinner presentation outside the Fragment. */
internal class DownloadOptionsController(
    private val context: Context,
    private val binding: DownloadDialogBinding,
    private val audioTrackAdapter: AudioTrackAdapter,
    private val audioStreamsAdapter: StreamItemAdapter<AudioStream, Stream>,
    private val videoStreamsAdapter: StreamItemAdapter<VideoStream, AudioStream>,
    private val subtitleStreamsAdapter: StreamItemAdapter<SubtitlesStream, Stream>,
    private val audioTracks: AudioTracksWrapper,
    private val hasMuxedAudioFallback: Boolean
) {
    fun setupOutputSpinners(selectedOutputIndex: Int, selectedBitrateIndex: Int) {
        val outputFormatAdapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(
            context,
            R.array.audio_output_format_entries,
            android.R.layout.simple_spinner_item
        )
        outputFormatAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
        binding.audioOutputFormatSpinner.adapter = outputFormatAdapter
        binding.audioOutputFormatSpinner.setSelection(selectedOutputIndex)

        val bitrateAdapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(
            context,
            R.array.mp3_bitrate_entries,
            android.R.layout.simple_spinner_item
        )
        bitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.mp3BitrateSpinner.adapter = bitrateAdapter
        binding.mp3BitrateSpinner.setSelection(selectedBitrateIndex)
    }

    fun setupInitial(
        selectedAudioTrackIndex: Int,
        selectedAudioIndex: Int,
        selectedVideoIndex: Int,
        selectedSubtitleIndex: Int,
        selectedAudioOutputIndex: Int
    ): Boolean {
        setRadioButtonsEnabled(false)
        binding.audioTrackSpinner.adapter = audioTrackAdapter
        binding.audioTrackSpinner.setSelection(selectedAudioTrackIndex)

        val videoAvailable = videoStreamsAdapter.count > 0
        val audioAvailable = audioStreamsAdapter.count > 0
        val subtitleAvailable = subtitleStreamsAdapter.count > 0
        binding.videoButton.visibility = visibility(videoAvailable)
        binding.audioButton.visibility = visibility(audioAvailable)
        binding.subtitleButton.visibility = visibility(subtitleAvailable)

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val videoKey = context.getString(R.string.last_download_type_video_key)
        val audioKey = context.getString(R.string.last_download_type_audio_key)
        val subtitleKey = context.getString(R.string.last_download_type_subtitle_key)
        val selectedOption = DownloadMediaOptionPolicy.select(
            defaultMedia = preferences.getString(
                context.getString(R.string.last_used_download_type),
                videoKey
            ),
            videoKey = videoKey,
            audioKey = audioKey,
            subtitleKey = subtitleKey,
            videoAvailable = videoAvailable,
            audioAvailable = audioAvailable,
            subtitleAvailable = subtitleAvailable
        )

        if (selectedOption == null) {
            Toast.makeText(context, R.string.no_streams_available_download, Toast.LENGTH_SHORT)
                .show()
            return false
        }

        when (selectedOption) {
            DownloadMediaOption.VIDEO -> {
                binding.videoButton.isChecked = true
                showVideo(selectedVideoIndex)
            }

            DownloadMediaOption.AUDIO -> {
                binding.audioButton.isChecked = true
                showAudio(selectedAudioIndex, selectedAudioOutputIndex)
            }

            DownloadMediaOption.SUBTITLE -> {
                binding.subtitleButton.isChecked = true
                showSubtitle(selectedSubtitleIndex)
            }
        }
        return true
    }

    fun showAudio(selectedAudioIndex: Int, selectedAudioOutputIndex: Int) {
        binding.qualitySpinner.visibility = View.GONE
        setRadioButtonsEnabled(true)
        binding.audioStreamSpinner.adapter = audioStreamsAdapter
        binding.audioStreamSpinner.setSelection(selectedAudioIndex)
        binding.audioStreamSpinner.visibility = View.VISIBLE
        binding.audioTrackSpinner.visibility = visibility(audioTracks.size() > 1)
        binding.audioTrackPresentInVideoText.setText(R.string.audio_extracted_from_video_notice)
        binding.audioTrackPresentInVideoText.visibility = visibility(hasMuxedAudioFallback)
        binding.audioOutputFormatLabel.visibility = View.VISIBLE
        binding.audioOutputFormatSpinner.visibility = View.VISIBLE
        updateMp3BitrateVisibility(selectedAudioOutputIndex, selectedAudioIndex)
    }

    fun showVideo(selectedVideoIndex: Int) {
        binding.qualitySpinner.adapter = videoStreamsAdapter
        binding.qualitySpinner.setSelection(selectedVideoIndex)
        binding.qualitySpinner.visibility = View.VISIBLE
        setRadioButtonsEnabled(true)
        binding.audioStreamSpinner.visibility = View.GONE
        hideAudioOutputOptions()
        updateVideoAudioTrackVisibility(selectedVideoIndex)
    }

    fun showSubtitle(selectedSubtitleIndex: Int) {
        binding.qualitySpinner.adapter = subtitleStreamsAdapter
        binding.qualitySpinner.setSelection(selectedSubtitleIndex)
        binding.qualitySpinner.visibility = View.VISIBLE
        setRadioButtonsEnabled(true)
        binding.audioStreamSpinner.visibility = View.GONE
        hideAudioOutputOptions()
        binding.audioTrackSpinner.visibility = View.GONE
        binding.audioTrackPresentInVideoText.visibility = View.GONE
    }

    fun updateVideoAudioTrackVisibility(selectedVideoIndex: Int) {
        val isVideoOnly = videoStreamsAdapter.getItem(selectedVideoIndex).isVideoOnly
        binding.audioTrackPresentInVideoText.setText(R.string.audio_track_present_in_video)
        binding.audioTrackSpinner.visibility = visibility(isVideoOnly && audioTracks.size() > 1)
        binding.audioTrackPresentInVideoText.visibility =
            visibility(!isVideoOnly && audioTracks.size() > 1)
    }

    fun updateMp3BitrateVisibility(selectedOutputIndex: Int, selectedAudioIndex: Int) {
        val selectedFormat = if (selectedAudioIndex in 0 until audioStreamsAdapter.count) {
            audioStreamsAdapter.getItem(selectedAudioIndex).format
        } else {
            null
        }
        val visible = DownloadAudioOutputPolicy.shouldShowBitrate(
            selectedOutputIndex,
            selectedAudioIndex,
            audioStreamsAdapter.count,
            selectedFormat
        )
        binding.mp3BitrateLabel.visibility = visibility(visible)
        binding.mp3BitrateSpinner.visibility = visibility(visible)
    }

    fun refreshLoadedOption(
        option: DownloadMediaOption,
        selectedVideoIndex: Int,
        selectedAudioIndex: Int,
        selectedSubtitleIndex: Int,
        selectedAudioOutputIndex: Int
    ) {
        when (option) {
            DownloadMediaOption.VIDEO -> if (binding.videoButton.isChecked) {
                showVideo(selectedVideoIndex)
            }

            DownloadMediaOption.AUDIO -> if (binding.audioButton.isChecked) {
                showAudio(selectedAudioIndex, selectedAudioOutputIndex)
            }

            DownloadMediaOption.SUBTITLE -> if (binding.subtitleButton.isChecked) {
                showSubtitle(selectedSubtitleIndex)
            }
        }
    }

    private fun setRadioButtonsEnabled(enabled: Boolean) {
        binding.audioButton.isEnabled = enabled
        binding.videoButton.isEnabled = enabled
        binding.subtitleButton.isEnabled = enabled
    }

    private fun hideAudioOutputOptions() {
        binding.audioOutputFormatLabel.visibility = View.GONE
        binding.audioOutputFormatSpinner.visibility = View.GONE
        binding.mp3BitrateLabel.visibility = View.GONE
        binding.mp3BitrateSpinner.visibility = View.GONE
    }

    private fun visibility(visible: Boolean): Int = if (visible) View.VISIBLE else View.GONE
}
