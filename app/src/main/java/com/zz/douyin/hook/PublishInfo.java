package com.zz.douyin.hook;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Shows the current video's publish time and location in a small single-line
 * overlay anchored below the video description.
 *
 * <p>The overlay is our own view because Douyin's feed UI does not reliably
 * expose publish-time/location TextViews to find and rewrite. It never
 * consumes touches and is removed whenever both switches are off or nothing
 * is known. The description anchor is found by matching visible text against
 * the snapshot; when no match exists the overlay falls back to a fixed
 * bottom-left slot so it never jumps to the top.
 */
final class PublishInfo {
    private static final int MIN_DESC_MATCH_LENGTH = 8;

    private static WeakReference<TextView> overlay = new WeakReference<>(null);
    private static WeakReference<View> overlayDecor = new WeakReference<>(null);
    private static WeakReference<View> anchor = new WeakReference<>(null);
    private static final int[] LOCATION = new int[2];
    private static final int[] DECOR_LOCATION = new int[2];
    private static String lastText;
    private static int lastTimeColor;
    private static int lastLocationColor;
    private static boolean lastCustomColors;
    private static int lastLeft = -1;
    private static int lastTop = -1;
    private static String lastAnchorAid;
    private static String lastAnchorMissAid;
    private static SimpleDateFormat formatter;

    private PublishInfo() {
    }

    static void update(
            View decor,
            boolean timeEnabled,
            boolean locationEnabled,
            boolean customColors,
            int timeColor,
            int locationColor
    ) {
        FeedContentTracker.Snapshot snapshot = null;
        if (decor != null) {
            try {
                snapshot = FeedContentTracker.current(decor);
            } catch (RuntimeException failed) {
                snapshot = null;
            }
        }
        OverlayContent content = composeOverlay(snapshot, timeEnabled, locationEnabled);
        TextView view = overlay.get();
        View host = overlayDecor.get();
        if (content == null) {
            removeOverlay(view);
            overlay.clear();
            overlayDecor.clear();
            lastText = null;
            return;
        }
        if (view == null || host != decor || view.getParent() == null) {
            removeOverlay(view);
            view = createOverlay(decor);
            overlay = new WeakReference<>(view);
            overlayDecor = new WeakReference<>(decor);
            lastText = null;
            lastLeft = -1;
            lastTop = -1;
        }
        if (view == null) {
            lastText = null;
            return;
        }
        reposition(view, decor, snapshot);
        if (!content.text.equals(lastText)
                || customColors != lastCustomColors
                || (customColors && (timeColor != lastTimeColor
                || locationColor != lastLocationColor))) {
            view.setText(style(content, customColors, timeColor, locationColor));
            lastText = content.text;
            lastCustomColors = customColors;
            lastTimeColor = timeColor;
            lastLocationColor = locationColor;
            LogBook.d("[PublishInfo] " + content.text + " aid="
                    + (snapshot == null ? "unknown" : snapshot.aid));
        }
    }

    static void remove() {
        removeOverlay(overlay.get());
        overlay.clear();
        overlayDecor.clear();
        anchor.clear();
        lastText = null;
        lastLeft = -1;
        lastTop = -1;
        lastAnchorAid = null;
    }

    private static void reposition(
            TextView view,
            View decor,
            FeedContentTracker.Snapshot snapshot
    ) {
        if (snapshot != null && !snapshot.aid.equals(lastAnchorAid)) {
            lastAnchorAid = snapshot.aid;
            anchor.clear();
        }
        View cached = anchor.get();
        if ((cached == null || !cached.isShown())
                && decor != null && snapshot != null) {
            cached = findDescriptionAnchor(
                    decor, snapshot.description, snapshot.title);
            anchor = new WeakReference<>(cached);
            logAnchor(cached, snapshot.aid);
        }
        applyPosition(view, decor, cached);
    }

