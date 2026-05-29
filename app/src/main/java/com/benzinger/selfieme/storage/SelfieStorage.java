package com.benzinger.selfieme.storage;

import android.net.Uri;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Pure file-system / URI helpers for the selfie store, extracted from
 * {@code SelfieMainActivity} so the scan / delete / naming logic can be unit tested
 * without driving the activity. All list/display URIs are {@code file://} URIs
 * (see the contracts in CLAUDE.md); the FileProvider {@code content://} URI used for
 * camera capture is built in the activity and is deliberately not modelled here.
 */
public final class SelfieStorage {

    public static final String FILE_PREFIX = "selfie_";
    public static final String FILE_SUFFIX = ".jpg";
    private static final String TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss";

    private SelfieStorage() {
    }

    /**
     * Builds the temp-file base name used for a capture, e.g. {@code "selfie_20260529_130501_"}.
     * The trailing underscore separates the prefix from the random suffix that
     * {@link File#createTempFile(String, String, File)} appends.
     */
    public static String buildImageFileName(Date date) {
        String timeStamp = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).format(date);
        return FILE_PREFIX + timeStamp + "_";
    }

    /**
     * Scans {@code storageDir} and appends a {@code file://} {@link Uri} for each file to
     * {@code into}, skipping any already present. A null {@code into} starts a fresh list.
     * Missing/unreadable directories yield no additions. Returns the populated list.
     */
    public static List<Uri> loadSelfies(File storageDir, List<Uri> into) {
        List<Uri> list = (into != null) ? into : new ArrayList<Uri>();
        if (storageDir != null) {
            File[] children = storageDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    Uri uri = Uri.fromFile(child);
                    if (!list.contains(uri)) {
                        list.add(uri);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Deletes the file referenced by a {@code file://} {@code uri}, resolved against
     * {@code storageDir} by its last path segment. Returns true only when a matching file
     * existed and was deleted; non-{@code file} schemes and missing files return false.
     */
    public static boolean deleteSelfie(File storageDir, Uri uri) {
        if (uri == null || !"file".equals(uri.getScheme())) {
            return false;
        }
        String fileName = uri.getLastPathSegment();
        if (fileName == null) {
            return false;
        }
        File file = new File(storageDir, fileName);
        return file.exists() && file.delete();
    }
}
