package com.zz.douyin.hook;

import static org.junit.Assert.assertFalse;
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
}
