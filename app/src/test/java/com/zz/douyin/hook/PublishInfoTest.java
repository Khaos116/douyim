package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.TimeZone;
import java.util.regex.Pattern;

public final class PublishInfoTest {
    private static final Pattern STAMP =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");

    @Test
    public void formatTimeRendersMinutePrecision() {
        String rendered = PublishInfo.formatTime(
                1758000000000L, TimeZone.getTimeZone("GMT"));

        assertTrue(STAMP.matcher(rendered).matches());
    }

    @Test
    public void formatTimeRespectsTimeZone() {
        String gmt = PublishInfo.formatTime(1758000000000L, TimeZone.getTimeZone("GMT"));
        String plusEight = PublishInfo.formatTime(
                1758000000000L, TimeZone.getTimeZone("GMT+8"));

        assertTrue(!gmt.equals(plusEight));
    }

    @Test
    public void formatTimeRejectsBadInput() {
        assertEquals("", PublishInfo.formatTime(0L, TimeZone.getTimeZone("GMT")));
        assertEquals("", PublishInfo.formatTime(-1L, TimeZone.getTimeZone("GMT")));
        assertEquals("", PublishInfo.formatTime(1758000000000L, null));
    }

    @Test
    public void composeTextNeedsSnapshotWithTime() {
        assertNull(PublishInfo.composeText(null));
    }
}
