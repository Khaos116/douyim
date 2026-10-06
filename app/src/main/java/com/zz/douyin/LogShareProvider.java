package com.zz.douyin;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Read-only access to the individual log snapshots granted by the share Intent. */
public final class LogShareProvider extends ContentProvider {
    private static final String EXPORT_DIRECTORY = "shared-logs";
    private static final String LOG_NAME_PATTERN = "runtime\\.\\d{4}-\\d{2}-\\d{2}\\.log";

    static File snapshot(File source, File cache) throws IOException {
        if (!source.isFile() || !source.getName().matches(LOG_NAME_PATTERN)) {
            throw new IOException("Log file unavailable");
        }
        File root = new File(cache, EXPORT_DIRECTORY);
        Files.createDirectories(root.toPath());
        // ponytail: keep snapshots in app cache for delayed recipients; prune old exports if frequent sharing fills cache.
        File directory = Files.createTempDirectory(root.toPath(), "log-").toFile();
        File target = new File(directory, source.getName());
        try {
            Files.copy(source.toPath(), target.toPath());
            return target;
        } catch (IOException failed) {
            try {
                Files.deleteIfExists(target.toPath());
                Files.deleteIfExists(directory.toPath());
            } catch (IOException cleanupFailed) {
                failed.addSuppressed(cleanupFailed);
            }
            throw failed;
        }
    }

    static File resolve(File cache, String path) throws IOException {
        if (path == null || !path.matches("log-[A-Za-z0-9_-]+/" + LOG_NAME_PATTERN)) {
            throw new IllegalArgumentException("Invalid log path");
        }
        File root = new File(cache, EXPORT_DIRECTORY).getCanonicalFile();
        File file = new File(root, path).getCanonicalFile();
        if (!file.getPath().startsWith(root.getPath() + File.separator) || !file.isFile()) {
            throw new IllegalArgumentException("Log snapshot unavailable");
        }
        return file;
    }

    private File fileFor(Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 2) {
            throw new IllegalArgumentException("Invalid log URI");
        }
        try {
            return resolve(getContext().getCacheDir(), segments.get(0) + "/" + segments.get(1));
        } catch (IOException failed) {
            throw new IllegalArgumentException("Cannot read log snapshot", failed);
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = fileFor(uri);
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            row.add(OpenableColumns.DISPLAY_NAME.equals(column) ? file.getName()
                    : OpenableColumns.SIZE.equals(column) ? file.length() : null);
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Log snapshots are read-only");
        }
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only log provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only log provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only log provider");
    }
}
