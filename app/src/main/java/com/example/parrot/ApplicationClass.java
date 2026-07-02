package com.example.parrot;

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
        createNotificationChannels();
        loadEqualizerSettings();
    }
    
    private void loadEqualizerSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("parrot_eq", android.content.Context.MODE_PRIVATE);
        com.bullhead.equalizer.Settings.isEqualizerEnabled = prefs.getBoolean("isEqualizerEnabled", true);
        com.bullhead.equalizer.Settings.presetPos = prefs.getInt("presetPos", 0);
        com.bullhead.equalizer.Settings.reverbPreset = (short) prefs.getInt("reverbPreset", -1);
        com.bullhead.equalizer.Settings.bassStrength = (short) prefs.getInt("bassStrength", -1);
        
        com.bullhead.equalizer.Settings.seekbarpos = new int[5];
        for (int i = 0; i < 5; i++) {
            com.bullhead.equalizer.Settings.seekbarpos[i] = prefs.getInt("band" + i, 0);
        }
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