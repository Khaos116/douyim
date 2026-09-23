package com.zz.douyin.hook.model;

import static org.junit.Assert.assertEquals;

import com.google.gson.annotations.SerializedName;

import org.junit.Test;

public final class StatisticsAccessorTest {
    @Test
    public void methodsArePreferredOverFields() {
        FakeStatistics statistics = new FakeStatistics();
        statistics.diggCount = 111L;
        statistics.commentCount = 222L;
        statistics.collectCount = 333L;
        statistics.shareCount = 444L;
        statistics.playCount = 555L;

        StatisticsAccessor accessor = new StatisticsAccessor(statistics);

        assertEquals(1001L, accessor.diggCount());
        assertEquals(222L, accessor.commentCount());
        assertEquals(333L, accessor.collectCount());
        assertEquals(444L, accessor.shareCount());
        assertEquals(555L, accessor.playCount());
    }

    @Test
    public void serializedNameFallbackResolvesObfuscatedFields() {
        ObfuscatedStatistics statistics = new ObfuscatedStatistics();
        statistics.a = 777L;

        StatisticsAccessor accessor = new StatisticsAccessor(statistics);

        assertEquals(777L, accessor.diggCount());
        assertEquals(-1L, accessor.commentCount());
    }

    @Test
    public void missingStatisticsYieldUnknown() {
        StatisticsAccessor accessor = new StatisticsAccessor(null);

        assertEquals(-1L, accessor.diggCount());
        assertEquals(-1L, accessor.commentCount());
        assertEquals(-1L, accessor.collectCount());
        assertEquals(-1L, accessor.shareCount());
        assertEquals(-1L, accessor.playCount());
    }

    @Test
    public void missingCountersYieldUnknown() {
        StatisticsAccessor accessor = new StatisticsAccessor(new Object());

        assertEquals(-1L, accessor.diggCount());
        assertEquals(-1L, accessor.playCount());
    }

    @Test
    public void integerCountersCoerceToLong() {
        IntegerStatistics statistics = new IntegerStatistics();

        StatisticsAccessor accessor = new StatisticsAccessor(statistics);

        assertEquals(42L, accessor.diggCount());
    }

    public static final class FakeStatistics {
        public long diggCount;
        public long commentCount;
        public long collectCount;
        public long shareCount;
        public long playCount;

        public long getDiggCount() {
            return 1001L;
        }
    }

    public static final class ObfuscatedStatistics {
        @SerializedName("digg_count")
        public long a;
    }

    public static final class IntegerStatistics {
        public int diggCount = 42;
    }
}
