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
    public void tabItemSizeBoundsSingleTab() {
        assertTrue(FeedUiHider.isTabItemSize(216, 140, 1080, 2280));
        assertTrue(FeedUiHider.isTabItemSize(120, 120, 1080, 2280));
        assertFalse(FeedUiHider.isTabItemSize(1080, 140, 1080, 2280));
        assertFalse(FeedUiHider.isTabItemSize(216, 600, 1080, 2280));
        assertFalse(FeedUiHider.isTabItemSize(0, 140, 1080, 2280));
        assertFalse(FeedUiHider.isTabItemSize(216, 140, 0, 2280));
    }

    @Test
    public void publishDescAreaIsBottomCenter() {
        assertTrue(FeedUiHider.isPublishDescArea(540, 2100, 1080, 2280));
        assertFalse(FeedUiHider.isPublishDescArea(540, 1500, 1080, 2280));
        assertFalse(FeedUiHider.isPublishDescArea(100, 2100, 1080, 2280));
        assertFalse(FeedUiHider.isPublishDescArea(540, 2100, 0, 2280));
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
    public void publishClassNameMatchesTokens() {
        assertTrue(FeedUiHider.isPublishClassName(
                "com.ss.android.ugc.aweme.main.hometab.bottom.PublishButton"));
        assertTrue(FeedUiHider.isPublishClassName(
                "com.ss.android.ugc.aweme.main.MainPublishTab"));
        assertFalse(FeedUiHider.isPublishClassName(
                "com.ss.android.ugc.aweme.main.MainBottomTabContainer"));
        assertFalse(FeedUiHider.isPublishClassName("android.widget.LinearLayout"));
        assertFalse(FeedUiHider.isPublishClassName(null));
    }

    @Test
    public void bottomTabClassNameMatchesContainerTokens() {
        assertTrue(FeedUiHider.isBottomTabClassName(
                "com.ss.android.ugc.aweme.main.MainBottomTabContainer"));
        assertTrue(FeedUiHider.isBottomTabClassName(
                "com.ss.android.ugc.aweme.main.hometab.HomeTabView"));
        assertFalse(FeedUiHider.isBottomTabClassName(
                "android.widget.FrameLayout"));
        assertFalse(FeedUiHider.isBottomTabClassName(null));
    }

    @Test
    public void matchesPublishClassWalksSuperclassChain() {
        assertTrue(FeedUiHider.matchesPublishClass(FakePublishButton.class));
        assertTrue(FeedUiHider.matchesPublishClass(FakePublishChild.class));
        assertFalse(FeedUiHider.matchesPublishClass(String.class));
        assertFalse(FeedUiHider.matchesPublishClass(null));
    }

    @Test
    public void publishTabIdAcceptsRawAndMappedForms() {
        assertTrue(FeedUiHider.isPublishTabId("PUBLISH"));
        assertTrue(FeedUiHider.isPublishTabId("homepage_publish"));
        assertFalse(FeedUiHider.isPublishTabId("HOME"));
        assertFalse(FeedUiHider.isPublishTabId("homepage_home"));
        assertFalse(FeedUiHider.isPublishTabId(null));
    }

    @Test
    public void resolveTabIdReadsTabIdAndObfuscatedField() {
        assertEquals("PUBLISH", FeedUiHider.resolveTabId(new FakeTabWithId()));
        assertEquals("homepage_publish", FeedUiHider.resolveTabId(new FakeTabWithObfuscatedId()));
        assertEquals("PUBLISH", FeedUiHider.resolveTabId(new FakeTabChild()));
        assertEquals(null, FeedUiHider.resolveTabId(new Object()));
        assertEquals(null, FeedUiHider.resolveTabId(null));
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

    static class FakePublishButton {
    }

    static final class FakePublishChild extends FakePublishButton {
    }

    static class FakeTabWithId {
        public String tabId = "PUBLISH";
    }

    static final class FakeTabChild extends FakeTabWithId {
    }

    static final class FakeTabWithObfuscatedId {
        public String LIZJ = "homepage_publish";
    }
}
