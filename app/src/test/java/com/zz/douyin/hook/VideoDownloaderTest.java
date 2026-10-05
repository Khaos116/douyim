package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class VideoDownloaderTest {
    @Test
    public void resolveCopyLinkPrefersShareUrl() {
        FeedContentTracker.Snapshot snapshot = snapshot("7691631033780306600", List.of(
                new FeedContentTracker.PlayUrl("https://example.invalid/a.mp4", "play_addr"),
                new FeedContentTracker.PlayUrl("https://example.invalid/b.mp4", "play_addr_h264")
        ));

        assertEquals(
                "https://www.douyin.com/video/7691631033780306600",
                VideoDownloader.resolveCopyLink(snapshot)
        );
    }

    @Test
    public void resolveCopyLinkFallsBackToPlayUrlWithoutAid() {
        FeedContentTracker.Snapshot snapshot = snapshot("unknown", List.of(
                new FeedContentTracker.PlayUrl("https://example.invalid/a.mp4", "play_addr")
        ));

        assertEquals(
                "https://example.invalid/a.mp4",
                VideoDownloader.resolveCopyLink(snapshot)
        );
    }

    @Test
    public void resolveCopyLinkReturnsNullWithoutAidOrUrls() {
        assertNull(VideoDownloader.resolveCopyLink(snapshot("unknown", Collections.emptyList())));
        assertNull(VideoDownloader.resolveCopyLink(null));
    }

    @Test
    public void shareUrlRejectsBlankAid() {
        assertNull(VideoDownloader.shareUrl(null));
        assertNull(VideoDownloader.shareUrl(""));
        assertNull(VideoDownloader.shareUrl("unknown"));
        assertEquals(
                "https://www.douyin.com/video/123",
                VideoDownloader.shareUrl("123")
        );
    }

    private static FeedContentTracker.Snapshot snapshot(
            List<FeedContentTracker.PlayUrl> playUrls
    ) {
        return snapshot("aid-1", playUrls);
    }

    private static FeedContentTracker.Snapshot snapshot(
            String aid,
            List<FeedContentTracker.PlayUrl> playUrls
    ) {
        return new FeedContentTracker.Snapshot(
                aid, 0, true,
                false, false, false,
                0, 0, false, false, false, false,
                "", "",
                -1L, -1L, -1L, -1L, -1L,
                -1L, -1L, "", "", "", "",
                "", "", false,
                null, playUrls);
    }
}
