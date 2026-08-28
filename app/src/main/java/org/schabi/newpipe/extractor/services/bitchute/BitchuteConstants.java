package org.schabi.newpipe.extractor.services.bitchute;

import java.util.Locale;

public final class BitchuteConstants {

    public static final String BASE_URL = "https://www.bitchute.com";
    public static final String BASE_URL_VIDEO = BASE_URL + "/video";
    public static final String BASE_URL_CHANNEL = BASE_URL + "/channel";
    // used only to get the initial search auth values for timestamp and nonce
    public static final String SEARCH_AUTH_URL = BASE_URL + "/search";
    public static final String SEARCH_URL_PREFIX =
            "https://www.bitchute.com/search/?query=";
    // TODO get automation url if there is one
    public static final String SEARCH_URL_AUTOM = SEARCH_URL_PREFIX;
    public static final Locale BITCHUTE_LOCALE = Locale.ENGLISH;
    public static final String COMMENTS_URL = "https://commentfreely.bitchute.com";

    public static final String INITIAL_PAGE_NO = "0";

    private BitchuteConstants() {
    }
}
