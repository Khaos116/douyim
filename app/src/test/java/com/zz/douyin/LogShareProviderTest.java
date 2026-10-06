package com.zz.douyin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class LogShareProviderTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void sharesCompleteIndependentSnapshotsWithOriginalName() throws Exception {
        File source = folder.newFile("runtime.2026-10-06.log");
        File cache = folder.newFolder("cache");
        byte[] original = ("12:00:00.000 D 完整日志，不受预览限制\n".repeat(12000))
                .getBytes(StandardCharsets.UTF_8);
        Files.write(source.toPath(), original);

        File first = LogShareProvider.snapshot(source, cache);
        assertNotEquals(source.getCanonicalFile(), first.getCanonicalFile());
        assertEquals(source.getName(), first.getName());
        assertArrayEquals(original, Files.readAllBytes(first.toPath()));

        Files.writeString(source.toPath(), "new content");
        File second = LogShareProvider.snapshot(source, cache);
        assertNotEquals(first, second);
        Files.delete(source.toPath());
        assertArrayEquals(original, Files.readAllBytes(first.toPath()));
        assertEquals("new content", Files.readString(second.toPath()));
    }

    @Test
    public void missingSourceFailsWithoutCreatingAnExport() throws Exception {
        File cache = folder.newFolder("cache");
        assertThrows(IOException.class, () -> LogShareProvider.snapshot(
                new File(folder.getRoot(), "runtime.2026-10-06.log"), cache));
        assertEquals(0, cache.list().length);
    }

    @Test
    public void onlyResolvesExistingLogsInsideTheExportDirectory() throws Exception {
        File source = folder.newFile("runtime.2026-10-06.log");
        Files.writeString(source.toPath(), "log");
        File cache = folder.newFolder("cache");
        File exported = LogShareProvider.snapshot(source, cache);
        String path = exported.getParentFile().getName() + "/" + exported.getName();
        assertEquals(exported.getCanonicalFile(), LogShareProvider.resolve(cache, path));
        assertThrows(IllegalArgumentException.class,
                () -> LogShareProvider.resolve(cache, "../runtime.2026-10-06.log"));
        assertThrows(IllegalArgumentException.class,
                () -> LogShareProvider.resolve(cache, "../shared-logs-other/runtime.2026-10-06.log"));
        assertThrows(IllegalArgumentException.class,
                () -> LogShareProvider.resolve(cache, "log-1/../../runtime.2026-10-06.log"));
        assertThrows(IllegalArgumentException.class,
                () -> LogShareProvider.resolve(cache, source.getAbsolutePath()));
        assertThrows(IllegalArgumentException.class,
                () -> LogShareProvider.resolve(cache, "log-1/runtime.2026-10-06.log"));
    }
}
