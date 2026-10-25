package org.schabi.newpipe.fragments.list.videos;

import org.jsoup.Jsoup;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class RelatedItemTopics {
    private static final int MAX_TOPICS = 12;
    private static final int MAX_TOPIC_LENGTH = 48;
    private static final Pattern HASHTAG = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{M}\\p{N}_]+)");

    private RelatedItemTopics() { }

    static List<String> from(final StreamInfo info) {
        final Map<String, String> topics = new LinkedHashMap<>();
        final Description description = info.getDescription();
        if (description != null) {
            final String text = description.getType() == Description.HTML
                    ? Jsoup.parse(description.getContent()).text() : description.getContent();
            final Matcher hashtags = HASHTAG.matcher(text);
            while (hashtags.find() && topics.size() < MAX_TOPICS) {
                add(topics, hashtags.group(1));
            }
        }
        if (info.getTags() != null) {
            for (final String tag : info.getTags()) {
                add(topics, tag);
            }
        }
        return new ArrayList<>(topics.values());
    }

    private static void add(final Map<String, String> topics, final String value) {
        if (value == null || topics.size() >= MAX_TOPICS) {
            return;
        }
        final String label = value.trim().replaceFirst("^#", "").trim();
        final String key = normalize(label);
        if (!key.isEmpty() && label.length() <= MAX_TOPIC_LENGTH) {
            topics.putIfAbsent(key, label);
        }
    }

    static List<InfoItem> filter(final List<InfoItem> items, final String topic) {
        if (topic.isEmpty()) {
            return new ArrayList<>(items);
        }
        final String query = normalize(topic);
        return items.stream().filter(item -> {
            if (!(item instanceof StreamInfoItem stream)) {
                return false;
            }
            return matches(stream.getName(), query) || matches(stream.getShortDescription(), query)
                    || matches(stream.getUploaderName(), query);
        }).collect(Collectors.toList());
    }

    private static boolean matches(final String value, final String query) {
        return value != null && (" " + normalize(value) + " ").contains(" " + query + " ");
    }

    private static String normalize(final String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ").trim();
    }
}
