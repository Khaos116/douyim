package com.zz.douyin.hook.model;

/**
 * Typed reads over a Douyin Aweme statistics object.
 *
 * <p>Every counter returns -1 when the statistics object or the field is
 * missing, so callers can distinguish "zero" from "unknown". Method reads are
 * tried before fields because public getters are more stable across versions
 * than obfuscated field names.
 */
public final class StatisticsAccessor {
    private final Object statistics;
    private final Class<?> type;

    public StatisticsAccessor(Object statistics) {
        this.statistics = statistics;
        this.type = statistics == null ? null : statistics.getClass();
    }

    public long diggCount() {
        return read("getDiggCount", "diggCount", "digg_count");
    }

    public long commentCount() {
        return read("getCommentCount", "commentCount", "comment_count");
    }

    public long collectCount() {
        return read("getCollectCount", "collectCount", "collect_count");
    }

    public long shareCount() {
        return read("getShareCount", "shareCount", "share_count");
    }

    public long playCount() {
        return read("getPlayCount", "playCount", "play_count");
    }

    private long read(String method, String field, String serialized) {
        if (statistics == null) {
            return -1L;
        }
        Object value = AwemeAccessor.invokeNoArg(type, statistics, method);
        if (value == null) {
            value = AwemeAccessor.readField(type, statistics, field);
        }
        if (value == null) {
            value = AwemeAccessor.readSerializedField(statistics, serialized);
        }
        return value == null ? -1L : AwemeAccessor.longValue(value, -1L);
    }
}
