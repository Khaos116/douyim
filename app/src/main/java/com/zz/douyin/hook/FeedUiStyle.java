package com.zz.douyin.hook;

import java.util.Locale;

/**
 * Pure color-value logic for configurable feed text colors.
 *
 * <p>Kept free of Android framework types so JVM unit tests can cover the
 * parsing rules; the thin View glue lives in {@code ExactCounts} and
 * {@code PublishInfo}.
 */
public final class FeedUiStyle {
    private FeedUiStyle() {
    }

    public static boolean isValidColor(String hex) {
        return normalize(hex) != null;
    }

    public static int parseColor(String hex, int fallback) {
        String clean = normalize(hex);
        if (clean == null) {
            return fallback;
        }
        try {
            if (clean.length() == 6) {
                return 0xFF000000 | Integer.parseInt(clean, 16);
            }
            return (int) Long.parseLong(clean, 16);
        } catch (NumberFormatException impossible) {
            return fallback;
        }
    }

    private static String normalize(String hex) {
        if (hex == null) {
            return null;
        }
        String clean = hex.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        if (clean.length() == 3) {
            StringBuilder expanded = new StringBuilder(6);
            for (int index = 0; index < 3; index++) {
                expanded.append(clean.charAt(index)).append(clean.charAt(index));
            }
            clean = expanded.toString();
        }
        if (clean.length() != 6 && clean.length() != 8) {
            return null;
        }
        for (int index = 0; index < clean.length(); index++) {
            if (Character.digit(clean.charAt(index), 16) < 0) {
                return null;
            }
        }
        return clean;
    }

    public static String toHex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }
}
