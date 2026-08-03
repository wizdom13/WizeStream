package org.schabi.newpipe.extractor.services.youtube;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.utils.JavaScript;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manage the extraction and usage of YouTube player JavaScript metadata and deobfuscation logic.
 *
 * <p>Local extraction from YouTube's base player is preferred so playback does not depend on the
 * PipePipe decoder service. The decoder API remains available as a fallback when local extraction
 * fails.</p>
 */
public final class YoutubeJavaScriptPlayerManager {

    private static final String LATEST_PLAYER_URL =
            "https://api.pipepipe.dev/decoder/latest-player";
    private static final String USER_AGENT = "PipePipe/4.9.0";
    private static final long PLAYER_METADATA_TTL_MILLIS = 24L * 60L * 60L * 1000L;

    @Nonnull
    private static final Map<String, String> LOCAL_THROTTLING_PARAMETERS = new HashMap<>();

    @Nullable
    private static PlayerMetadata playerMetadata;
    @Nullable
    private static String cachedJavaScriptPlayerCode;
    @Nullable
    private static Integer cachedLocalSignatureTimestamp;
    @Nullable
    private static String cachedSignatureDeobfuscationFunction;
    @Nullable
    private static String cachedThrottlingDeobfuscationFunctionName;
    @Nullable
    private static String cachedThrottlingDeobfuscationFunction;

    private YoutubeJavaScriptPlayerManager() {
    }

    /**
     * Get the signature timestamp of the current YouTube base player.
     */
    @Nonnull
    public static Integer getSignatureTimestamp(@Nonnull final String videoId)
            throws ParsingException {
        try {
            return getLocalSignatureTimestamp(videoId);
        } catch (final ParsingException localError) {
            try {
                return getPlayerMetadata(videoId).signatureTimestamp;
            } catch (final ParsingException apiError) {
                apiError.addSuppressed(localError);
                throw apiError;
            }
        }
    }

    /**
     * Deobfuscate a signature, preferring local YouTube player JavaScript.
     */
    @Nonnull
    public static String deobfuscateSignature(@Nonnull final String videoId,
                                              @Nonnull final String obfuscatedSignature)
            throws ParsingException {
        try {
            return deobfuscateSignatureLocally(videoId, obfuscatedSignature);
        } catch (final ParsingException localError) {
            try {
                return YoutubeApiDecoder.decodeSignature(
                        getPlayerMetadata(videoId).playerId, obfuscatedSignature);
            } catch (final ParsingException apiError) {
                apiError.addSuppressed(localError);
                throw apiError;
            }
        }
    }

    /**
     * Return a URL with its throttling parameter deobfuscated, when present.
     */
    @Nonnull
    public static String getUrlWithThrottlingParameterDeobfuscated(
            @Nonnull final String videoId,
            @Nonnull final String streamingUrl) throws ParsingException {
        final String obfuscatedThrottlingParameter =
                YoutubeThrottlingParameterUtils.getThrottlingParameterFromStreamingUrl(
                        streamingUrl);
        if (obfuscatedThrottlingParameter == null) {
            return streamingUrl;
        }

        final String deobfuscatedThrottlingParameter = deobfuscateThrottlingParameter(
                videoId, obfuscatedThrottlingParameter);
        return streamingUrl.replace(
                obfuscatedThrottlingParameter, deobfuscatedThrottlingParameter);
    }

    /**
     * Clear local and remote player caches.
     */
    public static void clearAllCaches() {
        playerMetadata = null;
        cachedJavaScriptPlayerCode = null;
        cachedLocalSignatureTimestamp = null;
        cachedSignatureDeobfuscationFunction = null;
        cachedThrottlingDeobfuscationFunctionName = null;
        cachedThrottlingDeobfuscationFunction = null;
        clearThrottlingParametersCache();
    }

    public static void clearThrottlingParametersCache() {
        LOCAL_THROTTLING_PARAMETERS.clear();
        YoutubeApiDecoder.clearCache();
    }

    public static int getThrottlingParametersCacheSize() {
        return LOCAL_THROTTLING_PARAMETERS.size() + YoutubeApiDecoder.getCacheSize();
    }

