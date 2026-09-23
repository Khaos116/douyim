package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class FeedNavigatorTest {
    @Test
    public void filterReasonsMapToTypedReasons() {
        assertEquals(
                FeedNavigator.Reason.FILTER_AD,
                FeedNavigator.reasonForFilter("advertisement model")
        );
        assertEquals(
                FeedNavigator.Reason.FILTER_LIVE,
                FeedNavigator.reasonForFilter("live model")
        );
        assertEquals(
                FeedNavigator.Reason.FILTER_IMAGE,
                FeedNavigator.reasonForFilter("long article model")
        );
        assertEquals(
                FeedNavigator.Reason.FILTER_IMAGE,
                FeedNavigator.reasonForFilter("photo article model")
        );
        assertEquals(
                FeedNavigator.Reason.FILTER_IMAGE,
                FeedNavigator.reasonForFilter("article model")
        );
        assertEquals(
                FeedNavigator.Reason.FILTER_IMAGE,
                FeedNavigator.reasonForFilter("non-video model")
        );
        assertEquals(
                FeedNavigator.Reason.FILTER_VIDEO,
                FeedNavigator.reasonForFilter("video type setting")
        );
        assertEquals(
                FeedNavigator.Reason.FILTER_KEYWORD,
                FeedNavigator.reasonForFilter("video keyword: test")
        );
    }

    @Test
    public void filterReasonMappingIgnoresClassificationDetails() {
        assertEquals(
                FeedNavigator.Reason.FILTER_AD,
                FeedNavigator.reasonForFilter("advertisement model aid=123 type=0")
        );
        assertEquals(
                FeedNavigator.Reason.FILTER_KEYWORD,
                FeedNavigator.reasonForFilter("video keyword: test aid=1 type=0")
        );
    }

    @Test
    public void unknownFilterReasonsFallBackToImageBucket() {
        assertEquals(
                FeedNavigator.Reason.FILTER_IMAGE,
                FeedNavigator.reasonForFilter(null)
        );
        assertEquals(
                FeedNavigator.Reason.FILTER_IMAGE,
                FeedNavigator.reasonForFilter("some future model")
        );
    }
}
