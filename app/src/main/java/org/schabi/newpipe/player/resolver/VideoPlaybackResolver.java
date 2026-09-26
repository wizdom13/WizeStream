package org.schabi.newpipe.player.resolver;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeCaptionTranslationHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediaitem.StreamInfoTag;
import org.schabi.newpipe.settings.CaptionTranslationPreferences;
import org.schabi.newpipe.util.ListHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.schabi.newpipe.util.ListHelper.getFilteredAudioStreams;
import static org.schabi.newpipe.util.ListHelper.getPlayableStreams;

public class VideoPlaybackResolver implements PlaybackResolver {
    private static final String TAG = VideoPlaybackResolver.class.getSimpleName();

    @NonNull
    private final Context context;
    @NonNull
    private final PlayerDataSource dataSource;
    @NonNull
    private final QualityResolver qualityResolver;
    private SourceType streamSourceType;

    @Nullable
    private String playbackQuality;
    private boolean autoQualitySelected;
    private boolean adaptiveQualityActive;
    @Nullable
    private String audioTrack;
    @Nullable
    private RejectedVideoStream rejectedVideoStream;
    @Nullable
    private RejectedVideoCodecFamily rejectedVideoCodecFamily;

    public enum SourceType {
        LIVE_STREAM,
        VIDEO_WITH_SEPARATED_AUDIO,
        VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
    }

    public VideoPlaybackResolver(@NonNull final Context context,
                                 @NonNull final PlayerDataSource dataSource,
                                 @NonNull final QualityResolver qualityResolver) {
        this.context = context;
        this.dataSource = dataSource;
        this.qualityResolver = qualityResolver;
    }

    @Override
    @Nullable
    public MediaSource resolve(@NonNull final StreamInfo info) {
        return resolve(info, playbackQuality);
    }

