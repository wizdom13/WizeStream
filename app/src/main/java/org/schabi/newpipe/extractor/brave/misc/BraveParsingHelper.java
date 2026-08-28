package org.schabi.newpipe.extractor.brave.misc;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public final class BraveParsingHelper {
    private BraveParsingHelper() { }
    public static OffsetDateTime parseDateFrom(final String textualUploadDate)
            throws ParsingException {
        try {
            return OffsetDateTime.parse(textualUploadDate);
        } catch (final DateTimeParseException e) {
            try {
                return LocalDate.parse(textualUploadDate).atStartOfDay().atOffset(ZoneOffset.UTC);
            } catch (final DateTimeParseException e1) {
                throw new ParsingException("Could not parse date: \"" + textualUploadDate + "\"",
                        e1);
            }
        }
    }
}
