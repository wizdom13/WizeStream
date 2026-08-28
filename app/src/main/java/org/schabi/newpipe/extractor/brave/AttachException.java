package org.schabi.newpipe.extractor.brave;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

/**
 * Substitute for ParsingException.
 * <p>
 * Attach data that might help the developer to fix the problem.
 */
public class AttachException extends ParsingException {
    private final List<String> exceptionData = new ArrayList<>();

    public AttachException(final String message) {
        super(message);
    }

    @Nonnull
    public static AttachException createAttachException(
            final String errMsg,
            final String content,
            final String displayedErrorKey,
            final String regexWithOnlyOneMatchingGroup
    ) {
        final AttachException exception =
                new AttachException(errMsg);
        Pattern p = Pattern.compile(regexWithOnlyOneMatchingGroup,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        final Matcher m = p.matcher(content);

        final StringBuilder output = new StringBuilder();
        while (m.find()) {
            output.append(m.group(1)).append("\n----\n");
        }
        exception.addExceptionData(displayedErrorKey, output.toString());
        return exception;
    }

    /**
     * Add useful data.
     *
     * @param data that might help the developer
     */
    public void addExceptionData(final String data) {
        exceptionData.add(data);
    }

    /**
     * Add useful data as key=value
     *
     * @param key   to describe the value
     * @param value that might help the developer
     */
    public void addExceptionData(final String key, final String value) {
        exceptionData.add(String.format("{%s}={%s}", key, value));
    }

    public List<String> getExceptionData() {
        return exceptionData;
    }
}
