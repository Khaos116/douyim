package com.zz.douyin.hook;

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
 * a missed guess never sticks. Every hide is logged for user feedback.
 */
final class FeedUiHider {
    private static final double PUBLISH_TOP_FRACTION = 0.80;
    private static final double PUBLISH_CENTER_TOLERANCE = 0.20;
    private static final double PUBLISH_MAX_WIDTH_FRACTION = 0.18;
    private static final double PUBLISH_MAX_HEIGHT_FRACTION = 0.12;
    private static final double TAB_TOP_FRACTION = 0.22;
    private static final int TAB_MAX_TEXT_LENGTH = 12;

    private static final Map<View, Integer> PUBLISH_HIDDEN =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> TAB_HIDDEN =
            Collections.synchronizedMap(new WeakHashMap<>());
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
        if (decorWidth <= 0 || decorHeight <= 0 || width <= 0 || height <= 0) {
            return false;
        }
        if (top < decorHeight * PUBLISH_TOP_FRACTION) {
            return false;
        }
        if (Math.abs(centerX - decorWidth / 2.0) > decorWidth * PUBLISH_CENTER_TOLERANCE) {
            return false;
        }
        if (width > decorWidth * PUBLISH_MAX_WIDTH_FRACTION
                || height > decorHeight * PUBLISH_MAX_HEIGHT_FRACTION) {
            return false;
        }
        return clickable || descHasPublish;
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
            boolean descHasPublish = description != null
                    && description.toString().contains("发布");
            if (isPublishCandidate(
                    centerX,
                    LOCATION[1],
                    node.getWidth(),
                    node.getHeight(),
                    decorWidth,
                    decorHeight,
                    node.isClickable(),
                    descHasPublish
            )) {
                hideInto(PUBLISH_HIDDEN, node, "publish");
            }
        }
        if (node instanceof ViewGroup group) {
            for (int index = 0, count = group.getChildCount(); index < count; index++) {
                collectPublishCandidates(group.getChildAt(index), decorWidth, decorHeight);
            }
        }
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
        synchronized (hidden) {
            if (hidden.containsKey(view)) {
                return;
            }
            hidden.put(view, view.getVisibility());
        }
        view.setVisibility(View.GONE);
        LogBook.i("[FeedUi] hide " + what + " view=" + view.getClass().getName());
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
