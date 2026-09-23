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
 * overlay pinned to the top-left corner.
 *
 * <p>The overlay is our own view because Douyin's feed UI does not reliably
 * expose publish-time/location TextViews to find and rewrite. It never
 * consumes touches and is removed whenever both switches are off or nothing
 * is known. A fixed corner is used deliberately: anchoring below the video
 * description overlapped feed text on real devices.
 *
 * <p>The {@code city} model value is an administrative-division code, not a
 * name, so it is resolved through {@link AdcodeResolver} before display.
 */
final class PublishInfo {
    private static WeakReference<TextView> overlay = new WeakReference<>(null);
    private static WeakReference<View> overlayDecor = new WeakReference<>(null);
    private static String lastText;
    private static int lastTimeColor;
    private static int lastLocationColor;
    private static boolean lastCustomColors;
    private static SimpleDateFormat formatter;

    private PublishInfo() {
    }

    static void update(
            View decor,
            boolean timeEnabled,
            boolean locationEnabled,
            boolean onlineLocationEnabled,
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
        if (locationEnabled && onlineLocationEnabled && snapshot != null) {
            maybePrefetchOnline(snapshot);
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
        }
        if (view == null) {
            lastText = null;
            return;
        }
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
        lastText = null;
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
        String location = resolvePlacePart(snapshot.location);
        if (!location.isEmpty()) {
            return location;
        }
        return resolvePlacePart(snapshot.city);
    }

    /**
     * Resolves a location/city model value for display. Real names pass
     * through untouched; division codes resolve via the offline table (or a
     * previously fetched online result), falling back to the raw code so an
     * unknown code stays visible for debugging instead of vanishing.
     */
    static String resolvePlacePart(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (!AdcodeResolver.isCode(raw)) {
            return raw;
        }
        String resolved = AdcodeResolver.resolveSync(raw);
        return resolved.isEmpty() ? raw.trim() : resolved;
    }

    private static void maybePrefetchOnline(FeedContentTracker.Snapshot snapshot) {
        if (!snapshot.poiName.isEmpty()) {
            return;
        }
        String part = snapshot.location.isEmpty() ? snapshot.city : snapshot.location;
        if (AdcodeResolver.isCode(part)
                && AdcodeResolver.resolveSync(part).isEmpty()) {
            AdcodeResolver.prefetchOnline(part);
        }
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
