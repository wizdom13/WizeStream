package org.schabi.newpipe.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.List;

public class InvidiousInstancesTest {
    @Test
    public void normalizesAndDeduplicatesInstancesWithoutChangingOrder() {
        assertEquals(List.of("https://one.example", "https://two.example:8443"),
                InvidiousInstances.parse(" HTTPS://ONE.EXAMPLE/ \r\n\n"
                        + "https://two.example:8443\nhttps://one.example"));
    }

    @Test
    public void rejectsAnInvalidEntryInsteadOfPartiallySaving() {
        for (final String invalid : List.of("http://bad.example", "https://youtube.com",
                "https://bad.example/api", "https://user:password@bad.example")) {
            assertThrows(IllegalArgumentException.class,
                    () -> InvidiousInstances.parse("https://one.example\n" + invalid));
        }
    }

    @Test
    public void restoredChoicesRetainTheActiveInstanceAndSkipMalformedEntries() {
        assertEquals(List.of("https://one.example", "https://active.example"),
                InvidiousInstances.choices("https://one.example\ncorrupt\nhttp://bad.example",
                        "https://active.example/"));
        assertEquals(List.of(), InvidiousInstances.parse("\n \r\n"));
    }
}
