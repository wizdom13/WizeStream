package org.schabi.newpipe.local.search;

import androidx.annotation.NonNull;

/**
 * A local tab whose currently displayed content can be filtered by the main toolbar.
 */
public interface ContextualSearchable {
    /**
     * Updates the local filter. An empty query must restore the complete, unfiltered content.
     *
     * @param query normalized query supplied by the main toolbar
     */
    void setContextualSearchQuery(@NonNull String query);
}
