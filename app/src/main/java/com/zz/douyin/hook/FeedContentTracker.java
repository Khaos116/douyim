package com.zz.douyin.hook;

import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

import com.zz.douyin.FilterPreferences;
import com.zz.douyin.hook.model.AwemeAccessor;
import com.zz.douyin.hook.model.StatisticsAccessor;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class FeedContentTracker {
    private static final Map<Object, Long> PANELS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, Map<String, Field>> SERIALIZED_FIELDS =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** 图文字段候选：优先大图/原图，最后才是缩略图。 */
    private static final String[] IMAGE_LABEL_FIELDS = {
            "labelLarge",
            "labelOrigin",
            "origin",
            "large",
            "labelThumb",
            "thumb"
    };

    private static Class<?> panelClass;
    private static Method getCurrentAweme;
    private static Field viewPagerField;
    private static WeakReference<Object> selectedPanel = new WeakReference<>(null);
    private static WeakReference<Object> lastPanel = new WeakReference<>(null);
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
        Object bestPanel = null;
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
        if (selected != null && !isPanelOnDecor(selected, pagerField, decor)) {
            selected = null;
        }
        for (Object panel : panels) {
            if (panel == null || !expectedPanelClass.isInstance(panel)) {
                continue;
            }
            if (selected != null && panel != selected) {
                // The largest-visible-area fallback is only safe when the
                // page-selection hook never reported a panel: during a tab
                // swipe a neighbour tab's panel also covers the screen centre
                // and would hand back another feed's aweme (often a live
                // card), filtering the video the user is actually watching.
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
                    bestPanel = panel;
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                LogBook.d("current feed model lookup failed", error);
            }
        }
        if (bestPanel != null) {
            lastPanel = new WeakReference<>(bestPanel);
        }
        return bestAweme == null ? null : snapshot(bestAweme, filterSettings);
    }

    private static boolean isPanelOnDecor(Object panel, Field pagerField, View decor) {
        try {
            return pagerField.get(panel) instanceof View pager
                    && pager.isAttachedToWindow()
                    && pager.getRootView() == decor;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }

    /** ViewPager of the feed panel that currently owns the screen, used for programmatic paging. */
    static View currentPager(View decor) {
        Object panel = lastPanel.get();
        Field pagerField = viewPagerField;
        if (panel == null || pagerField == null) {
            return null;
        }
        try {
            Object pager = pagerField.get(panel);
            if (pager instanceof View view
                    && view.isAttachedToWindow()
                    && (decor == null || view.getRootView() == decor)) {
                return view;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Panels are recycled aggressively; the caller falls back to the decor view.
        }
        return null;
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
        boolean hostLive = model.isLive();
        boolean hostImage = model.isImage();
        boolean hostMultiImage = model.isMultiImage();
        boolean slides = model.isSlides();
        Object video = model.video();
        Object article = model.article();
        Object images = model.images();
        Object imageInfos = model.imageInfos();
        int imageCount = AwemeAccessor.collectionSize(images);
        int imageInfoCount = AwemeAccessor.collectionSize(imageInfos);
        // Upstream's quality-aware ordering lives in this class because it needs the
        // module preferences; the plain accessor variant stays for callers without them.
        List<PlayUrl> playUrls = resolvePlayUrls(video);
        boolean hasPlayUrl = !playUrls.isEmpty();
        boolean live = Snapshot.isLiveModel(awemeType);
        Object riskModel = model.riskModel();
        String title = model.title();
        String description = model.description();
        StatisticsAccessor statistics = new StatisticsAccessor(model.statistics());
        long diggCount = statistics.diggCount();
        long commentCount = statistics.commentCount();
        long collectCount = statistics.collectCount();
        long shareCount = statistics.shareCount();
        long playCount = statistics.playCount();
        long createTimeMs = model.createTimeMs();
        long durationMs = model.getDurationMs();
        String ipLabel = model.ipLabel();
        String poiName = model.poiName();
        String location = model.location();
        String city = model.city();
        String awemeClass = AwemeAccessor.simpleName(aweme);
        String authorClass = AwemeAccessor.simpleName(model.author());
        boolean poiFound = model.poi() != null;
        boolean photo = Snapshot.isPhotoModel(
                hostImage, hostMultiImage, slides, awemeType, imageCount, imageInfoCount,
                hasPlayUrl);
        // 40.8 marks several ad formats only through these side channels; isAd/awemeRawAd
        // stay empty for 电商、团购、直播预约 and 小程序 ads, which therefore played on.
        // Only ad-exclusive markers may decide the skip: adAwemeSource / asyncAdInfo /
        // linkAdData exist on ordinary videos too, so they are logged for diagnosis only.
        Class<?> awemeClassRef = aweme.getClass();
        int adSource = AwemeAccessor.intValue(
                AwemeAccessor.readField(awemeClassRef, aweme, "adAwemeSource"), 0);
        Object gdAd = AwemeAccessor.readField(awemeClassRef, aweme, "gdAd");
        Object asyncAdInfo = AwemeAccessor.readField(awemeClassRef, aweme, "asyncAdInfo");
        Object linkAdData = AwemeAccessor.readField(awemeClassRef, aweme, "linkAdData");
        boolean advertisement = ad || rawAd != null || gdAd != null;
        boolean photoContent = photo;
        String adMarkers = adMarkerText(ad, rawAd, adSource, gdAd, asyncAdInfo, linkAdData);
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
        } else if (photo || article != null) {
            if (activeSettings.skipImages) {
                reason = photo ? "photo article model" : "article model";
            }
        } else if (activeSettings.skipLongVideos
                && durationMs >= 0L
                && durationMs >= activeSettings.longVideoThresholdMs) {
            reason = "long video model";
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
                images,
                imageInfos,
                imageCount,
                imageInfoCount,
                live,
                hostLive,
                riskModel,
                hostImage,
                hostMultiImage,
                slides,
                photoContent,
                title,
                description,
                diggCount,
                commentCount,
                collectCount,
                shareCount,
                playCount,
                createTimeMs,
                durationMs,
                ipLabel,
                poiName,
                location,
                city,
                awemeClass,
                authorClass,
                poiFound,
                reason,
                playUrls,
                adMarkers
        );
    }

    private static String adMarkerText(
            boolean isAd,
            Object rawAd,
            int source,
            Object gdAd,
            Object asyncAdInfo,
            Object linkAdData
    ) {
        StringBuilder markers = new StringBuilder();
        if (isAd) markers.append("isAd,");
        if (rawAd != null) markers.append("rawAd,");
        if (source > 0) markers.append("source=").append(source).append(',');
        if (gdAd != null) markers.append("gdAd,");
        if (asyncAdInfo != null) markers.append("async,");
        if (linkAdData != null) markers.append("link,");
        return markers.length() == 0 ? "none" : markers.toString();
    }

    private static Object readField(Class<?> type, Object instance, String name) {
        try {
            Field field = type.getField(name);
            return field.get(instance);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object readSerializedField(Object instance, String serializedName) {
        if (instance == null) {
            return null;
        }
        Field field = findSerializedField(instance.getClass(), serializedName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(instance);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field findSerializedField(Class<?> type, String serializedName) {
        synchronized (SERIALIZED_FIELDS) {
            Map<String, Field> cached = SERIALIZED_FIELDS.computeIfAbsent(
                    type,
                    ignored -> new HashMap<>()
            );
            if (cached.containsKey(serializedName)) {
                return cached.get(serializedName);
            }
            Field resolved = locateSerializedField(type, serializedName);
            cached.put(serializedName, resolved);
            return resolved;
        }
    }

    private static Field locateSerializedField(Class<?> type, String serializedName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                for (Annotation annotation : field.getDeclaredAnnotations()) {
                    if (!"com.google.gson.annotations.SerializedName"
                            .equals(annotation.annotationType().getName())) {
                        continue;
                    }
                    try {
                        Method valueMethod = annotation.annotationType().getMethod("value");
                        Object value = valueMethod.invoke(annotation);
                        if (serializedName.equals(value)) {
                            field.setAccessible(true);
                            return field;
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // Keep looking: model variants can use a different Gson runtime.
                    }
                }
            }
        }
        return null;
    }

    private static List<PlayUrl> resolvePlayUrls(Object video) {
        if (video == null) {
            return Collections.emptyList();
        }
        List<PlayUrl> primary = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        appendPlayUrls(primary, seen, video, "play_addr", "play_addr");
        appendPlayUrls(primary, seen, video, "play_addr_h264", "play_addr_h264");
        appendPlayUrls(primary, seen, video, "play_addr_bytevc1", "play_addr_bytevc1");
        // 清晰度为“自动”时保持原有顺序；钉死档位时把目标码率地址排到最前。
        String quality = FilterPreferences.readVideoQuality(filterPreferences);
        if (FilterPreferences.QUALITY_AUTO.equals(quality)) {
            return primary.isEmpty()
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(primary);
        }
        List<PlayUrl> ordered = orderPlayUrlsByQuality(
                primary,
                collectGears(video, seen),
                quality
        );
        return ordered.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(ordered);
    }

    private static void appendPlayUrls(
            List<PlayUrl> output,
            Set<String> seen,
            Object video,
            String field,
            String label
    ) {
        Object address = readSerializedField(video, field);
        if (address == null) {
            return;
        }
        Class<?> addressType = address.getClass();
        Object rawUrls = invokeNoArg(addressType, address, "getUrlList");
        if (!(rawUrls instanceof List<?>)) {
            rawUrls = readField(addressType, address, "urlList");
        }
        if (!(rawUrls instanceof List<?> candidates)) {
            return;
        }

        appendUrlsWithScheme(output, seen, candidates, label, "https://");
        appendUrlsWithScheme(output, seen, candidates, label, "http://");
    }

    /**
     * 下载地址按配置清晰度排序：目标档位（精确 > 不超过目标的最大档 > 高于目标的最小档，
     * 与播放钉死规则一致）排最前，原有顶层地址兜底，其余档位按与目标的差距排后。
     */
    private static List<PlayUrl> orderPlayUrlsByQuality(
            List<PlayUrl> primary,
            List<Gear> gears,
            String quality
    ) {
        if (gears.isEmpty()) {
            return primary;
        }
        int chosen = chooseGearHeight(gears, downloadTargetHeight(quality));
        if (chosen <= 0) {
            return primary;
        }
        List<PlayUrl> ordered = new ArrayList<>();
        for (Gear gear : gears) {
            if (gear.height == chosen) {
                ordered.addAll(gear.urls);
            }
        }
        ordered.addAll(primary);
        List<Gear> rest = new ArrayList<>();
        for (Gear gear : gears) {
            if (gear.height != chosen) {
                rest.add(gear);
            }
        }
        rest.sort((left, right) -> {
            int leftDistance = Math.abs(left.height - chosen);
            int rightDistance = Math.abs(right.height - chosen);
            if (leftDistance != rightDistance) {
                return Integer.compare(leftDistance, rightDistance);
            }
            return Long.compare(right.bitrate, left.bitrate);
        });
        for (Gear gear : rest) {
            ordered.addAll(gear.urls);
        }
        LogBook.d("download urls ordered by quality: setting="
                + quality + " chosen=" + chosen + "p gears=" + describeGears(gears));
        return ordered;
    }

    /** video.bit_rate[] 里的码率档位，每项带 gear_name 与各自的 play_addr。 */
    private static List<Gear> collectGears(Object video, Set<String> seen) {
        Object bitRates = readSerializedField(video, "bit_rate");
        if (bitRates == null) {
            bitRates = readField(video.getClass(), video, "bitRate");
        }
        List<Gear> gears = new ArrayList<>();
        for (Object item : asIterable(bitRates)) {
            if (item == null) {
                continue;
            }
            int height = gearHeight(item);
            if (height <= 0) {
                continue;
            }
            String label = "bit_rate:" + height + "p";
            List<PlayUrl> urls = new ArrayList<>();
            appendPlayUrls(urls, seen, item, "play_addr", label);
            appendPlayUrls(urls, seen, item, "play_addr_h264", label);
            appendPlayUrls(urls, seen, item, "play_addr_bytevc1", label);
            if (urls.isEmpty()) {
                continue;
            }
            gears.add(new Gear(height, gearBitrate(item), urls));
        }
        gears.sort((left, right) -> {
            if (left.height != right.height) {
                return Integer.compare(right.height, left.height);
            }
            return Long.compare(right.bitrate, left.bitrate);
        });
        return gears;
    }

    private static int downloadTargetHeight(String quality) {
        if (FilterPreferences.QUALITY_MAX.equals(quality)) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(quality);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** 选档规则与播放钉死一致：精确匹配 > 不超过目标的最大档 > 高于目标的最小档。 */
    private static int chooseGearHeight(List<Gear> gears, int target) {
        if (target <= 0) {
            return -1;
        }
        int below = -1;
        int above = -1;
        for (Gear gear : gears) {
            int height = gear.height;
            if (height == target) {
                return target;
            }
            if (height < target && height > below) {
                below = height;
            }
            if (height > target && (above < 0 || height < above)) {
                above = height;
            }
        }
        return below > 0 ? below : above;
    }

    private static int gearHeight(Object gear) {
        Object name = readSerializedField(gear, "gear_name");
        if (name == null) {
            name = readField(gear.getClass(), gear, "gearName");
        }
        if (name instanceof CharSequence text) {
            int height = heightFromGearName(text.toString());
            if (height > 0) {
                return height;
            }
        }
        Object height = readSerializedField(gear, "height");
        if (height == null) {
            height = readField(gear.getClass(), gear, "height");
        }
        return height instanceof Number number ? number.intValue() : 0;
    }

    /** gear_name 形如 normal_1080_0 / adapt_lower_540_1，取第一个落在常见高度区间的数字段。 */
    private static int heightFromGearName(String gearName) {
        for (String part : gearName.split("[^0-9]+")) {
            if (part.isEmpty()) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (value >= 144 && value <= 4320) {
                return value;
            }
        }
        return 0;
    }

    private static long gearBitrate(Object gear) {
        Object bitrate = readSerializedField(gear, "bit_rate");
        if (bitrate == null) {
            bitrate = readField(gear.getClass(), gear, "bitrate");
        }
        return bitrate instanceof Number number ? number.longValue() : 0L;
    }

    private static String describeGears(List<Gear> gears) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < gears.size(); index++) {
            builder.append(gears.get(index).height).append('p');
            if (index < gears.size() - 1) {
                builder.append(',');
            }
        }
        return builder.append(']').toString();
    }

    private static void appendUrlsWithScheme(
            List<PlayUrl> output,
            Set<String> seen,
            List<?> candidates,
            String source,
            String scheme
    ) {
        for (Object candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String url = candidate.toString().trim();
            if (url.regionMatches(true, 0, scheme, 0, scheme.length())
                    && seen.add(url)) {
                output.add(new PlayUrl(url, source));
            }
        }
    }

    /**
     * 图文作品的图片地址：{@code images[].url_list} 是展示图（无水印），
     * {@code image_infos[].label_large/label_origin} 是 40.x 起的大图与原始图。
     * 带 {@code download} / {@code watermark} 字样的分支是客户端「保存」用的水印图，只在没有
     * 其它候选时才兜底使用。
     */
    private static List<ImageUrl> resolveImageUrls(Object images, Object imageInfos) {
        List<ImageUrl> output = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        appendImageUrls(output, seen, images, "images");
        appendImageUrls(output, seen, imageInfos, "imageInfos");
        return output.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(output);
    }

    private static void appendImageUrls(
            List<ImageUrl> output,
            Set<String> seen,
            Object container,
            String source
    ) {
        for (Object item : asIterable(container)) {
            if (item == null) {
                continue;
            }
            List<String> urls = pickImageUrls(item);
            if (urls.isEmpty() || !seen.add(urls.get(0))) {
                continue;
            }
            output.add(new ImageUrl(urls, source));
        }
    }

    private static Iterable<?> asIterable(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return iterable;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(Array.get(value, index));
            }
            return copy;
        }
        return Collections.emptyList();
    }

    /** 一张图通常带多个镜像域名，按顺序全部保留，下载时逐个尝试。 */
    private static List<String> pickImageUrls(Object image) {
        for (String name : IMAGE_LABEL_FIELDS) {
            List<String> urls = httpUrlsFrom(readNested(image, name));
            if (!urls.isEmpty()) {
                return urls;
            }
        }
        List<String> direct = httpUrlsFrom(image);
        if (!direct.isEmpty()) {
            return direct;
        }
        return scanForHttpUrls(image, 0);
    }

    private static Object readNested(Object instance, String name) {
        Class<?> type = instance.getClass();
        Object value = readField(type, instance, name);
        if (value != null) {
            return value;
        }
        String snake = camelToSnake(name);
        if (!snake.equals(name)) {
            value = readSerializedField(instance, snake);
            if (value != null) {
                return value;
            }
        }
        String suffix = name.substring(0, 1).toUpperCase() + name.substring(1);
        return invokeNoArg(type, instance, "get" + suffix);
    }

    private static String camelToSnake(String name) {
        StringBuilder builder = new StringBuilder(name.length() + 4);
        for (int index = 0; index < name.length(); index++) {
            char current = name.charAt(index);
            if (Character.isUpperCase(current)) {
                builder.append('_').append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    /** 从 UrlModel 结构（getUrlList/urlList/url_list）或裸字符串中取出所有 http 地址。 */
    private static List<String> httpUrlsFrom(Object candidate) {
        if (candidate == null) {
            return Collections.emptyList();
        }
        if (candidate instanceof CharSequence text) {
            String value = text.toString().trim();
            return isHttpUrl(value) ? List.of(value) : Collections.emptyList();
        }
        Class<?> type = candidate.getClass();
        Object urls = invokeNoArg(type, candidate, "getUrlList");
        if (!(urls instanceof List<?>)) {
            urls = readField(type, candidate, "urlList");
        }
        if (!(urls instanceof List<?>)) {
            urls = readSerializedField(candidate, "url_list");
        }
        if (!(urls instanceof List<?> values)) {
            return Collections.emptyList();
        }
        List<String> output = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value instanceof CharSequence text) {
                String url = text.toString().trim();
                if (isHttpUrl(url)) {
                    output.add(url);
                }
            }
        }
        return output;
    }

    private static boolean isHttpUrl(String value) {
        return value.regionMatches(true, 0, "https://", 0, 8)
                || value.regionMatches(true, 0, "http://", 0, 7);
    }

    private static List<String> scanForHttpUrls(Object value, int depth) {
        if (value == null || depth > 2) {
            return Collections.emptyList();
        }
        Class<?> type = value.getClass();
        if (type.isPrimitive() || type.getName().startsWith("java.")) {
            return Collections.emptyList();
        }
        List<String> fallback = Collections.emptyList();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || field.getType().isPrimitive()) {
                    continue;
                }
                Object child;
                try {
                    field.setAccessible(true);
                    child = field.get(value);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    continue;
                }
                if (child == null) {
                    continue;
                }
                List<String> urls = httpUrlsFrom(child);
                if (urls.isEmpty()) {
                    urls = scanForHttpUrls(child, depth + 1);
                }
                if (urls.isEmpty()) {
                    continue;
                }
                String fieldName = field.getName().toLowerCase();
                if (fieldName.contains("download") || fieldName.contains("watermark")) {
                    if (fallback.isEmpty()) {
                        fallback = urls;
                    }
                    continue;
                }
                return urls;
            }
        }
        return fallback;
    }

    private static Object invokeNoArg(
            Class<?> type,
            Object instance,
            String name
    ) {
        try {
            Method method = type.getMethod(name);
            return method.invoke(instance);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof Number number && number.intValue() != 0;
    }

    private static int collectionSize(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        return value != null && value.getClass().isArray()
                ? Array.getLength(value)
                : 0;
    }

    private static String stringValue(Object value) {
        return value == null ? "unknown" : value.toString();
    }

    private static String textValue(Object value) {
        return value instanceof CharSequence text ? text.toString().trim() : "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
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
        final boolean hostLive;
        /**
         * Diagnostics only: the host risk model as read, never inspected here.
         * Field walking belongs in the once-per-aweme logging branch, because
         * a snapshot is rebuilt several times a second.
         */
        final Object riskModel;
        final boolean hostImage;
        final boolean hostMultiImage;
        final boolean slides;
        final boolean photoContent;
        final String title;
        final String description;
        final long diggCount;
        final long commentCount;
        final long collectCount;
        final long shareCount;
        final long playCount;
        final long createTimeMs;
        final long durationMs;
        final String ipLabel;
        final String poiName;
        final String location;
        final String city;
        final String awemeClass;
        final String authorClass;
        final boolean poiFound;
        final String filterReason;
        final List<PlayUrl> playUrls;
        final String adMarkers;
        private final Object images;
        private final Object imageInfos;
        private List<ImageUrl> imageUrls;

        Snapshot(
                String aid,
                int awemeType,
                boolean hasVideo,
                boolean isAd,
                boolean hasRawAd,
                boolean hasArticle,
                Object images,
                Object imageInfos,
                int imageCount,
                int imageInfoCount,
                boolean live,
                boolean hostLive,
                Object riskModel,
                boolean hostImage,
                boolean hostMultiImage,
                boolean slides,
                boolean photoContent,
                String title,
                String description,
                long diggCount,
                long commentCount,
                long collectCount,
                long shareCount,
                long playCount,
                long createTimeMs,
                long durationMs,
                String ipLabel,
                String poiName,
                String location,
                String city,
                String awemeClass,
                String authorClass,
                boolean poiFound,
                String filterReason,
                List<PlayUrl> playUrls,
                String adMarkers
        ) {
            this.aid = aid;
            this.awemeType = awemeType;
            this.hasVideo = hasVideo;
            this.isAd = isAd;
            this.hasRawAd = hasRawAd;
            this.hasArticle = hasArticle;
            this.images = images;
            this.imageInfos = imageInfos;
            this.imageCount = imageCount;
            this.imageInfoCount = imageInfoCount;
            this.live = live;
            this.hostLive = hostLive;
            this.riskModel = riskModel;
            this.hostImage = hostImage;
            this.hostMultiImage = hostMultiImage;
            this.slides = slides;
            this.photoContent = photoContent;
            this.title = title;
            this.description = description;
            this.diggCount = diggCount;
            this.commentCount = commentCount;
            this.collectCount = collectCount;
            this.shareCount = shareCount;
            this.playCount = playCount;
            this.createTimeMs = createTimeMs;
            this.durationMs = durationMs;
            this.ipLabel = ipLabel;
            this.poiName = poiName;
            this.location = location;
            this.city = city;
            this.awemeClass = awemeClass;
            this.authorClass = authorClass;
            this.poiFound = poiFound;
            this.filterReason = filterReason;
            this.playUrls = playUrls;
            this.adMarkers = adMarkers;
        }

        boolean shouldFilter() {
            return filterReason != null;
        }

        /**
         * Content kind is decided by the host {@code awemeType} alone. The two
         * tables are the ones the Kevin module actually applies in its feed
         * path (read out of its APK; its code was not copied). Host booleans
         * are never allowed to add a live verdict: {@code isLive()} answers
         * true for replay and third-party live types, which are ordinary
         * playable videos, and a missing play url must not flip the verdict.
         */
        static final int[] LIVE_TYPES = {41, 101, 105};
        static final int[] IMAGE_TYPES = {2, 0x44};

        static boolean isTypeIn(int[] types, int awemeType) {
            for (int type : types) {
                if (type == awemeType) {
                    return true;
                }
            }
            return false;
        }

        static boolean isLiveModel(int awemeType) {
            return isTypeIn(LIVE_TYPES, awemeType);
        }

        static boolean isPhotoModel(
                boolean hostImage,
                boolean hostMultiImage,
                boolean slides,
                int awemeType,
                int imageCount,
                int imageInfoCount,
                boolean hasPlayUrl) {
            if (isTypeIn(IMAGE_TYPES, awemeType)) {
                return true;
            }
            if (hasPlayUrl) {
                return false;
            }
            return hostImage
                    || hostMultiImage
                    || slides
                    || imageCount > 0
                    || imageInfoCount > 0;
        }

        boolean isPhoto() {
            return isPhotoModel(
                    hostImage,
                    hostMultiImage,
                    slides,
                    awemeType,
                    imageCount,
                    imageInfoCount,
                    hasDownloadUrl());
        }

        boolean hasDownloadUrl() {
            return !playUrls.isEmpty();
        }

        /**
         * 图文图片地址按需解析：内容检查每秒会构造多个快照，反射遍历留到真正点击下载时再做。
         */
        synchronized List<ImageUrl> imageUrls() {
            if (imageUrls == null) {
                imageUrls = resolveImageUrls(images, imageInfos);
            }
            return imageUrls;
        }

        boolean hasImages() {
            return !imageUrls().isEmpty();
        }

        boolean isAdvertisement() {
            return isAd || hasRawAd;
        }

        /** 下载将优先使用的清晰度（来自码率档位标签），没有时为空串。 */
        String downloadQualityLabel() {
            if (playUrls.isEmpty()) {
                return "";
            }
            String source = playUrls.get(0).source;
            String prefix = "bit_rate:";
            return source.startsWith(prefix)
                    ? source.substring(prefix.length())
                    : "";
        }

        String classificationDetails() {
            return "aid=" + aid
                    + " type=" + awemeType
                    + " video=" + hasVideo
                    + " isAd=" + isAd
                    + " rawAd=" + hasRawAd
                    + " adMarkers=" + adMarkers
                    + " article=" + hasArticle
                    + " images=" + imageCount
                    + " imageInfos=" + imageInfoCount
                    + " live=" + live
                    + " hostImage=" + hostImage
                    + " hostMultiImage=" + hostMultiImage
                    + " slides=" + slides
                    + " photo=" + photoContent
                    + " titleChars=" + title.length()
                    + " descChars=" + description.length()
                    + " durationMs=" + durationMs
                    + " playUrls=" + playUrls.size();
        }
    }

    static final class ImageUrl {
        /** 同一张图的多个镜像地址，按优先顺序排列。 */
        final List<String> urls;
        final String source;

        ImageUrl(List<String> urls, String source) {
            this.urls = Collections.unmodifiableList(new ArrayList<>(urls));
            this.source = source;
        }

        String primary() {
            return urls.get(0);
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

    /** video.bit_rate[] 里的一个码率档位：高度、码率与该档位的下载地址。 */
    private static final class Gear {
        final int height;
        final long bitrate;
        final List<PlayUrl> urls;

        Gear(int height, long bitrate, List<PlayUrl> urls) {
            this.height = height;
            this.bitrate = bitrate;
            this.urls = Collections.unmodifiableList(new ArrayList<>(urls));
        }
    }
}
