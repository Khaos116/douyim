package com.zz.douyin.hook;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Dual-write logger: every message goes to logcat (as before) and, best-effort,
 * to a day-rotated file the settings UI can display without adb.
 *
 * <p>File location mirrors Sesame-M: the <i>target</i> app's
 * {@code Android/media/<package>/} directory, which the hooked process can write
 * without any permission. The settings UI reads the same files (it may need the
 * storage permission on some Android versions; the viewer degrades to an adb
 * hint when the directory is unreadable).
 *
 * <p>File output never throws and never blocks the caller: lines are queued and
 * written by a single daemon thread; any failure disables file output and the
 * module keeps running on logcat alone.
 */
public final class LogBook {
    private static final String FILE_PREFIX = "runtime.";
    private static final String FILE_SUFFIX = ".log";
    private static final String LOG_SUBDIR =
            "Android/media/" + DouyinModule.TARGET_PACKAGE + "/douyim/log";
    private static final int RETENTION_DAYS = 7;
    private static final int MAX_TAIL_BYTES = 256 * 1024;
    private static final int MAX_VIEW_LINES = 4_000;

    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();
    private static volatile boolean writerStarted;
    private static volatile boolean fileEnabled = true;

    private LogBook() {
    }

    static void d(String message) {
        Log.d(DouyinModule.TAG, message);
        enqueue('D', message, null);
    }

    static void d(String message, Throwable error) {
        Log.d(DouyinModule.TAG, message, error);
        enqueue('D', message, error);
    }

    static void i(String message) {
        Log.i(DouyinModule.TAG, message);
        enqueue('I', message, null);
    }

    static void i(String message, Throwable error) {
        Log.i(DouyinModule.TAG, message, error);
        enqueue('I', message, error);
    }

    static void w(String message) {
        Log.w(DouyinModule.TAG, message);
        enqueue('W', message, null);
    }

    static void w(String message, Throwable error) {
        Log.w(DouyinModule.TAG, message, error);
        enqueue('W', message, error);
    }

    static void e(String message) {
        Log.e(DouyinModule.TAG, message);
        enqueue('E', message, null);
    }

    static void e(String message, Throwable error) {
        Log.e(DouyinModule.TAG, message, error);
        enqueue('E', message, error);
    }

