package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class FeedUiHiderTest {
    @Test
    public void publishCandidateNeedsBottomCenterSmallClickable() {
        assertTrue(FeedUiHider.isPublishCandidate(
                540, 1920, 120, 120, 1080, 2280, true, false));
    }

    @Test
    public void publishCandidateAcceptsDescriptionSignal() {
        assertTrue(FeedUiHider.isPublishCandidate(
                540, 1920, 120, 120, 1080, 2280, false, true));
    }

    @Test
    public void publishCandidateRejectsWrongGeometry() {
        assertFalse(FeedUiHider.isPublishCandidate(
                540, 1000, 120, 120, 1080, 2280, true, false));
        assertFalse(FeedUiHider.isPublishCandidate(
                100, 1920, 120, 120, 1080, 2280, true, false));
        assertFalse(FeedUiHider.isPublishCandidate(
                540, 1920, 900, 120, 1080, 2280, true, false));
        assertFalse(FeedUiHider.isPublishCandidate(
                540, 1920, 120, 600, 1080, 2280, true, false));
        assertFalse(FeedUiHider.isPublishCandidate(
                540, 1920, 120, 120, 1080, 2280, false, false));
        assertFalse(FeedUiHider.isPublishCandidate(
                540, 1920, 0, 120, 1080, 2280, true, false));
    }

    @Test
    public void publishGeometryAndSignalSplitMatchesCandidate() {
        assertTrue(FeedUiHider.isPublishGeometry(540, 1920, 120, 120, 1080, 2280));
        assertTrue(FeedUiHider.isPublishSignal(true, false));
        assertTrue(FeedUiHider.isPublishSignal(false, true));
        assertFalse(FeedUiHider.isPublishGeometry(540, 1000, 120, 120, 1080, 2280));
        assertFalse(FeedUiHider.isPublishGeometry(100, 1920, 120, 120, 1080, 2280));
        assertFalse(FeedUiHider.isPublishGeometry(540, 1920, 900, 120, 1080, 2280));
        assertFalse(FeedUiHider.isPublishGeometry(540, 1920, 120, 600, 1080, 2280));
        assertFalse(FeedUiHider.isPublishSignal(false, false));
    }

    @Test
    public void tabKeywordMatchesShortText() {
        List<String> keywords = List.of("商城", "精选");

        assertTrue(FeedUiHider.matchesTabKeyword("商城", keywords));
        assertTrue(FeedUiHider.matchesTabKeyword("  精选  ", keywords));
        assertFalse(FeedUiHider.matchesTabKeyword("推荐", keywords));
    }

    @Test
    public void tabKeywordIgnoresLatinCase() {
        assertTrue(FeedUiHider.matchesTabKeyword(
                "LIVE", List.of("live")));
    }

    @Test
    public void tabKeywordRejectsLongOrBlankText() {
        List<String> keywords = List.of("商城");

        assertFalse(FeedUiHider.matchesTabKeyword(
                "这是一段很长的视频描述文字商城", keywords));
        assertFalse(FeedUiHider.matchesTabKeyword("   ", keywords));
        assertFalse(FeedUiHider.matchesTabKeyword(null, keywords));
        assertFalse(FeedUiHider.matchesTabKeyword("商城", Collections.emptyList()));
        assertFalse(FeedUiHider.matchesTabKeyword("商城", null));
    }

    @Test
    public void parseKeywordsSplitsAndDedupes() {
        assertEquals(
                List.of("商城", "精选"),
                FeedUiHider.parseKeywords("商城\n精选，商城；精选")
        );
        assertEquals(
                Collections.emptyList(),
                FeedUiHider.parseKeywords("  \n，")
        );
        assertEquals(Collections.emptyList(), FeedUiHider.parseKeywords(null));
    }
}
