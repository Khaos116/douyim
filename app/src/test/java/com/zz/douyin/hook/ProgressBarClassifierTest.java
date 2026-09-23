package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ProgressBarClassifierTest {
    @Test
    public void bottomWideThinProgressBarMatches() {
        assertTrue(ProgressBarClassifier.isProgressBar(
                "com.ss.android.ugc.aweme.feed.ui.VideoProgressBar",
                1000, 12, 2100, 1080, 2280));
        assertTrue(ProgressBarClassifier.isProgressBar(
                "android.widget.SeekBar",
                900, 20, 2000, 1080, 2280));
    }

    @Test
    public void nonProgressClassesRejected() {
        assertFalse(ProgressBarClassifier.isCandidateClass(null));
        assertFalse(ProgressBarClassifier.isCandidateClass(
                "android.widget.FrameLayout"));
        assertFalse(ProgressBarClassifier.isProgressBar(
                "android.widget.FrameLayout",
                1000, 12, 2100, 1080, 2280));
    }

    @Test
    public void decomposedChecksAgreeWithMatcher() {
        assertTrue(ProgressBarClassifier.matchesProgressSize(1000, 12, 1080, 2280));
        assertTrue(ProgressBarClassifier.isBottomStrip(2100, 2280));
        assertFalse(ProgressBarClassifier.matchesProgressSize(400, 12, 1080, 2280));
        assertFalse(ProgressBarClassifier.matchesProgressSize(1000, 200, 1080, 2280));
        assertFalse(ProgressBarClassifier.isBottomStrip(500, 2280));
    }

    @Test
    public void rejectStageNamesFirstFailingCheck() {
        assertEquals("", ProgressBarClassifier.rejectStage(
                "android.widget.SeekBar", 1000, 12, 2100, 1080, 2280));
        assertEquals("class", ProgressBarClassifier.rejectStage(
                "android.widget.FrameLayout", 1000, 12, 2100, 1080, 2280));
        assertEquals("width", ProgressBarClassifier.rejectStage(
                "android.widget.SeekBar", 400, 12, 2100, 1080, 2280));
        assertEquals("height", ProgressBarClassifier.rejectStage(
                "android.widget.SeekBar", 1000, 200, 2100, 1080, 2280));
        assertEquals("top", ProgressBarClassifier.rejectStage(
                "android.widget.SeekBar", 1000, 12, 500, 1080, 2280));
    }

    @Test
    public void forceVisibleOnlyOnKnownEqualAid() {
        assertTrue(ProgressBarClassifier.shouldForceVisible("a1", "a1"));
        assertFalse(ProgressBarClassifier.shouldForceVisible("a1", "a2"));
        assertFalse(ProgressBarClassifier.shouldForceVisible("a1", null));
        assertFalse(ProgressBarClassifier.shouldForceVisible(null, "a1"));
        assertFalse(ProgressBarClassifier.shouldForceVisible("", ""));
        assertFalse(ProgressBarClassifier.shouldForceVisible("unknown", "unknown"));
        assertFalse(ProgressBarClassifier.shouldForceVisible("unknown", "a1"));
    }

    @Test
    public void wrongGeometryRejected() {
        assertFalse(ProgressBarClassifier.isProgressBar(
                "android.widget.ProgressBar",
                100, 100, 1100, 1080, 2280));
        assertFalse(ProgressBarClassifier.isProgressBar(
                "android.widget.ProgressBar",
                1000, 12, 500, 1080, 2280));
        assertFalse(ProgressBarClassifier.isProgressBar(
                "android.widget.ProgressBar",
                400, 12, 2100, 1080, 2280));
        assertFalse(ProgressBarClassifier.isProgressBar(
                "android.widget.ProgressBar",
                1000, 200, 2100, 1080, 2280));
    }
}
