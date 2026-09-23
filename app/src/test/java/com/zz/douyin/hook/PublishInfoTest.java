package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
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
        assertNull(PublishInfo.composeText(null, true, true));
        assertNull(PublishInfo.composeText(null, false, false));
    }

    @Test
    public void composeTextRendersTimeIpAndPlace() {
        FeedContentTracker.Snapshot snapshot = snapshot(
                1758000000000L, "四川", "春熙路", "", "");

        String expected = "发布于 "
                + PublishInfo.formatTime(1758000000000L, TimeZone.getDefault())
                + "\nIP属地：四川\n地点：春熙路";

        assertEquals(expected, PublishInfo.composeText(snapshot, true, true));
    }

    @Test
    public void composeTextWithoutAnythingReturnsNull() {
        FeedContentTracker.Snapshot snapshot = snapshot(-1L, "", "", "", "");

        assertNull(PublishInfo.composeText(snapshot, true, true));
    }

    @Test
    public void composeTextHonorsSwitches() {
        FeedContentTracker.Snapshot snapshot = snapshot(
                1758000000000L, "四川", "春熙路", "", "");

        String locationOnly = PublishInfo.composeText(snapshot, false, true);
        assertTrue(locationOnly.startsWith("IP属地：四川"));

        String timeOnly = PublishInfo.composeText(snapshot, true, false);
        assertTrue(timeOnly.startsWith("发布于 "));
        assertTrue(!timeOnly.contains("IP属地"));
        assertTrue(!timeOnly.contains("地点"));

        assertNull(PublishInfo.composeText(snapshot, false, false));
    }

    @Test
    public void composeTextHidesAdvertisement() {
        FeedContentTracker.Snapshot ad = new FeedContentTracker.Snapshot(
                "ad-1", 0, true,
                true, false, false,
                0, 0, false, false, false, false,
                "", "",
                -1L, -1L, -1L, -1L, -1L,
                1758000000000L, "四川", "春熙路", "", "",
                null, Collections.emptyList());

        assertNull(PublishInfo.composeText(ad, true, true));
    }

    @Test
    public void composeOverlayMarksTimeAndLocationRanges() {
        FeedContentTracker.Snapshot snapshot = snapshot(
                1758000000000L, "四川", "春熙路", "", "");

        PublishInfo.OverlayContent content =
                PublishInfo.composeOverlay(snapshot, true, true);

        String timeLine = "发布于 "
                + PublishInfo.formatTime(1758000000000L, TimeZone.getDefault());
        assertEquals(0, content.timeStart);
        assertEquals(timeLine.length(), content.timeEnd);
        assertEquals(timeLine.length() + 1, content.locationStart);
        assertEquals(content.text.length(), content.locationEnd);
        assertEquals(
                "IP属地：四川\n地点：春熙路",
                content.text.substring(content.locationStart, content.locationEnd)
        );
    }

    @Test
    public void composeOverlayWithoutTimeStartsLocationAtZero() {
        FeedContentTracker.Snapshot snapshot = snapshot(
                -1L, "四川", "", "", "");

        PublishInfo.OverlayContent content =
                PublishInfo.composeOverlay(snapshot, true, true);

        assertEquals(-1, content.timeStart);
        assertEquals(0, content.locationStart);
        assertEquals(content.text.length(), content.locationEnd);
    }

    @Test
    public void composeOverlayWithoutLocationLeavesRangeEmpty() {
        FeedContentTracker.Snapshot snapshot = snapshot(
                1758000000000L, "", "", "", "");

        PublishInfo.OverlayContent content =
                PublishInfo.composeOverlay(snapshot, true, true);

        assertEquals(0, content.timeStart);
        assertEquals(-1, content.locationStart);
        assertEquals(-1, content.locationEnd);
    }

    @Test
    public void composeOverlayReturnsNullWhenEmpty() {
        assertNull(PublishInfo.composeOverlay(null, true, true));
        assertNull(PublishInfo.composeOverlay(
                snapshot(-1L, "", "", "", ""), true, true));
    }

    @Test
    public void displayPlacePrefersPoiOverLocationOverCity() {
        assertEquals("春熙路", PublishInfo.displayPlace(
                snapshot(-1L, "", "春熙路", "太古里", "成都市")));
        assertEquals("太古里", PublishInfo.displayPlace(
                snapshot(-1L, "", "", "太古里", "成都市")));
        assertEquals("成都市", PublishInfo.displayPlace(
                snapshot(-1L, "", "", "", "成都市")));
        assertEquals("", PublishInfo.displayPlace(
                snapshot(-1L, "", "", "", "")));
        assertEquals("", PublishInfo.displayPlace(null));
    }

    private static FeedContentTracker.Snapshot snapshot(
            long createTimeMs,
            String ipLabel,
            String poiName,
            String location,
            String city
    ) {
        return new FeedContentTracker.Snapshot(
                "aid-1", 0, true,
                false, false, false,
                0, 0, false, false, false, false,
                "", "",
                -1L, -1L, -1L, -1L, -1L,
                createTimeMs, ipLabel, poiName, location, city,
                null, Collections.emptyList());
    }
}
