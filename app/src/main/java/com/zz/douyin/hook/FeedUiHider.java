package com.zz.douyin.hook;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hides the bottom publish button and top tabs by user keywords.
 *
 * <p>Both finders are intentionally conservative: the publish button must sit
 * in the bottom-center area and be small, and tabs only match short texts in
 * the top strip. Anything hidden is tracked in its own map (never the
 * immersive alpha-based registry) and restored when its switch turns off, so
 * a missed guess never sticks. Every hide is logged for user feedback, and
 * matches are re-hidden while they keep matching, so a host re-show never
 * sticks either.
 */
final class FeedUiHider {
    private static final double PUBLISH_TOP_FRACTION = 0.80;
    private static final double PUBLISH_CENTER_TOLERANCE = 0.20;
    private static final double PUBLISH_MAX_WIDTH_FRACTION = 0.18;
    private static final double PUBLISH_MAX_HEIGHT_FRACTION = 0.12;
    private static final double PUBLISH_DESC_TOP_FRACTION = 0.85;
    private static final double TAB_ITEM_MAX_WIDTH_FRACTION = 0.30;
    private static final double TAB_ITEM_MAX_HEIGHT_FRACTION = 0.20;
    private static final double TAB_TOP_FRACTION = 0.22;
    private static final int TAB_MAX_TEXT_LENGTH = 12;

    private static final Map<View, Integer> PUBLISH_HIDDEN =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> TAB_HIDDEN =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> PUBLISH_MISS_LOGGED =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final long REHIDE_LOG_INTERVAL_MS = 5000L;
    private static long lastRehideLogAt;
    private static final int[] LOCATION = new int[2];

    private FeedUiHider() {
    }

