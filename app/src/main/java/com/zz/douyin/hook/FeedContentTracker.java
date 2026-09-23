package com.zz.douyin.hook;

import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

import com.zz.douyin.FilterPreferences;
import com.zz.douyin.hook.model.AwemeAccessor;
import com.zz.douyin.hook.model.StatisticsAccessor;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class FeedContentTracker {
    private static final Map<Object, Long> PANELS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static Class<?> panelClass;
    private static Method getCurrentAweme;
    private static Field viewPagerField;
    private static WeakReference<Object> selectedPanel = new WeakReference<>(null);
    private static volatile FilterPreferences.Values filterSettings =
            FilterPreferences.defaults();
    private static SharedPreferences filterPreferences;
    private static final SharedPreferences.OnSharedPreferenceChangeListener
            FILTER_PREFERENCE_LISTENER =
            (preferences, key) -> filterSettings = FilterPreferences.read(preferences);

    private FeedContentTracker() {
    }

    static void install(
            DouyinModule module,
            ClassLoader loader,
            SharedPreferences preferences
    )
            throws ReflectiveOperationException {
        configurePreferences(preferences);
        panelClass = Class.forName(
                "com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel",
                false,
                loader
        );
        getCurrentAweme = panelClass.getDeclaredMethod("getCurrentAweme");
        viewPagerField = panelClass.getField("f");

        for (Constructor<?> constructor : panelClass.getDeclaredConstructors()) {
            module.hook(constructor)
                    .setId("douyin-immersive-feed-panel-" + constructor.toGenericString())
                    .setExceptionMode(DouyinModule.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        remember(chain.getThisObject());
                        return result;
                    });
        }

        try {
            Method onPageSelected = panelClass.getDeclaredMethod("y0", int.class);
            module.hook(onPageSelected)
                    .setId("douyin-immersive-feed-page-selected")
                    .setExceptionMode(DouyinModule.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object panel = chain.getThisObject();
                        remember(panel);
                        selectedPanel = new WeakReference<>(panel);
                        ImmersiveUi.onFeedPageSelected();
                        return result;
                    });
        } catch (NoSuchMethodException error) {
            LogBook.w(
                    "feed page-selection hook unavailable; using visible-panel fallback");
        }
        LogBook.i("current feed model hook installed");
    }

    private static synchronized void configurePreferences(
            SharedPreferences preferences
    ) {
        if (filterPreferences != null && filterPreferences != preferences) {
            try {
                filterPreferences.unregisterOnSharedPreferenceChangeListener(
                        FILTER_PREFERENCE_LISTENER
                );
            } catch (RuntimeException ignored) {
                // A dead framework binder will be replaced by the new preference proxy.
            }
        }
        filterPreferences = preferences;
        filterSettings = FilterPreferences.read(preferences);
        if (preferences != null) {
            preferences.registerOnSharedPreferenceChangeListener(
                    FILTER_PREFERENCE_LISTENER
            );
        }
    }

    static Snapshot current(View decor) {
        Class<?> expectedPanelClass = panelClass;
        Method currentMethod = getCurrentAweme;
        Field pagerField = viewPagerField;
        if (decor == null
                || expectedPanelClass == null
                || currentMethod == null
                || pagerField == null) {
            return null;
        }

        List<Object> panels;
        synchronized (PANELS) {
            panels = new ArrayList<>(PANELS.keySet());
        }

        Object bestAweme = null;
        long bestScore = Long.MIN_VALUE;
        Object selected = selectedPanel.get();
        Rect decorVisible = new Rect();
        if (!decor.getGlobalVisibleRect(decorVisible)) {
            return null;
        }
        int centerX = decorVisible.centerX();
        int centerY = decorVisible.centerY();
        Rect visible = new Rect();
        long now = SystemClock.uptimeMillis();
        for (Object panel : panels) {
            if (panel == null || !expectedPanelClass.isInstance(panel)) {
                continue;
            }
            try {
                Object pagerObject = pagerField.get(panel);
                if (!(pagerObject instanceof View pager)
                        || !pager.isAttachedToWindow()
                        || pager.getRootView() != decor
                        || !isEffectivelyVisible(pager, decor)
                        || !pager.getGlobalVisibleRect(visible)
                        || !visible.contains(centerX, centerY)) {
                    continue;
                }
                long area = (long) visible.width() * visible.height();
                if (area < (long) decor.getWidth() * decor.getHeight() / 5L) {
                    continue;
                }

                long recent;
                synchronized (PANELS) {
                    recent = PANELS.getOrDefault(panel, 0L);
                }
                Object aweme = currentMethod.invoke(panel);
                if (aweme == null) {
                    continue;
                }
                long agePenalty = Math.min(999_999L, Math.max(0L, now - recent));
                long score = panel == selected
                        ? Long.MAX_VALUE - agePenalty
                        : area * 1_000_000L - agePenalty;
                if (score > bestScore) {
                    bestScore = score;
                    bestAweme = aweme;
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                LogBook.d("current feed model lookup failed", error);
            }
        }
        return bestAweme == null ? null : snapshot(bestAweme, filterSettings);
    }

    private static boolean isEffectivelyVisible(View view, View decor) {
        View current = view;
        while (current != null) {
            if (current.getVisibility() != View.VISIBLE || current.getAlpha() <= 0.05f) {
                return false;
            }
            if (current == decor) {
                return true;
            }
            if (!(current.getParent() instanceof View parent)) {
                return false;
            }
            current = parent;
        }
        return false;
    }

    private static void remember(Object panel) {
        if (panel != null) {
            PANELS.put(panel, SystemClock.uptimeMillis());
        }
    }

    private static Snapshot snapshot(Object aweme) {
        return snapshot(aweme, filterSettings);
    }

    static Snapshot snapshot(
            Object aweme,
            FilterPreferences.Values settings
    ) {
        AwemeAccessor model = new AwemeAccessor(aweme);
        String aid = model.aid();
        int awemeType = model.awemeType();
        boolean ad = model.isAd();
        Object rawAd = model.rawAd();
        boolean live = model.isLive();
        boolean hostImage = model.isImage();
        boolean hostMultiImage = model.isMultiImage();
        boolean slides = model.isSlides();
        Object video = model.video();
        Object article = model.article();
        Object images = model.images();
        Object imageInfos = model.imageInfos();
        int imageCount = AwemeAccessor.collectionSize(images);
        int imageInfoCount = AwemeAccessor.collectionSize(imageInfos);
        List<PlayUrl> playUrls = AwemeAccessor.resolvePlayUrls(video);
        String title = model.title();
        String description = model.description();
        StatisticsAccessor statistics = new StatisticsAccessor(model.statistics());
        long diggCount = statistics.diggCount();
        long commentCount = statistics.commentCount();
        long collectCount = statistics.collectCount();
        long shareCount = statistics.shareCount();
        long playCount = statistics.playCount();
        long createTimeMs = model.createTimeMs();
        boolean photo =
                hostImage
                        || hostMultiImage
                        || slides
                        || awemeType == 2
                        || awemeType == 0x44
                        || imageCount > 0
                        || imageInfoCount > 0;
        boolean advertisement = ad || rawAd != null;
        FilterPreferences.Values activeSettings =
                settings == null ? FilterPreferences.defaults() : settings;

        String reason = null;
        if (advertisement) {
            if (activeSettings.skipAds) {
                reason = "advertisement model";
            }
        } else if (live) {
            if (activeSettings.skipLives) {
                reason = "live model";
            }
        } else if (photo || awemeType == 0xA3 || video == null) {
            if (activeSettings.skipImages) {
                if (awemeType == 0xA3) {
                    reason = "long article model";
                } else if (photo) {
                    reason = "photo article model";
                } else if (article != null) {
                    reason = "article model";
                } else {
                    reason = "non-video model";
                }
            }
        } else {
            String keyword = activeSettings.matchingVideoKeyword(
                    title,
                    description
            );
            if (activeSettings.skipVideos) {
                reason = "video type setting";
            } else if (keyword != null) {
                reason = "video keyword: " + keyword;
            }
        }
        return new Snapshot(
                aid,
                awemeType,
                video != null,
                ad,
                rawAd != null,
                article != null,
                imageCount,
                imageInfoCount,
                live,
                hostImage,
                hostMultiImage,
                slides,
                title,
                description,
                diggCount,
                commentCount,
                collectCount,
                shareCount,
                playCount,
                createTimeMs,
                reason,
                playUrls
        );
    }

    static final class Snapshot {
        final String aid;
        final int awemeType;
        final boolean hasVideo;
        final boolean isAd;
        final boolean hasRawAd;
        final boolean hasArticle;
        final int imageCount;
        final int imageInfoCount;
        final boolean live;
        final boolean hostImage;
        final boolean hostMultiImage;
        final boolean slides;
        final String title;
        final String description;
        final long diggCount;
        final long commentCount;
        final long collectCount;
        final long shareCount;
        final long playCount;
        final long createTimeMs;
        final String filterReason;
        final List<PlayUrl> playUrls;

        Snapshot(
                String aid,
                int awemeType,
                boolean hasVideo,
                boolean isAd,
                boolean hasRawAd,
                boolean hasArticle,
                int imageCount,
                int imageInfoCount,
                boolean live,
                boolean hostImage,
                boolean hostMultiImage,
                boolean slides,
                String title,
                String description,
                long diggCount,
                long commentCount,
                long collectCount,
                long shareCount,
                long playCount,
                long createTimeMs,
                String filterReason,
                List<PlayUrl> playUrls
        ) {
            this.aid = aid;
            this.awemeType = awemeType;
            this.hasVideo = hasVideo;
            this.isAd = isAd;
            this.hasRawAd = hasRawAd;
            this.hasArticle = hasArticle;
            this.imageCount = imageCount;
            this.imageInfoCount = imageInfoCount;
            this.live = live;
            this.hostImage = hostImage;
            this.hostMultiImage = hostMultiImage;
            this.slides = slides;
            this.title = title;
            this.description = description;
            this.diggCount = diggCount;
            this.commentCount = commentCount;
            this.collectCount = collectCount;
            this.shareCount = shareCount;
            this.playCount = playCount;
            this.createTimeMs = createTimeMs;
            this.filterReason = filterReason;
            this.playUrls = playUrls;
        }

        boolean shouldFilter() {
            return filterReason != null;
        }

        boolean hasDownloadUrl() {
            return !playUrls.isEmpty();
        }

        boolean isAdvertisement() {
            return isAd || hasRawAd;
        }

        String classificationDetails() {
            return "aid=" + aid
                    + " type=" + awemeType
                    + " video=" + hasVideo
                    + " isAd=" + isAd
                    + " rawAd=" + hasRawAd
                    + " article=" + hasArticle
                    + " images=" + imageCount
                    + " imageInfos=" + imageInfoCount
                    + " live=" + live
                    + " hostImage=" + hostImage
                    + " hostMultiImage=" + hostMultiImage
                    + " slides=" + slides
                    + " titleChars=" + title.length()
                    + " descChars=" + description.length()
                    + " playUrls=" + playUrls.size();
        }
    }

    public static final class PlayUrl {
        final String url;
        final String source;

        public PlayUrl(String url, String source) {
            this.url = url;
            this.source = source;
        }
    }
}
