package com.benzinger.selfieme.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Unit tests for {@link SelfieStorage}. Runs on Robolectric because the helper uses
 * {@link android.net.Uri}, which is stubbed (throws) under a plain JVM test.
 */
@RunWith(RobolectricTestRunner.class)
public class SelfieStorageTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void buildImageFileName_usesSelfiePrefixAndTimestamp() {
        // 2026-05-29 13:05:01 local time
        long millis = new GregorianCalendar(2026, Calendar.MAY, 29, 13, 5, 1).getTimeInMillis();
        String name = SelfieStorage.buildImageFileName(new java.util.Date(millis));
        assertEquals("selfie_20260529_130501_", name);
    }

    @Test
    public void loadSelfies_returnsFileUriForEachChild() throws IOException {
        File dir = tmp.newFolder();
        assertTrue(new File(dir, "a.jpg").createNewFile());
        assertTrue(new File(dir, "b.jpg").createNewFile());

        List<Uri> result = SelfieStorage.loadSelfies(dir, null);

        assertEquals(2, result.size());
        for (Uri uri : result) {
            assertEquals("file", uri.getScheme());
        }
    }

    @Test
    public void loadSelfies_skipsUrisAlreadyInList() throws IOException {
        File dir = tmp.newFolder();
        assertTrue(new File(dir, "a.jpg").createNewFile());

        List<Uri> list = new ArrayList<>();
        SelfieStorage.loadSelfies(dir, list);
        SelfieStorage.loadSelfies(dir, list); // second scan must not duplicate

        assertEquals(1, list.size());
    }

    @Test
    public void loadSelfies_nullDirectoryYieldsEmptyList() {
        assertTrue(SelfieStorage.loadSelfies(null, null).isEmpty());
    }

    @Test
    public void loadSelfies_nonDirectoryYieldsEmptyList() throws IOException {
        // listFiles() returns null for a regular file, exercising the children == null branch.
        File notADir = tmp.newFile("regular.txt");
        assertTrue(SelfieStorage.loadSelfies(notADir, null).isEmpty());
    }

    @Test
    public void deleteSelfie_deletesExistingFileUri() throws IOException {
        File dir = tmp.newFolder();
        File file = new File(dir, "selfie_x.jpg");
        assertTrue(file.createNewFile());

        boolean deleted = SelfieStorage.deleteSelfie(dir, Uri.fromFile(file));

        assertTrue(deleted);
        assertFalse(file.exists());
    }

    @Test
    public void deleteSelfie_missingFileReturnsFalse() throws IOException {
        File dir = tmp.newFolder();
        Uri uri = Uri.fromFile(new File(dir, "does_not_exist.jpg"));
        assertFalse(SelfieStorage.deleteSelfie(dir, uri));
    }

    @Test
    public void deleteSelfie_nonFileSchemeReturnsFalse() throws IOException {
        File dir = tmp.newFolder();
        assertFalse(SelfieStorage.deleteSelfie(dir, Uri.parse("content://media/external/images/1")));
    }

    @Test
    public void deleteSelfie_nullUriReturnsFalse() throws IOException {
        assertFalse(SelfieStorage.deleteSelfie(tmp.newFolder(), null));
    }

    @Test
    public void deleteSelfie_fileUriWithoutSegmentReturnsFalse() throws IOException {
        // "file:///" has the file scheme but resolves to the root "/", which cannot be deleted.
        assertFalse(SelfieStorage.deleteSelfie(tmp.newFolder(), Uri.parse("file:///")));
    }

    @Test
    public void privateConstructor_isInvocable() throws Exception {
        // SelfieStorage is a stateless utility; exercising the hidden constructor keeps it covered.
        java.lang.reflect.Constructor<SelfieStorage> constructor =
                SelfieStorage.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
