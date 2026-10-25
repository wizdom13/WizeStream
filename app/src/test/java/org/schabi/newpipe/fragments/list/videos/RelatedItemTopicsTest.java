package org.schabi.newpipe.fragments.list.videos;

import org.junit.Test;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RelatedItemTopicsTest {
    @Test
    public void extractsVisibleHashtagsAndDeduplicatesTagsAcrossCaseAndWidth() {
        final StreamInfo info = stream();
        info.setDescription(new Description(
                "<a href='https://example.org/#hidden'>#Space</a> #موسيقى #Space",
                Description.HTML));
        info.setTags(Arrays.asList("space", "ＳＰＡＣＥ", " Music ", "#music", "", null));
        assertEquals(Arrays.asList("Space", "موسيقى", "Music"), RelatedItemTopics.from(info));
    }

    @Test
    public void boundsChipCountAndSkipsUnusableTags() {
        final StreamInfo info = stream();
        info.setTags(Arrays.asList("---", "x".repeat(49)));
        assertTrue(RelatedItemTopics.from(info).isEmpty());
        info.setTags(IntStream.range(0, 50).mapToObj(i -> "Topic " + i)
                .collect(Collectors.toList()));
        assertEquals(12, RelatedItemTopics.from(info).size());
        info.setTags(Collections.emptyList());
        assertTrue(RelatedItemTopics.from(info).isEmpty());
    }

    @Test
    public void filtersLoadedMetadataAndAllRestoresOriginalOrderWithoutMutation() {
        final StreamInfoItem title = video("Space exploration");
        final StreamInfoItem description = video("An interview");
        description.setShortDescription("Discussing SPACE travel.");
        final StreamInfoItem uploader = video("Latest upload");
        uploader.setUploaderName("Space Agency");
        final StreamInfoItem other = video("Workspace setup");
        final List<InfoItem> items = Arrays.asList(title, other, description, uploader);
        assertEquals(Arrays.asList(title, description, uploader),
                RelatedItemTopics.filter(items, "space"));
        assertTrue(RelatedItemTopics.filter(items, "unavailable").isEmpty());
        assertEquals(Arrays.asList(title, other, description, uploader),
                RelatedItemTopics.filter(items, ""));
        assertEquals(4, items.size());
    }

    @Test
    public void topicsAndOriginalRecommendationsSurviveStateSerialization() throws Exception {
        final StreamInfo info = stream();
        info.setTags(Collections.singletonList("Space"));
        info.setRelatedItems(Arrays.asList(video("Space news"), video("Cooking")));
        final RelatedItemsInfo related = new RelatedItemsInfo(info);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(related);
        }
        final RelatedItemsInfo restored;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (RelatedItemsInfo) input.readObject();
        }
        assertEquals(Collections.singletonList("Space"), restored.getTopics());
        assertEquals(1, RelatedItemTopics.filter(restored.getRelatedItems(), "Space").size());
        assertEquals(2, restored.getRelatedItems().size());
        assertEquals("Cooking", restored.getRelatedItems().get(1).getName());
    }

    private static StreamInfo stream() {
        return new StreamInfo(0, "test", "https://example.org/video", "Video");
    }

    private static StreamInfoItem video(final String title) {
        return new StreamInfoItem(0, "https://example.org/" + title, title,
                StreamType.VIDEO_STREAM);
    }
}
