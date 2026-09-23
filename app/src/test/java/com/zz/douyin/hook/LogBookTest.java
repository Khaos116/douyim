package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class LogBookTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void fileNameUsesDaySuffix() {
        assertEquals("runtime.2026-09-23.log", LogBook.fileNameForDay("2026-09-23"));
    }

    @Test
    public void retentionKeepsRecentWeek() {
        assertTrue(LogBook.shouldRetain("runtime.2026-09-23.log", "2026-09-17"));
        assertTrue(LogBook.shouldRetain("runtime.2026-09-17.log", "2026-09-17"));
        assertFalse(LogBook.shouldRetain("runtime.2026-09-16.log", "2026-09-17"));
    }

    @Test
    public void retentionIgnoresForeignFiles() {
        assertTrue(LogBook.shouldRetain("other.txt", "2026-09-17"));
        assertTrue(LogBook.shouldRetain(null, "2026-09-17"));
    }

    @Test
    public void parseLevelReadsFormattedLines() {
        assertEquals('D', LogBook.parseLevel("12:34:56.789 D message"));
        assertEquals('I', LogBook.parseLevel("12:34:56.789 I message"));
        assertEquals('W', LogBook.parseLevel("12:34:56.789 W message"));
        assertEquals('E', LogBook.parseLevel("12:34:56.789 E message"));
    }

    @Test
    public void parseLevelRejectsMalformedLines() {
        assertEquals(0, LogBook.parseLevel(null));
        assertEquals(0, LogBook.parseLevel(""));
        assertEquals(0, LogBook.parseLevel("short"));
        assertEquals(0, LogBook.parseLevel("12:34:56.789 X message"));
        assertEquals(0, LogBook.parseLevel("12:34:56.789 Imessage"));
    }

    @Test
    public void levelRankOrdersSeverities() {
        assertTrue(LogBook.levelRank('E') > LogBook.levelRank('W'));
        assertTrue(LogBook.levelRank('W') > LogBook.levelRank('I'));
        assertTrue(LogBook.levelRank('I') > LogBook.levelRank('D'));
        assertEquals(0, LogBook.levelRank('D'));
    }

    @Test
    public void readTailFiltersByLevelButKeepsUnknownLines() throws Exception {
        File file = folder.newFile("runtime.2026-09-23.log");
        OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8);
        writer.write("12:00:00.000 D debug line\n");
        writer.write("12:00:01.000 I info line\n");
        writer.write("unformatted continuation\n");
        writer.write("12:00:02.000 W warn line\n");
        writer.write("12:00:03.000 E error line\n");
        writer.close();

        String warnings = LogBook.readTail(file, 100, 'W');
        assertTrue(warnings.contains("warn line"));
        assertTrue(warnings.contains("error line"));
        assertTrue(warnings.contains("unformatted continuation"));
        assertFalse(warnings.contains("debug line"));
        assertFalse(warnings.contains("info line"));

        String all = LogBook.readTail(file, 100, 'D');
        assertTrue(all.contains("debug line"));
        assertTrue(all.contains("error line"));
    }

    @Test
    public void readTailReturnsEmptyForMissingFile() {
        assertEquals("", LogBook.readTail(new File(folder.getRoot(), "nope.log"), 100, 'D'));
        assertEquals("", LogBook.readTail(null, 100, 'D'));
    }
}
