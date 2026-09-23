package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FeedUiStyleTest {
    @Test
    public void parseColorAcceptsSixDigits() {
        assertEquals(0xFFFF5722, FeedUiStyle.parseColor("#FF5722", 0));
        assertEquals(0xFFFF5722, FeedUiStyle.parseColor("FF5722", 0));
        assertEquals(0xFFFF5722, FeedUiStyle.parseColor("  #ff5722  ", 0));
    }

    @Test
    public void parseColorExpandsThreeDigits() {
        assertEquals(0xFFFFFFFF, FeedUiStyle.parseColor("#FFF", 0));
        assertEquals(0xFF000000, FeedUiStyle.parseColor("000", 0));
    }

    @Test
    public void parseColorAcceptsEightDigits() {
        assertEquals(0x80FF5722, FeedUiStyle.parseColor("#80FF5722", 0));
    }

    @Test
    public void parseColorRejectsBadInput() {
        assertEquals(7, FeedUiStyle.parseColor(null, 7));
        assertEquals(7, FeedUiStyle.parseColor("", 7));
        assertEquals(7, FeedUiStyle.parseColor("#", 7));
        assertEquals(7, FeedUiStyle.parseColor("#GGGGGG", 7));
        assertEquals(7, FeedUiStyle.parseColor("#FFFFF", 7));
        assertEquals(7, FeedUiStyle.parseColor("red", 7));
    }

    @Test
    public void isValidColorMatchesParse() {
        assertTrue(FeedUiStyle.isValidColor("#12AB34"));
        assertTrue(FeedUiStyle.isValidColor("#abc"));
        assertFalse(FeedUiStyle.isValidColor(null));
        assertFalse(FeedUiStyle.isValidColor(""));
        assertFalse(FeedUiStyle.isValidColor("#12AB3"));
    }

    @Test
    public void toHexRoundTrips() {
        assertEquals("#FF5722", FeedUiStyle.toHex(0xFFFF5722));
        assertEquals("#FFFFFF", FeedUiStyle.toHex(0xFFFFFFFF));
        assertEquals(
                "#FF5722",
                FeedUiStyle.toHex(FeedUiStyle.parseColor("#FF5722", 0))
        );
    }
}
