package com.zz.douyin.hook;

import android.graphics.Color;
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
 * Shows the current video's publish time and location in a small
 * overlay so it stays visible in both playing and paused states.
 *
 * <p>The overlay is our own view because Douyin's feed UI does not reliably
 * expose publish-time/location TextViews to find and rewrite. It never
 * consumes touches and is removed whenever both switches are off or nothing
 * is known. IP attribution is always labeled "IP属地", never "地点".
 */
final class PublishInfo {
    private static WeakReference<TextView> overlay = new WeakReference<>(null);
    private static WeakReference<View> overlayDecor = new WeakReference<>(null);
    private static String lastText;
    private static SimpleDateFormat formatter;

    private PublishInfo() {
    }

    static void update(View decor, boolean timeEnabled, boolean locationEnabled) {
        FeedContentTracker.Snapshot snapshot = null;
        if (decor != null) {
            try {
                snapshot = FeedContentTracker.current(decor);
            } catch (RuntimeException failed) {
                snapshot = null;
            }
        }
        String text = composeText(snapshot, timeEnabled, locationEnabled);
        TextView view = overlay.get();
        View host = overlayDecor.get();
        if (text == null) {
            removeOverlay(view);
            lastText = null;
            return;
        }
        if (view == null || host != decor) {
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
        if (!text.equals(lastText)) {
            view.setText(text);
            lastText = text;
            LogBook.d("[PublishInfo] " + text + " aid="
                    + (snapshot == null ? "unknown" : snapshot.aid));
        }
    }

    static void remove() {
        removeOverlay(overlay.get());
        overlay.clear();
        overlayDecor.clear();
        lastText = null;
    }

    static String composeText(
            FeedContentTracker.Snapshot snapshot,
            boolean timeEnabled,
            boolean locationEnabled
    ) {
        if (snapshot == null || snapshot.isAdvertisement()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (timeEnabled && snapshot.createTimeMs > 0L) {
            text.append("发布于 ")
                    .append(formatTime(snapshot.createTimeMs, TimeZone.getDefault()));
        }
        if (locationEnabled) {
            if (!snapshot.ipLabel.isEmpty()) {
                breakLine(text);
                text.append("IP属地：").append(snapshot.ipLabel);
            }
            String place = displayPlace(snapshot);
            if (!place.isEmpty()) {
                breakLine(text);
                text.append("地点：").append(place);
            }
        }
        return text.length() == 0 ? null : text.toString();
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

    private static void breakLine(StringBuilder text) {
        if (text.length() > 0) {
            text.append('\n');
        }
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
        view.setTextSize(12f);
        view.setTextColor(Color.WHITE);
        view.setShadowLayer(3f, 0f, 1f, Color.BLACK);
        view.setBackgroundColor(Color.argb(140, 0, 0, 0));
        int padding = Math.round(4f * density);
        view.setPadding(padding, padding, padding, padding);
        view.setClickable(false);
        view.setFocusable(false);
        view.setSingleLine(false);
        view.setMaxLines(3);
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
