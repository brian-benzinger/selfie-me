package com.benzinger.selfieme.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.benzinger.selfieme.R;
import com.benzinger.selfieme.SelfieMainActivity;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "selfie_reminders";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel(notificationManager);

        Intent resultIntent = new Intent(context, SelfieMainActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                context,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("New Selfie Notification")
                .setContentText("Time For A New Selfie!")
                .setAutoCancel(true)
                .setContentIntent(resultPendingIntent);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    /**
     * Notification channels are mandatory from API 26 (the project's minSdk), so the channel is
     * always (re)created before posting. createNotificationChannel is a no-op if it already exists.
     */
    private void createChannel(NotificationManager manager) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Selfie Reminders", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Reminders to take a new selfie");
        manager.createNotificationChannel(channel);
    }
}
