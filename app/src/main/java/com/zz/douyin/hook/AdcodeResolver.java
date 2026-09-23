package com.zz.douyin.hook;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resolves administrative-division codes such as {@code Aweme.city}
 * ({@code "110101"}) to display names ({@code "北京市 东城区"}).
 *
 * <p>The offline {@link AdcodeTable} is always consulted first. The optional
 * online fallback queries the same ip33 area-code endpoint family that
 * FreedomPlus uses, and only fires when the offline table has no match. Online
 * results are cached in memory; the feed scan loop picks them up on its next
 * pass, so callers never block on the network.
 */
final class AdcodeResolver {
    private static final String ONLINE_ENDPOINT =
            "http://api.ip33.com/Area_Code/GetArea/?code=";
    private static final int ONLINE_TIMEOUT_MS = 3000;
    private static final int ONLINE_MAX_BYTES = 4096;
    private static final int ONLINE_CACHE_MAX = 256;
    private static final long ONLINE_FAILURE_RETRY_MS = 10L * 60L * 1000L;

    private static final Map<String, String> ONLINE_CACHE =
            Collections.synchronizedMap(new BoundedCache<>(ONLINE_CACHE_MAX));
    private static final Map<String, Long> ONLINE_FAILURES =
            Collections.synchronizedMap(new HashMap<>());
    private static final Set<String> IN_FLIGHT =
            Collections.synchronizedSet(new HashSet<>());
    private static ExecutorService onlinePool;

    private AdcodeResolver() {
    }

    static boolean isCode(String raw) {
        return normalize(raw) != null;
    }

    /**
     * Normalizes 2/4/6 digit codes to 6 digits. Returns null for blank,
     * non-numeric, all-zero, or oddly-sized input.
     */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.trim();
        int length = code.length();
        if (length != 2 && length != 4 && length != 6) {
            return null;
        }
        for (int i = 0; i < length; i++) {
            char c = code.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        if (length == 2) {
            code = code + "0000";
        } else if (length == 4) {
            code = code + "00";
        }
        if ("000000".equals(code)) {
            return null;
        }
        return code;
    }

    /** Offline lookup with city/province fallback. Returns "" when unknown. */
    static String resolveOffline(String raw) {
        String code = normalize(raw);
        if (code == null) {
            return "";
        }
        String exact = AdcodeTable.lookup(code);
        if (exact != null) {
            return exact;
        }
        String cityLevel = AdcodeTable.lookup(code.substring(0, 4) + "00");
        if (cityLevel != null) {
            return cityLevel;
        }
        String provinceLevel = AdcodeTable.lookup(code.substring(0, 2) + "0000");
        return provinceLevel == null ? "" : provinceLevel;
    }

    /** Previously fetched online result, or "" when absent. */
    static String resolveCached(String raw) {
        String code = normalize(raw);
        if (code == null) {
            return "";
        }
        String cached = ONLINE_CACHE.get(code);
        return cached == null ? "" : cached;
    }

    /** Synchronous resolution: offline table first, then online cache. */
    static String resolveSync(String raw) {
        String offline = resolveOffline(raw);
        return offline.isEmpty() ? resolveCached(raw) : offline;
    }

    /**
     * Kicks off a background online lookup when the offline table and cache
     * both miss. Safe to call on every scan; duplicate and recently-failed
     * codes are skipped.
     */
    static void prefetchOnline(String raw) {
        String code = normalize(raw);
        if (code == null || !resolveOffline(code).isEmpty()
                || !resolveCached(code).isEmpty()) {
            return;
        }
        Long failedAt = ONLINE_FAILURES.get(code);
        if (failedAt != null
                && System.currentTimeMillis() - failedAt < ONLINE_FAILURE_RETRY_MS) {
            return;
        }
        if (!IN_FLIGHT.add(code)) {
            return;
        }
        ExecutorService pool;
        synchronized (AdcodeResolver.class) {
            if (onlinePool == null) {
                onlinePool = Executors.newSingleThreadExecutor(new ThreadFactory() {
                    private final AtomicInteger sequence = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable task) {
                        Thread thread = new Thread(
                                task, "douyim-adcode-" + sequence.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                });
            }
            pool = onlinePool;
        }
        pool.execute(() -> fetchAndCache(code));
    }

    private static void fetchAndCache(String code) {
        try {
            String path = fetchOnline(code);
            if (path == null || path.isEmpty()) {
                ONLINE_FAILURES.put(code, System.currentTimeMillis());
                return;
            }
            ONLINE_CACHE.put(code, path);
            ONLINE_FAILURES.remove(code);
            LogBook.d("[Adcode] online resolved " + code + " -> " + path);
        } catch (RuntimeException error) {
            ONLINE_FAILURES.put(code, System.currentTimeMillis());
        } finally {
            IN_FLIGHT.remove(code);
        }
    }

    static String fetchOnline(String code) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(ONLINE_ENDPOINT + code)
                    .openConnection();
            connection.setConnectTimeout(ONLINE_TIMEOUT_MS);
            connection.setReadTimeout(ONLINE_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "";
            }
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > ONLINE_MAX_BYTES) {
                        return "";
                    }
                    out.write(buffer, 0, read);
                }
                return parseIp33Response(
                        new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (Exception failed) {
            LogBook.d("[Adcode] online lookup failed for " + code, failed);
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Parses the ip33 {@code GetArea} JSON ({@code province}/{@code city}/
     * {@code county} keys) without org.json so plain JVM unit tests can cover
     * it. Returns "" when nothing usable is present.
     */
    static String parseIp33Response(String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        StringBuilder path = new StringBuilder();
        appendPart(path, extractJsonString(json, "province"));
        appendPart(path, extractJsonString(json, "city"));
        appendPart(path, extractJsonString(json, "county"));
        return path.toString();
    }

    private static void appendPart(StringBuilder path, String part) {
        if (part == null) {
            return;
        }
        String name = part.trim();
        if (name.isEmpty()) {
            return;
        }
        String current = path.toString();
        if (!current.isEmpty() && current.endsWith(name)
                && isBoundary(current, current.length() - name.length())) {
            return;
        }
        if (path.length() > 0) {
            path.append(' ');
        }
        path.append(name);
    }

    private static boolean isBoundary(String text, int index) {
        return index <= 0 || text.charAt(index - 1) == ' ';
    }

    private static String extractJsonString(String json, String key) {
        String quoted = "\"" + key + "\"";
        int keyAt = json.indexOf(quoted);
        if (keyAt < 0) {
            return null;
        }
        int colonAt = json.indexOf(':', keyAt + quoted.length());
        if (colonAt < 0) {
            return null;
        }
        int valueAt = colonAt + 1;
        while (valueAt < json.length()
                && Character.isWhitespace(json.charAt(valueAt))) {
            valueAt++;
        }
        if (valueAt >= json.length() || json.charAt(valueAt) != '"') {
            return null;
        }
        StringBuilder value = new StringBuilder();
        for (int i = valueAt + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                value.append(json.charAt(i + 1));
                i++;
                continue;
            }
            if (c == '"') {
                return value.toString();
            }
            value.append(c);
        }
        return null;
    }

    private static final class BoundedCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxEntries;

        BoundedCache(int maxEntries) {
            super(16, 0.75f, true);
            this.maxEntries = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxEntries;
        }
    }
}
