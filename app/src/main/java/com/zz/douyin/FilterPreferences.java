package com.zz.douyin;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FilterPreferences {
    public static final String NAME = "content_filter";
    public static final String KEY_MODULE_ENABLED = "module_enabled";
    public static final String KEY_BLOCK_DOUBLE_TAP = "block_double_tap";
    public static final String KEY_IMMERSIVE_ENABLED = "immersive_enabled";
    public static final String KEY_SKIP_ADS = "skip_ads";
    public static final String KEY_SKIP_IMAGES = "skip_images";
    public static final String KEY_SKIP_LIVES = "skip_lives";
    public static final String KEY_SKIP_VIDEOS = "skip_videos";
    public static final String KEY_VIDEO_KEYWORDS = "video_keywords";
    public static final String KEY_SKIP_LONG_VIDEOS = "skip_long_videos";
    public static final String KEY_LONG_VIDEO_THRESHOLD_MS = "long_video_threshold_ms";
    public static final String KEY_SHOW_DANMAKU = "show_danmaku";
    public static final String KEY_AUTO_NEXT = "auto_next";
    public static final String KEY_EXACT_COUNTS = "exact_counts";
    public static final String KEY_PUBLISH_TIME = "publish_time";
    public static final String KEY_PUBLISH_LOCATION = "publish_location";
    public static final String KEY_CUSTOM_TEXT_COLORS = "custom_text_colors";
    public static final String KEY_COUNT_TEXT_COLOR = "count_text_color";
    public static final String KEY_PUBLISH_TIME_COLOR = "publish_time_color";
    public static final String KEY_LOCATION_TEXT_COLOR = "location_text_color";
    public static final String KEY_COPY_LINK = "copy_link";
    public static final String KEY_HIDE_PUBLISH = "hide_publish";
    public static final String KEY_HIDE_TABS = "hide_tabs";
    public static final String KEY_SHOW_PROGRESS = "show_progress";

    public static final boolean DEFAULT_SKIP_ADS = true;
    public static final boolean DEFAULT_SKIP_IMAGES = true;
    public static final boolean DEFAULT_SKIP_LIVES = true;
    public static final boolean DEFAULT_SKIP_VIDEOS = false;
    public static final boolean DEFAULT_SKIP_LONG_VIDEOS = false;
    public static final long DEFAULT_LONG_VIDEO_THRESHOLD_MS = 180_000L;
    public static final boolean DEFAULT_SHOW_DANMAKU = false;
    public static final boolean DEFAULT_AUTO_NEXT = true;
    public static final boolean DEFAULT_EXACT_COUNTS = true;
    public static final boolean DEFAULT_PUBLISH_TIME = true;
    public static final boolean DEFAULT_PUBLISH_LOCATION = true;
    public static final boolean DEFAULT_CUSTOM_TEXT_COLORS = false;
    public static final int DEFAULT_COUNT_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_PUBLISH_TIME_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_LOCATION_TEXT_COLOR = 0xFFFFFFFF;
    public static final boolean DEFAULT_COPY_LINK = true;
    public static final boolean DEFAULT_HIDE_PUBLISH = false;
    public static final String DEFAULT_HIDE_TABS = "";
    public static final boolean DEFAULT_SHOW_PROGRESS = true;

    private FilterPreferences() {
    }

    public static boolean readModuleEnabled(SharedPreferences preferences) {
        return preferences == null || preferences.getBoolean(KEY_MODULE_ENABLED, true);
    }

    public static boolean readBlockDoubleTap(SharedPreferences preferences) {
        return preferences != null && preferences.getBoolean(KEY_BLOCK_DOUBLE_TAP, false);
    }

    public static boolean readImmersiveEnabled(SharedPreferences preferences) {
        return preferences == null || preferences.getBoolean(KEY_IMMERSIVE_ENABLED, true);
    }

    public static Values defaults() {
        return new Values(
                DEFAULT_SKIP_ADS,
                DEFAULT_SKIP_IMAGES,
                DEFAULT_SKIP_LIVES,
                DEFAULT_SKIP_VIDEOS,
                "",
                DEFAULT_SKIP_LONG_VIDEOS,
                DEFAULT_LONG_VIDEO_THRESHOLD_MS
        );
    }

    public static Values read(SharedPreferences preferences) {
        if (preferences == null) {
            return defaults();
        }
        return new Values(
                preferences.getBoolean(KEY_SKIP_ADS, DEFAULT_SKIP_ADS),
                preferences.getBoolean(KEY_SKIP_IMAGES, DEFAULT_SKIP_IMAGES),
                preferences.getBoolean(KEY_SKIP_LIVES, DEFAULT_SKIP_LIVES),
                preferences.getBoolean(KEY_SKIP_VIDEOS, DEFAULT_SKIP_VIDEOS),
                preferences.getString(KEY_VIDEO_KEYWORDS, ""),
                preferences.getBoolean(KEY_SKIP_LONG_VIDEOS, DEFAULT_SKIP_LONG_VIDEOS),
                preferences.getLong(
                        KEY_LONG_VIDEO_THRESHOLD_MS, DEFAULT_LONG_VIDEO_THRESHOLD_MS)
        );
    }

    public static boolean readShowDanmaku(SharedPreferences preferences) {
        return preferences == null
                ? DEFAULT_SHOW_DANMAKU
                : preferences.getBoolean(KEY_SHOW_DANMAKU, DEFAULT_SHOW_DANMAKU);
    }

    public static boolean readAutoNext(SharedPreferences preferences) {
        return preferences == null
                ? DEFAULT_AUTO_NEXT
                : preferences.getBoolean(KEY_AUTO_NEXT, DEFAULT_AUTO_NEXT);
    }

    public static boolean readExactCounts(SharedPreferences preferences) {
        return preferences == null
                ? DEFAULT_EXACT_COUNTS
                : preferences.getBoolean(KEY_EXACT_COUNTS, DEFAULT_EXACT_COUNTS);
    }

    public static boolean readPublishTime(SharedPreferences preferences) {
        return preferences == null
                ? DEFAULT_PUBLISH_TIME
                : preferences.getBoolean(KEY_PUBLISH_TIME, DEFAULT_PUBLISH_TIME);
    }

    public static boolean readPublishLocation(SharedPreferences preferences) {
        return preferences == null
                ? DEFAULT_PUBLISH_LOCATION
                : preferences.getBoolean(KEY_PUBLISH_LOCATION, DEFAULT_PUBLISH_LOCATION);
    }

    public static boolean readCustomTextColors(SharedPreferences preferences) {
        return preferences != null
                && preferences.getBoolean(KEY_CUSTOM_TEXT_COLORS, DEFAULT_CUSTOM_TEXT_COLORS);
    }

    public static int readCountTextColor(SharedPreferences preferences) {
        return preferences == null
                ? DEFAULT_COUNT_TEXT_COLOR
                : preferences.getInt(KEY_COUNT_TEXT_COLOR, DEFAULT_COUNT_TEXT_COLOR);
    }

    public static int readPublishTimeColor(SharedPreferences preferences) {
        return preferences == null
                ? DEFAULT_PUBLISH_TIME_COLOR
                : preferences.getInt(KEY_PUBLISH_TIME_COLOR, DEFAULT_PUBLISH_TIME_COLOR);
    }

    public static int readLocationTextColor(SharedPreferences preferences) {
        return preferences == null
                ? DEFAULT_LOCATION_TEXT_COLOR
                : preferences.getInt(KEY_LOCATION_TEXT_COLOR, DEFAULT_LOCATION_TEXT_COLOR);
    }

    public static boolean readCopyLink(SharedPreferences preferences) {
        return preferences == null
                ? DEFAULT_COPY_LINK
                : preferences.getBoolean(KEY_COPY_LINK, DEFAULT_COPY_LINK);
    }

    public static boolean readHidePublish(SharedPreferences preferences) {
        return preferences != null
                && preferences.getBoolean(KEY_HIDE_PUBLISH, DEFAULT_HIDE_PUBLISH);
    }

    public static String readHideTabs(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_HIDE_TABS;
        }
        String raw = preferences.getString(KEY_HIDE_TABS, DEFAULT_HIDE_TABS);
        return raw == null ? DEFAULT_HIDE_TABS : raw;
    }

    public static boolean readShowProgress(SharedPreferences preferences) {
        return preferences == null
                ? DEFAULT_SHOW_PROGRESS
                : preferences.getBoolean(KEY_SHOW_PROGRESS, DEFAULT_SHOW_PROGRESS);
    }

    public static final class Values {
        public final boolean skipAds;
        public final boolean skipImages;
        public final boolean skipLives;
        public final boolean skipVideos;
        public final String keywordText;
        public final boolean skipLongVideos;
        public final long longVideoThresholdMs;
        private final List<String> keywords;

        public Values(
                boolean skipAds,
                boolean skipImages,
                boolean skipLives,
                boolean skipVideos,
                String keywordText,
                boolean skipLongVideos,
                long longVideoThresholdMs
        ) {
            this.skipAds = skipAds;
            this.skipImages = skipImages;
            this.skipLives = skipLives;
            this.skipVideos = skipVideos;
            this.keywordText = keywordText == null ? "" : keywordText;
            this.skipLongVideos = skipLongVideos;
            this.longVideoThresholdMs = longVideoThresholdMs;
            this.keywords = parseKeywords(this.keywordText);
        }

        public List<String> keywords() {
            return keywords;
        }

        public String matchingVideoKeyword(String title, String description) {
            if (keywords.isEmpty()) {
                return null;
            }
            String searchable = safeLower(title) + "\n" + safeLower(description);
            for (String keyword : keywords) {
                if (searchable.contains(safeLower(keyword))) {
                    return keyword;
                }
            }
            return null;
        }

        private static List<String> parseKeywords(String raw) {
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

        private static String safeLower(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT);
        }
    }
}
