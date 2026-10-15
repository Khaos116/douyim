package com.zz.douyin.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TabDetectorTest {
    @Test
    public void recommendationTabLabelMatchesExactText() {
        assertTrue(TabDetector.isRecommendationTabLabel("推荐"));
        assertTrue(TabDetector.isRecommendationTabLabel("  推荐  "));
    }

    @Test
    public void recommendationTabLabelRejectsOthers() {
        assertFalse(TabDetector.isRecommendationTabLabel(null));
        assertFalse(TabDetector.isRecommendationTabLabel(""));
        assertFalse(TabDetector.isRecommendationTabLabel("直播"));
        assertFalse(TabDetector.isRecommendationTabLabel("推荐视频"));
        assertFalse(TabDetector.isRecommendationTabLabel("猜你喜欢"));
    }

    @Test
    public void recommendationTabRejectsNullDecor() {
        assertFalse(TabDetector.isRecommendationTab(null));
    }

    @Test
    public void candidateMatchesSelectedTopStripView() {
        assertTrue(TabDetector.matchesCandidate(100, 2000, 120, 60, true, true, false));
        assertTrue(TabDetector.matchesCandidate(100, 2000, 120, 60, true, false, true));
    }

    @Test
    public void candidateRejectsBadGeometryOrDetached() {
        assertFalse(TabDetector.matchesCandidate(600, 2000, 120, 60, true, true, false));
        assertFalse(TabDetector.matchesCandidate(-10, 2000, 120, 60, true, true, false));
        assertFalse(TabDetector.matchesCandidate(0, 2000, 0, 0, true, true, false));
        assertFalse(TabDetector.matchesCandidate(100, 2000, 120, 60, false, true, false));
        assertFalse(TabDetector.matchesCandidate(100, 0, 120, 60, true, true, false));
    }

    @Test
    public void candidateRequiresSelection() {
        assertFalse(TabDetector.matchesCandidate(100, 2000, 120, 60, true, false, false));
    }

    @Test
    public void selectionMustBelongToAnIndividualTabItem() {
        assertTrue(TabDetector.isTabSelectionOwner(120, 60, 1080, 2000));
        assertFalse(TabDetector.isTabSelectionOwner(1080, 120, 1080, 2000));
        assertFalse(TabDetector.isTabSelectionOwner(1080, 2000, 1080, 2000));
        assertFalse(TabDetector.isTabSelectionOwner(0, 0, 1080, 2000));
    }

    @Test
    public void recognizesTheHostsSelectedRecommendationTabDescription() {
        assertEquals(Boolean.TRUE, TabDetector.recommendationTabDescriptionSelection("已选中，推荐，按钮"));
        assertEquals(Boolean.TRUE, TabDetector.recommendationTabDescriptionSelection(" 已选中, 推荐, 按钮 "));
    }

    @Test
    public void descriptionRequiresBothExactRecommendationAndSelectedTokens() {
        assertEquals(null, TabDetector.recommendationTabDescriptionSelection(null));
        assertEquals(Boolean.FALSE, TabDetector.recommendationTabDescriptionSelection("推荐，按钮"));
        assertEquals(Boolean.FALSE, TabDetector.recommendationTabDescriptionSelection("未选中，推荐，按钮"));
        assertEquals(null, TabDetector.recommendationTabDescriptionSelection("已选中，直播，按钮"));
        assertEquals(null, TabDetector.recommendationTabDescriptionSelection("已选中，推荐视频，按钮"));
        assertEquals(null, TabDetector.recommendationTabDescriptionSelection("点击进入直播间按钮"));
    }
}
