package org.schabi.newpipe.player.info

import android.content.Context
import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.util.Locale
import org.schabi.newpipe.R

/** In-memory decoder state; never collects URLs, headers or network traffic. */
class PlaybackInformation : AnalyticsListener {
    private var engine: ExoPlayer? = null
    internal var videoDecoder: String? = null
        private set
    internal var audioDecoder: String? = null
        private set

    fun attach(player: ExoPlayer) {
        detach()
        engine = player
        player.addAnalyticsListener(this)
    }

    fun detach() {
        engine?.removeAnalyticsListener(this)
        engine = null
        videoDecoder = null
        audioDecoder = null
    }

    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        videoDecoder = decoderName
    }

    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        audioDecoder = decoderName
    }

    override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
        if (videoDecoder == decoderName) videoDecoder = null
    }

    override fun onAudioDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
        if (audioDecoder == decoderName) audioDecoder = null
    }

    fun text(context: Context): String {
        val player = engine
        val unavailable = context.getString(R.string.playback_info_unavailable)
        val video = player?.videoFormat
        val audio = player?.audioFormat
        val counters = player?.videoDecoderCounters
        counters?.ensureUpdated()
        fun row(label: Int, value: String?) = context.getString(label) + ": " + (value ?: unavailable)
        return listOf(
            row(R.string.playback_info_resolution, video?.takeIf { it.width > 0 && it.height > 0 }?.let { "${it.width} × ${it.height}" }),
            row(R.string.playback_info_frame_rate, video?.frameRate?.takeIf { it > 0 }?.let { String.format(Locale.getDefault(), "%.2f fps", it) }),
            row(R.string.playback_info_video_codec, codec(video)),
            row(R.string.playback_info_audio_codec, codec(audio)),
            row(R.string.playback_info_video_bitrate, bitrate(video)),
            row(R.string.playback_info_audio_bitrate, bitrate(audio)),
            row(R.string.playback_info_video_decoder, video?.let { videoDecoder }),
            row(R.string.playback_info_audio_decoder, audio?.let { audioDecoder }),
            row(R.string.playback_info_buffer, player?.let { context.getString(R.string.playback_info_seconds, it.totalBufferedDuration / 1000.0) }),
            row(R.string.playback_info_dropped, video?.let { counters?.droppedBufferCount?.toString() })
        ).joinToString("\n")
    }

    internal fun codec(format: Format?): String? = format?.let { it.codecs?.takeIf(String::isNotBlank) ?: it.sampleMimeType }

    internal fun bitrate(format: Format?): String? {
        val rate = format?.averageBitrate?.takeIf { it > 0 } ?: return null
        return String.format(Locale.getDefault(), "%.0f kb/s", rate / 1000.0)
    }
}
