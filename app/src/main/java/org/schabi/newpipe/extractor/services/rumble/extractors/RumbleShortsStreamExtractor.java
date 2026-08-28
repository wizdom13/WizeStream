package org.schabi.newpipe.extractor.services.rumble.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import static org.schabi.newpipe.extractor.stream.Stream.ID_UNKNOWN;

public class RumbleShortsStreamExtractor extends StreamExtractor {

    private JsonArray relatedItems;
    private JsonObject currentVideo;
    private JsonArray videos;
    private JsonObject uploader;
    private String videoId;

    RumbleShortsStreamExtractor(StreamingService service, LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader)
            throws IOException, ExtractionException {

        final String html = downloader.get(getUrl()).responseBody();
        final Document doc = Jsoup.parse(html);

        Element jsonScript = doc.selectFirst("rum-shorts script[type=application/json]");
        if (jsonScript == null) {
            throw new ExtractionException("Rumble shorts JSON not found");
        }

        try {
            JsonObject root = JsonParser.object().from(jsonScript.data());
            relatedItems = root.getArray("items");
            videoId = getId();

            for (Object o : relatedItems) {
                JsonObject item = (JsonObject) o;

                if ("video".equals(item.getString("object_type"))
                        && videoId.equals(item.getString("permalink_id"))) {
                    currentVideo = item;
                    videos = item.getArray("videos");
                    uploader = item.getObject("by");
                    break;
                }
            }

            if (currentVideo == null) {
                throw new ExtractionException("Short video not found in JSON");
            }
        } catch (final JsonParserException e) {
            e.printStackTrace();
            throw new ParsingException("Could not read json from: " + getUrl());
        }
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return currentVideo.getString("title");
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        String desc = currentVideo.getString("processed_description", "");
        return new Description(desc, Description.PLAIN_TEXT);
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        assertPageFetched();
        final String thumbUrl = currentVideo.getString("thumb");
        return List.of(new Image(thumbUrl,
                Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        return uploader.getString("name");
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        return uploader.getString("url");
    }

    @Override
    public long getLength() throws ParsingException {
        return currentVideo.getLong("duration");
    }

    @Override
    public long getViewCount() throws ParsingException {
        return currentVideo.getLong("views");
    }

    @Override
    public List<VideoStream> getVideoStreams() throws ExtractionException {

        List<VideoStream> streamList = new ArrayList<>();

        for (Object obj : videos) {
            JsonObject video = (JsonObject) obj;

            String url = video.getString("url");
            int bitrateKbps = video.getInt("bitrate_kbps");
            String resolution = String.format(Locale.ROOT, "%dp@%dk", video.getInt("res"),
                    bitrateKbps
            );

            VideoStream videoStream = new VideoStream.Builder()
                    .setId(ID_UNKNOWN)
                    .setIsVideoOnly(false)
                    .setResolution(resolution)
                    .setContent(url, true)
                    .setBitrate(bitrateKbps * 1000)
                    .setMediaFormat(MediaFormat.MPEG_4)
                    .build();

            streamList.add(videoStream);
        }

        return streamList;
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws IOException, ExtractionException {
        return Collections.emptyList();
    }

    @Override
    public List<AudioStream> getAudioStreams() {
        return Collections.emptyList();
    }

    @Override
    public StreamInfoItemsCollector getRelatedItems() throws ExtractionException {

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());

        for (Object obj : relatedItems) {
            JsonObject relatedItem = (JsonObject) obj;

            if ("video".equals(relatedItem.getString("object_type"))
                    // videoId stream is the main stream and no related stream
                    && !videoId.equals(relatedItem.getString("permalink_id"))) {
                collector.commit(new RumbleShortInfoItemExtractor(relatedItem));
            }
        }

        return collector;
    }

}
