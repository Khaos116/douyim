package com.zz.douyin.hook;

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
