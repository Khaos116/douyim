package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class VideoDownloaderTest {
    @Test
    public void resolveCopyLinkReturnsFirstPlayUrl() {
        FeedContentTracker.Snapshot snapshot = snapshot(List.of(
                new FeedContentTracker.PlayUrl("https://example.invalid/a.mp4", "play_addr"),
                new FeedContentTracker.PlayUrl("https://example.invalid/b.mp4", "play_addr_h264")
        ));

        assertEquals(
                "https://example.invalid/a.mp4",
                VideoDownloader.resolveCopyLink(snapshot)
        );
    }

    @Test
    public void resolveCopyLinkReturnsNullWithoutUrls() {
        assertNull(VideoDownloader.resolveCopyLink(snapshot(Collections.emptyList())));
        assertNull(VideoDownloader.resolveCopyLink(null));
    }

    private static FeedContentTracker.Snapshot snapshot(
            List<FeedContentTracker.PlayUrl> playUrls
    ) {
        return new FeedContentTracker.Snapshot(
                "aid-1", 0, true,
                false, false, false,
                0, 0, false, false, false, false,
                "", "",
                -1L, -1L, -1L, -1L, -1L,
                -1L, -1L, "", "", "", "",
                "", "", false,
                null, playUrls);
    }
}