    /**
     * Resolves a stream with a quality override that applies only to this media source.
     *
     * @param info stream to resolve
     * @param qualityOverride preferred resolution, or {@code null} to use the default resolution
     * @return the resolved media source
     */
    @Nullable
    public MediaSource resolve(@NonNull final StreamInfo info,
                               @Nullable final String qualityOverride) {
        final boolean autoRequested = qualityOverride == null
                ? qualityResolver.isDefaultAutoQuality()
                : qualityResolver.isAutoQuality(qualityOverride);
        autoQualitySelected = autoRequested;
        adaptiveQualityActive = false;

        final MediaSource liveSource = PlaybackResolver.maybeBuildLiveMediaSource(dataSource, info);
        if (liveSource != null) {
            streamSourceType = SourceType.LIVE_STREAM;
            adaptiveQualityActive = autoRequested;
            return liveSource;
        }

        final List<MediaSource> mediaSources = new ArrayList<>();

        final Integer rejectedItag = consumeRejectedItag(info.getUrl());
        final String rejectedCodecFamily = consumeRejectedCodecFamily(info.getUrl());
        final List<VideoStream> playableVideoStreams = withoutRejectedCodecFamily(
                withoutRejectedItag(
                        getPlayableStreams(info.getVideoStreams(), info.getServiceId()),
                        rejectedItag),
                rejectedCodecFamily);
        final List<VideoStream> playableVideoOnlyStreams = withoutRejectedCodecFamily(
                withoutRejectedItag(
                        getPlayableStreams(info.getVideoOnlyStreams(), info.getServiceId()),
                        rejectedItag),
                rejectedCodecFamily);
        final List<VideoStream> videoStreamsList = ListHelper.getSortedStreamVideosList(context,
                playableVideoStreams, playableVideoOnlyStreams, false, true);
        if (rejectedItag != null) {
            Log.w(TAG, "Falling back from rejected YouTube video itag " + rejectedItag);
        }
        if (rejectedCodecFamily != null) {
            Log.w(TAG, "Falling back from rejected video codec family "
                    + rejectedCodecFamily);
        }

        final List<AudioStream> audioStreamsList =
                getFilteredAudioStreams(context, info.getAudioStreams());
        final int audioIndex =
                ListHelper.getAudioFormatIndex(context, audioStreamsList, audioTrack);

        final List<VideoStream> adaptiveCandidates = autoRequested
                && info.getServiceId() == ServiceList.YouTube.getServiceId()
                ? AdaptiveVideoQuality.youtubeCandidates(context, videoStreamsList)
                : List.of();
        final boolean useAdaptiveSource = adaptiveCandidates.size() >= 2;

        int videoIndex = -1;
        if (!videoStreamsList.isEmpty() && !useAdaptiveSource) {
            if (autoRequested) {
                if (adaptiveCandidates.size() == 1) {
                    videoIndex = videoStreamsList.indexOf(adaptiveCandidates.get(0));
                }
                if (videoIndex < 0) {
                    videoIndex = qualityResolver.getAutoFallbackResolutionIndex(videoStreamsList);
                }
            } else if (qualityOverride == null) {
                videoIndex = qualityResolver.getDefaultResolutionIndex(videoStreamsList);
            } else {
                videoIndex = qualityResolver.getOverrideResolutionIndex(
                        videoStreamsList, qualityOverride);
            }
        }

        MediaItemTag tag = useAdaptiveSource
                ? StreamInfoTag.adaptive(info, videoStreamsList, audioStreamsList, audioIndex)
                : StreamInfoTag.of(
                        info, videoStreamsList, videoIndex, audioStreamsList, audioIndex);
        @Nullable VideoStream video = useAdaptiveSource
                ? adaptiveCandidates.get(0)
                : tag.getMaybeQuality()
                        .map(MediaItemTag.Quality::getSelectedVideoStream)
                        .orElse(null);
        final AudioStream audio = tag.getMaybeAudioTrack()
                .map(MediaItemTag.AudioTrack::getSelectedAudioStream)
                .orElse(null);

        if (useAdaptiveSource) {
            try {
                mediaSources.add(PlaybackResolver.buildYoutubeAdaptiveVideoMediaSource(
                        dataSource, adaptiveCandidates, info, tag));
                adaptiveQualityActive = true;
            } catch (final ResolverException e) {
                Log.w(TAG, "Unable to create adaptive video source; using fixed fallback", e);
                adaptiveQualityActive = false;
                videoIndex = qualityResolver.getAutoFallbackResolutionIndex(videoStreamsList);
                tag = StreamInfoTag.of(
                        info, videoStreamsList, videoIndex, audioStreamsList, audioIndex);
                video = tag.getMaybeQuality()
                        .map(MediaItemTag.Quality::getSelectedVideoStream)
                        .orElse(null);
            }
        }

        if (!adaptiveQualityActive && video != null) {
            try {
                final MediaSource streamSource = PlaybackResolver.buildMediaSource(
                        dataSource, video, info, PlaybackResolver.cacheKeyOf(info, video), tag);
                mediaSources.add(streamSource);
            } catch (final ResolverException e) {
                Log.e(TAG, "Unable to create video source", e);
                return null;
            }
        }

        if (audio != null && (video == null || video.isVideoOnly() || audioTrack != null)) {
            try {
                final MediaSource audioSource = PlaybackResolver.buildMediaSource(
                        dataSource, audio, info, PlaybackResolver.cacheKeyOf(info, audio), tag);
                mediaSources.add(audioSource);
                streamSourceType = SourceType.VIDEO_WITH_SEPARATED_AUDIO;
            } catch (final ResolverException e) {
                Log.e(TAG, "Unable to create audio source", e);
                return null;
            }
        } else {
            streamSourceType = SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY;
        }

        if (mediaSources.isEmpty()) {
            return null;
        }

        // Create subtitle sources. StreamInfo is cached, but the translation preference can change
        // afterwards, so synthesize the requested YouTube translation at resolution time too.
        final List<SubtitlesStream> subtitlesStreams = info.getSubtitles() == null
                ? null : new ArrayList<>(info.getSubtitles());
        if (subtitlesStreams != null
                && info.getServiceId() == ServiceList.YouTube.getServiceId()) {
            YoutubeCaptionTranslationHelper.addTranslatedSubtitleFromExtractedStreams(
                    subtitlesStreams,
                    CaptionTranslationPreferences.getTargetLanguage(context));
        }
        if (subtitlesStreams != null) {
            for (final SubtitlesStream subtitle : subtitlesStreams) {
                final MediaSource textSource = SubtitlePlaybackSource.create(
                        subtitle, PlayerHelper.captionLanguageOf(context, subtitle),
                        dataSource.getSingleSampleMediaSourceFactory());
                if (textSource != null) {
                    mediaSources.add(textSource);
                }
            }
        }

        return mediaSources.size() == 1
                ? mediaSources.get(0)
                : new MergingMediaSource(true, mediaSources.toArray(new MediaSource[0]));
    }

    /**
     * Returns the last resolved {@link StreamInfo}'s {@link SourceType source type}.
     *
     * @return {@link Optional#empty()} if nothing was resolved, otherwise the {@link SourceType}
     * of the last resolved {@link StreamInfo} inside an {@link Optional}
     */
    public Optional<SourceType> getStreamSourceType() {
        return Optional.ofNullable(streamSourceType);
    }