    /**
     * Batch deobfuscate signatures and throttling parameters.
     *
     * <p>The local player code is cached, so processing the values individually avoids a hard
     * dependency on the remote batch decoder while keeping the existing return type.</p>
     */
    @Nonnull
    public static YoutubeApiDecoder.BatchDecodeResult deobfuscateBatch(
            @Nonnull final String videoId,
            @Nullable final List<String> signatures,
            @Nullable final List<String> throttlingParams) throws ParsingException {
        final Map<String, String> signatureResults = new HashMap<>();
        final Map<String, String> throttlingResults = new HashMap<>();

        if (signatures != null) {
            for (final String signature : signatures) {
                signatureResults.put(signature, deobfuscateSignature(videoId, signature));
            }
        }
        if (throttlingParams != null) {
            for (final String throttlingParam : throttlingParams) {
                throttlingResults.put(throttlingParam,
                        deobfuscateThrottlingParameter(videoId, throttlingParam));
            }
        }

        return new YoutubeApiDecoder.BatchDecodeResult(
                signatureResults, throttlingResults);
    }

    @Nonnull
    private static Integer getLocalSignatureTimestamp(@Nonnull final String videoId)
            throws ParsingException {
        if (cachedLocalSignatureTimestamp != null) {
            return cachedLocalSignatureTimestamp;
        }

        extractJavaScriptCodeIfNeeded(videoId);
        try {
            cachedLocalSignatureTimestamp = Integer.valueOf(
                    YoutubeSignatureUtils.getSignatureTimestamp(cachedJavaScriptPlayerCode));
            return cachedLocalSignatureTimestamp;
        } catch (final NumberFormatException e) {
            throw new ParsingException("Could not convert signature timestamp to a number", e);
        } catch (final ParsingException e) {
            throw e;
        } catch (final Exception e) {
            throw new ParsingException("Could not get signature timestamp", e);
        }
    }

    @Nonnull
    private static String deobfuscateSignatureLocally(
            @Nonnull final String videoId,
            @Nonnull final String obfuscatedSignature) throws ParsingException {
        extractJavaScriptCodeIfNeeded(videoId);

        if (cachedSignatureDeobfuscationFunction == null) {
            cachedSignatureDeobfuscationFunction = YoutubeSignatureUtils.getDeobfuscationCode(
                    cachedJavaScriptPlayerCode);
        }

        try {
            return Objects.requireNonNullElse(
                    JavaScript.run(cachedSignatureDeobfuscationFunction,
                            YoutubeSignatureUtils.DEOBFUSCATION_FUNCTION_NAME,
                            obfuscatedSignature), "");
        } catch (final Exception e) {
            throw new ParsingException(
                    "Could not run signature parameter deobfuscation JavaScript function", e);
        }
    }

    @Nonnull
    private static String deobfuscateThrottlingParameter(
            @Nonnull final String videoId,
            @Nonnull final String obfuscatedThrottlingParameter) throws ParsingException {
        try {
            return deobfuscateThrottlingParameterLocally(
                    videoId, obfuscatedThrottlingParameter);
        } catch (final ParsingException localError) {
            try {
                return YoutubeApiDecoder.decodeThrottlingParameter(
                        getPlayerMetadata(videoId).playerId,
                        obfuscatedThrottlingParameter);
            } catch (final ParsingException apiError) {
                apiError.addSuppressed(localError);
                throw apiError;
            }
        }
    }

