package org.schabi.newpipe.extractor.services.bitchute.misc;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BitchuteTimeAgoParser {

    private final class Triple {
        public final boolean isApproximation;
        public final String pattern;
        public final ChronoUnit chronoUnit;

        Triple(final boolean isApproximation, final String pattern, final ChronoUnit chronoUnit) {
            this.isApproximation = isApproximation;
            this.pattern = pattern;
            this.chronoUnit = chronoUnit;
        }
    }

    private OffsetDateTime now;

    final Triple[] timeDateMatchers = {
            new Triple(true, "year", ChronoUnit.YEARS),
            new Triple(true, "month", ChronoUnit.MONTHS),
            new Triple(true, "week", ChronoUnit.WEEKS),
            new Triple(true, "day", ChronoUnit.DAYS),
            new Triple(false, "hour", ChronoUnit.HOURS),
            new Triple(false, "minute", ChronoUnit.MINUTES),
            new Triple(false, "second", ChronoUnit.SECONDS)
    };


    public BitchuteTimeAgoParser() {
        now = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void setOffsetDateTime(final OffsetDateTime offsetDateTime) {
        if (null != offsetDateTime) {
            now = offsetDateTime;
        }
    }

    public DateWrapper parse(final String textualDate) throws ExtractionException {
        OffsetDateTime offsetDateTime = now;
        boolean isApproximation = false;

        // bitchute has the weired behavior to call it "an hour ago"
        // instead "1 hour ago" as they do for longer ago dates.
        final String weiredPattern = "an hour ago";
        if (weiredPattern.equals(textualDate)) {
            // ready to go back
            offsetDateTime = getResultFor(offsetDateTime, 1, ChronoUnit.HOURS);
            return new DateWrapper(offsetDateTime, false);
        } else { // pattern matching

            for (final Triple triple : timeDateMatchers) {
                final Pattern regexp = Pattern.compile("(\\d+)\\W" + triple.pattern + "(?:s?)");
                final Matcher match = regexp.matcher(textualDate);
                if (match.find()) {
                    final int timeValue = Integer.parseInt(match.group(1));
                    offsetDateTime = getResultFor(offsetDateTime, timeValue, triple.chronoUnit);
                    isApproximation = triple.isApproximation;
                }
            }
        }

        if (offsetDateTime.isEqual(now)) {
            throw new ExtractionException("could not read the date: " + textualDate);
        }

        return new DateWrapper(offsetDateTime, isApproximation);
    }

    @SuppressWarnings({"checkstyle:InvalidJavadocPosition", "checkstyle:LineLength"})
    private OffsetDateTime getResultFor(final OffsetDateTime offsetDateTime,
                                        final int timeAgoAmount,
                                        final ChronoUnit chronoUnit) {
        /**
         * copied from{@link
         * org.schabi.newpipe.extractor.localization.TimeAgoParser#getResultFor(int, ChronoUnit)} ()}
         * but adjusted to set the OffSetDateTime via param and also return the modified OffsetDateTime
         */
        boolean isApproximation = false;
        OffsetDateTime newOffsetDateTime = null;

        switch (chronoUnit) {
            case SECONDS:
            case MINUTES:
            case HOURS:
                newOffsetDateTime = offsetDateTime.minus(timeAgoAmount, chronoUnit);
                break;

            case DAYS:
            case WEEKS:
            case MONTHS:
                newOffsetDateTime = offsetDateTime.minus(timeAgoAmount, chronoUnit);
                isApproximation = true;
                break;

            case YEARS:
                // minusDays is needed to prevent `PrettyTime` from showing '12 months ago'.
                newOffsetDateTime = offsetDateTime.minusYears(timeAgoAmount).minusDays(1);
                isApproximation = true;
                break;
        }

        if (isApproximation) {
            newOffsetDateTime = newOffsetDateTime.truncatedTo(ChronoUnit.HOURS);
        }

        return newOffsetDateTime;
    }
}