    private static void enqueue(char level, String message, Throwable error) {
        if (!fileEnabled) {
            return;
        }
        ensureWriter();
        StringBuilder block = new StringBuilder();
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date());
        appendLine(block, time, level, message == null ? "" : message);
        if (error != null) {
            String stack = Log.getStackTraceString(error);
            for (String line : stack.split("\n")) {
                appendLine(block, time, level, line);
            }
        }
        // Never blocks the caller; drops the block only if the queue is full,
        // which cannot happen with an unbounded queue.
        QUEUE.offer(block.toString());
    }

    private static void appendLine(StringBuilder out, String time, char level, String text) {
        out.append(time).append(' ').append(level).append(' ').append(text).append('\n');
    }

    private static synchronized void ensureWriter() {
        if (writerStarted) {
            return;
        }
        writerStarted = true;
        Thread worker = new Thread(LogBook::writeLoop, "DouyinLogBook");
        worker.setDaemon(true);
        worker.start();
    }

    private static void writeLoop() {
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        deleteOldFiles(dayFormat);
        String currentDay = "";
        FileOutputStream stream = null;
        OutputStreamWriter writer = null;
        try {
            while (true) {
                String block = QUEUE.take();
                String day = dayFormat.format(new Date());
                if (!day.equals(currentDay)) {
                    closeQuietly(writer, stream);
                    writer = null;
                    stream = null;
                    File file = openDayFile(day);
                    if (file == null) {
                        fileEnabled = false;
                        return;
                    }
                    stream = new FileOutputStream(file, true);
                    writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
                    currentDay = day;
                }
                writer.write(block);
                writer.flush();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException failed) {
            fileEnabled = false;
            Log.w(DouyinModule.TAG, "file logging disabled", failed);
        } finally {
            closeQuietly(writer, stream);
        }
    }

    private static File openDayFile(String day) {
        try {
            File dir = logDir();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            return new File(dir, fileNameForDay(day));
        } catch (RuntimeException failed) {
            return null;
        }
    }

    private static void deleteOldFiles(SimpleDateFormat dayFormat) {
        try {
            long cutoff = System.currentTimeMillis()
                    - (long) (RETENTION_DAYS - 1) * 24L * 60L * 60L * 1000L;
            String cutoffDay = dayFormat.format(new Date(cutoff));
            File dir = logDir();
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (!shouldRetain(file.getName(), cutoffDay) && !file.delete()) {
                    Log.d(DouyinModule.TAG, "could not delete old log: " + file.getName());
                }
            }
        } catch (RuntimeException ignored) {
            // Retention is best-effort; never break logging.
        }
    }

    private static void closeQuietly(OutputStreamWriter writer, FileOutputStream stream) {
        try {
            if (writer != null) {
                writer.close();
            } else if (stream != null) {
                stream.close();
            }
        } catch (IOException | RuntimeException ignored) {
            // Best-effort close.
        }
    }

    static String fileNameForDay(String day) {
        return FILE_PREFIX + day + FILE_SUFFIX;
    }

    static boolean shouldRetain(String fileName, String cutoffDay) {
        if (fileName == null
                || !fileName.startsWith(FILE_PREFIX)
                || !fileName.endsWith(FILE_SUFFIX)) {
            return true;
        }
        String day = fileName.substring(
                FILE_PREFIX.length(),
                fileName.length() - FILE_SUFFIX.length()
        );
        return day.compareTo(cutoffDay) >= 0;
    }

    /** Returns the D/I/W/E level of a formatted line, or 0 when unparseable. */
    static char parseLevel(String line) {
        if (line == null
                || line.length() < 15
                || line.charAt(12) != ' '
                || line.charAt(14) != ' ') {
            return 0;
        }
        char level = line.charAt(13);
        return level == 'D' || level == 'I' || level == 'W' || level == 'E' ? level : 0;
    }

    static int levelRank(char level) {
        switch (level) {
            case 'E':
                return 3;
            case 'W':
                return 2;
            case 'I':
                return 1;
            default:
                return 0;
        }
    }

    /** Log directory shared with the settings UI (target app's media dir). */
    @SuppressWarnings("deprecation")
    public static File logDir() {
        return new File(
                Environment.getExternalStorageDirectory(),
                LOG_SUBDIR
        );
    }

    /** Log files newest first; empty when the directory is missing or unreadable. */
    public static List<File> listLogFiles() {
        File[] files;
        try {
            File dir = logDir();
            files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File ignored, String name) {
                    return name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX);
                }
            });
        } catch (RuntimeException failed) {
            return Collections.emptyList();
        }
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        Collections.sort(sorted, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return right.getName().compareTo(left.getName());
            }
        });
        return sorted;
    }

    /**
     * Reads the tail of a log file for display, newest lines last, capped for
     * TextView performance. Lines below {@code minLevel} are dropped; lines
     * without a parseable level are always kept so diagnostics are never hidden.
     */
    public static String readTail(File file, int maxLines, char minLevel) {
        if (file == null) {
            return "";
        }
        byte[] raw;
        try {
            long length = file.length();
            long skip = Math.max(0L, length - MAX_TAIL_BYTES);
            int size = (int) Math.min(length - skip, MAX_TAIL_BYTES);
            raw = new byte[size];
            FileInputStream input = new FileInputStream(file);
            try {
                long remaining = skip;
                while (remaining > 0L) {
                    long skipped = input.skip(remaining);
                    if (skipped <= 0L) {
                        break;
                    }
                    remaining -= skipped;
                }
                int offset = 0;
                while (offset < size) {
                    int read = input.read(raw, offset, size - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
                if (offset < size) {
                    byte[] trimmed = new byte[offset];
                    System.arraycopy(raw, 0, trimmed, 0, offset);
                    raw = trimmed;
                }
            } finally {
                input.close();
            }
        } catch (IOException | RuntimeException failed) {
            return "";
        }
        String[] lines = new String(raw, StandardCharsets.UTF_8).split("\n");
        int minRank = levelRank(minLevel);
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            char level = parseLevel(line);
            if (level == 0 || levelRank(level) >= minRank) {
                kept.add(line);
            }
        }
        int limit = Math.min(maxLines, MAX_VIEW_LINES);
        int start = Math.max(0, kept.size() - limit);
        StringBuilder out = new StringBuilder();
        for (int index = start; index < kept.size(); index++) {
            out.append(kept.get(index)).append('\n');
        }
        return out.toString();
    }

    /** Deletes all module log files; returns true when none remain. */
    public static boolean clearLogs() {
        boolean clean = true;
        for (File file : listLogFiles()) {
            try {
                if (!file.delete()) {
                    clean = false;
                }
            } catch (RuntimeException failed) {
                clean = false;
            }
        }
        return clean;
    }
}
