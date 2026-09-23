package com.zz.douyin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ColorMathTest {
    private static final float EPSILON = 0.5f;

    @Test
    public void primaryColorsMapToExpectedHues() {
        assertHsv(new float[]{0f, 1f, 1f}, ColorMath.rgbToHsv(255, 0, 0));
        assertHsv(new float[]{120f, 1f, 1f}, ColorMath.rgbToHsv(0, 255, 0));
        assertHsv(new float[]{240f, 1f, 1f}, ColorMath.rgbToHsv(0, 0, 255));
        assertHsv(new float[]{60f, 1f, 1f}, ColorMath.rgbToHsv(255, 255, 0));
        assertHsv(new float[]{180f, 1f, 1f}, ColorMath.rgbToHsv(0, 255, 255));
        assertHsv(new float[]{300f, 1f, 1f}, ColorMath.rgbToHsv(255, 0, 255));
    }

    @Test
    public void grayscaleHasNoSaturation() {
        assertHsv(new float[]{0f, 0f, 1f}, ColorMath.rgbToHsv(255, 255, 255));
        assertHsv(new float[]{0f, 0f, 0f}, ColorMath.rgbToHsv(0, 0, 0));
        float[] gray = ColorMath.rgbToHsv(128, 128, 128);
        assertEquals(0f, gray[1], EPSILON / 255f);
        assertEquals(128f / 255f, gray[2], EPSILON / 255f);
    }

    @Test
    public void hsvToRgbRoundsToPrimaries() {
        assertArrayEquals(new int[]{255, 0, 0}, ColorMath.hsvToRgb(0f, 1f, 1f));
        assertArrayEquals(new int[]{0, 255, 0}, ColorMath.hsvToRgb(120f, 1f, 1f));
        assertArrayEquals(new int[]{0, 0, 255}, ColorMath.hsvToRgb(240f, 1f, 1f));
        assertArrayEquals(new int[]{255, 255, 255}, ColorMath.hsvToRgb(0f, 0f, 1f));
        assertArrayEquals(new int[]{0, 0, 0}, ColorMath.hsvToRgb(200f, 0.5f, 0f));
    }

    @Test
    public void hsvToRgbWrapsHueAndClamps() {
        assertArrayEquals(new int[]{255, 0, 0}, ColorMath.hsvToRgb(360f, 1f, 1f));
        assertArrayEquals(new int[]{255, 0, 0}, ColorMath.hsvToRgb(-0f, 1f, 1f));
        assertArrayEquals(
                ColorMath.hsvToRgb(300f, 1f, 1f),
                ColorMath.hsvToRgb(-60f, 1f, 1f));
        assertArrayEquals(new int[]{255, 255, 255}, ColorMath.hsvToRgb(0f, 0f, 9f));
    }

    @Test
    public void roundTripPreservesKnownColors() {
        int[][] samples = {
                {255, 87, 34},
                {33, 150, 243},
                {139, 195, 74},
                {255, 255, 255},
                {18, 18, 18},
        };
        for (int[] rgb : samples) {
            float[] hsv = ColorMath.rgbToHsv(rgb[0], rgb[1], rgb[2]);
            int[] back = ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2]);
            assertEquals(rgb[0], back[0], 1);
            assertEquals(rgb[1], back[1], 1);
            assertEquals(rgb[2], back[2], 1);
        }
    }

    @Test
    public void hsvToColorPacksOpaqueInt() {
        assertEquals(0xFFFF0000, ColorMath.hsvToColor(0f, 1f, 1f));
        assertEquals(0xFFFFFFFF, ColorMath.hsvToColor(0f, 0f, 1f));
    }

    @Test
    public void toHexOmitsAlphaWhenOpaque() {
        assertEquals("#FF5722", ColorMath.toHex(0xFFFF5722));
        assertEquals("#FFFFFF", ColorMath.toHex(0xFFFFFFFF));
        assertEquals("#80FF5722", ColorMath.toHex(0x80FF5722));
    }

    private static void assertHsv(float[] expected, float[] actual) {
        assertEquals(expected[0], actual[0], EPSILON);
        assertEquals(expected[1], actual[1], EPSILON / 255f);
        assertEquals(expected[2], actual[2], EPSILON / 255f);
    }
}
