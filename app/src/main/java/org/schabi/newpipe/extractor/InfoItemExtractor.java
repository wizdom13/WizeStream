package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.util.Collections;
import java.util.List;

public interface InfoItemExtractor {
    String getName() throws ParsingException;
    String getUrl() throws ParsingException;

    default String getThumbnailUrl() throws ParsingException {
        final List<Image> thumbnails = getThumbnails();
        return thumbnails.isEmpty() ? null : thumbnails.get(0).getUrl();
    }

    default List<Image> getThumbnails() throws ParsingException {
        return Collections.emptyList();
    }
}
