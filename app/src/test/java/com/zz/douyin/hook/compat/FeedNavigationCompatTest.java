package com.zz.douyin.hook.compat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FeedNavigationCompatTest {
    @Test
    public void recognizesTheLiveRoomActivityFromRuntimeLogs() {
        assertTrue(FeedNavigationCompat.isLiveRoomActivity(
                "com.ss.android.ugc.aweme.live.LivePlayActivity"));
    }

    @Test
    public void keepsFeedAndVideoDetailNavigationAvailable() {
        assertFalse(FeedNavigationCompat.isLiveRoomActivity(null));
        assertFalse(FeedNavigationCompat.isLiveRoomActivity(""));
        assertFalse(FeedNavigationCompat.isLiveRoomActivity(
                "com.ss.android.ugc.aweme.main.MainActivity"));
        assertFalse(FeedNavigationCompat.isLiveRoomActivity(
                "com.ss.android.ugc.aweme.detail.ultra.ui.UltraDetailActivity"));
        assertFalse(FeedNavigationCompat.isLiveRoomActivity(
                "com.example.LivePlayActivity"));
    }
}
