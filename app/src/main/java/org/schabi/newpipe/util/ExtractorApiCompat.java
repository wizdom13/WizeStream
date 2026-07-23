package org.schabi.newpipe.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.Description;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Compatibility helpers for extractor API accessors that differ across WizeStreamExtractor builds.
 */
public final class ExtractorApiCompat {
    private static final String DESCRIPTION_GETTER = "get" + "Description";

    private ExtractorApiCompat() {
    }

    @NonNull
    public static Description description(@Nullable final Object item) {
        final Object value = invokeNoArg(item, DESCRIPTION_GETTER);
        if (value instanceof Description description) {
            return description;
        }
        if (value instanceof String description) {
            return new Description(description, Description.PLAIN_TEXT);
        }
        return Description.EMPTY_DESCRIPTION;
    }

    @NonNull
    public static String descriptionText(@Nullable final Object item) {
        final Object value = invokeNoArg(item, DESCRIPTION_GETTER);
        if (value instanceof Description description) {
            return description.getContent();
        }
        if (value instanceof String description) {
            return description;
        }
        return "";
    }

    @Nullable
    private static Object invokeNoArg(@Nullable final Object item,
                                      @NonNull final String methodName) {
        if (item == null) {
            return null;
        }
        try {
            final Method method = item.getClass().getMethod(methodName);
            return method.invoke(item);
        } catch (final IllegalAccessException | InvocationTargetException
                       | NoSuchMethodException ignored) {
            return null;
        }
    }
}
