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

    static boolean isProgressBar(
            String className,
            int width,
            int height,
            int top,
            int decorWidth,
            int decorHeight
    ) {
        if (!isCandidateClass(className) || decorWidth <= 0 || decorHeight <= 0) {
            return false;
        }
        if (width <= decorWidth * 0.5
                || height <= 0
                || height > decorHeight * 0.03) {
            return false;
        }
        return top >= decorHeight * 0.85;
    }
}
