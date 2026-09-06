package org.schabi.newpipe.extractor.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ExtractorUtilsKotlinInteropTest {
    @Test
    public void htmlParserPreservesNullAndLineBreakBehavior() {
        assertNull(HtmlParser.htmlToString(null));
        assertEquals(
                "a\nb\nc",
                HtmlParser.htmlToString("<b>a</b><br>b<BR/>c"));
    }

    @Test
    public void regexUtilsReturnsFirstMatchOrNull() {
        assertEquals("123", RegexUtils.extract("abc123def456", "\\d+"));
        assertNull(RegexUtils.extract("abcdef", "\\d+"));
    }
}
