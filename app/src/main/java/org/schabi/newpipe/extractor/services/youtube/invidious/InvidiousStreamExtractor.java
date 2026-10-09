package org.schabi.newpipe.extractor.services.youtube.invidious;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InvidiousStreamExtractor extends StreamExtractor {
    private final InvidiousApi api = new InvidiousApi();
    private JsonObject video;

    public InvidiousStreamExtractor(final StreamingService service, final LinkHandler handler) {
        super(service, handler);
    }

    @Override
    public void onFetchPage(final Downloader downloader) throws IOException, ExtractionException {
        video = api.object(api.url("videos/" + InvidiousApi.encode(getId()) + "?local=true"));
        final JsonArray formats = video.getArray("formatStreams");
        final JsonArray adaptive = video.getArray("adaptiveFormats");
        if ((formats == null || formats.isEmpty())
                && (adaptive == null || adaptive.isEmpty())
                && video.getString("hlsUrl", "").isEmpty()
                && video.getString("dashUrl", "").isEmpty()) {
            throw new ExtractionException("Invidious returned video metadata without any media"
                    + " formats or HLS/DASH manifests. Try another saved Invidious instance.");
        }
    }

    @Override
    public String getName() {
        return video.getString("title", "");
    }
    @Override
    public String getUploaderName() {
        return video.getString("author", "");
    }
    @Override
    public String getUploaderUrl() {
        return InvidiousApi.channelUrl(video);
    }
    @Override
    public boolean isUploaderVerified() {
        return video.getBoolean("authorVerified");
    }
    @Override
    public List<Image> getUploaderAvatars() {
        return api.images(video.getArray("authorThumbnails"));
    }
    @Override
    public String getUploaderAvatarUrl() {
        return api.firstImage(video.getArray("authorThumbnails"));
    }
    @Override
    public List<Image> getThumbnails() {
        return api.images(video.getArray("videoThumbnails"));
    }
    @Override
    public String getThumbnailUrl() {
        return api.firstImage(video.getArray("videoThumbnails"));
    }
    @Override
    public Description getDescription() {
        // Plain text prevents inline images in description HTML from bypassing backend routing.
        return new Description(video.getString("description", ""), Description.PLAIN_TEXT);
    }
    @Override
    public long getLength() {
        return video.getLong("lengthSeconds", 0);
    }
    @Override
    public long getViewCount() {
        return video.getLong("viewCount", -1);
    }
    @Override
    public long getLikeCount() {
        return video.getLong("likeCount", -1);
    }
    @Override
    public DateWrapper getUploadDate() {
        return InvidiousApi.date(video);
    }
    @Override
    public String getTextualUploadDate() {
        return video.getString("publishedText", "");
    }
    @Override
    public int getAgeLimit() {
        return video.getBoolean("isFamilyFriendly", true) ? 0 : 18;
    }
    @Override
    public StreamType getStreamType() {
        return video.getBoolean("liveNow") ? StreamType.LIVE_STREAM : StreamType.VIDEO_STREAM;
    }

    @Override
    public String getHlsUrl() throws ParsingException {
        return manifestUrl("hlsUrl");
    }

    @Override
    public String getDashMpdUrl() throws ParsingException {
        return manifestUrl("dashUrl");
    }

    private String manifestUrl(final String key) throws ParsingException {
        final String value = video.getString(key, "");
        if (value.isEmpty()) {
            return "";
        }
        try {
            final String local = api.localUrl(value);
            return local + (local.contains("?") ? "&" : "?") + "local=true";
        } catch (final ExtractionException e) {
            throw new ParsingException(e.getMessage(), e);
        }
    }

    @Override
    public List<VideoStream> getVideoStreams() throws ExtractionException {
        return videos(video.getArray("formatStreams"), false);
    }
    @Override
    public List<VideoStream> getVideoOnlyStreams() throws ExtractionException {
        return videos(video.getArray("adaptiveFormats"), true);
    }

    private List<VideoStream> videos(final JsonArray formats, final boolean videoOnly)
            throws ExtractionException {
        final List<VideoStream> result = new ArrayList<>();
        if (getStreamType() == StreamType.LIVE_STREAM || formats == null) {
            return result;
        }
        for (final Object item : formats) {
            final JsonObject format = (JsonObject) item;
            final String type = format.getString("type", "");
            if (!type.startsWith("video/")) {
                continue;
            }
            result.add(new VideoStream.Builder()
                    .setId(format.getString("itag", "invidious"))
                    .setContent(api.localUrl(format.getString("url", "")), true)
                    .setMediaFormat(type.startsWith("video/webm") ? MediaFormat.WEBM
                            : MediaFormat.MPEG_4)
                    .setResolution(format.getString("qualityLabel", format.getString("resolution",
                            "unknown")))
                    .setIsVideoOnly(videoOnly)
                    .build());
        }
        return result;
    }

    @Override
    public List<AudioStream> getAudioStreams() throws ExtractionException {
        final List<AudioStream> result = new ArrayList<>();
        final JsonArray formats = video.getArray("adaptiveFormats");
        if (getStreamType() == StreamType.LIVE_STREAM || formats == null) {
            return result;
        }
        for (final Object item : formats) {
            final JsonObject format = (JsonObject) item;
            final String type = format.getString("type", "");
            if (!type.startsWith("audio/")) {
                continue;
            }
            int bitrate = AudioStream.UNKNOWN_BITRATE;
            try {
                bitrate = Integer.parseInt(format.getString("bitrate", "0")) / 1000;
            } catch (final NumberFormatException ignored) {
                // Some instances omit the bitrate.
            }
            result.add(new AudioStream.Builder()
                    .setId(format.getString("itag", "invidious"))
                    .setContent(api.localUrl(format.getString("url", "")), true)
                    .setMediaFormat(type.startsWith("audio/webm")
                            ? (type.contains("opus") ? MediaFormat.WEBMA_OPUS : MediaFormat.WEBMA)
                            : MediaFormat.M4A)
                    .setAverageBitrate(bitrate)
                    .build());
        }
        return result;
    }

    @Override
    public List<SubtitlesStream> getSubtitlesDefault() throws ExtractionException {
        return getSubtitles(MediaFormat.VTT);
    }

    @Override
    public List<SubtitlesStream> getSubtitles(final MediaFormat format) throws ExtractionException {
        if (format != MediaFormat.VTT) {
            return Collections.emptyList();
        }
        final List<SubtitlesStream> result = new ArrayList<>();
        final JsonArray captions = video.getArray("captions");
        if (captions != null) {
            for (final Object item : captions) {
                final JsonObject caption = (JsonObject) item;
                final String label = caption.getString("label", "");
                result.add(new SubtitlesStream.Builder()
                        .setId(label)
                        .setContent(api.url("captions/" + InvidiousApi.encode(getId())
                                + "?label=" + InvidiousApi.encode(label)), true)
                        .setMediaFormat(MediaFormat.VTT)
                        .setLanguageCode(caption.getString("language_code", "en"))
                        .setAutoGenerated(label.contains("auto-generated"))
                        .build());
            }
        }
        return result;
    }

    @Override
    public StreamInfoItemsCollector getRelatedItems() {
        return InvidiousItems.videos(video.getArray("recommendedVideos"), getServiceId(), api);
    }
}
