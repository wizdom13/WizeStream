package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.comments.CommentSortOrder;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

/** Reads YouTube's server-provided sort continuation, never sorts a partial page locally. */
final class YoutubeCommentSort {
    private YoutubeCommentSort() {
    }

    static String continuation(final JsonObject response, final CommentSortOrder order)
            throws ParsingException {
        for (final Object endpoint : response.getArray("onResponseReceivedEndpoints")) {
            if (!(endpoint instanceof JsonObject)) {
                continue;
            }
            final JsonObject command = ((JsonObject) endpoint).getObject(
                    "reloadContinuationItemsCommand",
                    ((JsonObject) endpoint).getObject("appendContinuationItemsAction"));
            for (final Object item : command.getArray("continuationItems")) {
                if (!(item instanceof JsonObject)) {
                    continue;
                }
                final JsonArray sorts = ((JsonObject) item).getObject("commentsHeaderRenderer")
                        .getObject("sortMenu").getObject("sortFilterSubMenuRenderer")
                        .getArray("subMenuItems");
                // The service defines this order independently of translated menu titles.
                final int index = order == CommentSortOrder.NEWEST ? 1 : 0;
                if (sorts.size() > index) {
                    final String token = sorts.getObject(index).getObject("serviceEndpoint")
                            .getObject("continuationCommand").getString("token");
                    if (token != null && !token.isEmpty()) {
                        return token;
                    }
                }
            }
        }
        throw new ParsingException("The requested comment order is unavailable");
    }
}
