package com.benzinger.selfieme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.provider.MediaStore;
import android.widget.GridView;

import androidx.test.core.app.ApplicationProvider;

import com.benzinger.selfieme.adapters.ImageAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.io.IOException;

/** Lifecycle / wiring tests for {@link SelfieMainActivity} driven by Robolectric. */
@RunWith(RobolectricTestRunner.class)
public class SelfieMainActivityTest {

    private SelfieMainActivity createActivity() {
        return Robolectric.buildActivity(SelfieMainActivity.class).setup().get();
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

        // Register a fake camera app so resolveActivity() succeeds.
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = "com.example.camera";
        resolveInfo.activityInfo.name = "CameraActivity";
        ShadowPackageManager shadowPm = shadowOf(activity.getPackageManager());
        shadowPm.addResolveInfoForIntent(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), resolveInfo);

        shadowOf(activity).clickMenuItem(R.id.action_camera);

        ShadowActivity.IntentForResult started = shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull("expected a camera intent to be started for result", started);
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, started.intent.getAction());
        assertTrue("camera intent must request output via EXTRA_OUTPUT",
                started.intent.hasExtra(MediaStore.EXTRA_OUTPUT));
    }

    @Test
    public void tappingGridItem_opensFullScreenWithFilenameExtra() throws IOException {
        // Seed an existing selfie so loadPics() populates the grid.
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
}
