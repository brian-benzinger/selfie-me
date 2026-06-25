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
import android.app.PendingIntent;
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
import com.benzinger.selfieme.receivers.AlarmReceiver;
import com.benzinger.selfieme.storage.SelfieStorage;

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
import org.robolectric.shadows.ShadowPendingIntent;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

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
    public void onCreate_alarmPendingIntentTargetsAlarmReceiverExplicitly() {
        // CLAUDE.md contracts: alarm must use an explicit intent to AlarmReceiver (implicit broadcasts
        // to manifest receivers are restricted on modern Android) and must be a broadcast PendingIntent.
        SelfieMainActivity activity = createActivity();
        AlarmManager alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager.ScheduledAlarm alarm = shadowOf(alarmManager).getNextScheduledAlarm();
        assertNotNull(alarm);

        ShadowPendingIntent shadowOp = shadowOf(alarm.operation);
        assertTrue("alarm PendingIntent must be a broadcast (not activity/service)",
                shadowOp.isBroadcast());
        assertEquals("alarm intent must explicitly target AlarmReceiver",
                AlarmReceiver.class.getName(),
                shadowOp.getSavedIntent().getComponent().getClassName());
        assertTrue("alarm PendingIntent must set FLAG_IMMUTABLE (required from API 31+)",
                (shadowOp.getFlags() & PendingIntent.FLAG_IMMUTABLE) != 0);
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
        assertTrue("camera intent must grant write permission via FLAG_GRANT_WRITE_URI_PERMISSION",
                (started.intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0);
        // CLAUDE.md contract: EXTRA_OUTPUT must use a content:// FileProvider URI, never a raw file://.
        Uri outputUri = started.intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri.class);
        assertNotNull(outputUri);
        assertEquals("EXTRA_OUTPUT must use content:// URI (FileProvider), not a raw file:// URI",
                "content", outputUri.getScheme());
        // openCamera() must pre-create currentFile so the camera has a concrete path to write to.
        assertNotNull("openCamera() must assign currentFile before launching the camera",
                activity.currentFile);
        assertTrue("pre-created currentFile must exist on disk before the camera is launched",
                activity.currentFile.exists());
        assertEquals("pre-created file must live in the app's external files dir",
                activity.getExternalFilesDir(null), activity.currentFile.getParentFile());
        assertTrue("pre-created file name must start with the selfie_ prefix",
                activity.currentFile.getName().startsWith(SelfieStorage.FILE_PREFIX));
        assertTrue("pre-created file name must end with .jpg",
                activity.currentFile.getName().endsWith(SelfieStorage.FILE_SUFFIX));
    }

    @Test
    public void nonCameraOptionsItem_isNotHandled() {
        SelfieMainActivity activity = createActivity();
        boolean handled = activity.onOptionsItemSelected(new RoboMenuItem(android.R.id.home));
        assertFalse(handled);
    }

    @Test
    public void noCameraApp_doesNotLaunchCameraOrCreateFile() {
        // Without registerCameraApp(), resolveActivity() returns null for ACTION_IMAGE_CAPTURE.
        // openCamera() must short-circuit: no intent launched and currentFile stays null.
        SelfieMainActivity activity = createActivity();

        activity.onOptionsItemSelected(new RoboMenuItem(R.id.action_camera));

        assertNull("currentFile must remain null when no camera app resolves ACTION_IMAGE_CAPTURE",
                activity.currentFile);
        assertNull("no camera intent should be launched when resolveActivity() returns null",
                shadowOf(activity).getNextStartedActivityForResult());
    }

    @Test
    public void captureSuccess_showsYouveBeenSelfiedToast() throws IOException {
        SelfieMainActivity activity = createActivity();
        activity.currentFile = newCapturedFile(activity);
        activity.onCaptureResult(new ActivityResult(Activity.RESULT_OK, null));
        assertEquals("You've been Selfied!", ShadowToast.getTextOfLatestToast());
    }

    @Test
    @Config(sdk = 33)
    public void onCreate_tiramisu_withPermissionNotGranted_requestsNotificationPermission() {
        // POST_NOTIFICATIONS is denied by default in Robolectric; the activity must call requestPermissions.
        SelfieMainActivity activity = createActivity();
        ShadowActivity.PermissionsRequest requested = shadowOf(activity).getLastRequestedPermission();
        assertNotNull("requestPermissions must be called on API 33 when POST_NOTIFICATIONS is not granted",
                requested);
        assertTrue("POST_NOTIFICATIONS must be among the requested permissions",
                Arrays.asList(requested.requestedPermissions)
                        .contains(Manifest.permission.POST_NOTIFICATIONS));
    }

    @Test
    public void createImageFile_returnsFileInStorageDir() {
        SelfieMainActivity activity = createActivity();
        File dir = activity.getExternalFilesDir(null);

        File created = activity.createImageFile(dir);

        assertNotNull(created);
        assertTrue(created.exists());
        assertEquals(dir, created.getParentFile());
        // CLAUDE.md contract: pre-created file must follow the selfie_<timestamp>_ naming scheme.
        // If createImageFile switched to a different prefix or suffix, the camera flow would
        // break (FullScreenPicActivity uses the path, deleteSelfie uses the URI). Verify both ends.
        assertTrue("createImageFile must produce a file whose name starts with FILE_PREFIX",
                created.getName().startsWith(SelfieStorage.FILE_PREFIX));
        assertTrue("createImageFile must produce a file whose name ends with FILE_SUFFIX (.jpg)",
                created.getName().endsWith(SelfieStorage.FILE_SUFFIX));
    }

    @Test
    public void createImageFile_returnsNullWhenDirectoryUnusable() {
        SelfieMainActivity activity = createActivity();
        // A non-existent directory makes File.createTempFile throw IOException, which is swallowed.
        File badDir = new File(activity.getExternalFilesDir(null), "missing-subdir");
        assertNull(activity.createImageFile(badDir));
        // The error must be surfaced to the user via a Toast, not silently swallowed.
        assertTrue("file creation error must show a storage-problem Toast",
                ShadowToast.getTextOfLatestToast().startsWith("Problem creating file in external storage:"));
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
    public void captureSuccess_addsFileUriForCapturedFile() throws IOException {
        // CLAUDE.md contract: "the list/display path uses file:// URIs". onCaptureResult must add
        // Uri.fromFile(currentFile) — a file:// URI — not a content:// URI or any other form.
        // If the scheme were wrong, FullScreenPicActivity.getPath() and deleteSelfie's scheme check
        // would silently break in production even though the count-only test still passes.
        SelfieMainActivity activity = createActivity();
        File capturedFile = newCapturedFile(activity);
        activity.currentFile = capturedFile;

        activity.onCaptureResult(new ActivityResult(Activity.RESULT_OK, null));

        Uri addedUri = activity.picturePaths.get(activity.picturePaths.size() - 1);
        assertEquals("captured URI must use the file:// scheme (CLAUDE.md display contract)",
                "file", addedUri.getScheme());
        assertEquals("captured URI path must match the captured file",
                capturedFile.getAbsolutePath(), addedUri.getPath());
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
        // The "You've been Selfied!" toast is shown unconditionally on RESULT_OK — even when
        // currentFile is null — because the toast precedes the null guard.
        assertEquals("You've been Selfied!", ShadowToast.getTextOfLatestToast());
        assertEquals(0, gridView.getAdapter().getCount());
    }

    @Test
    public void captureCancelled_showsCancelledShotToast() throws IOException {
        SelfieMainActivity activity = createActivity();
        activity.currentFile = newCapturedFile(activity);
        activity.onCaptureResult(new ActivityResult(Activity.RESULT_CANCELED, null));
        assertEquals("Cancelled the shot", ShadowToast.getTextOfLatestToast());
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
    public void contextMenuDelete_removesItemAtCorrectPosition() throws IOException {
        // The existing delete test always uses position 0. This test verifies that
        // selectedPosition (set by onCreateContextMenu) drives the delete correctly when
        // a non-zero position is long-pressed — deleting seed2 at position 1 must leave
        // seed1 at position 0 untouched. A bug that hardcodes position 0 would pass the
        // existing test but fail here.
        SelfieMainActivity activity = createActivity();
        File seed1 = seedSelfie("selfie_pos0.jpg");
        File seed2 = seedSelfie("selfie_pos1.jpg");
        activity.picturePaths.add(Uri.fromFile(seed1));
        activity.picturePaths.add(Uri.fromFile(seed2));
        GridView gridView = activity.findViewById(R.id.gridview);

        FakeContextMenu menu = new FakeContextMenu(activity);
        AdapterView.AdapterContextMenuInfo info = new AdapterView.AdapterContextMenuInfo(gridView, 1, 1);
        activity.onCreateContextMenu(menu, gridView, info);
        boolean handled = activity.onContextItemSelected(new RoboMenuItem(R.id.delete_pic));

        assertTrue(handled);
        assertEquals("only one selfie must remain after deleting position 1", 1, activity.picturePaths.size());
        assertTrue("seed1 at position 0 must still exist on disk", seed1.exists());
        assertFalse("seed2 at position 1 must be deleted from disk", seed2.exists());
        assertEquals("remaining picturePath must reference seed1",
                Uri.fromFile(seed1), activity.picturePaths.get(0));
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
    public void onCreate_tiramisu_withPermissionGranted_doesNotRequestNotificationPermission() {
        // When the permission is already granted, requestPermissions must NOT be called — calling it
        // again would show an unnecessary system dialog.
        Application app = (Application) ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        SelfieMainActivity activity = createActivity();
        assertNotNull(activity);
        assertNull("must not re-request POST_NOTIFICATIONS when already granted",
                shadowOf(activity).getLastRequestedPermission());
    }

    @Test
    @Config(sdk = 26)
    public void onCreate_belowTiramisu_doesNotRequestNotificationPermission() {
        SelfieMainActivity activity = createActivity();
        assertNotNull(activity);
        assertNull("below API 33 must not request POST_NOTIFICATIONS",
                shadowOf(activity).getLastRequestedPermission());
    }

    @Test
    public void loadPics_findsExistingFilesInExternalStorage() throws IOException {
        // createActivity() initializes Robolectric's external storage, so seedSelfie()
        // can then write into the same dir that getExternalFilesDir(null) will return.
        // loadPics() is package-private (following the onCaptureResult/createImageFile
        // pattern) so we can call it directly after seeding to verify it surfaces the
        // file and adds a file:// URI to picturePaths without going through a fresh
        // activity launch (which NPEs in Robolectric 4.14.1 when two controllers coexist).
        SelfieMainActivity activity = createActivity();
        File seeded = seedSelfie("selfie_preexisting.jpg");

        activity.loadPics();

        assertTrue("file on disk must appear in picturePaths after loadPics()",
                activity.picturePaths.contains(Uri.fromFile(seeded)));
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
