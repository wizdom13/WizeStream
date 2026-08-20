package org.schabi.newpipe.dearrow;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.util.image.ExtractorImageCompat;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Fetches and applies community-contributed DeArrow titles and thumbnails. */
public final class DeArrowService {
    static final String BRANDING_ENDPOINT = "https://sponsor.ajay.app/api/branding";
    static final String THUMBNAIL_ENDPOINT =
            "https://dearrow-thumb.ajay.app/api/v1/getThumbnail";
    private static final int CACHE_SIZE = 500;
    private static final Pattern YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile(
            "(?:[?&]v=|youtu\\.be/|/(?:embed|shorts|live)/)([A-Za-z0-9_-]{11})");
    private static final Object CACHE_LOCK = new Object();
    private static final LruCache<String, Branding> CACHE = new LruCache<>(CACHE_SIZE);
    private static final Map<String, Single<Branding>> IN_FLIGHT = new HashMap<>();

    private DeArrowService() {
    }

    /**
     * Returns branding for a YouTube video. Errors and missing submissions deliberately resolve to
     * {@link Branding#EMPTY}, allowing callers to keep the original metadata without error UI.
     *
     * @param context context used to read the DeArrow preferences
     * @param info    video metadata containing the service and video identifier
     * @return a single that emits accepted branding or {@link Branding#EMPTY}
     */
    @NonNull
    public static Single<Branding> getBranding(@NonNull final Context context,
                                                @NonNull final Info info) {
        return getBranding(context, info.getServiceId(), info.getId());
    }

    /**
     * Returns branding for a YouTube list item.
     *
     * @param context context used to read the DeArrow preferences
     * @param item    list item containing the service and video URL
     * @return a single that emits accepted branding or {@link Branding#EMPTY}
     */
    @NonNull
    public static Single<Branding> getBranding(@NonNull final Context context,
                                                @NonNull final InfoItem item) {
        return getBranding(context, item.getServiceId(), youtubeVideoId(item.getUrl()));
    }

    @NonNull
    private static Single<Branding> getBranding(@NonNull final Context context,
                                                 final int serviceId,
                                                 final String videoId) {
        if (!isEnabled(context) || serviceId != ServiceList.YouTube.getServiceId()
                || !isYoutubeVideoId(videoId)) {
            return Single.just(Branding.EMPTY);
        }

        synchronized (CACHE_LOCK) {
            final Branding cached = CACHE.get(videoId);
            if (cached != null) {
                return Single.just(cached);
            }
            final Single<Branding> pending = IN_FLIGHT.get(videoId);
            if (pending != null) {
                return pending;
            }

            final Single<Branding> request = Single.fromCallable(() -> fetch(videoId))
                    .subscribeOn(Schedulers.io())
                    .onErrorReturnItem(Branding.EMPTY)
                    .doOnSuccess(branding -> {
                        synchronized (CACHE_LOCK) {
                            CACHE.put(videoId, branding);
                        }
                    })
                    .doFinally(() -> {
                        synchronized (CACHE_LOCK) {
                            IN_FLIGHT.remove(videoId);
                        }
                    })
                    .cache();
            IN_FLIGHT.put(videoId, request);
            return request;
        }
    }