    @Nullable
    public String getPlaybackQuality() {
        return playbackQuality;
    }

    public void setPlaybackQuality(@Nullable final String playbackQuality) {
        this.playbackQuality = playbackQuality;
    }

    public boolean isAutoQualitySelected() {
        return autoQualitySelected;
    }

    public boolean isAdaptiveQualityActive() {
        return adaptiveQualityActive;
    }

    @Nullable
    public String getAudioTrack() {
        return audioTrack;
    }

    public void setAudioTrack(@Nullable final String audioLanguage) {
        this.audioTrack = audioLanguage;
    }

    public synchronized void rejectVideoStreamOnce(@NonNull final String streamUrl,
                                                    final int itag) {
        rejectedVideoStream = new RejectedVideoStream(streamUrl, itag);
    }

    public synchronized void rejectVideoCodecFamilyOnce(
            @NonNull final String streamUrl,
            @Nullable final String codec) {
        final String codecFamily = AdaptiveVideoQuality.codecFamily(codec);
        if (!codecFamily.isEmpty()) {
            rejectedVideoCodecFamily = new RejectedVideoCodecFamily(streamUrl, codecFamily);
        }
    }

    @Nullable
    private synchronized Integer consumeRejectedItag(@NonNull final String streamUrl) {
        if (rejectedVideoStream == null || !rejectedVideoStream.streamUrl.equals(streamUrl)) {
            return null;
        }

        final int itag = rejectedVideoStream.itag;
        rejectedVideoStream = null;
        return itag;
    }

    @Nullable
    private synchronized String consumeRejectedCodecFamily(@NonNull final String streamUrl) {
        if (rejectedVideoCodecFamily == null
                || !rejectedVideoCodecFamily.streamUrl.equals(streamUrl)) {
            return null;
        }

        final String codecFamily = rejectedVideoCodecFamily.codecFamily;
        rejectedVideoCodecFamily = null;
        return codecFamily;
    }

    @NonNull
    private static List<VideoStream> withoutRejectedItag(
            @NonNull final List<VideoStream> streams,
            @Nullable final Integer rejectedItag) {
        if (rejectedItag == null) {
            return streams;
        }
        return streams.stream()
                .filter(stream -> stream.getItag() != rejectedItag)
                .collect(Collectors.toList());
    }

    @NonNull
    private static List<VideoStream> withoutRejectedCodecFamily(
            @NonNull final List<VideoStream> streams,
            @Nullable final String rejectedCodecFamily) {
        if (rejectedCodecFamily == null || rejectedCodecFamily.isEmpty()) {
            return streams;
        }
        return streams.stream()
                .filter(stream -> !rejectedCodecFamily.equals(
                        AdaptiveVideoQuality.codecFamily(stream.getCodec())))
                .collect(Collectors.toList());
    }

    public static boolean hasAlternativeCodecFamily(
            @NonNull final List<VideoStream> streams,
            @Nullable final String codec) {
        final String rejectedCodecFamily = AdaptiveVideoQuality.codecFamily(codec);
        if (rejectedCodecFamily.isEmpty()) {
            return false;
        }

        return streams.stream()
                .map(VideoStream::getCodec)
                .map(AdaptiveVideoQuality::codecFamily)
                .anyMatch(codecFamily -> !codecFamily.isEmpty()
                        && !rejectedCodecFamily.equals(codecFamily));
    }

    private static final class RejectedVideoStream {
        @NonNull
        private final String streamUrl;
        private final int itag;

        private RejectedVideoStream(@NonNull final String streamUrl, final int itag) {
            this.streamUrl = streamUrl;
            this.itag = itag;
        }
    }

    private static final class RejectedVideoCodecFamily {
        @NonNull
        private final String streamUrl;
        @NonNull
        private final String codecFamily;

        private RejectedVideoCodecFamily(@NonNull final String streamUrl,
                                         @NonNull final String codecFamily) {
            this.streamUrl = streamUrl;
            this.codecFamily = codecFamily;
        }
    }

    public interface QualityResolver {
        int getDefaultResolutionIndex(List<VideoStream> sortedVideos);

        int getOverrideResolutionIndex(List<VideoStream> sortedVideos, String playbackQuality);

        int getAutoFallbackResolutionIndex(List<VideoStream> sortedVideos);

        boolean isDefaultAutoQuality();

        boolean isAutoQuality(String playbackQuality);
    }
}
