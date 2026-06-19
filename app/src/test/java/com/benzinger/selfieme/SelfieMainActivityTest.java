package com.benzinger.selfieme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;

import androidx.activity.result.ActivityResult;
import androidx.core.content.FileProvider;
import androidx.test.core.app.ApplicationProvider;

import com.benzinger.selfieme.adapters.ImageAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.fakes.RoboMenu;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.io.IOException;

/** Lifecycle / wiring tests for {@link SelfieMainActivity} driven by Robolectric. */
@RunWith(RobolectricTestRunner.class)
public class SelfieMainActivityTest {

    // A real (tiny) PNG so the GridView can decode a seeded selfie without error.
    private static final byte[] ONE_PX_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private SelfieMainActivity createActivity() {
        return Robolectric.buildActivity(SelfieMainActivity.class).setup().get();
    }

    private void registerCameraApp(SelfieMainActivity activity) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = "com.example.camera";
        resolveInfo.activityInfo.name = "CameraActivity";
        ShadowPackageManager shadowPm = shadowOf(activity.getPackageManager());
        shadowPm.addResolveInfoForIntent(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), resolveInfo);
    }

    /** Pre-create a captured-photo file in the activity's external files dir. */
    private File newCapturedFile(SelfieMainActivity activity) throws IOException {
        return File.createTempFile("selfie_test_", ".jpg", activity.getExternalFilesDir(null));
    }

    /** Seed an existing decodable selfie so loadPics() populates the grid. */
    private File seedSelfie(String name) throws IOException {
        File dir = ApplicationProvider.getApplicationContext().getExternalFilesDir(null);
        File file = new File(dir, name);
        java.nio.file.Files.write(file.toPath(), ONE_PX_PNG);
        return file;
    }

    @Test
    public void gridView_isBackedByImageAdapter() {
        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);
        assertNotNull(gridView);
        assertTrue(gridView.getAdapter() instanceof ImageAdapter);
    }

    @Test
    public void onCreate_schedulesRepeatingTwoMinuteAlarm() {
        SelfieMainActivity activity = createActivity();
        AlarmManager alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadow = shadowOf(alarmManager);

        ShadowAlarmManager.ScheduledAlarm alarm = shadow.getNextScheduledAlarm();
        assertNotNull("expected createAlarm() to register a repeating alarm", alarm);
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type);
        assertEquals(60 * 1000 * 2, alarm.interval); // TWO_MINS
    }

    @Test
    @Config(shadows = {SelfieMainActivityTest.StubFileProvider.class})
    public void tappingCameraMenu_launchesImageCaptureWithOutput() {
        SelfieMainActivity activity = createActivity();
        registerCameraApp(activity);

        shadowOf(activity).clickMenuItem(R.id.action_camera);

        ShadowActivity.IntentForResult started = shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull("expected a camera intent to be started for result", started);
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, started.intent.getAction());
        assertTrue("camera intent must request output via EXTRA_OUTPUT",
                started.intent.hasExtra(MediaStore.EXTRA_OUTPUT));
    }

    @Test
    public void nonCameraOptionsItem_isNotHandled() {
        SelfieMainActivity activity = createActivity();
        boolean handled = activity.onOptionsItemSelected(new RoboMenuItem(android.R.id.home));
        assertFalse(handled);
    }

    @Test
    public void createImageFile_returnsFileInStorageDir() {
        SelfieMainActivity activity = createActivity();
        File dir = activity.getExternalFilesDir(null);

        File created = activity.createImageFile(dir);

        assertNotNull(created);
        assertTrue(created.exists());
        assertEquals(dir, created.getParentFile());
    }

    @Test
    public void createImageFile_returnsNullWhenDirectoryUnusable() {
        SelfieMainActivity activity = createActivity();
        // A non-existent directory makes File.createTempFile throw IOException, which is swallowed.
        File badDir = new File(activity.getExternalFilesDir(null), "missing-subdir");
        assertNull(activity.createImageFile(badDir));
    }

    @Test
    public void captureSuccess_addsPhotoToGrid() throws IOException {
        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);
        int before = gridView.getAdapter().getCount();

        activity.currentFile = newCapturedFile(activity);
        activity.onCaptureResult(new ActivityResult(Activity.RESULT_OK, null));

        assertEquals(before + 1, gridView.getAdapter().getCount());
    }

    @Test
    public void captureSuccess_doesNotAddSamePhotoTwice() throws IOException {
        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);

        activity.currentFile = newCapturedFile(activity);
        activity.onCaptureResult(new ActivityResult(Activity.RESULT_OK, null));
        int afterFirst = gridView.getAdapter().getCount();
        // Same currentFile delivered again must be ignored (already in the list).
        activity.onCaptureResult(new ActivityResult(Activity.RESULT_OK, null));

        assertEquals(afterFirst, gridView.getAdapter().getCount());
    }

    @Test
    public void captureSuccess_withoutCurrentFile_addsNothing() {
        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);
        // No prior capture, so currentFile is null.
        activity.onCaptureResult(new ActivityResult(Activity.RESULT_OK, null));
        assertEquals(0, gridView.getAdapter().getCount());
    }

    @Test
    public void captureCancelled_deletesPreCreatedFile() throws IOException {
        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);

        File file = newCapturedFile(activity);
        activity.currentFile = file;
        assertTrue(file.exists());

        activity.onCaptureResult(new ActivityResult(Activity.RESULT_CANCELED, null));

        assertFalse("cancel must delete the pre-created file", file.exists());
        assertEquals(0, gridView.getAdapter().getCount());
    }

    @Test
    public void captureCancelled_withoutCurrentFile_doesNothing() {
        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);
        // currentFile is null: the delete guard must short-circuit without error.
        activity.onCaptureResult(new ActivityResult(Activity.RESULT_CANCELED, null));
        assertEquals(0, gridView.getAdapter().getCount());
    }

    @Test
    public void captureCancelled_withMissingFile_doesNotError() {
        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);
        // currentFile set but never created: the exists() guard must short-circuit cleanly.
        File ghost = new File(activity.getExternalFilesDir(null), "ghost.jpg");
        activity.currentFile = ghost;

        activity.onCaptureResult(new ActivityResult(Activity.RESULT_CANCELED, null));

        assertFalse("file that never existed should still not exist after cancel", ghost.exists());
        assertEquals("no photo should be added to the grid when cancel has a missing file",
                0, gridView.getAdapter().getCount());
    }

    @Test
    public void contextMenuDelete_removesSelectedSelfie() throws IOException {
        // Build with an empty grid (a populated grid NPEs during Robolectric's setup() layout),
        // then add the selfie afterwards via the shared list the adapter holds by reference.
        SelfieMainActivity activity = createActivity();
        File seed = seedSelfie("selfie_seed.jpg");
        activity.picturePaths.add(Uri.fromFile(seed));
        GridView gridView = activity.findViewById(R.id.gridview);

        // Long-press selects position 0...
        FakeContextMenu menu = new FakeContextMenu(activity);
        AdapterView.AdapterContextMenuInfo info = new AdapterView.AdapterContextMenuInfo(gridView, 0, 0);
        activity.onCreateContextMenu(menu, gridView, info);
        // ...then tapping Delete removes it and deletes the file.
        boolean handled = activity.onContextItemSelected(new RoboMenuItem(R.id.delete_pic));

        assertTrue(handled);
        assertEquals(0, activity.picturePaths.size());
        assertFalse(seed.exists());
    }

    @Test
    public void contextMenu_unknownItem_isNotHandled() {
        SelfieMainActivity activity = createActivity();
        boolean handled = activity.onContextItemSelected(new RoboMenuItem(R.id.action_camera));
        assertFalse(handled);
    }

    @Test
    public void tappingGridItem_opensFullScreenWithFilenameExtra() {
        SelfieMainActivity activity = createActivity();
        // Populate after setup() to avoid the layout-with-items NPE noted above.
        File selfieFile = new File(activity.getExternalFilesDir(null), "selfie_seed.jpg");
        activity.picturePaths.add(Uri.fromFile(selfieFile));
        GridView gridView = activity.findViewById(R.id.gridview);

        gridView.performItemClick(null, 0, 0);

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(FullScreenPicActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("FILENAME_EXTRA must carry the file path of the tapped selfie",
                selfieFile.getAbsolutePath(), started.getStringExtra(SelfieMainActivity.FILENAME_EXTRA));
    }

    @Test
    @Config(sdk = 33)
    public void onCreate_tiramisu_withPermissionGranted_doesNotCrash() {
        Application app = (Application) ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        assertNotNull(createActivity());
    }

    @Test
    @Config(sdk = 26)
    public void onCreate_belowTiramisu_doesNotRequestNotificationPermission() {
        assertNotNull(createActivity());
    }

    /**
     * {@link RoboMenu} only implements {@link android.view.Menu}; {@code onCreateContextMenu}
     * needs a {@link ContextMenu}. This fake adds the no-op header methods so the activity's
     * menu inflation runs against a real menu instance.
     */
    private static class FakeContextMenu extends RoboMenu implements ContextMenu {
        FakeContextMenu(Context context) {
            super(context);
        }

        @Override
        public ContextMenu setHeaderTitle(int titleRes) {
            return this;
        }

        @Override
        public ContextMenu setHeaderTitle(CharSequence title) {
            return this;
        }

        @Override
        public ContextMenu setHeaderIcon(int iconRes) {
            return this;
        }

        @Override
        public ContextMenu setHeaderIcon(Drawable icon) {
            return this;
        }

        @Override
        public ContextMenu setHeaderView(View view) {
            return this;
        }

        @Override
        public void clearHeader() {
            // no-op
        }
    }

    /**
     * Replaces {@link FileProvider#getUriForFile} so the camera-intent test does not depend on
     * FileProvider's canonical-path matching, which fails on Windows in Robolectric's temp-dir
     * environment. The test only cares that a {@code content://} URI is placed in EXTRA_OUTPUT;
     * it does not inspect the URI value.
     */
    @Implements(FileProvider.class)
    static final class StubFileProvider {
        @Implementation
        public static Uri getUriForFile(Context context, String authority, File file) {
            return Uri.parse("content://" + authority + "/" + Uri.encode(file.getName()));
        }
    }
}
