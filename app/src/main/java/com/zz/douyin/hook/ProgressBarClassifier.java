package com.zz.douyin.hook;

final class ProgressBarClassifier {
    private ProgressBarClassifier() {
    }

    static boolean isCandidateClass(String className) {
        if (className == null) {
            return false;
        }
        String name = className.toLowerCase(java.util.Locale.ROOT);
        return name.contains("progress") || name.contains("seekbar");
    }

    static boolean matchesProgressSize(
            int width,
            int height,
            int decorWidth,
            int decorHeight
    ) {
        return decorWidth > 0
                && decorHeight > 0
                && width > decorWidth * 0.5
                && height > 0
                && height <= decorHeight * 0.03;
    }

    static boolean isBottomStrip(int top, int decorHeight) {
        return decorHeight > 0 && top >= decorHeight * 0.85;
    }

    static boolean isProgressBar(
            String className,
            int width,
            int height,
            int top,
            int decorWidth,
            int decorHeight
    ) {
        return isCandidateClass(className)
                && matchesProgressSize(width, height, decorWidth, decorHeight)
                && isBottomStrip(top, decorHeight);
    }

    /**
     * Names the first check a candidate view fails, for near-miss
     * diagnostics. Returns "" when the view fully matches.
     */
    static String rejectStage(
            String className,
            int width,
            int height,
            int top,
            int decorWidth,
            int decorHeight
    ) {
        if (!isCandidateClass(className)) {
            return "class";
        }
        if (decorWidth <= 0 || decorHeight <= 0) {
            return "decor";
        }
        if (width <= decorWidth * 0.5) {
            return "width";
        }
        if (height <= 0 || height > decorHeight * 0.03) {
            return "height";
        }
        if (top < decorHeight * 0.85) {
            return "top";
        }
        return "";
    }
}
