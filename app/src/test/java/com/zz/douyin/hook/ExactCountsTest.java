package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ExactCountsTest {
    @Test
    public void parseAbbreviatedReadsPlainNumbers() {
        assertEquals(0L, ExactCounts.parseAbbreviated("0"));
        assertEquals(999L, ExactCounts.parseAbbreviated("999"));
        assertEquals(8432L, ExactCounts.parseAbbreviated("8,432"));
        assertEquals(1000L, ExactCounts.parseAbbreviated("1000+"));
    }

    @Test
    public void parseAbbreviatedReadsWanSuffix() {
        assertEquals(12000L, ExactCounts.parseAbbreviated("1.2万"));
        assertEquals(34000L, ExactCounts.parseAbbreviated("3.4万"));
        assertEquals(100000L, ExactCounts.parseAbbreviated("10万+"));
        assertEquals(10000L, ExactCounts.parseAbbreviated("1w"));
    }

    @Test
    public void parseAbbreviatedRejectsNonNumbers() {
        assertEquals(-1L, ExactCounts.parseAbbreviated(null));
        assertEquals(-1L, ExactCounts.parseAbbreviated(""));
        assertEquals(-1L, ExactCounts.parseAbbreviated("分享"));
        assertEquals(-1L, ExactCounts.parseAbbreviated("12:34"));
        assertEquals(-1L, ExactCounts.parseAbbreviated("1.2.3"));
        assertEquals(-1L, ExactCounts.parseAbbreviated("万"));
    }

    @Test
    public void matchesAbbreviationAllowsRoundingError() {
        assertTrue(ExactCounts.matchesAbbreviation("1.2万", 12084L));
        assertTrue(ExactCounts.matchesAbbreviation("1.2万", 11950L));
        assertTrue(ExactCounts.matchesAbbreviation("999", 999L));
        assertFalse(ExactCounts.matchesAbbreviation("1.2万", 13500L));
        assertTrue(ExactCounts.matchesAbbreviation("999", 1000L));
        assertFalse(ExactCounts.matchesAbbreviation("分享", 12000L));
        assertFalse(ExactCounts.matchesAbbreviation("1.2万", -1L));
    }
}
