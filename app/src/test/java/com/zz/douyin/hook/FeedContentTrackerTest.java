package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.annotations.SerializedName;
import com.zz.douyin.FilterPreferences;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public final class FeedContentTrackerTest {
    @Test
    public void typeTwoWithPlaceholderVideoIsFiltered() throws Exception {
        FakeAweme aweme = videoAweme(2);

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertTrue(snapshot.shouldFilter());
        assertEquals("photo article model", snapshot.filterReason);
    }

    @Test
    public void multiImageTypeWithPlaceholderVideoIsFiltered() throws Exception {
        FakeAweme aweme = videoAweme(0x44);

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertTrue(snapshot.shouldFilter());
        assertEquals("photo article model", snapshot.filterReason);
    }

    @Test
    public void slidesWithPlaceholderVideoAreFiltered() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.isSlides = true;

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertTrue(snapshot.shouldFilter());
        assertTrue(snapshot.slides);
    }

    @Test
    public void imageInfosWithPlaceholderVideoAreFiltered() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.imageInfos = List.of(new Object(), new Object());

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertTrue(snapshot.shouldFilter());
        assertEquals(2, snapshot.imageInfoCount);
    }

    @Test
    public void hostImageMethodsOverridePlaceholderVideo() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.hostImage = true;
        aweme.hostMultiImage = true;

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertTrue(snapshot.shouldFilter());
        assertTrue(snapshot.hostImage);
        assertTrue(snapshot.hostMultiImage);
    }

    @Test
    public void ordinaryNonAdPlaceholderVideoRemainsAccepted() throws Exception {
        FakeAweme aweme = videoAweme(0);

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertFalse(snapshot.shouldFilter());
        assertTrue(snapshot.hasVideo);
    }

    @Test
    public void videoAdvertisementWithPlaybackUrlIsFiltered() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.video = new FakeVideo(
                List.of("https://example.invalid/video.mp4")
        );
        aweme.isAd = true;
        aweme.rawAd = new Object();

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertTrue(snapshot.shouldFilter());
        assertEquals("advertisement model", snapshot.filterReason);
        assertTrue(snapshot.hasVideo);
        assertTrue(snapshot.isAd);
        assertTrue(snapshot.hasRawAd);
        assertEquals(1, snapshot.playUrls.size());
        assertEquals("play_addr", snapshot.playUrls.get(0).source);
    }

    @Test
    public void advertisementPlaceholderVideoIsFiltered()
            throws Exception {
        FakeAweme aweme = videoAweme(140);
        aweme.isAd = true;
        aweme.rawAd = new Object();

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertTrue(snapshot.hasVideo);
        assertTrue(snapshot.playUrls.isEmpty());
        assertTrue(snapshot.shouldFilter());
        assertEquals("advertisement model", snapshot.filterReason);
    }

    @Test
    public void nonHttpAdvertisementPlaybackUrlIsFiltered()
            throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.video = new FakeVideo(
                List.of("ftp://example.invalid/video.mp4")
        );
        aweme.isAd = true;

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertTrue(snapshot.playUrls.isEmpty());
        assertTrue(snapshot.shouldFilter());
        assertEquals("advertisement model", snapshot.filterReason);
    }

    @Test
    public void nonVideoAdvertisementIsFiltered() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.video = null;
        aweme.isAd = true;
        aweme.rawAd = new Object();

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertTrue(snapshot.shouldFilter());
        assertEquals("advertisement model", snapshot.filterReason);
    }

    @Test
    public void photoAdvertisementWithPlaceholderVideoRemainsFiltered()
            throws Exception {
        FakeAweme aweme = videoAweme(2);
        aweme.isAd = true;
        aweme.rawAd = new Object();

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertTrue(snapshot.shouldFilter());
        assertEquals("advertisement model", snapshot.filterReason);
    }

    @Test
    public void disabledTypeSettingsKeepEveryCategory() {
        FilterPreferences.Values disabled =
                new FilterPreferences.Values(
                        false, false, false, false, "",
                        false, FilterPreferences.DEFAULT_LONG_VIDEO_THRESHOLD_MS);

        FakeAweme ad = videoAweme(0);
        ad.isAd = true;
        assertFalse(snapshot(ad, disabled).shouldFilter());

        FakeAweme image = videoAweme(2);
        assertFalse(snapshot(image, disabled).shouldFilter());

        FakeAweme live = videoAweme(101);
        assertFalse(snapshot(live, disabled).shouldFilter());
    }

    @Test
    public void liveSettingFiltersTypeOneHundredOne() {
        FakeAweme live = videoAweme(101);

        FeedContentTracker.Snapshot snapshot = snapshot(
                live,
                new FilterPreferences.Values(
                        false, false, true, false, "",
                        false, FilterPreferences.DEFAULT_LONG_VIDEO_THRESHOLD_MS)
        );

        assertTrue(snapshot.live);
        assertTrue(snapshot.shouldFilter());
        assertEquals("live model", snapshot.filterReason);
    }

    @Test
    public void videoSettingFiltersOrdinaryVideo() {
        FakeAweme aweme = videoAweme(0);

        FeedContentTracker.Snapshot snapshot = snapshot(
                aweme,
                new FilterPreferences.Values(
                        false, false, false, true, "",
                        false, FilterPreferences.DEFAULT_LONG_VIDEO_THRESHOLD_MS)
        );

        assertTrue(snapshot.shouldFilter());
        assertEquals("video type setting", snapshot.filterReason);
    }

    @Test
    public void createTimeSecondsNormalizeToMs() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.createTime = 1758000000L;

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals(1758000000000L, snapshot.createTimeMs);
    }

    @Test
    public void missingCreateTimeYieldsUnknown() throws Exception {
        FeedContentTracker.Snapshot snapshot = snapshot(videoAweme(0));

        assertEquals(-1L, snapshot.createTimeMs);
    }

    @Test
    public void locationFieldsFromDirectMembers() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.ipLabel = "四川";
        aweme.city = "成都市";

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("四川", snapshot.ipLabel);
        assertEquals("成都市", snapshot.city);
        assertEquals("", snapshot.poiName);
        assertEquals("", snapshot.location);
    }

    @Test
    public void ipLabelPrefersGetterOverField() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.ipLabelMethod = "浙江";
        aweme.ipLabel = "四川";

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("浙江", snapshot.ipLabel);
    }

    @Test
    public void ipLabelFallsBackToAuthor() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.author = new FakeAuthor("广东");

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("广东", snapshot.ipLabel);
    }

    @Test
    public void poiNameFromNestedObject() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.poi = new FakePoi("春熙路");

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("春熙路", snapshot.poiName);
    }

    @Test
    public void missingLocationYieldsBlank() throws Exception {
        FeedContentTracker.Snapshot snapshot = snapshot(videoAweme(0));

        assertEquals("", snapshot.ipLabel);
        assertEquals("", snapshot.poiName);
        assertEquals("", snapshot.location);
        assertEquals("", snapshot.city);
    }

    @Test
    public void ipLocationStringSerializedNameResolves() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.ipLocationString = "四川";

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("四川", snapshot.ipLabel);
    }

    @Test
    public void poiStructGetterResolves() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.poiStruct = new FakePoi("宽窄巷子");

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("宽窄巷子", snapshot.poiName);
    }

    @Test
    public void awemeCityResolvesAsCity() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.awemeCity = "成都市";

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("成都市", snapshot.city);
    }

    @Test
    public void poiCityNestedResolvesAsCity() throws Exception {
        FakeAweme aweme = videoAweme(0);
        FakePoi poi = new FakePoi("");
        poi.poiCity = "成都市";
        aweme.poi = poi;

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("成都市", snapshot.city);
    }

    @Test
    public void poiAddressNestedResolvesAsLocation() throws Exception {
        FakeAweme aweme = videoAweme(0);
        FakePoi poi = new FakePoi("");
        poi.address = "春熙路北段";
        aweme.poi = poi;

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("春熙路北段", snapshot.location);
    }

    @Test
    public void ipLocationBeatsIpLabel() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.ipLabel = "甲";
        aweme.ipLoc = "乙";

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("乙", snapshot.ipLabel);
    }

    @Test
    public void authorIpLocationFieldResolves() throws Exception {
        FakeAweme aweme = videoAweme(0);
        aweme.authorIpLocation = "丙";

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals("丙", snapshot.ipLabel);
    }

    @Test
    public void diagnosticsFieldsPopulated() throws Exception {
        FeedContentTracker.Snapshot blank = snapshot(videoAweme(0));

        assertEquals("FakeAweme", blank.awemeClass);
        assertEquals("", blank.authorClass);
        assertFalse(blank.poiFound);

        FakeAweme aweme = videoAweme(0);
        aweme.author = new FakeAuthor("广东");
        aweme.poi = new FakePoi("春熙路");
        FeedContentTracker.Snapshot filled = snapshot(aweme);

        assertEquals("FakeAweme", filled.awemeClass);
        assertEquals("FakeAuthor", filled.authorClass);
        assertTrue(filled.poiFound);
    }

    @Test
    public void keywordMatchesVideoItemTitle() {
        FakeAweme aweme = videoAweme(0);
        aweme.itemTitle = "今天一起玩超级游戏";

        FeedContentTracker.Snapshot snapshot = snapshot(
                aweme,
                new FilterPreferences.Values(
                        false,
                        false,
                        false,
                        false,
                        "推广\n游戏",
                        false,
                        FilterPreferences.DEFAULT_LONG_VIDEO_THRESHOLD_MS
                )
        );

        assertTrue(snapshot.shouldFilter());
        assertEquals("video keyword: 游戏", snapshot.filterReason);
        assertEquals(aweme.itemTitle, snapshot.title);
    }

    @Test
    public void keywordMatchesVideoDescriptionIgnoringEnglishCase() {
        FakeAweme aweme = videoAweme(0);
        aweme.desc = "A closer look at the New Gadget today";

        FeedContentTracker.Snapshot snapshot = snapshot(
                aweme,
                new FilterPreferences.Values(
                        false,
                        false,
                        false,
                        false,
                        "GADGET，其他",
                        false,
                        FilterPreferences.DEFAULT_LONG_VIDEO_THRESHOLD_MS
                )
        );

        assertTrue(snapshot.shouldFilter());
        assertEquals("video keyword: GADGET", snapshot.filterReason);
        assertEquals(aweme.desc, snapshot.description);
    }

    @Test
    public void videoKeywordsDoNotOverrideDisabledAdvertisementType() {
        FakeAweme aweme = videoAweme(0);
        aweme.isAd = true;
        aweme.desc = "游戏推广";

        FeedContentTracker.Snapshot snapshot = snapshot(
                aweme,
                new FilterPreferences.Values(
                        false,
                        false,
                        false,
                        false,
                        "游戏",
                        false,
                        FilterPreferences.DEFAULT_LONG_VIDEO_THRESHOLD_MS
                )
        );

        assertFalse(snapshot.shouldFilter());
    }

    @Test
    public void longVideoOverThresholdIsFiltered() {
        FakeAweme aweme = videoAweme(0);
        FakeVideo video = new FakeVideo(List.of("https://example.invalid/v.mp4"));
        video.duration = 300_000L;
        aweme.video = video;

        FeedContentTracker.Snapshot snapshot = snapshot(
                aweme,
                new FilterPreferences.Values(
                        false, false, false, false, "",
                        true, 180_000L)
        );

        assertTrue(snapshot.shouldFilter());
        assertEquals("long video model", snapshot.filterReason);
        assertEquals(300_000L, snapshot.durationMs);
    }

    @Test
    public void videoUnderThresholdPasses() {
        FakeAweme aweme = videoAweme(0);
        FakeVideo video = new FakeVideo(List.of("https://example.invalid/v.mp4"));
        video.duration = 60_000L;
        aweme.video = video;

        FeedContentTracker.Snapshot snapshot = snapshot(
                aweme,
                new FilterPreferences.Values(
                        false, false, false, false, "",
                        true, 180_000L)
        );

        assertFalse(snapshot.shouldFilter());
    }

    @Test
    public void unknownDurationPassesWhenLongFilterEnabled() {
        FeedContentTracker.Snapshot snapshot = snapshot(
                videoAweme(0),
                new FilterPreferences.Values(
                        false, false, false, false, "",
                        true, 180_000L)
        );

        assertEquals(-1L, snapshot.durationMs);
        assertFalse(snapshot.shouldFilter());
    }

    @Test
    public void longVideoPassesWhenFilterDisabled() {
        FakeAweme aweme = videoAweme(0);
        FakeVideo video = new FakeVideo(List.of("https://example.invalid/v.mp4"));
        video.duration = 300_000L;
        aweme.video = video;

        FeedContentTracker.Snapshot snapshot = snapshot(
                aweme,
                new FilterPreferences.Values(
                        false, false, false, false, "",
                        false, 180_000L)
        );

        assertFalse(snapshot.shouldFilter());
    }

    @Test
    public void durationSecondsNormalizeToMs() throws Exception {
        FakeAweme aweme = videoAweme(0);
        FakeVideo video = new FakeVideo(List.of("https://example.invalid/v.mp4"));
        video.duration = 372L;
        aweme.video = video;

        FeedContentTracker.Snapshot snapshot = snapshot(aweme);

        assertEquals(372_000L, snapshot.durationMs);
    }

    private static FakeAweme videoAweme(int awemeType) {
        FakeAweme aweme = new FakeAweme();
        aweme.aid = "test-" + awemeType;
        aweme.awemeType = awemeType;
        aweme.video = new Object();
        aweme.images = Collections.emptyList();
        aweme.imageInfos = Collections.emptyList();
        return aweme;
    }

    private static FeedContentTracker.Snapshot snapshot(Object aweme)
            throws Exception {
        Method method = FeedContentTracker.class.getDeclaredMethod(
                "snapshot",
                Object.class
        );
        method.setAccessible(true);
        return (FeedContentTracker.Snapshot) method.invoke(null, aweme);
    }

    private static FeedContentTracker.Snapshot snapshot(
            Object aweme,
            FilterPreferences.Values settings
    ) {
        return FeedContentTracker.snapshot(aweme, settings);
    }

    public static final class FakeAweme {
        public String aid;
        public int awemeType;
        public boolean isAd;
        public boolean isSlides;
        public String itemTitle;
        public String title;
        public String desc;
        public Object video;
        public Object articleInfo;
        public List<Object> images;
        public List<Object> imageInfos;
        public boolean hostImage;
        public boolean hostMultiImage;
        public Object rawAd;
        public long createTime;
        public String ipLabel;
        public String ipLabelMethod;
        public String location;
        public String city;
        public String awemeCity;
        public Object author;
        public Object poi;
        public Object poiStruct;
        @SerializedName("ip_location_string")
        public String ipLocationString;
        @SerializedName("ip_location")
        public String ipLoc;
        public String authorIpLocation;

        public String getIpLabel() {
            return ipLabelMethod;
        }

        public Object getPoiStruct() {
            return poiStruct;
        }

        public boolean isImage() {
            return hostImage;
        }

        public boolean isMultiImage() {
            return hostMultiImage;
        }

        public Object getAwemeRawAd() {
            return rawAd;
        }
    }

    public static final class FakeAuthor {
        public final String ipLocation;

        FakeAuthor(String ipLocation) {
            this.ipLocation = ipLocation;
        }
    }

    public static final class FakePoi {
        public final String poiName;
        public String poiCity;
        public String address;

        FakePoi(String poiName) {
            this.poiName = poiName;
        }
    }

    public static final class FakeVideo {
        @SerializedName("play_addr")
        public final FakeUrlModel playAddress;
        public long duration;

        FakeVideo(List<String> urls) {
            playAddress = new FakeUrlModel(urls);
        }
    }

    public static final class FakeUrlModel {
        private final List<String> urls;

        FakeUrlModel(List<String> urls) {
            this.urls = urls;
        }

        public List<String> getUrlList() {
            return urls;
        }
    }
}