    @Nonnull
    private static String deobfuscateThrottlingParameterLocally(
            @Nonnull final String videoId,
            @Nonnull final String obfuscatedThrottlingParameter) throws ParsingException {
        final String cachedResult = LOCAL_THROTTLING_PARAMETERS.get(
                obfuscatedThrottlingParameter);
        if (cachedResult != null) {
            return cachedResult;
        }

        extractJavaScriptCodeIfNeeded(videoId);
        if (cachedThrottlingDeobfuscationFunction == null) {
            cachedThrottlingDeobfuscationFunctionName =
                    YoutubeThrottlingParameterUtils.getDeobfuscationFunctionName(
                            cachedJavaScriptPlayerCode);
            cachedThrottlingDeobfuscationFunction =
                    YoutubeThrottlingParameterUtils.getDeobfuscationFunction(
                            cachedJavaScriptPlayerCode,
                            cachedThrottlingDeobfuscationFunctionName);
        }

        try {
            final String result = JavaScript.run(
                    cachedThrottlingDeobfuscationFunction,
                    cachedThrottlingDeobfuscationFunctionName,
                    obfuscatedThrottlingParameter);
            if (result == null || result.isEmpty()) {
                throw new IllegalStateException("Extracted n-parameter is empty");
            }
            LOCAL_THROTTLING_PARAMETERS.put(obfuscatedThrottlingParameter, result);
            return result;
        } catch (final Exception e) {
            throw new ParsingException(
                    "Could not run throttling parameter deobfuscation JavaScript function", e);
        }
    }

    private static void extractJavaScriptCodeIfNeeded(@Nonnull final String videoId)
            throws ParsingException {
        if (cachedJavaScriptPlayerCode == null) {
            cachedJavaScriptPlayerCode = YoutubeJavaScriptExtractor.extractJavaScriptPlayerCode(
                    videoId);
        }
    }

    @Nonnull
    private static PlayerMetadata getPlayerMetadata(@Nonnull final String videoId)
            throws ParsingException {
        final PlayerMetadata currentMetadata = playerMetadata;
        if (currentMetadata != null && !currentMetadata.isExpired()) {
            return currentMetadata;
        }

        try {
            playerMetadata = fetchLatestPlayerMetadata();
            return playerMetadata;
        } catch (final ParsingException e) {
            if (currentMetadata != null) {
                return currentMetadata;
            }
            throw e;
        }
    }

    @Nonnull
    private static PlayerMetadata fetchLatestPlayerMetadata() throws ParsingException {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Collections.singletonList(USER_AGENT));

        try {
            final Response response = NewPipe.getDownloader().get(
                    LATEST_PLAYER_URL, headers, Localization.DEFAULT);
            if (response.responseCode() < 200 || response.responseCode() >= 300) {
                throw new ParsingException("latest-player request failed with HTTP "
                        + response.responseCode());
            }

            final String responseBody = response.responseBody();
            if (responseBody == null || responseBody.trim().isEmpty()) {
                throw new ParsingException("latest-player response body is empty");
            }

            final String trimmedResponseBody = responseBody.trim();
            if (!trimmedResponseBody.startsWith("{")) {
                throw new ParsingException("latest-player response was not JSON; prefix="
                        + sanitizeResponsePrefix(trimmedResponseBody));
            }

            final JsonObject responseJson = JsonParser.object().from(trimmedResponseBody);
            final String playerId = responseJson.getString("player", "");
            if (playerId.isEmpty()) {
                throw new ParsingException("latest-player response missing player");
            }
            if (!responseJson.has("signatureTimestamp")) {
                throw new ParsingException("latest-player response missing signatureTimestamp");
            }

            return new PlayerMetadata(playerId,
                    responseJson.getInt("signatureTimestamp"),
                    System.currentTimeMillis() + PLAYER_METADATA_TTL_MILLIS);
        } catch (final IOException | ReCaptchaException e) {
            throw new ParsingException("Failed to fetch latest player metadata", e);
        } catch (final JsonParserException e) {
            throw new ParsingException("Failed to parse latest player metadata", e);
        }
    }

    @Nonnull
    private static String sanitizeResponsePrefix(@Nonnull final String responseBody) {
        final String singleLine = responseBody.replaceAll("[\\r\\n\\t]+", " ");
        return singleLine.substring(0, Math.min(singleLine.length(), 48));
    }

    private static final class PlayerMetadata {
        @Nonnull
        private final String playerId;
        private final int signatureTimestamp;
        private final long expiresAt;

        private PlayerMetadata(@Nonnull final String playerId,
                               final int signatureTimestamp,
                               final long expiresAt) {
            this.playerId = playerId;
            this.signatureTimestamp = signatureTimestamp;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
