package com.benzinger.selfieme.receivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowNotificationManager;

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
        assertEquals("Time For A New Selfie!",
                notification.extras.getString(Notification.EXTRA_TEXT));
        // Tapping the notification must reopen the app.
        assertNotNull(notification.contentIntent);
    }

    @Test
    public void onReceive_createsReminderChannel() {
        new AlarmReceiver().onReceive(context, new Intent());

        ShadowNotificationManager shadow = shadowOf(notificationManager);
        assertNotNull(shadow.getNotificationChannel(AlarmReceiver.CHANNEL_ID));
    }
}
