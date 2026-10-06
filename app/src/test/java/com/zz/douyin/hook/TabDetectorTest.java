package com.zz.douyin.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TabDetectorTest {
    @Test
    public void liveTabLabelMatchesExactText() {
        assertTrue(TabDetector.isLiveTabLabel("直播"));
        assertTrue(TabDetector.isLiveTabLabel("  直播  "));
    }

    @Test
    public void liveTabLabelRejectsOthers() {
        assertFalse(TabDetector.isLiveTabLabel(null));
        assertFalse(TabDetector.isLiveTabLabel(""));
        assertFalse(TabDetector.isLiveTabLabel("推荐"));
        assertFalse(TabDetector.isLiveTabLabel("直播中"));
        assertFalse(TabDetector.isLiveTabLabel("看直播"));
    }

    @Test
    public void liveTabRejectsNullDecor() {
        assertFalse(TabDetector.isLiveTab(null));
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
    public void recognizesTheHostsSelectedLiveTabDescription() {
        assertEquals(Boolean.TRUE, TabDetector.liveTabDescriptionSelection("已选中，直播，按钮"));
        assertEquals(Boolean.TRUE, TabDetector.liveTabDescriptionSelection(" 已选中, 直播, 按钮 "));
    }

    @Test
    public void descriptionRequiresBothExactLiveAndSelectedTokens() {
        assertEquals(null, TabDetector.liveTabDescriptionSelection(null));
        assertEquals(Boolean.FALSE, TabDetector.liveTabDescriptionSelection("直播，按钮"));
        assertEquals(Boolean.FALSE, TabDetector.liveTabDescriptionSelection("未选中，直播，按钮"));
        assertEquals(null, TabDetector.liveTabDescriptionSelection("已选中，推荐，按钮"));
        assertEquals(null, TabDetector.liveTabDescriptionSelection("已选中，直播中，按钮"));
        assertEquals(null, TabDetector.liveTabDescriptionSelection("点击进入直播间按钮"));
    }
}
