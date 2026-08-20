package org.schabi.newpipe.dearrow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DeArrowServiceTest {
    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    @Test
    public void parseBrandingAcceptsNonNegativeVotes() throws Exception {
        final String json = "{\"titles\":[{\"title\":\"A descriptive title\","
                + "\"original\":false,\"votes\":0,\"locked\":false}],"
                + "\"thumbnails\":[{\"timestamp\":12.5,\"original\":false,"
                + "\"votes\":3,\"locked\":false}]}";

        final DeArrowService.Branding branding =
                DeArrowService.parseBranding(VIDEO_ID, json);

        assertEquals("A descriptive title", branding.getTitle());
        assertEquals(DeArrowService.THUMBNAIL_ENDPOINT + "?videoID=" + VIDEO_ID + "&time=12.5",
                branding.getThumbnailUrl());
    }

    @Test
    public void parseBrandingRejectsNegativeVotes() throws Exception {
        final String json = "{\"titles\":[{\"title\":\"Rejected\",\"original\":false,"
                + "\"votes\":-1,\"locked\":false}],\"thumbnails\":[{\"timestamp\":2,"
                + "\"original\":false,\"votes\":-2,\"locked\":false}]}";

        final DeArrowService.Branding branding =
                DeArrowService.parseBranding(VIDEO_ID, json);

        assertNull(branding.getTitle());
        assertNull(branding.getThumbnailUrl());
    }

    @Test
    public void parseBrandingAcceptsLockedSubmission() throws Exception {
        final String json = "{\"titles\":[{\"title\":\"Locked title\",\"original\":false,"
                + "\"votes\":-4,\"locked\":true}],\"thumbnails\":[]}";

        assertEquals("Locked title",
                DeArrowService.parseBranding(VIDEO_ID, json).getTitle());
    }

    @Test
    public void parseBrandingKeepsOriginalSubmissionsUnchanged() throws Exception {
        final String json = "{\"titles\":[{\"title\":\"Original\",\"original\":true,"
                + "\"votes\":10,\"locked\":true}],\"thumbnails\":[{\"original\":true,"
                + "\"votes\":10,\"locked\":true}]}";

        final DeArrowService.Branding branding =
                DeArrowService.parseBranding(VIDEO_ID, json);

        assertNull(branding.getTitle());
        assertNull(branding.getThumbnailUrl());
    }
}