    static void applyPublishHide(View decor, boolean enabled) {
        if (!enabled) {
            restoreMap(PUBLISH_HIDDEN, "publish");
            return;
        }
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) {
            return;
        }
        collectPublishCandidates(decor, decor.getWidth(), decor.getHeight());
    }

    static void applyTabHide(View decor, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            restoreMap(TAB_HIDDEN, "tabs");
            return;
        }
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) {
            return;
        }
        pruneStaleTabs(keywords);
        collectTabCandidates(decor, decor.getHeight(), keywords);
    }

    static void restoreAll() {
        restoreMap(PUBLISH_HIDDEN, "publish");
        restoreMap(TAB_HIDDEN, "tabs");
    }

    static boolean isPublishCandidate(
            int centerX,
            int top,
            int width,
            int height,
            int decorWidth,
            int decorHeight,
            boolean clickable,
            boolean descHasPublish
    ) {
        return isPublishGeometry(
                centerX, top, width, height, decorWidth, decorHeight)
                && isPublishSignal(clickable, descHasPublish);
    }

    static boolean isPublishGeometry(
            int centerX,
            int top,
            int width,
            int height,
            int decorWidth,
            int decorHeight
    ) {
        if (decorWidth <= 0 || decorHeight <= 0 || width <= 0 || height <= 0) {
            return false;
        }
        if (top < decorHeight * PUBLISH_TOP_FRACTION) {
            return false;
        }
        if (Math.abs(centerX - decorWidth / 2.0) > decorWidth * PUBLISH_CENTER_TOLERANCE) {
            return false;
        }
        return width <= decorWidth * PUBLISH_MAX_WIDTH_FRACTION
                && height <= decorHeight * PUBLISH_MAX_HEIGHT_FRACTION;
    }

    static boolean isPublishSignal(boolean clickable, boolean descHasPublish) {
        return clickable || descHasPublish;
    }

    static boolean isTabItemSize(int width, int height, int decorWidth, int decorHeight) {
        if (decorWidth <= 0 || decorHeight <= 0 || width <= 0 || height <= 0) {
            return false;
        }
        return width <= decorWidth * TAB_ITEM_MAX_WIDTH_FRACTION
                && height <= decorHeight * TAB_ITEM_MAX_HEIGHT_FRACTION;
    }

    static boolean isPublishDescArea(int centerX, int top, int decorWidth, int decorHeight) {
        if (decorWidth <= 0 || decorHeight <= 0) {
            return false;
        }
        return top >= decorHeight * PUBLISH_DESC_TOP_FRACTION
                && Math.abs(centerX - decorWidth / 2.0)
                        <= decorWidth * PUBLISH_CENTER_TOLERANCE;
    }

    static boolean matchesTabKeyword(String text, List<String> keywords) {
        if (text == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String clean = text.trim();
        if (clean.isEmpty() || clean.length() > TAB_MAX_TEXT_LENGTH) {
            return false;
        }
        String lower = clean.toLowerCase(java.util.Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty()
                    && lower.contains(keyword.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    static List<String> parseKeywords(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String part : raw.split("[\\r\\n,，;；]+")) {
            String keyword = part.trim();
            if (!keyword.isEmpty()) {
                unique.add(keyword);
            }
        }
        return unique.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(unique));
    }

    private static void collectPublishCandidates(View node, int decorWidth, int decorHeight) {
        if (node.isShown()) {
            node.getLocationOnScreen(LOCATION);
            int centerX = LOCATION[0] + node.getWidth() / 2;
            CharSequence description = node.getContentDescription();
            String desc = description == null ? "" : description.toString();
            boolean descHasPublish = desc.contains("发布");
            boolean clickable = node.isClickable();
            boolean geometry = isPublishGeometry(
                    centerX,
                    LOCATION[1],
                    node.getWidth(),
                    node.getHeight(),
                    decorWidth,
                    decorHeight);
            boolean signal = isPublishSignal(clickable, descHasPublish);
            if (geometry && signal) {
                hideInto(PUBLISH_HIDDEN, node, "publish");
            }
            if (descHasPublish) {
                hidePublishTabItem(node, centerX, LOCATION[1], decorWidth, decorHeight);
            }
            if (!descHasPublish
                    && !(geometry && signal)
                    && (geometry || signal)
                    && PUBLISH_MISS_LOGGED.add(node)) {
                LogBook.i("[FeedUi] publish miss " + node.getClass().getName()
                        + " geo=" + geometry + " sig=" + signal
                        + " w=" + node.getWidth() + " h=" + node.getHeight()
                        + " top=" + LOCATION[1] + " cx=" + centerX
                        + " decor=" + decorWidth + "x" + decorHeight
                        + " clickable=" + clickable
                        + " desc=" + abbreviate(desc));
            }
        }
        if (node instanceof ViewGroup group) {
            for (int index = 0, count = group.getChildCount(); index < count; index++) {
                collectPublishCandidates(group.getChildAt(index), decorWidth, decorHeight);
            }
        }
    }

    /**
     * Hides the whole bottom-tab item anchored by a "发布" description,
     * climbing from the (possibly tiny) described view up to the largest
     * ancestor that still fits a tab item. Both references hide the tab unit
     * rather than the glyph: FreedomPlus hides the X-named wrapper, DYHelper
     * resolves the button class; the size-bounded climb is the DexKit-free
     * equivalent.
     */
    private static void hidePublishTabItem(
            View node, int centerX, int top, int decorWidth, int decorHeight) {
        if (!isPublishDescArea(centerX, top, decorWidth, decorHeight)) {
            logPublishDescMiss(node, "area", decorWidth, decorHeight);
            return;
        }
        View item = node;
        android.view.ViewParent parent = node.getParent();
        while (parent instanceof ViewGroup group) {
            if (!isTabItemSize(
                    group.getWidth(), group.getHeight(), decorWidth, decorHeight)) {
                break;
            }
            item = group;
            parent = group.getParent();
        }
        if (item == node
                && !isTabItemSize(
                        node.getWidth(), node.getHeight(), decorWidth, decorHeight)) {
            logPublishDescMiss(node, "size", decorWidth, decorHeight);
            return;
        }
        hideInto(PUBLISH_HIDDEN, item, "publish-tab");
    }

    private static void logPublishDescMiss(
            View node, String stage, int decorWidth, int decorHeight) {
        if (!PUBLISH_MISS_LOGGED.add(node)) {
            return;
        }
        node.getLocationOnScreen(LOCATION);
        LogBook.i("[FeedUi] publish desc-miss " + node.getClass().getName()
                + " stage=" + stage
                + " w=" + node.getWidth() + " h=" + node.getHeight()
                + " top=" + LOCATION[1]
                + " cx=" + (LOCATION[0] + node.getWidth() / 2)
                + " decor=" + decorWidth + "x" + decorHeight);
    }

    private static String abbreviate(String desc) {
        String clean = desc.replace('\n', ' ').trim();
        return clean.length() <= 12 ? clean : clean.substring(0, 12) + "…";
    }

    private static void collectTabCandidates(
            View node,
            int decorHeight,
            List<String> keywords
    ) {
        if (node instanceof TextView text && node.isShown()) {
            CharSequence content = text.getText();
            String shown = content == null ? "" : content.toString();
            if (matchesTabKeyword(shown, keywords)) {
                node.getLocationOnScreen(LOCATION);
                if (LOCATION[1] < decorHeight * TAB_TOP_FRACTION) {
                    hideInto(TAB_HIDDEN, node, "tab text=" + shown.trim());
                }
            }
        }
        if (node instanceof ViewGroup group) {
            for (int index = 0, count = group.getChildCount(); index < count; index++) {
                collectTabCandidates(group.getChildAt(index), decorHeight, keywords);
            }
        }
    }

    private static void pruneStaleTabs(List<String> keywords) {
        List<View> stale = new ArrayList<>();
        synchronized (TAB_HIDDEN) {
            for (View view : TAB_HIDDEN.keySet()) {
                if (!(view instanceof TextView text)) {
                    continue;
                }
                CharSequence content = text.getText();
                String shown = content == null ? "" : content.toString();
                if (!matchesTabKeyword(shown, keywords)) {
                    stale.add(view);
                }
            }
        }
        for (View view : stale) {
            restoreOne(TAB_HIDDEN, view);
        }
    }

    private static void hideInto(Map<View, Integer> hidden, View view, String what) {
        boolean first;
        synchronized (hidden) {
            first = !hidden.containsKey(view);
            if (first) {
                hidden.put(view, view.getVisibility());
            }
        }
        if (view.getVisibility() == View.GONE) {
            return;
        }
        view.setVisibility(View.GONE);
        if (first) {
            LogBook.i("[FeedUi] hide " + what + " view=" + view.getClass().getName());
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastRehideLogAt >= REHIDE_LOG_INTERVAL_MS) {
            lastRehideLogAt = now;
            LogBook.i("[FeedUi] re-hide " + what + " view=" + view.getClass().getName());
        }
    }

    private static void restoreMap(Map<View, Integer> hidden, String what) {
        List<Map.Entry<View, Integer>> entries;
        synchronized (hidden) {
            if (hidden.isEmpty()) {
                return;
            }
            entries = new ArrayList<>(hidden.entrySet());
            hidden.clear();
        }
        int restored = 0;
        for (Map.Entry<View, Integer> entry : entries) {
            if (restoreValue(entry.getKey(), entry.getValue())) {
                restored++;
            }
        }
        if (restored > 0) {
            LogBook.d("[FeedUi] restored " + what + " views=" + restored);
        }
    }

    private static boolean restoreOne(Map<View, Integer> hidden, View view) {
        Integer visibility;
        synchronized (hidden) {
            visibility = hidden.remove(view);
        }
        return restoreValue(view, visibility);
    }

    private static boolean restoreValue(View view, Integer visibility) {
        if (view == null || visibility == null) {
            return false;
        }
        try {
            view.setVisibility(visibility);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
