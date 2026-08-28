package org.schabi.newpipe.extractor.services.rumble.extractors;

import com.github.evermindzz.hlsdownloader.common.Fetcher;
import com.github.evermindzz.hlsdownloader.parser.HlsParser;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.brave.misc.BraveParsingHelper;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.rumble.RumbleParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.schabi.newpipe.extractor.stream.Stream.ID_UNKNOWN;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class RumbleStreamExtractor extends StreamExtractor {

    private final String videoUploaderJsonKey = "author";
    private final String videoTitleJsonKey = "title";
    private final String videoCoverImageJsonKey = "i";
    private final String videoDateJsonKey = "pubDate";
    private final String videoDurationJsonKey = "duration";
    private final String bitrateJsonKey = "bitrate";
    private final String resHeightJsonKey = "h";

    private final String videoViewerCountHtmlKey =
            "div.media-engage div.video-counters--item.video-item--views";
    private static final String RELATED_STREAM_HTML_KEY = "ul.mediaList-list";

    private Document doc;
    JsonObject embedJsonStreamInfoObj;

    private int ageLimit = -1;
    private List<VideoStream> videoStreams;
    private List<AudioStream> audioStreams;
    private String hlsUrl = "";

    public static StreamExtractor factory(
            final StreamingService service,
            final LinkHandler linkHandler) {
       if (linkHandler.getOriginalUrl().contains("/shorts/")) {
           return new RumbleShortsStreamExtractor(service, linkHandler);
       } else {
           return new RumbleStreamExtractor(service, linkHandler);
       }
    }

    private RumbleStreamExtractor(final StreamingService service, final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        assertPageFetched();
        final String title =
                Parser.unescapeEntities(embedJsonStreamInfoObj.getString(videoTitleJsonKey), true);

        return title;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {

        final String textualDate = embedJsonStreamInfoObj.getString(videoDateJsonKey);
        return textualDate;
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final String textualUploadDate = getTextualUploadDate();


        if (isNullOrEmpty(textualUploadDate)) {
            return null;
        }
        // the format is: 2021-02-08T19:37:25+00:00  youtube-dl pares it with iso8601b
        return new DateWrapper(BraveParsingHelper.parseDateFrom(textualUploadDate), false);
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        assertPageFetched();
        final String thumbUrl = embedJsonStreamInfoObj.getString(videoCoverImageJsonKey);
        return List.of(new Image(thumbUrl,
                Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        assertPageFetched();
        String description = "";

        final Elements descriptionData = doc.select("p.media-description");
        if (!descriptionData.isEmpty()) {
            final List<Node> nodes = descriptionData.first().childNodes();

            // the node that contains the the description may vary.
            // Some videos do not have a description at all
            for (final Node node : nodes) {
                if (node instanceof TextNode) {
                    if (!((TextNode) node).isBlank()) {
                        description += node.toString();
                    }
                }
            }
        }

        return new Description(Parser.unescapeEntities(description, false), Description.PLAIN_TEXT);
    }

    @Override
    public int getAgeLimit() throws ParsingException {
        if (ageLimit == -1) {
            ageLimit = NO_AGE_LIMIT;
        }

        return ageLimit;
    }

    public static class FetchWithDownloaderImpl implements Fetcher {
        private final Downloader downloader;

        public FetchWithDownloaderImpl(final Downloader downloader) {
            this.downloader = downloader;
        }

        private InputStream stringToInputStream(String input) {
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            return new ByteArrayInputStream(bytes);
        }

        public InputStream fetchContent(URI uri) throws IOException {
            try {
                final Response response = downloader.get(uri.toString());
                return stringToInputStream(response.responseBody());
            } catch (ReCaptchaException e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public long getLength() throws ParsingException {
        assertPageFetched();
        final Number duration = embedJsonStreamInfoObj.getNumber(videoDurationJsonKey);

        return duration.longValue();
    }

    /**
     * @return 0 means no timestamp is found.
     */
    @Override
    public long getTimeStamp() {
        return 0;
    }

    @Override
    public long getViewCount() throws ParsingException {
        assertPageFetched();
        if (getStreamType() == StreamType.LIVE_STREAM) {
            return getLiveViewCount();
        } else {
            final String errorMsg = "Could not extract the view count";
            try {
                final String viewCount =
                        RumbleParsingHelper.extractSafely(false, errorMsg,
                                () -> doc.select("div.media-description-info-views")
                                        .first().text());
                // some or all recorded live streams have no view count
                // eg.: https://rumble.com/v6q1s7c
                if (null == viewCount) {
                    return super.getViewCount();
                }
                return Utils.mixedNumberWordToLong(viewCount.replace(",", ""));
            } catch (final NumberFormatException e) {
                throw new ParsingException(errorMsg, e);
            }
        }
    }

    public long getLikeOrDislikesCount(final String cssQuery) throws ParsingException {
        try {
            final String votes = RumbleParsingHelper.extractSafely(false, "",
                    () -> doc.select(cssQuery)
                            .first().text());
            return Utils.mixedNumberWordToLong(votes);
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public long getLikeCount() throws ParsingException {
        return getLikeOrDislikesCount("span[data-js=\"rumbles_up_votes\"]");
    }

    @Override
    public long getDislikeCount() throws ParsingException {
        return getLikeOrDislikesCount("span[data-js=\"rumbles_down_votes\"]");
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        assertPageFetched();
        return embedJsonStreamInfoObj.getObject(videoUploaderJsonKey).getString("url");
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        assertPageFetched();
        final String uploaderName =
                embedJsonStreamInfoObj.getObject(videoUploaderJsonKey).getString("name");
        return uploaderName;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        // TODO can be done
        return false;
    }

    @Nonnull
    @Override
    public List<Image> getUploaderAvatars() throws ParsingException {
        assertPageFetched();
        final Elements elems = doc.getElementsByClass("media-by--a");
        final String theUserPathToHisAvatar =
                elems.get(0).getElementsByTag("i").first().attributes().get("class");
        try {
            final String thumbnailUrl = RumbleParsingHelper
                    .totalMessMethodToGetUploaderThumbnailUrl(theUserPathToHisAvatar, doc);
            return List.of(new Image(thumbnailUrl,
                    Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
        } catch (final Exception e) {
            throw new ParsingException(
                    "Could not extract the avatar url: " + theUserPathToHisAvatar);
        }
    }

    @Nonnull
    @Override
    public String getSubChannelUrl() {
        return "";
    }

    @Nonnull
    @Override
    public String getSubChannelName() {
        return "";
    }

    @Nonnull
    @Override
    public String getDashMpdUrl() {
        return "";
    }

    @Nonnull
    @Override
    public String getHlsUrl() {
        try {
            this.hlsUrl = embedJsonStreamInfoObj.getObject("ua").getObject("hls")
                    .getObject("auto").getString("url", "");

        } catch (final Exception e) {

        }
        return this.hlsUrl;
    }

    @Override
    public List<AudioStream> getAudioStreams() {
        return audioStreams;
    }

    @Override
    public List<VideoStream> getVideoStreams() throws ExtractionException {
        return videoStreams;
    }

    private void extractStreams(final Downloader downloader) throws ExtractionException {

        final List<AudioStream> audioStreamsList = new ArrayList<>();
        final List<VideoStream> videoStreamsList = new ArrayList<>();
        final String videoAlternativesKey = "ua";
        final String videoMetaKey = "meta";
        final String videoUrlKey = "url";

        String masterPlayListUrl = null;

        final Set<String> formatKeys =
                embedJsonStreamInfoObj.getObject(videoAlternativesKey).keySet();
        // mp4 or webm or whatever format: 20250409 there are mp4, webm, tar, timeline, audio
        // -> tar is a hls m3u8 playlist
        for (final String formatKey : formatKeys) {

            // For some videos there is also a "timeline stream" that is identified
            // by the key 'timeline'. It has only one frame per second and is
            // not useful here --> so we skip it
            if (formatKey.equals("timeline")) {
                continue;
            }

            // todo validate if we want to support this formats or not
            final JsonObject formatObj =
                    embedJsonStreamInfoObj.getObject(videoAlternativesKey).getObject(formatKey);
            final Set<String> resolutionKeys = formatObj.keySet();
            for (final String res : resolutionKeys) { // 240, 360 , 480 ...
                // todo validate if we support this resolution

                final JsonObject metadata =
                        formatObj.getObject(res).getObject(videoMetaKey); // size w h bitrate
                final String videoUrl =
                        formatObj.getObject(res).getString(videoUrlKey); // where the mp4 sits

                // rumble has some videos resolution data incorrect in 'res'
                // --> now we use the apparently correct resolution from the available metadata.
                // --> as the resolution is not sufficient to distinguish the streams, we also
                //     add the bitrate to the resolution specification, e.g: "1080p@2000k"
                final String actualRes;
                final String bitrate;
                if (metadata.has(resHeightJsonKey) && metadata.has(bitrateJsonKey)) {
                    actualRes = String.valueOf(metadata.getInt(resHeightJsonKey));
                    bitrate = "@" + metadata.getInt(bitrateJsonKey) + "k";
                } else {
                    actualRes = res;
                    bitrate = "";
                }

                if ("audio".equals(formatKey)) {
                    audioStreamsList.add(createAudioStream(
                            videoUrl,
                            metadata.getInt(bitrateJsonKey)));
                } else { // video streams
                    if (res.equals("auto")) {
                        if (getStreamType() == StreamType.LIVE_STREAM) {
                            extractStreamsFromMasterHlsPlaylist(downloader, videoUrl, videoStreamsList);
                        } else {
                            // 'auto' provides a master HLS playlist but we only use it in
                            // case there are no other video streams available
                            masterPlayListUrl = videoUrl; // store for possible later use
                            // -> skip it for now
                        }
                        continue;
                    }

                    final int bitrateValue = metadata.has(bitrateJsonKey)
                            ? metadata.getInt(bitrateJsonKey) * 1000 : 0;
                    final VideoStream videoStream = createVideoStream(
                            formatKey,
                            videoUrl,
                            actualRes + (!bitrate.isBlank() ? "p" + bitrate : ""),
                            bitrateValue);
                    videoStreamsList.add(videoStream);
                }
            }
        }

        videoStreams = videoStreamsList;

        if (videoStreams.isEmpty() && masterPlayListUrl != null) {
            // Is only called if there are no "ua" streams (mp4/webm/tar) found.
            // In this case we fall back to parsing the "auto" stream entry.
            // The "auto" entry points to an HLS master playlist, so we need to
            // fetch and parse it here in order to extract the available HLS variant playlists.
            extractStreamsFromMasterHlsPlaylist(downloader, masterPlayListUrl, videoStreamsList);
        }

        // - Some videos have only HLS video streams but audio as http progressive.
        // - BravePipe/NewPipe switches to an audio only stream for background (if available)
        //   -> problem is it starts from the beginning
        //   -> workaround? disable possible audiostreams for now (20250409):
        //      so BravePipe/NewPipe uses the video stream also for background
        // audioStreams = audioStreamsList.isEmpty() ? Collections.emptyList() : audioStreamsList;
        audioStreams = Collections.emptyList();
    }

    /**
     *  Extract available HLS variant playlists as streams from master HLS playlist.
     *
     * @param downloader        the downloader instance
     * @param hlsMasterPlaylist URL of the HLS master playlist
     * @param videoStreamsList  the video stream list to add found streams
     */
    private void extractStreamsFromMasterHlsPlaylist(
            final Downloader downloader,
            final String hlsMasterPlaylist,
            final List<VideoStream> videoStreamsList) {
        try {
            final HlsParser parser = new HlsParser(
                    variants -> {
                        insertVariantsIntoVideoStreams(variants, videoStreamsList);

                        // this will exit the parse function early with a nullpointer
                        // exception. This is what we want and we catch it below.
                        // HLSParser() should allow null to be a valid choice and exit
                        // early - but until that may get fixed we go this route.
                        return null;
                    },
                    new FetchWithDownloaderImpl(downloader),
                    false
            );

            try {
                parser.parse(new URI(hlsMasterPlaylist));
            } catch (final NullPointerException ignored) {
                // expect to throw a nullpointer and we ignore it
            }
        } catch (final Exception e) {
            System.out.println(e);
        }
    }

    private void insertVariantsIntoVideoStreams(
            List<HlsParser.VariantStream> variants,
            List<VideoStream> videoStreamsList) {
        for (final HlsParser.VariantStream stream : variants) {
            // hls only provides bandwidth so the bitrate is lower
            // a simple tests shows that the bitrate is approx. 15% less
            final float bandwidth2bitrateFactor = 0.85f;

            final URI playlistUrl = stream.getUri();
            if (playlistUrl == null) {
                continue;
            }
            final int bitrate = (int)(stream.getBandwidth() * bandwidth2bitrateFactor);
            final String actualRes = getHeight(stream.getResolution());

            final String bitrateString = "@" + bitrate/1000 + "k";
            final VideoStream videoStream = createVideoStream(
                    "hls",
                    playlistUrl.toString(),
                    actualRes + (!bitrateString.isBlank() ? "p" + bitrateString : ""),
                    bitrate
            );
            videoStreamsList.add(videoStream);
        }
    }

    private String getHeight(final String res) {
        String[] parts = res.split("x");

        if (parts.length == 2) {
            return parts[1];
        }
        return null;
    }

    private AudioStream createAudioStream(
            final String videoUrl,
            final int bitrate) {
        // media format should be aac but it's not mentioned in MediaFormat so default to 'null'
        final AudioStream.Builder builder = new AudioStream.Builder()
                .setId(ID_UNKNOWN)
                .setContent(videoUrl, true)
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .setAverageBitrate(bitrate);
        return builder.build();
    }

    private VideoStream createVideoStream(
            final String formatKey,
            final String videoUrl,
            final String resolution,
            final int bitrate) {
        if ("tar".equals(formatKey) || "hls".equals(formatKey)) { // its a m3u8 playlist
            return hlsStream(videoUrl, resolution, MediaFormat.MPEG_4, bitrate);
        } else {
            final MediaFormat format = MediaFormat.getFromSuffix(formatKey);
            return normalStream(videoUrl, resolution, format, bitrate);
        }
    }

    private VideoStream normalStream(
            final String videoUrl,
            final String resolution,
            final MediaFormat format,
            final int bitrate) {
        final VideoStream.Builder builder = new VideoStream.Builder()
                .setId(ID_UNKNOWN)
                .setIsVideoOnly(false)
                .setResolution(resolution)
                .setContent(videoUrl, true)
                .setBitrate(bitrate)
                .setMediaFormat(format);
        return builder.build();
    }
    private VideoStream hlsStream(
            final String videoUrl,
            final String resolution,
            final MediaFormat format,
            final int bitrate) {
        final VideoStream.Builder builder = new VideoStream.Builder()
                .setId(ID_UNKNOWN)
                .setIsVideoOnly(false)
                .setResolution(resolution)
                .setContent(videoUrl, true)
                .setManifestUrl(videoUrl)
                .setDeliveryMethod(DeliveryMethod.HLS)
                .setBitrate(bitrate)
                .setMediaFormat(format);
        return builder.build();
    }

    @Override
    public StreamType getStreamType() {
        final String videoLiveStreamKey = "live";
        // There is actual a "meta" : { "live" : live } json entry next to the hls/auto tag,
        // but that indication is not always a live stream:: shortly after a live stream just
        // has ended this flag is still set but it is no longer a live stream so we have to
        // ignore it. So we rely on the global entry: '1' is also assumed live stream.
        final Number isLive = embedJsonStreamInfoObj.getNumber(videoLiveStreamKey);
        final boolean isLiveStream = (isLive.intValue() == 1 || isLive.intValue() == 2);
        return isLiveStream ? StreamType.LIVE_STREAM : StreamType.VIDEO_STREAM;
    }

    static List<Node> getRelatedItemNodes(final Document document) {
        final Element relatedItems = document.selectFirst(RELATED_STREAM_HTML_KEY);
        if (relatedItems == null) {
            return Collections.emptyList();
        }
        return relatedItems.childNodes();
    }

    @Nullable
    @Override
    public StreamInfoItemsCollector getRelatedItems() throws ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final Node node : getRelatedItemNodes(doc)) {
            // we only want Element(s) as they might bear useful content
            if ((node instanceof Element)
                    && (null != ((Element) node).closest(".mediaList-item"))) {
                collector.commit(new RumbleStreamRelatedInfoItemExtractor(
                        getTimeAgoParser(), (Element) node, doc
                ));
            }
        }

        return collector;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getErrorMessage() {
        return null;
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {


        doc = RumbleParsingHelper.fetchParseValidate(downloader, getUrl());

        final String queryUrl = "https://rumble.com/embedJS/u4/?request=video&ver=2&v=v"
                + RumbleParsingHelper.getEmbedVideoId(getUrl(), () -> doc.toString());

        final Response response2 = downloader.get(
                queryUrl);

        // TODO keep some cookies to be more browser like
        //curl 'https://rumble.com/embedJS/u3/?request=video&ver=2&v=vb294t&ext=%7B%22ad_count%22%3Anull%7D&ad_wt=0'
        // -H 'Referer: https://rumble.com/vdofb7-1-year-old-pulls-pony-behind-electric-car.html'
        // -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)
        // Chrome/80.0.3987.149 Safari/537.36' -H 'Sec-Fetch-Dest: empty' --compressed
        //Document doc = Jsoup.parse(response.responseBody(), getUrl());
        try {
            embedJsonStreamInfoObj = JsonParser.object().from(response2.responseBody());
            extractStreams(downloader);
        } catch (final JsonParserException e) {
            e.printStackTrace();
            throw new ParsingException("Could not read json from: " + queryUrl);
        }
    }

    @Nonnull
    @Override
    public String getHost() {
        return "";
    }

    @Nonnull
    @Override
    public Privacy getPrivacy() {
        return Privacy.PUBLIC;
    }

    @Nonnull
    @Override
    public String getCategory() {
        return "";
    }

    @Nonnull
    @Override
    public String getLicence() {
        return "";
    }

    @Override
    public Locale getLanguageInfo() {
        return null;
    }

    @Nonnull
    @Override
    public List<String> getTags() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public String getSupportInfo() {
        return "";
    }

    @Nonnull
    @Override
    public List<StreamSegment> getStreamSegments() throws ParsingException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return Collections.emptyList();
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<SubtitlesStream> getSubtitlesDefault() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<SubtitlesStream> getSubtitles(final MediaFormat format) {
        return Collections.emptyList();
    }

    private long getLiveViewCount() throws ParsingException {
        final Pattern matchChecksum = Pattern.compile("viewer_id: \"(.*)\"");
        final Matcher matcher = matchChecksum.matcher(doc.toString());
        if (!matcher.find()) {
            throw new ParsingException("Could not extract viewer_id");
        }
        final String viewerId = matcher.group(1);

        return retrieveLiveStreamViewerCount(getDownloader(),
                retrieveNumericVideoId(),
                viewerId);
    }

    private long retrieveLiveStreamViewerCount(final Downloader downloader,
                                               final String videoNumericId,
                                               final String theViewerId) {
        try {

            // This is the post version of below get version. It does not work but kept here
            // for maybe later:)
            // final Response response = downloader
            //         .post("https://wn0.rumble.com/service.php?api=7&name=video.watching-now",
            //                 null,
            //                 ("video_id=" + videoId + "&viewer_id=" + theViewerId).getBytes());
            final Response response = downloader
                    .get("https://wn0.rumble.com/service.php?video_id="
                            + videoNumericId
                            + "&viewer_id="
                            + theViewerId
                            + "&name=video.watching-now");
            final JsonObject jsonObject =
                    JsonParser.object().from(response.responseBody());
            return jsonObject.getObject("data").getLong("viewer_count", -1);
        } catch (final IOException | ReCaptchaException | JsonParserException e) {
            e.printStackTrace();
        }

        return -1;
    }

    // as of somewhere in 2023 it is no longer working. Kept for reference
    private String createRandomViewerId() {
        // the magic 8 comes from: $$.generateRandomID(8));
        // from the html page that belongs to the video
        return YoutubeParsingHelper.generateTParameter().substring(0, 8);
    }

    private String retrieveNumericVideoId() {
        return embedJsonStreamInfoObj.get("vid").toString();
    }
}
