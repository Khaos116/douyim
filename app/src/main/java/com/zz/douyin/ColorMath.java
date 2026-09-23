package com.zz.douyin;

import java.util.Locale;

/**
 * Pure HSV/RGB math for the color picker. Uses no {@code android.*} APIs so
 * plain JVM unit tests cover it.
 */
final class ColorMath {
    private ColorMath() {
    }

    /**
     * Converts RGB bytes to {@code [hue 0-360, saturation 0-1, value 0-1]}.
     */
    static float[] rgbToHsv(int red, int green, int blue) {
        float r = clampByte(red) / 255f;
        float g = clampByte(green) / 255f;
        float b = clampByte(blue) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0f;
        if (delta > 0f) {
            if (max == r) {
                hue = 60f * (((g - b) / delta) % 6f);
            } else if (max == g) {
                hue = 60f * ((b - r) / delta + 2f);
            } else {
                hue = 60f * ((r - g) / delta + 4f);
            }
            if (hue < 0f) {
                hue += 360f;
            }
        }
        float saturation = max <= 0f ? 0f : delta / max;
        return new float[]{hue, saturation, max};
    }

    /**
     * Converts HSV to {@code [red, green, blue]} bytes.
     */
    static int[] hsvToRgb(float hue, float saturation, float value) {
        float h = ((hue % 360f) + 360f) % 360f;
        float s = clamp01(saturation);
        float v = clamp01(value);
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r;
        float g;
        float b;
        if (h < 60f) {
            r = c;
            g = x;
            b = 0f;
        } else if (h < 120f) {
            r = x;
            g = c;
            b = 0f;
        } else if (h < 180f) {
            r = 0f;
            g = c;
            b = x;
        } else if (h < 240f) {
            r = 0f;
            g = x;
            b = c;
        } else if (h < 300f) {
            r = x;
            g = 0f;
            b = c;
        } else {
            r = c;
            g = 0f;
            b = x;
        }
        return new int[]{
                Math.round((r + m) * 255f),
                Math.round((g + m) * 255f),
                Math.round((b + m) * 255f)
        };
    }

    /**
     * Packs opaque HSV into an ARGB int.
     */
    static int hsvToColor(float hue, float saturation, float value) {
        int[] rgb = hsvToRgb(hue, saturation, value);
        return 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    /**
     * Formats {@code #RRGGBB}, or {@code #AARRGGBB} when not fully opaque.
     */
    static String toHex(int color) {
        if (((color >>> 24) & 0xFF) == 0xFF) {
            return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
        }
        return String.format(Locale.ROOT, "#%08X", color);
    }

    static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
