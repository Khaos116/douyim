package com.zz.douyin;

/**
 * Single version-tag format shared by the manager UI and the hook-side load
 * log, so a build is identifiable in both places. Pure string logic so plain
 * JVM unit tests cover it; callers pass {@code BuildConfig} fields in.
 */
public final class ModuleVersion {
    private ModuleVersion() {
    }

    public static String versionTag(String versionName, int versionCode, String buildTime) {
        String name = versionName == null || versionName.isEmpty() ? "?" : versionName;
        String time = buildTime == null || buildTime.isEmpty() ? "?" : buildTime;
        return "v" + name + "+" + versionCode + " " + time;
    }
}
