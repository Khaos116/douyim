package com.zz.douyin.hook;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Single entry point for every "move to the next feed item" action.
 *
 * <p>Phase 1 extraction: the synthetic-swipe mechanics, debounce, running lock
 * and watchdog moved here verbatim from {@code ImmersiveUi.swipeToNext}; only
 * the free-form reason string became a typed {@link Reason}. Callers must not
 * implement their own next-item swipe.
 */
final class FeedNavigator {
    enum Reason {
        AUTO_PLAY_FINISHED,
        PLAYBACK_ERROR,
        FILTER_AD,
        FILTER_LIVE,
        FILTER_IMAGE,
        FILTER_KEYWORD,
        FILTER_VIDEO,
        FILTER_LONG_VIDEO,
    }

    private static final long SWIPE_DEBOUNCE_MS = 1_500L;
    private static final long SWIPE_WATCHDOG_MS = 1_200L;

    private static Handler mainHandler;
    private static boolean swipeRunning;
    private static long swipeToken;
    private static long lastSwipeAt;

    private FeedNavigator() {
    }

    static boolean isRunning() {
        return swipeRunning;
    }

    static Reason reasonForFilter(String filterReason) {
        if (filterReason == null) {
            return Reason.FILTER_IMAGE;
        }
        if (filterReason.startsWith("advertisement")) {
            return Reason.FILTER_AD;
        }
        if (filterReason.startsWith("live")) {
            return Reason.FILTER_LIVE;
        }
        if (filterReason.startsWith("long video")) {
            return Reason.FILTER_LONG_VIDEO;
        }
        if (filterReason.startsWith("video keyword")) {
            return Reason.FILTER_KEYWORD;
        }
        if (filterReason.startsWith("video type")) {
            return Reason.FILTER_VIDEO;
        }
        return Reason.FILTER_IMAGE;
    }

    static boolean shouldFireAutoNext(String aid, String lastAid, boolean alreadyFired) {
        if (aid == null || aid.isEmpty()) {
            return true;
        }
        if (!aid.equals(lastAid)) {
            return true;
        }
        return !alreadyFired;
    }

    static boolean moveToNext(View decor, Reason reason) {
        if (!ImmersiveUi.isModuleEnabled()) {
            LogBook.d("[FeedNav] moveToNext ignored: module disabled");
            return false;
        }
        long now = SystemClock.uptimeMillis();
        if (swipeRunning) {
            LogBook.d("[FeedNav] moveToNext ignored: swipe in progress");
            return false;
        }
        if (!decor.isAttachedToWindow()) {
            LogBook.d("[FeedNav] moveToNext ignored: decor detached");
            return false;
        }
        if (now - lastSwipeAt < SWIPE_DEBOUNCE_MS) {
            LogBook.d("[FeedNav] moveToNext ignored: debounce");
            return false;
        }
        int width = decor.getWidth();
        int height = decor.getHeight();
        if (width <= 0 || height <= 0) {
            LogBook.w("[FeedNav] moveToNext ignored: bad decor size "
                    + width + "x" + height);
            return false;
        }
        swipeRunning = true;
        long currentSwipeToken = ++swipeToken;
        lastSwipeAt = now;
        PlaybackState.beginAutoSwitch();
        ImmersiveUi.armContentFilter(700L);
        LogBook.i("swipe to next feed item: " + reason);

        final float x = width * 0.5f;
        final float startY = height * 0.72f;
        final float endY = height * 0.24f;
        final long downTime = SystemClock.uptimeMillis();
        try {
            dispatch(decor, downTime, downTime, MotionEvent.ACTION_DOWN, x, startY);
        } catch (Throwable error) {
            finishSwipe(currentSwipeToken, "initial dispatch failed", error);
            return false;
        }
        main().postDelayed(
                () -> finishSwipe(currentSwipeToken, "watchdog timeout", null),
                SWIPE_WATCHDOG_MS
        );

        int steps = 8;
        for (int i = 1; i <= steps; i++) {
            final int step = i;
            main().postDelayed(() -> {
                if (!swipeRunning || currentSwipeToken != swipeToken) {
                    return;
                }
                if (!ImmersiveUi.isModuleEnabled()) {
                    dispatch(decor, downTime, SystemClock.uptimeMillis(),
                            MotionEvent.ACTION_CANCEL, x, startY);
                    finishSwipe(currentSwipeToken, "module disabled", null);
                    return;
                }
                float fraction = step / (float) steps;
                float y = startY + (endY - startY) * fraction;
                int action = step == steps ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
                try {
                    dispatch(
                            decor,
                            downTime,
                            SystemClock.uptimeMillis(),
                            action,
                            x,
                            y
                    );
                } catch (Throwable error) {
                    finishSwipe(currentSwipeToken, "gesture dispatch failed", error);
                    return;
                }
                if (step == steps) {
                    main().postDelayed(
                            () -> finishSwipe(currentSwipeToken, null, null),
                            500L
                    );
                }
            }, i * 22L);
        }
        return true;
    }

    private static void finishSwipe(long token, String reason, Throwable error) {
        if (token != swipeToken || !swipeRunning) {
            return;
        }
        swipeRunning = false;
        if (reason != null) {
            if (error == null) {
                LogBook.w(
                        "synthetic swipe recovered: " + reason);
            } else {
                LogBook.w(
                        "synthetic swipe recovered: " + reason, error);
            }
        }
        ImmersiveUi.scheduleScan(0L);
    }

    private static void dispatch(View view, long downTime, long eventTime,
                                 int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            view.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static Handler main() {
        // Lazily created so JVM unit tests can load this class without Android.
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        return mainHandler;
    }
}
