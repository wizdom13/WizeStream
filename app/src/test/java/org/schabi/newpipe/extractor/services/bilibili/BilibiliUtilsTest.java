package org.schabi.newpipe.extractor.services.bilibili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.Test;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.util.LinkedHashMap;

public class BilibiliUtilsTest {
    @Test
    public void convertsKnownAvAndBvIdentifiersBothWays() {
        assertEquals("BV17x411w7KC", utils.av2bv(170001));
        assertEquals(170001L, utils.bv2av("BV17x411w7KC"));
        assertEquals(455017605L, utils.bv2av(utils.av2bv(455017605L)));
    }

    @Test
    public void identifiesFirstPartFromMissingOrExplicitPageParameter() {
        assertTrue(utils.isFirstP("https://www.bilibili.com/video/BV17x411w7KC"));
        assertTrue(utils.isFirstP("https://www.bilibili.com/video/BV17x411w7KC?p=1"));
        assertFalse(utils.isFirstP("https://www.bilibili.com/video/BV17x411w7KC?p=2"));
    }

    @Test
    public void viewApiUrlPreservesRequestedPart() {
        assertEquals(
                "https://api.bilibili.com/x/web-interface/view?bvid=BV17x411w7KC&p=1",
                utils.getUrl("https://www.bilibili.com/video/BV17x411w7KC", "BV17x411w7KC"));
        assertEquals(
                "https://api.bilibili.com/x/web-interface/view?bvid=BV17x411w7KC&p=3",
                utils.getUrl("https://www.bilibili.com/video/BV17x411w7KC?p=3&share_source=copy_web",
                        "BV17x411w7KC"));
    }

    @Test
    public void stripsQueryFromBvIdentifier() {
        assertEquals("BV17x411w7KC", utils.getPureBV("BV17x411w7KC?p=2"));
        assertEquals("BV17x411w7KC", utils.getPureBV("BV17x411w7KC"));
    }

    @Test
    public void parsesDurationsAndFormatsSubtitleTimestamps() {
        assertEquals(65L, utils.getDurationFromString("1:05"));
        assertEquals(3_723L, utils.getDurationFromString("1:02:03"));
        assertEquals("01:02:03,456", utils.sec2time(3_723.456));
    }

    @Test
    public void convertsBccCaptionsToSrt() throws Exception {
        final JsonObject bcc = JsonParser.object().from(
                "{\"body\":[{\"from\":1.25,\"to\":2.5,\"content\":\"hello\"}]}"
        );

        assertEquals("1\n00:00:01,250 --> 00:00:02,500\nhello\n\n", utils.bcc2srt(bcc));
    }

    @Test
    public void incrementsExistingPaginationParameter() throws Exception {
        assertEquals(
                "https://api.bilibili.com/x/test?pn=3&ps=30",
                utils.getNextPageFromCurrentUrl(
                        "https://api.bilibili.com/x/test?pn=2&ps=30", "pn", 1));
    }

    @Test
    public void canInitializeMissingPaginationParameter() throws Exception {
        assertEquals(
                "https://api.bilibili.com/x/test?pn=2",
                utils.getNextPageFromCurrentUrl(
                        "https://api.bilibili.com/x/test", "pn", 1, true, "1", "?"));
    }

    @Test
    public void missingPaginationParameterFailsClearly() {
        assertThrows(ParsingException.class,
                () -> utils.getNextPageFromCurrentUrl(
                        "https://api.bilibili.com/x/test?ps=30", "pn", 1));
    }

    @Test
    public void percentSpaceEncodingAndQueryOrderStayStable() {
        assertEquals("hello%20world", utils.formatParamWithPercentSpace("hello world"));

        final LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("mid", "123");
        params.put("pn", "2");
        assertEquals("mid=123&pn=2", utils.createQueryString(params));
    }
}
