package org.schabi.newpipe.extractor.services.bitchute;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.schabi.newpipe.extractor.NewPipe.getDownloader;
import static org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants.BASE_URL;
import static org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants.BITCHUTE_LOCALE;
import static org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants.SEARCH_AUTH_URL;
import static org.schabi.newpipe.extractor.services.bitchute.BitchuteService.BITCHUTE_LINK;

public final class BitchuteParserHelper {

    private static final Map<String, String> VIDEO_ID_2_COMMENT_CF_AUTH = new HashMap<>();
    // the time interval the searchAuthTimestamp/Nonce value should be used (in seconds)
    // before renewing
    private static final int SEARCH_AUTH_DATA_TIMEOUT = 60 * 10;
    private static String cookies;
    private static String csrfToken;
    private static String searchAuthNonce;
    private static String searchAuthTimestamp;

    private BitchuteParserHelper() {
    }

    public static boolean isInitDone() {
        if (cookies == null || csrfToken == null || cookies.isEmpty() || csrfToken.isEmpty()) {
            return false;
        }
        return true;
    }

    public static void init() throws ReCaptchaException, IOException {
        final Response response = getDownloader().get(BITCHUTE_LINK);
        initCookies(response);
    }

    private static void initCookies(final Response response) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, List<String>> entry : response.responseHeaders().entrySet()) {
            if (entry.getKey().equals("set-cookie")) {
                final List<String> values = entry.getValue();
                for (final String v : values) {
                    final String val = v.split(";", 2)[0];
                    sb.append(val).append(";");
                    if (val.contains("csrf")) {
                        csrfToken = val.split("=", 2)[1];
                    }
                }
                break;
            }
        }
        cookies = sb.toString();
    }

    public static Map<String, List<String>> getPostHeader(final int contentLength)
            throws IOException, ReCaptchaException {
        final Map<String, List<String>> headers = getBasicHeader();
        headers.put("Content-Type", Collections.singletonList("application/x-www-form-urlencoded"));
        headers.put("Content-Length", Collections.singletonList(String.valueOf(contentLength)));
        // DBG System.out.println("Headers: ");
        // DBG for (final Map.Entry m : headers.entrySet()) {
        // DBG     System.out.println(m.getKey() + ": " + m.getValue());
        // DBG }
        return headers;
    }

    // evermind-zz: quick duplication of getPostHeader() adjusted to suit new API
    public static Map<String, List<String>> getPostHeaderNew(final int contentLength) {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        //headers.put("Content-Length", Collections.singletonList(String.valueOf(contentLength)));
        // DBG System.out.println("Headers: ");
        // DBG for (final Map.Entry m : headers.entrySet()) {
        // DBG     System.out.println(m.getKey() + ": " + m.getValue());
        // DBG }
        return headers;
    }

    public static Map<String, List<String>> getBasicHeader()
            throws IOException, ReCaptchaException {
        if (!isInitDone()) {
            init();
        }
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Cookie", Collections.singletonList(cookies));
        headers.put("Referer", Collections.singletonList(BITCHUTE_LINK));
        return headers;
    }

    @Nonnull
    public static JsonArray getComments(@Nonnull final String id,
                                        @Nonnull final String url,
                                        final int commentCount)
            throws IOException, ExtractionException {
        final String cfAuth = getCfAuth(id);
        final JsonArray jsonArray;

        if (cfAuth != null) {
            jsonArray = getComments(cfAuth, commentCount);
        } else {
            final Downloader downloader = NewPipe.getDownloader();
            final Response response = downloader.get(url);
            final Pattern pattern = Pattern.compile("\\{cf_auth: '([^']+)'");
            final Matcher match = pattern.matcher(response.responseBody());

            if (match.find()) {
                jsonArray = getComments(match.group(1), commentCount);
            } else {
                // could not find anything so empty array
                jsonArray = new JsonArray();
            }
        }

        return jsonArray;
    }

    @Nonnull
    private static JsonArray getComments(@Nonnull final String cfAuth, final int commentCount)
            throws IOException, ExtractionException {
        // in case you get a JsonObject instead of JsonArray that might be because they changed
        // something with this parameter 'isNameValuesArrays=false'
        // -> basically ATM if you add this parameter to the url with the help of 'moreHeaders'
        //    parameter (regardless if set to false or true) you will get an JsonObject.
        //    ONLY dropping this parameter results in getting a JsonArray as we want.
        return (JsonArray) getJsonDataFromCommentEndpoints("/api/get_comments/", cfAuth,
                String.format(BITCHUTE_LOCALE, "&commentCount=%d", commentCount));
    }

    /*
    private static int getCommentsCount(final String cfAuth)
            throws IOException, ExtractionException {
        final String key = "commentCount";
        final JsonObject counts = (JsonObject)
                getJsonDataFromCommentEndpoints("/api/get_comment_count/", cfAuth, "");

        Objects.requireNonNull(counts.get(key));
        return counts.getInt(key);
    }
     */

    @Nonnull
    private static Object getJsonDataFromCommentEndpoints(
            @Nonnull final String apiEndpoint,
            @Nonnull final String cfAuth,
            @Nonnull final String moreHeaders) throws IOException, ExtractionException {
        if (!isInitDone()) {
            init();
        }

        final String dataWithPlaceholders = "cf_auth=%s" + moreHeaders;

        final String urlEncodeCfAuth = Utils.encodeUrlUtf8(cfAuth);
        final byte[] data = String.format(dataWithPlaceholders, urlEncodeCfAuth)
                .getBytes(StandardCharsets.UTF_8);

        final Response response = getDownloader().post(
                String.format(BITCHUTE_LOCALE, "%s%s", BitchuteConstants.COMMENTS_URL, apiEndpoint),
                getPostHeader(data.length),
                data
        );

        try {
            final Object jsonObject = JsonParser.any().from(response.responseBody());
            return Objects.requireNonNull(jsonObject);
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse bitchute comments results JsonObject");
        }
    }

    public static JsonObject callJsonDjangoApi(
            final JsonBuilder<JsonObject> sortQueryJson,
            final String endpoint)
            throws IOException, ExtractionException {
        final JsonObject thing = sortQueryJson.done();
        final byte[] data = JsonWriter.string(thing).getBytes(StandardCharsets.UTF_8);

        final Response response = getDownloader().post(
                endpoint,
                getPostHeaderNew(data.length),
                data
        );
        return getJsonObject(response);
    }

    private static JsonObject getJsonObject(final Response response) throws ExtractionException {
        final JsonObject jsonObject;
        try {
            jsonObject = JsonParser.object().from(response.responseBody());
            if (response.responseCode() == 200) {

                return jsonObject;
            }
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse bitchute search results JsonObject: "
                    + e.getMessage());
        }

        final String errorsKey = "errors";
        if (response.responseCode() == 404 && jsonObject.has(errorsKey)) {
            if (!jsonObject.getArray(errorsKey).isEmpty()) {
                final String reason = ((JsonObject) jsonObject.getArray(errorsKey).get(0))
                        .getString("message");
                if (reason.contains("Not Found")) {
                    throw new ContentNotAvailableException(reason);
                }
            }
        }

        throw new ExtractionException(
                "Server response for bitchute search results was not successful: (httpCode="
                        + response.responseCode() + " body: " + response.responseBody());
    }

    private static void initSearch() throws ReCaptchaException, IOException {
        final Response response = getDownloader().get(SEARCH_AUTH_URL);
        initCookies(response);

        extractAndStoreSearchAuth(response.responseBody());
    }

    public static boolean extractAndStoreSearchAuth(@Nonnull final String body) {
        final Pattern pattern = Pattern.compile("searchAuth\\('([^']+)', '([^']+)'");

        final Matcher match = pattern.matcher(body);

        if (match.find()) {
            searchAuthTimestamp = match.group(1);
            searchAuthNonce = match.group(2);

            return true;
        }
        return false;
    }

    private static boolean isSearchInitDone() {
        if (searchAuthTimestamp == null || searchAuthNonce == null) {
            return false;
        } else {
            return isSearchAuthStillUsable(searchAuthTimestamp);
        }
    }

    // Check if the timestamp we currently have is not too old.
    private static boolean isSearchAuthStillUsable(@Nonnull final String timestamp) {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        final LocalDateTime authTimestamp = LocalDateTime.parse(timestamp,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX"));

        final long diff =
                now.toEpochSecond(ZoneOffset.UTC) - authTimestamp.toEpochSecond(ZoneOffset.UTC);

        return diff < SEARCH_AUTH_DATA_TIMEOUT;
    }

    public static boolean extractAndStoreCfAuth(@Nonnull final String id,
                                                @Nonnull final String body) {
        final Pattern pattern = Pattern.compile("\\{cf_auth: '([^']+)'");
        final Matcher match = pattern.matcher(body);

        if (match.find()) {
            VIDEO_ID_2_COMMENT_CF_AUTH.put(id, match.group(1));
            return true;
        }
        return false;
    }

    @Nullable
    public static String getCfAuth(@Nonnull final String id) {
        return VIDEO_ID_2_COMMENT_CF_AUTH.get(id);
    }

    public static String prependBaseUrl(final String urlPath) {
        return BASE_URL + urlPath;
    }
}
