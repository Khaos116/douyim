package com.zz.douyin.hook.model;

import com.zz.douyin.hook.FeedContentTracker;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Typed reads over a Douyin Aweme model object.
 *
 * <p>Phase 3 extraction: all reflection against Aweme/video/image models moved
 * here verbatim from {@code FeedContentTracker}. Filter policy (photo/ad/live
 * composition, reason mapping) stays in the tracker; this class only answers
 * "what does this model say", never "should it be filtered".
 */
public final class AwemeAccessor {
    private static final Map<Class<?>, Map<String, Field>> SERIALIZED_FIELDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Object aweme;
    private final Class<?> type;

    public AwemeAccessor(Object aweme) {
        this.aweme = aweme;
        this.type = aweme.getClass();
    }

    public String aid() {
        return stringValue(readField(type, aweme, "aid"));
    }

    public int awemeType() {
        return intValue(readField(type, aweme, "awemeType"), -1);
    }

    public boolean isAd() {
        return booleanValue(readField(type, aweme, "isAd"));
    }

    public Object rawAd() {
        return invokeNoArg(type, aweme, "getAwemeRawAd");
    }

    public boolean isLive() {
        return booleanValue(invokeNoArg(type, aweme, "isLive"))
                || awemeType() == 101;
    }

    public boolean isImage() {
        return booleanValue(invokeNoArg(type, aweme, "isImage"));
    }

    public boolean isMultiImage() {
        return booleanValue(invokeNoArg(type, aweme, "isMultiImage"));
    }

    public boolean isSlides() {
        return booleanValue(readField(type, aweme, "isSlides"));
    }

    public Object video() {
        Object video = readField(type, aweme, "video");
        if (video == null) {
            video = readSerializedField(aweme, "video");
        }
        return video;
    }

    public Object article() {
        Object article = readField(type, aweme, "articleInfo");
        if (article == null) {
            article = readSerializedField(aweme, "article_info");
        }
        return article;
    }

    public Object images() {
        Object images = readField(type, aweme, "images");
        if (images == null) {
            images = readSerializedField(aweme, "images");
        }
        return images;
    }

    public Object imageInfos() {
        Object imageInfos = readField(type, aweme, "imageInfos");
        if (imageInfos == null) {
            imageInfos = readSerializedField(aweme, "image_infos");
        }
        return imageInfos;
    }

    public String title() {
        return firstNonBlank(
                textValue(readField(type, aweme, "itemTitle")),
                textValue(readSerializedField(aweme, "item_title")),
                textValue(readField(type, aweme, "title")),
                textValue(readSerializedField(aweme, "title"))
        );
    }

    public String description() {
        return firstNonBlank(
                textValue(readField(type, aweme, "desc")),
                textValue(readSerializedField(aweme, "desc")),
                textValue(invokeNoArg(type, aweme, "getProcessedDesc")),
                textValue(invokeNoArg(type, aweme, "getEllipsizeDesc"))
        );
    }

    public long createTimeMs() {
        Object value = invokeNoArg(type, aweme, "getCreateTime");
        if (value == null) {
            value = readField(type, aweme, "createTime");
        }
        if (value == null) {
            value = readSerializedField(aweme, "create_time");
        }
        long seconds = longValue(value, -1L);
        if (seconds <= 0L) {
            return -1L;
        }
        return seconds < 1_000_000_000_000L ? seconds * 1000L : seconds;
    }

    public long getDurationMs() {
        Object video = video();
        long duration = longOn(video, "getDuration", "duration", "duration", -1L);
        if (duration < 0L) {
            duration = longOn(video, "getDurationMs", "durationMs", "duration_ms", -1L);
        }
        if (duration < 0L) {
            duration = longOn(aweme, "getDuration", "duration", "duration", -1L);
        }
        if (duration <= 0L) {
            return -1L;
        }
        return duration < 1000L ? duration * 1000L : duration;
    }

    public Object statistics() {
        Object statistics = invokeNoArg(type, aweme, "getStatistics");
        if (statistics == null) {
            statistics = readField(type, aweme, "statistics");
        }
        if (statistics == null) {
            statistics = readSerializedField(aweme, "statistics");
        }
        return statistics;
    }

    public Object author() {
        Object author = invokeNoArg(type, aweme, "getAuthor");
        if (author == null) {
            author = readField(type, aweme, "author");
        }
        if (author == null) {
            author = readSerializedField(aweme, "author");
        }
        return author;
    }

    public Object poi() {
        Object poi = invokeNoArg(type, aweme, "getPoiStruct");
        if (poi == null) {
            poi = readField(type, aweme, "poiStruct");
        }
        if (poi == null) {
            poi = invokeNoArg(type, aweme, "getPoi");
        }
        if (poi == null) {
            poi = readField(type, aweme, "poi");
        }
        if (poi == null) {
            poi = readField(type, aweme, "poiInfo");
        }
        if (poi == null) {
            poi = readSerializedField(aweme, "poi_struct");
        }
        if (poi == null) {
            poi = readSerializedField(aweme, "poi");
        }
        if (poi == null) {
            poi = readSerializedField(aweme, "poi_info");
        }
        return poi;
    }

