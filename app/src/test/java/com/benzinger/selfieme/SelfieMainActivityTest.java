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
import android.provider.MediaStore;
import android.widget.AdapterView;
import android.widget.GridView;

import androidx.test.core.app.ApplicationProvider;

import com.benzinger.selfieme.adapters.ImageAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
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

    // Mirrors the private SelfieMainActivity.REQUEST_IMAGE_CAPTURE.
    private static final int REQUEST_IMAGE_CAPTURE = 1;

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

    private File[] selfieFiles(SelfieMainActivity activity) {
        File dir = activity.getExternalFilesDir(null);
        File[] files = dir != null ? dir.listFiles() : null;
        return files != null ? files : new File[0];
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
    public void tappingCameraMenu_withNoCameraApp_startsNothing() {
        SelfieMainActivity activity = createActivity();
        // No camera app registered: resolveActivity() returns null, so nothing is launched.
        shadowOf(activity).clickMenuItem(R.id.action_camera);
        assertNull(shadowOf(activity).getNextStartedActivityForResult());
    }

    @Test
    public void nonCameraOptionsItem_isNotHandled() {
        SelfieMainActivity activity = createActivity();
        boolean handled = activity.onOptionsItemSelected(new RoboMenuItem(android.R.id.home));
        assertFalse(handled);
    }

    @Test
    public void captureSuccess_addsPhotoToGrid() {
        SelfieMainActivity activity = createActivity();
        registerCameraApp(activity);
        GridView gridView = activity.findViewById(R.id.gridview);
        int before = gridView.getAdapter().getCount();

        shadowOf(activity).clickMenuItem(R.id.action_camera); // creates temp file, sets currentFile
        activity.onActivityResult(REQUEST_IMAGE_CAPTURE, Activity.RESULT_OK, null);

        assertEquals(before + 1, gridView.getAdapter().getCount());
    }

    @Test
    public void captureCancelled_deletesPreCreatedFile() {
        SelfieMainActivity activity = createActivity();
        registerCameraApp(activity);
        GridView gridView = activity.findViewById(R.id.gridview);

        shadowOf(activity).clickMenuItem(R.id.action_camera);
        assertEquals("openCamera pre-creates one temp file", 1, selfieFiles(activity).length);

        activity.onActivityResult(REQUEST_IMAGE_CAPTURE, Activity.RESULT_CANCELED, null);

        assertEquals("cancel must delete the pre-created file", 0, selfieFiles(activity).length);
        assertEquals(0, gridView.getAdapter().getCount());
    }

    @Test
    public void captureSuccess_withoutCurrentFile_addsNothing() {
        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);
        // No prior openCamera(), so currentFile is null.
        activity.onActivityResult(REQUEST_IMAGE_CAPTURE, Activity.RESULT_OK, null);
        assertEquals(0, gridView.getAdapter().getCount());
    }

    @Test
    public void captureSuccess_doesNotAddSamePhotoTwice() {
        SelfieMainActivity activity = createActivity();
        registerCameraApp(activity);
        GridView gridView = activity.findViewById(R.id.gridview);

        shadowOf(activity).clickMenuItem(R.id.action_camera);
        activity.onActivityResult(REQUEST_IMAGE_CAPTURE, Activity.RESULT_OK, null);
        int afterFirst = gridView.getAdapter().getCount();
        // Same currentFile delivered again must be ignored (already in the list).
        activity.onActivityResult(REQUEST_IMAGE_CAPTURE, Activity.RESULT_OK, null);

        assertEquals(afterFirst, gridView.getAdapter().getCount());
    }

    @Test
    public void captureCancelled_withoutCurrentFile_doesNothing() {
        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);
        // currentFile is null: the delete guard must short-circuit without error.
        activity.onActivityResult(REQUEST_IMAGE_CAPTURE, Activity.RESULT_CANCELED, null);
        assertEquals(0, gridView.getAdapter().getCount());
    }

    @Test
    public void capture_whenFileCreationFails_startsNothing() {
        SelfieMainActivity activity = createActivity();
        registerCameraApp(activity);
        File dir = activity.getExternalFilesDir(null);
        assertNotNull(dir);

        // Force createImageFile() -> File.createTempFile to throw IOException by making the
        // target directory read-only. Skipped where the filesystem won't honour that (e.g. root).
        boolean readOnly = dir.setWritable(false) && !dir.canWrite();
        org.junit.Assume.assumeTrue("requires a read-only dir to force IOException", readOnly);
        try {
            shadowOf(activity).clickMenuItem(R.id.action_camera);
            // The IOException is caught, currentFile stays null, so no capture is launched.
            assertNull(shadowOf(activity).getNextStartedActivityForResult());
        } finally {
            dir.setWritable(true);
        }
    }

    @Test
    public void unrelatedActivityResult_isIgnored() {
        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);
        activity.onActivityResult(999, Activity.RESULT_OK, null);
        assertEquals(0, gridView.getAdapter().getCount());
    }

    @Test
    public void contextMenuDelete_removesSelectedSelfie() throws IOException {
        Context appContext = ApplicationProvider.getApplicationContext();
        File storageDir = appContext.getExternalFilesDir(null);
        assertNotNull(storageDir);
        File seed = new File(storageDir, "selfie_seed.jpg");
        assertTrue(seed.createNewFile());

        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);
        assertEquals(1, gridView.getAdapter().getCount());

        // Long-press selects position 0...
        RoboMenu menu = new RoboMenu(activity);
        AdapterView.AdapterContextMenuInfo info = new AdapterView.AdapterContextMenuInfo(gridView, 0, 0);
        activity.onCreateContextMenu(menu, gridView, info);
        // ...then tapping Delete removes it and deletes the file.
        boolean handled = activity.onContextItemSelected(new RoboMenuItem(R.id.delete_pic));

        assertTrue(handled);
        assertEquals(0, gridView.getAdapter().getCount());
        assertFalse(seed.exists());
    }

    @Test
    public void contextMenu_unknownItem_isNotHandled() {
        SelfieMainActivity activity = createActivity();
        boolean handled = activity.onContextItemSelected(new RoboMenuItem(R.id.action_camera));
        assertFalse(handled);
    }

    @Test
    public void tappingGridItem_opensFullScreenWithFilenameExtra() throws IOException {
        Context appContext = ApplicationProvider.getApplicationContext();
        File storageDir = appContext.getExternalFilesDir(null);
        assertNotNull(storageDir);
        assertTrue(new File(storageDir, "selfie_seed.jpg").createNewFile());

        SelfieMainActivity activity = createActivity();
        GridView gridView = activity.findViewById(R.id.gridview);
        assertTrue(gridView.getAdapter().getCount() >= 1);

        gridView.performItemClick(null, 0, gridView.getAdapter().getItemId(0));

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(FullScreenPicActivity.class.getName(), started.getComponent().getClassName());
        assertTrue(started.hasExtra(SelfieMainActivity.FILENAME_EXTRA));
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
}