    private static void applyPosition(
            TextView view,
            View decor,
            View anchorView
    ) {
        if (decor == null || decor.getResources() == null) {
            return;
        }
        float density = decor.getResources().getDisplayMetrics().density;
        int left;
        int top;
        if (anchorView != null) {
            decor.getLocationOnScreen(DECOR_LOCATION);
            anchorView.getLocationOnScreen(LOCATION);
            left = LOCATION[0] - DECOR_LOCATION[0];
            top = LOCATION[1] + anchorView.getHeight() - DECOR_LOCATION[1]
                    + Math.round(2f * density);
            left = Math.max(0, left);
            top = Math.max(0, Math.min(
                    top, decor.getHeight() - Math.round(32f * density)));
        } else {
            left = Math.round(12f * density);
            top = decor.getHeight() - Math.round(180f * density);
            top = Math.max(0, top);
        }
        if (left == lastLeft && top == lastTop) {
            return;
        }
        lastLeft = left;
        lastTop = top;
        if (view.getLayoutParams() instanceof FrameLayout.LayoutParams params) {
            params.gravity = Gravity.TOP | Gravity.START;
            params.leftMargin = left;
            params.topMargin = top;
            view.setLayoutParams(params);
        }
    }

    private static View findDescriptionAnchor(
            View decor,
            String desc,
            String title
    ) {
        View[] best = new View[1];
        int[] bestLength = new int[]{-1};
        int[] bestTop = new int[]{Integer.MIN_VALUE};
        collectAnchor(
                decor, desc, title, decor.getHeight(),
                best, bestLength, bestTop);
        return best[0];
    }

    private static void collectAnchor(
            View node,
            String desc,
            String title,
            int decorHeight,
            View[] best,
            int[] bestLength,
            int[] bestTop
    ) {
        if (node instanceof TextView text
                && node.isShown()
                && !"douyin_publish_info".equals(node.getTag())) {
            CharSequence content = text.getText();
            String shown = content == null ? "" : content.toString();
            if (isDescriptionMatch(shown, desc, title)) {
                node.getLocationOnScreen(LOCATION);
                if (LOCATION[1] < decorHeight * 0.5) {
                    // Descriptions live in the lower half; top matches are
                    // false positives from other screens (tabs, comments).
                } else {
                    int length = shown.trim().length();
                    if (length > bestLength[0]
                            || (length == bestLength[0]
                            && LOCATION[1] > bestTop[0])) {
                        best[0] = node;
                        bestLength[0] = length;
                        bestTop[0] = LOCATION[1];
                    }
                }
            }
        }
        if (node instanceof ViewGroup group) {
            for (int index = 0, count = group.getChildCount();
                    index < count;
                    index++) {
                collectAnchor(
                        group.getChildAt(index), desc, title, decorHeight,
                        best, bestLength, bestTop);
            }
        }
    }

    private static void logAnchor(View anchorView, String aid) {
        if (anchorView != null) {
            anchorView.getLocationOnScreen(LOCATION);
            LogBook.i("[PublishInfo] anchor desc view="
                    + anchorView.getClass().getName()
                    + " top=" + LOCATION[1] + " aid=" + aid);
            return;
        }
        if (!aid.equals(lastAnchorMissAid)) {
            lastAnchorMissAid = aid;
            LogBook.d("[PublishInfo] no desc anchor; bottom fallback aid=" + aid);
        }
    }

    static boolean isDescriptionMatch(String shown, String desc, String title) {
        if (shown == null) {
            return false;
        }
        return containsFolded(desc, shown) || containsFolded(title, shown);
    }

    private static boolean containsFolded(String model, String shown) {
        if (model == null || shown == null) {
            return false;
        }
        String flatModel = model.replaceAll("\\s+", "");
        String flatShown = shown.replaceAll("\\s+", "").trim();
        if (flatShown.length() >= MIN_DESC_MATCH_LENGTH
                && flatModel.contains(flatShown)) {
            return true;
        }
        return flatModel.length() >= MIN_DESC_MATCH_LENGTH
                && flatShown.contains(flatModel);
    }

