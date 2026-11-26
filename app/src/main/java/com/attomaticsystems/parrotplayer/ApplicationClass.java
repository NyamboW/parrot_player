package com.attomaticsystems.parrotplayer;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class ApplicationClass extends Application {

    public static final String CHANNEL_ID_1 = "CHANNEL_1";
    public static final String CHANNEL_ID_2 = "CHANNEL_2";

    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREV = "PREVIOUS";
    public static final String ACTION_PLAY = "PLAY";

    @Override
    public void onCreate() {
        super.onCreate();
        TrackTagEditorHelper.init(this);
        createNotificationChannels();
    }

    private void createNotificationChannels() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel1 = new NotificationChannel(
                    CHANNEL_ID_1,
                    "Playback Controls",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel1.setDescription("Music playback controls");

            NotificationChannel channel2 = new NotificationChannel(
                    CHANNEL_ID_2,
                    "Playback Status",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel2.setDescription("Music playback notifications");

            NotificationManager manager = getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel1);
                manager.createNotificationChannel(channel2);
            }
        }
    }
}