    public String ipLabel() {
        String direct = firstNonBlank(
                textOn(aweme, "getIpLabel", "ipLabel", "ip_label"),
                textOn(aweme, "getIpLocation", "ipLocation", "ip_location"),
                textValue(readSerializedField(aweme, "ip_location_string")),
                textOn(aweme, "getIpAttribution", "ipAttribution", "ip_attribution")
        );
        if (!direct.isEmpty()) {
            return direct;
        }
        Object author = author();
        return firstNonBlank(
                textOn(author, "getIpLabel", "ipLabel", "ip_label"),
                textOn(author, "getIpLocation", "ipLocation", "ip_location"),
                textValue(readSerializedField(author, "ip_location_string"))
        );
    }

    public String poiName() {
        String direct = textOn(aweme, "getPoiName", "poiName", "poi_name");
        if (!direct.isEmpty()) {
            return direct;
        }
        Object poi = poi();
        return firstNonBlank(
                textOn(poi, "getPoiName", "poiName", "poi_name"),
                textOn(poi, "getName", "name", "name")
        );
    }

    public String location() {
        String direct = firstNonBlank(
                textOn(aweme, "getLocation", "location", "location"),
                textOn(aweme, "getAddress", "address", "address")
        );
        if (!direct.isEmpty()) {
            return direct;
        }
        Object poi = poi();
        return firstNonBlank(
                textOn(poi, "getAddress", "address", "address"),
                textOn(poi, "getPoiAddress", "poiAddress", "poi_address")
        );
    }

    public String city() {
        String direct = firstNonBlank(
                textOn(aweme, "getCity", "city", "city"),
                textOn(aweme, "getCityName", "cityName", "city_name"),
                textOn(aweme, "getAwemeCity", "awemeCity", "aweme_city")
        );
        if (!direct.isEmpty()) {
            return direct;
        }
        Object poi = poi();
        String nested = firstNonBlank(
                textOn(poi, "getCity", "city", "city"),
                textOn(poi, "getCityName", "cityName", "city_name"),
                textOn(poi, "getPoiCity", "poiCity", "poi_city")
        );
        if (!nested.isEmpty()) {
            return nested;
        }
        return firstNonBlank(
                textOn(aweme, "getAwemeRegion", "awemeRegion", "aweme_region"),
                textOn(aweme, "getRegion", "region", "region")
        );
    }

    private static String textOn(
            Object target,
            String method,
            String field,
            String serialized
    ) {
        if (target == null) {
            return "";
        }
        Class<?> targetType = target.getClass();
        Object value = invokeNoArg(targetType, target, method);
        if (value == null) {
            value = readField(targetType, target, field);
        }
        if (value == null) {
            value = readSerializedField(target, serialized);
        }
        return textValue(value);
    }

    private static long longOn(
            Object target,
            String method,
            String field,
            String serialized,
            long fallback
    ) {
        if (target == null) {
            return fallback;
        }
        Class<?> targetType = target.getClass();
        Object value = invokeNoArg(targetType, target, method);
        if (value == null) {
            value = readField(targetType, target, field);
        }
        if (value == null) {
            value = readSerializedField(target, serialized);
        }
        return value == null ? fallback : longValue(value, fallback);
    }

    public static Object readField(Class<?> type, Object instance, String name) {
        try {
            Field field = type.getField(name);
            return field.get(instance);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static Object readSerializedField(Object instance, String serializedName) {
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

    public static Object invokeNoArg(Class<?> type, Object instance, String name) {
        try {
            Method method = type.getMethod(name);
            return method.invoke(instance);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof Number number && number.intValue() != 0;
    }

    public static int collectionSize(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        return value != null && value.getClass().isArray()
                ? Array.getLength(value)
                : 0;
    }

    public static long longValue(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    public static double doubleValue(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public static String stringValue(Object value) {
        return value == null ? "unknown" : value.toString();
    }

    public static String textValue(Object value) {
        return value instanceof CharSequence text ? text.toString().trim() : "";
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    public static List<FeedContentTracker.PlayUrl> resolvePlayUrls(Object video) {
        if (video == null) {
            return Collections.emptyList();
        }
        List<FeedContentTracker.PlayUrl> urls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        appendPlayUrls(urls, seen, video, "play_addr");
        appendPlayUrls(urls, seen, video, "play_addr_h264");
        appendPlayUrls(urls, seen, video, "play_addr_bytevc1");
        return urls.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(urls);
    }

    private static void appendPlayUrls(
            List<FeedContentTracker.PlayUrl> output,
            Set<String> seen,
            Object video,
            String source
    ) {
        Object address = readSerializedField(video, source);
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

        appendUrlsWithScheme(output, seen, candidates, source, "https://");
        appendUrlsWithScheme(output, seen, candidates, source, "http://");
    }

    private static void appendUrlsWithScheme(
            List<FeedContentTracker.PlayUrl> output,
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
                output.add(new FeedContentTracker.PlayUrl(url, source));
            }
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
}