    /**
     * Applies the enabled parts of a branding response.
     *
     * @param context  context used to read the DeArrow preferences
     * @param info     metadata object to update
     * @param branding accepted branding returned by {@link #getBranding(Context, Info)}
     * @return whether a title or thumbnail was changed
     */
    public static boolean applyBranding(@NonNull final Context context,
                                        @NonNull final Info info,
                                        @NonNull final Branding branding) {
        if (!isEnabled(context)) {
            return false;
        }
        boolean changed = false;
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean(context.getString(R.string.dearrow_titles_key), true)
                && branding.title != null && !branding.title.equals(info.getName())) {
            info.setName(branding.title);
            changed = true;
        }
        if (preferences.getBoolean(context.getString(R.string.dearrow_thumbnails_key), true)
                && branding.thumbnailUrl != null) {
            final Image image = new Image(branding.thumbnailUrl, Image.HEIGHT_UNKNOWN,
                    Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN);
            if (info instanceof StreamInfo) {
                ((StreamInfo) info).setThumbnails(Collections.singletonList(image));
            }
            changed = true;
        }
        return changed;
    }

    /**
     * Applies the enabled parts of a branding response to a list item.
     *
     * @param context  context used to read the DeArrow preferences
     * @param item     list item to update
     * @param branding accepted branding returned by {@link #getBranding(Context, InfoItem)}
     * @return whether a title or thumbnail was changed
     */
    public static boolean applyBranding(@NonNull final Context context,
                                        @NonNull final InfoItem item,
                                        @NonNull final Branding branding) {
        if (!isEnabled(context)) {
            return false;
        }
        boolean changed = false;
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean(context.getString(R.string.dearrow_titles_key), true)
                && branding.title != null && !branding.title.equals(item.getName())) {
            item.setName(branding.title);
            changed = true;
        }
        if (preferences.getBoolean(context.getString(R.string.dearrow_thumbnails_key), true)
                && branding.thumbnailUrl != null) {
            final Image image = new Image(branding.thumbnailUrl, Image.HEIGHT_UNKNOWN,
                    Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN);
            ExtractorImageCompat.setThumbnailImages(item, Collections.singletonList(image));
            changed = true;
        }
        return changed;
    }

    private static boolean isEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.dearrow_enable_key), false);
    }

    private static boolean isYoutubeVideoId(final String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }

    private static String youtubeVideoId(final String url) {
        if (url == null) {
            return null;
        }
        final Matcher matcher = YOUTUBE_VIDEO_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    @NonNull
    private static Branding fetch(@NonNull final String videoId) throws Exception {
        final String encodedId = URLEncoder.encode(videoId, StandardCharsets.UTF_8.name());
        final Response response = DownloaderImpl.getInstance().get(
                BRANDING_ENDPOINT + "?videoID=" + encodedId + "&fetchAll=true");
        if (response.responseCode() != 200) {
            return Branding.EMPTY;
        }
        return parseBranding(videoId, response.responseBody());
    }

    @NonNull
    static Branding parseBranding(@NonNull final String videoId, @NonNull final String body)
            throws JsonParserException, IOException {
        final JsonObject root = JsonParser.object().from(body);
        String title = null;
        String thumbnailUrl = null;

        final JsonArray titles = root.getArray("titles");
        if (titles != null && !titles.isEmpty()) {
            final JsonObject candidate = titles.getObject(0);
            if (isAccepted(candidate) && !candidate.getBoolean("original", false)) {
                final String candidateTitle = candidate.getString("title");
                title = candidateTitle == null ? null : candidateTitle.trim();
                if (title != null && title.isEmpty()) {
                    title = null;
                }
            }
        }

        final JsonArray thumbnails = root.getArray("thumbnails");
        if (thumbnails != null && !thumbnails.isEmpty()) {
            final JsonObject candidate = thumbnails.getObject(0);
            if (isAccepted(candidate) && !candidate.getBoolean("original", false)
                    && candidate.containsKey("timestamp")) {
                final double timestamp = candidate.getDouble("timestamp");
                if (Double.isFinite(timestamp) && timestamp >= 0) {
                    thumbnailUrl = THUMBNAIL_ENDPOINT + "?videoID="
                            + URLEncoder.encode(videoId, StandardCharsets.UTF_8.name())
                            + "&time=" + timestamp;
                }
            }
        }
        return title == null && thumbnailUrl == null
                ? Branding.EMPTY : new Branding(title, thumbnailUrl);
    }

    private static boolean isAccepted(final JsonObject candidate) {
        return candidate != null
                && (candidate.getBoolean("locked", false) || candidate.getInt("votes", -1) >= 0);
    }

    public static final class Branding {
        public static final Branding EMPTY = new Branding(null, null);

        private final String title;
        private final String thumbnailUrl;

        Branding(final String title, final String thumbnailUrl) {
            this.title = title;
            this.thumbnailUrl = thumbnailUrl;
        }

        public String getTitle() {
            return title;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }
    }
}
