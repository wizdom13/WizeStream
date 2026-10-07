package org.schabi.newpipe.settings;

import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Validated, ordered HTTPS origins saved independently from the active instance. */
final class InvidiousInstances {
    private InvidiousInstances() { }

    static List<String> parse(final String text) {
        final LinkedHashSet<String> instances = new LinkedHashSet<>();
        for (final String line : text.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                instances.add(InvidiousBackend.normalizeInstance(line));
            }
        }
        return new ArrayList<>(instances);
    }

    static List<String> choices(final String saved, final String active) {
        final LinkedHashSet<String> instances = new LinkedHashSet<>();
        // A malformed restored entry should not hide the other usable saved instances.
        for (final String line : (saved + "\n" + active).split("\\r?\\n")) {
            try {
                instances.add(InvidiousBackend.normalizeInstance(line));
            } catch (final IllegalArgumentException ignored) {
                // The editor still validates every entry before saving.
            }
        }
        return new ArrayList<>(instances);
    }
}
