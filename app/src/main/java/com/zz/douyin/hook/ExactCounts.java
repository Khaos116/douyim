package com.zz.douyin.hook;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Rewrites abbreviated feed counters ("1.2万") with exact values from the
 * Aweme statistics model.
 *
 * <p>Safety rules: only ordinary videos (no ads/live/photo), only views in the
 * right rail whose current text parses to a number near the model value, and
 * originals are restored whenever the video changes or the feature is off.
 * Everything rewrites or skips is logged so mismatches are visible in the log
 * viewer instead of silently showing a wrong number.
 */
final class ExactCounts {
    private static final String[] COUNTER_NAMES = {"digg", "comment", "collect"};
    private static final double RAIL_FRACTION = 0.65;

    private static final Map<TextView, String> ORIGINALS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final int[] LOCATION = new int[2];
    private static String lastAid;

    private ExactCounts() {
    }

    static void apply(View decor) {
        FeedContentTracker.Snapshot snapshot;
        try {
            snapshot = FeedContentTracker.current(decor);
        } catch (RuntimeException failed) {
            return;
        }
        if (snapshot == null
                || snapshot.isAdvertisement()
                || snapshot.live
                || !snapshot.hasVideo) {
            restoreAll();
            lastAid = null;
            return;
        }
        if (!snapshot.aid.equals(lastAid)) {
            restoreAll();
            lastAid = snapshot.aid;
        }
        List<TextView> candidates = new ArrayList<>();
        collectNumberViews(decor, decor.getWidth(), candidates);
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort((left, right) -> {
            left.getLocationOnScreen(LOCATION);
            int leftY = LOCATION[1];
            right.getLocationOnScreen(LOCATION);
            int rightY = LOCATION[1];
            return Integer.compare(leftY, rightY);
        });
        long[] values = {snapshot.diggCount, snapshot.commentCount, snapshot.collectCount};
        List<TextView> used = new ArrayList<>(values.length);
        for (int index = 0; index < values.length; index++) {
            long exact = values[index];
            if (exact < 0) {
                continue;
            }
            TextView best = null;
            for (TextView view : candidates) {
                if (used.contains(view)) {
                    continue;
                }
                String shown = view.getText().toString();
                if (shown.equals(String.valueOf(exact))) {
                    best = view;
                    break;
                }
                if (matchesAbbreviation(shown, exact)) {
                    best = view;
                    break;
                }
            }
            if (best == null) {
                LogBook.d("[Counts] no view matched " + COUNTER_NAMES[index]
                        + "=" + exact + " aid=" + snapshot.aid);
                continue;
            }
            used.add(best);
            String exactText = String.valueOf(exact);
            if (best.getText().toString().equals(exactText)) {
                continue;
            }
            if (!ORIGINALS.containsKey(best)) {
                ORIGINALS.put(best, best.getText().toString());
            }
            best.setText(exactText);
            LogBook.i("[Counts] aid=" + snapshot.aid + " " + COUNTER_NAMES[index]
                    + " " + ORIGINALS.get(best) + "->" + exactText);
        }
    }

    static void restoreAll() {
        if (ORIGINALS.isEmpty()) {
            return;
        }
        List<Map.Entry<TextView, String>> entries =
                new ArrayList<>(ORIGINALS.entrySet());
        ORIGINALS.clear();
        for (Map.Entry<TextView, String> entry : entries) {
            try {
                entry.getKey().setText(entry.getValue());
            } catch (RuntimeException ignored) {
                // A detached view cannot be restored; the next bind fixes it.
            }
        }
    }

    static boolean matchesAbbreviation(String displayed, long exact) {
        long parsed = parseAbbreviated(displayed);
        if (parsed < 0 || exact < 0) {
            return false;
        }
        long tolerance = Math.max(9L, parsed / 20L);
        return Math.abs(parsed - exact) <= tolerance;
    }

    static long parseAbbreviated(String text) {
        if (text == null) {
            return -1L;
        }
        String clean = text.trim().replace(",", "").replace("，", "");
        while (clean.endsWith("+")) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        if (clean.isEmpty()) {
            return -1L;
        }
        try {
            char last = clean.charAt(clean.length() - 1);
            if (last == '万' || last == 'w' || last == 'W') {
                double value = Double.parseDouble(clean.substring(0, clean.length() - 1));
                return value < 0 ? -1L : (long) (value * 10000.0);
            }
            if (last == 'k' || last == 'K') {
                double value = Double.parseDouble(clean.substring(0, clean.length() - 1));
                return value < 0 ? -1L : (long) (value * 1000.0);
            }
            for (int index = 0; index < clean.length(); index++) {
                if (!Character.isDigit(clean.charAt(index))) {
                    return -1L;
                }
            }
            return Long.parseLong(clean);
        } catch (NumberFormatException failed) {
            return -1L;
        }
    }

    private static void collectNumberViews(View root, int width, List<TextView> out) {
        if (root instanceof TextView text) {
            CharSequence content = text.getText();
            if (content != null
                    && content.length() > 0
                    && content.length() <= 12
                    && parseAbbreviated(content.toString()) >= 0
                    && isInRightRail(text, width)) {
                out.add(text);
            }
        }
        if (root instanceof ViewGroup group) {
            for (int index = 0, count = group.getChildCount(); index < count; index++) {
                collectNumberViews(group.getChildAt(index), width, out);
            }
        }
    }

    private static boolean isInRightRail(TextView view, int width) {
        if (width <= 0) {
            return true;
        }
        view.getLocationOnScreen(LOCATION);
        return LOCATION[0] + view.getWidth() / 2.0 > width * RAIL_FRACTION;
    }
}
