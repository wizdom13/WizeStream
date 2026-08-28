package org.schabi.newpipe.extractor.services.bitchute.extractor;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.util.Collections;
import java.util.List;

public class BitchuteSuggestionExtractor extends SuggestionExtractor {

    private static final String AUTOCOMPLETE_URL
            = BitchuteConstants.SEARCH_URL_AUTOM;

    public BitchuteSuggestionExtractor(final StreamingService service) {
        super(service);
    }

    @Override
    public List<String> suggestionList(final String query) {
        /*
        try {
            Response response = NewPipe.getDownloader().get(AUTOCOMPLETE_URL + query);
            return Arrays.asList(response.responseBody().split("\n", 0));
        } catch (Exception e) {
            return Collections.emptyList();
        }

         */
        // there is no support for autosuggestion for Bitchute atm. 20210412
        return Collections.emptyList();
    }
}
