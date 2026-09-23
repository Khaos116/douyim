package com.zz.douyin.hook;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
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
 * <p>Publish detection is class-first: a view whose class chain names a
 * publish tab/button, or a bottom-tab view whose reflected tab id says
 * publish, is hidden directly. Geometry plus content-description stays as a
 * fallback for hosts where the class names changed. Tabs only match short
 * texts in the top strip. Anything hidden is tracked in its own map (never
 * the immersive alpha-based registry) and restored when its switch turns
 * off, so a missed guess never sticks. Every hide is logged for user
 * feedback, and matches are re-hidden while they keep matching, so a host
 * re-show never sticks either.
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
    private static final String[] PUBLISH_CLASS_TOKENS = {"publishtab", "publishbutton"};
    private static final String[] BOTTOM_TAB_CLASS_TOKENS = {"bottomtab", "hometab"};
    private static final String[] TAB_ID_FIELD_NAMES = {"tabId", "LIZJ"};

    private static final Map<View, HiddenState> PUBLISH_HIDDEN =
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
            restorePublish();
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
        restorePublish();
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

    static boolean isPublishClassName(String name) {
        return containsToken(name, PUBLISH_CLASS_TOKENS);
    }

    static boolean isBottomTabClassName(String name) {
        return containsToken(name, BOTTOM_TAB_CLASS_TOKENS);
    }

    static boolean matchesPublishClass(Class<?> clazz) {
        return matchesClassTokens(clazz, PUBLISH_CLASS_TOKENS);
    }

    static boolean matchesBottomTabClass(Class<?> clazz) {
        return matchesClassTokens(clazz, BOTTOM_TAB_CLASS_TOKENS);
    }

    static boolean isPublishTabId(String raw) {
        return "PUBLISH".equals(raw) || "homepage_publish".equals(raw);
    }

    /**
     * Reads the tab id field ({@code tabId}, obfuscated {@code LIZJ}) off a
     * bottom-tab object, walking superclasses. Returns the raw value or null.
     */
    static String resolveTabId(Object obj) {
        if (obj == null) {
            return null;
        }
        for (Class<?> current = obj.getClass();
                current != null && !current.equals(Object.class);
                current = current.getSuperclass()) {
            Field[] fields;
            try {
                fields = current.getDeclaredFields();
            } catch (RuntimeException ignored) {
                continue;
            }
            for (Field field : fields) {
                if (!isTabIdField(field.getName())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    if (value instanceof String) {
                        return (String) value;
                    }
                } catch (RuntimeException | IllegalAccessException ignored) {
                    // Keep scanning: another field may hold the id.
                }
            }
        }
        return null;
    }

    private static boolean matchesClassTokens(Class<?> clazz, String[] tokens) {
        for (Class<?> current = clazz;
                current != null && !current.equals(Object.class);
                current = current.getSuperclass()) {
            if (containsToken(current.getName(), tokens)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsToken(String name, String[] tokens) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTabIdField(String name) {
        for (String candidate : TAB_ID_FIELD_NAMES) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPublishTabView(View view) {
        if (matchesPublishClass(view.getClass())) {
            return true;
        }
        return matchesBottomTabClass(view.getClass())
                && isPublishTabId(resolveTabId(view));
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
            if (isPublishTabView(node)) {
                hidePublishView(node, "publish-tab");
            } else {
                collectPublishFallback(node, decorWidth, decorHeight);
            }
        }
        if (node instanceof ViewGroup group) {
            for (int index = 0, count = group.getChildCount(); index < count; index++) {
                collectPublishCandidates(group.getChildAt(index), decorWidth, decorHeight);
            }
        }
    }

    private static void collectPublishFallback(View node, int decorWidth, int decorHeight) {
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
            hidePublishView(node, "publish");
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

    /**
     * Hides the whole bottom-tab item anchored by a "发布" description,
     * climbing from the (possibly tiny) described view up to the largest
     * ancestor that still fits a tab item. This is only the fallback for
     * hosts where the publish class names changed; normally the class/tab-id
     * path hides the tab directly.
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
        hidePublishView(item, "publish-tab");
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

    /**
     * Everything a publish hide touches, so switching the toggle off
     * restores the tab exactly. Layout size uses MATCH/WRAP sentinels from
     * the framework, so {@code hasLayout} marks whether size was captured.
     */
    static final class HiddenState {
        final int visibility;
        final boolean enabled;
        final boolean clickable;
        final boolean hasLayout;
        final int width;
        final int height;
        final float weight;

        HiddenState(
                int visibility,
                boolean enabled,
                boolean clickable,
                boolean hasLayout,
                int width,
                int height,
                float weight
        ) {
            this.visibility = visibility;
            this.enabled = enabled;
            this.clickable = clickable;
            this.hasLayout = hasLayout;
            this.width = width;
            this.height = height;
            this.weight = weight;
        }

        static HiddenState capture(View view) {
            boolean hasLayout = false;
            int width = 0;
            int height = 0;
            float weight = Float.NaN;
            try {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params != null) {
                    hasLayout = true;
                    width = params.width;
                    height = params.height;
                    if (params instanceof LinearLayout.LayoutParams) {
                        weight = ((LinearLayout.LayoutParams) params).weight;
                    }
                }
            } catch (RuntimeException ignored) {
                hasLayout = false;
            }
            return new HiddenState(
                    view.getVisibility(),
                    view.isEnabled(),
                    view.isClickable(),
                    hasLayout,
                    width,
                    height,
                    weight
            );
        }
    }

    private static void hidePublishView(View view, String what) {
        boolean first;
        synchronized (PUBLISH_HIDDEN) {
            first = !PUBLISH_HIDDEN.containsKey(view);
            if (first) {
                PUBLISH_HIDDEN.put(view, HiddenState.capture(view));
            }
        }
        applyPublishHidden(view);
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

    private static void applyPublishHidden(View view) {
        try {
            view.setEnabled(false);
            view.setClickable(false);
            view.setVisibility(View.GONE);
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params != null) {
                params.width = 0;
                params.height = 0;
                if (params instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) params).weight = 0f;
                }
                view.setLayoutParams(params);
            }
            view.requestLayout();
        } catch (RuntimeException ignored) {
            // The host view is gone; its WeakHashMap entry dies with it.
        }
    }

    private static void restorePublish() {
        List<Map.Entry<View, HiddenState>> entries;
        synchronized (PUBLISH_HIDDEN) {
            if (PUBLISH_HIDDEN.isEmpty()) {
                return;
            }
            entries = new ArrayList<>(PUBLISH_HIDDEN.entrySet());
            PUBLISH_HIDDEN.clear();
        }
        int restored = 0;
        for (Map.Entry<View, HiddenState> entry : entries) {
            if (restorePublishValue(entry.getKey(), entry.getValue())) {
                restored++;
            }
        }
        if (restored > 0) {
            LogBook.d("[FeedUi] restored publish views=" + restored);
        }
    }

    private static boolean restorePublishValue(View view, HiddenState state) {
        if (view == null || state == null) {
            return false;
        }
        try {
            view.setEnabled(state.enabled);
            view.setClickable(state.clickable);
            view.setVisibility(state.visibility);
            if (state.hasLayout) {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params != null) {
                    params.width = state.width;
                    params.height = state.height;
                    if (params instanceof LinearLayout.LayoutParams
                            && !Float.isNaN(state.weight)) {
                        ((LinearLayout.LayoutParams) params).weight = state.weight;
                    }
                    view.setLayoutParams(params);
                }
            }
            view.requestLayout();
            return true;
        } catch (RuntimeException ignored) {
            return false;
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
