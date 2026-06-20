package com.benzinger.selfieme.receivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.benzinger.selfieme.SelfieMainActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowNotificationManager;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.List;

/** Unit tests for {@link AlarmReceiver}: it must create the channel and post the reminder. */
@RunWith(RobolectricTestRunner.class)
public class AlarmReceiverTest {

    private Context context;
    private NotificationManager notificationManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Test
    public void onReceive_postsExactlyOneReminderNotification() {
        new AlarmReceiver().onReceive(context, new Intent());

        ShadowNotificationManager shadow = shadowOf(notificationManager);
        List<Notification> posted = shadow.getAllNotifications();
        assertEquals(1, posted.size());

        Notification notification = posted.get(0);
        assertEquals("New Selfie Notification",
                notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Time For A New Selfie!",
                notification.extras.getString(Notification.EXTRA_TEXT));
        // Tapping the notification must reopen the app.
        assertNotNull(notification.contentIntent);
    }

    @Test
    public void onReceive_createsReminderChannel() {
        new AlarmReceiver().onReceive(context, new Intent());

        // Query the real (public) NotificationManager API — the shadow's accessor is protected.
        assertNotNull(notificationManager.getNotificationChannel(AlarmReceiver.CHANNEL_ID));
    }

    @Test
    public void onReceive_contentIntentTargetsSelfieMainActivity() {
        new AlarmReceiver().onReceive(context, new Intent());

        Notification notification = shadowOf(notificationManager).getAllNotifications().get(0);
        ShadowPendingIntent shadowPendingIntent = shadowOf(notification.contentIntent);
        Intent tapIntent = shadowPendingIntent.getSavedIntent();
        assertEquals("notification tap must reopen SelfieMainActivity",
                SelfieMainActivity.class.getName(),
                tapIntent.getComponent().getClassName());
        assertTrue("notification contentIntent must set FLAG_IMMUTABLE (required from API 31+)",
                (shadowPendingIntent.getFlags() & PendingIntent.FLAG_IMMUTABLE) != 0);
    }

    @Test
    public void onReceive_channelHasCorrectNameAndImportance() {
        new AlarmReceiver().onReceive(context, new Intent());

        NotificationChannel channel = notificationManager.getNotificationChannel(AlarmReceiver.CHANNEL_ID);
        assertEquals("Selfie Reminders", channel.getName().toString());
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.getImportance());
        assertEquals("Reminders to take a new selfie", channel.getDescription());
    }
}