    private static CharSequence style(
            OverlayContent content,
            boolean customColors,
            int timeColor,
            int locationColor
    ) {
        if (!customColors) {
            return content.text;
        }
        SpannableString styled = new SpannableString(content.text);
        if (content.timeStart >= 0) {
            styled.setSpan(
                    new ForegroundColorSpan(timeColor),
                    content.timeStart,
                    content.timeEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        if (content.locationStart >= 0) {
            styled.setSpan(
                    new ForegroundColorSpan(locationColor),
                    content.locationStart,
                    content.locationEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return styled;
    }

    static String composeText(
            FeedContentTracker.Snapshot snapshot,
            boolean timeEnabled,
            boolean locationEnabled
    ) {
        OverlayContent content = composeOverlay(snapshot, timeEnabled, locationEnabled);
        return content == null ? null : content.text;
    }

    static OverlayContent composeOverlay(
            FeedContentTracker.Snapshot snapshot,
            boolean timeEnabled,
            boolean locationEnabled
    ) {
        if (snapshot == null || snapshot.isAdvertisement()) {
            return null;
        }
        String timePart = null;
        if (timeEnabled && snapshot.createTimeMs > 0L) {
            timePart = formatTime(snapshot.createTimeMs, TimeZone.getDefault());
            if (snapshot.durationMs >= 0L) {
                timePart += " · " + formatDuration(snapshot.durationMs);
            }
        }
        String ipPart = locationEnabled && !snapshot.ipLabel.isEmpty()
                ? snapshot.ipLabel
                : null;
        String placePart = null;
        if (locationEnabled) {
            String place = displayPlace(snapshot);
            if (!place.isEmpty()) {
                placePart = place;
            }
        }
        if (timePart == null && ipPart == null && placePart == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        int timeStart = -1;
        int timeEnd = -1;
        int locationStart = -1;
        if (timePart != null) {
            timeStart = 0;
            text.append(timePart);
            timeEnd = text.length();
        }
        if (ipPart != null) {
            locationStart = separate(text);
            text.append(ipPart);
        }
        if (placePart != null) {
            if (locationStart < 0) {
                locationStart = separate(text);
            } else {
                separate(text);
            }
            text.append(placePart);
        }
        return new OverlayContent(
                text.toString(),
                timeStart,
                timeEnd,
                locationStart,
                locationStart < 0 ? -1 : text.length()
        );
    }

    static final class OverlayContent {
        final String text;
        final int timeStart;
        final int timeEnd;
        final int locationStart;
        final int locationEnd;

        OverlayContent(
                String text,
                int timeStart,
                int timeEnd,
                int locationStart,
                int locationEnd
        ) {
            this.text = text;
            this.timeStart = timeStart;
            this.timeEnd = timeEnd;
            this.locationStart = locationStart;
            this.locationEnd = locationEnd;
        }
    }

    static String displayPlace(FeedContentTracker.Snapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        if (!snapshot.poiName.isEmpty()) {
            return snapshot.poiName;
        }
        if (!snapshot.location.isEmpty()) {
            return snapshot.location;
        }
        return snapshot.city;
    }

    private static int separate(StringBuilder text) {
        if (text.length() > 0) {
            text.append(' ');
        }
        return text.length();
    }

    static String formatDuration(long durationMs) {
        if (durationMs < 0L) {
            return "";
        }
        long totalSeconds = durationMs / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + ":"
                    + String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        }
        return minutes + ":"
                + String.format(Locale.ROOT, "%02d", seconds);
    }

    static String formatTime(long epochMs, TimeZone zone) {
        if (epochMs <= 0L || zone == null) {
            return "";
        }
        if (formatter == null) {
            formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        }
        formatter.setTimeZone(zone);
        return formatter.format(new Date(epochMs));
    }

    private static TextView createOverlay(View decor) {
        if (!(decor instanceof ViewGroup) || decor.getResources() == null) {
            return null;
        }
        float density = decor.getResources().getDisplayMetrics().density;
        TextView view = new TextView(decor.getContext());
        view.setTag("douyin_publish_info");
        view.setTextSize(10f);
        view.setTextColor(Color.WHITE);
        view.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        view.setClickable(false);
        view.setFocusable(false);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        params.leftMargin = Math.round(12f * density);
        params.topMargin = Math.round(96f * density);
        ((ViewGroup) decor).addView(view, params);
        return view;
    }

    private static void removeOverlay(TextView view) {
        if (view == null) {
            return;
        }
        try {
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
        } catch (RuntimeException ignored) {
            // The decor is gone; nothing to detach from.
        }
    }
}
