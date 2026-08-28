package org.schabi.newpipe.extractor.services.rumble.extractors;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.rumble.RumbleParsingHelper;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RumbleCommentsExtractor extends CommentsExtractor {
    private final int maxCommentsPerPage = 15;

    private Map<String, String> imageMap;

    private Document doc;

    public RumbleCommentsExtractor(
            final StreamingService service,
            final ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        final String videoUrl = super.getUrl();
        final String id = RumbleParsingHelper.getEmbedVideoId(
                videoUrl,
                () -> getDownloader().get(videoUrl).responseBody()
        );
        return "https://rumble.com/service.php?video=" + id + "&name=comment.list";
    }

    @Override
    public boolean isCommentsDisabled() throws ExtractionException {
        return doc == null;
    }

    @Nonnull
    @Override
    public InfoItemsPage<CommentsInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        Downloader downloader = NewPipe.getDownloader();
        byte[] responseBody = downloader.get(getUrl()).responseBody().getBytes();
        return getPage(new Page("1", responseBody));
    }

    @Override
    public InfoItemsPage<CommentsInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        byte[] responseBody = page.getBody();
        loadFromResponseBody(responseBody);
        if (isCommentsDisabled()) {
            return new InfoItemsPage<>(Collections.emptyList(), null, Collections.emptyList());
        }
        int[] ids = stringToIntArray(page.getUrl());
        int startIndex = ids[ids.length - 1] - 1;
        int count = startIndex + maxCommentsPerPage + 1;
        Element next = null;
        final CommentsInfoItemsCollector collector = new CommentsInfoItemsCollector(
                getServiceId());
        for (; startIndex < count; startIndex++) {
            ids[ids.length - 1] = startIndex + 1;
            next = getComments(ids).first();
            if (next == null || startIndex == count - 1) {
                break;
            }
            collector.commit(new RumbleCommentsInfoItemExtractor(this, ids, responseBody));
        }
        return new InfoItemsPage<>(collector, next != null ?
                new Page(intArrayToString(ids), responseBody) : null);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
    }

    public Elements getComments(int[] id) {
        if (doc == null) {
            return null;
        }
        int level = 1;
        StringBuilder selection = new StringBuilder();
        for (int i : id) {
            if (level != 1) {
                selection.append(" > div.comment-replies > ");
            }
            selection.append("ul.comments-").append(level++).append(" > li.comment-item");
            if (i != 0) {
                selection.append(":nth-child(").append(i).append(")");
            }
        }
        return doc.select(selection.toString());
    }

    public String getImage(Element e) {
        Element element = e.selectFirst("i.user-image");
        if (element == null || imageMap == null) {
            return null;
        }
        String attr = element.className();
        String[] classes = attr.split(" ");
        for (String name : classes) {
            if (name.startsWith("user-image--img--id-") &&
                    imageMap.containsKey(name)) {
                return imageMap.get(name);
            }
        }
        return null;
    }

    public static String intArrayToString(int[] intArray) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < intArray.length; i++) {
            sb.append(intArray[i]);
            if (i < intArray.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private static int[] stringToIntArray(String str) {
        String[] stringArray = str.split(" ");
        int[] intArray = new int[stringArray.length];
        for (int i = 0; i < stringArray.length; i++) {
            intArray[i] = Integer.parseInt(stringArray[i]);
        }
        return intArray;
    }

    private void initImageMap(String css) {
        Pattern pattern = Pattern.compile("i\\.user-image--img--id-(\\w+)\\s*\\{\\s*background-image:\\s*url\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(css);
        imageMap = new HashMap<>();
        while (matcher.find()) {
            String key = "user-image--img--id-" + matcher.group(1);
            String value = matcher.group(2);
            imageMap.put(key, value);
        }
    }

    private void loadFromResponseBody(byte[] responseBody) throws ExtractionException {
        try {
            if (responseBody == null) {
                return;
            }
            JsonObject info = JsonParser.object().from(new String(responseBody));
            if (info.has("html") && info.has("css_libs")) {
                doc = Jsoup.parse(info.get("html").toString());
                if (doc.selectFirst("ul.comments-1") == null) {
                    doc = null;
                    return;
                }
                Elements createComment = doc.select("li.comment-item.comment-item.comments-create");
                if (!createComment.isEmpty()) {
                    createComment.remove();
                }
                initImageMap(info.get("css_libs").toString());
            }
        } catch (final JsonParserException e) {
            e.printStackTrace();
            throw new ExtractionException("Could not read json from: " + getUrl());
        }
    }
}
