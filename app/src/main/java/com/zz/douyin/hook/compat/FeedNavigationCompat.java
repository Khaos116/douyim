package com.zz.douyin.hook.compat;

public final class FeedNavigationCompat {
    private FeedNavigationCompat() {
    }

    public static boolean isLiveRoomActivity(String activityClassName) {
        // Confirmed by runtime logs; adapt here if the host changes its live-room entry.
        return "com.ss.android.ugc.aweme.live.LivePlayActivity".equals(activityClassName);
    }
}
