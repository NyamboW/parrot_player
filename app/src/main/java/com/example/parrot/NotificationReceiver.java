package com.example.parrot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class NotificationReceiver extends BroadcastReceiver {

    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREV = "PREVIOUS";
    public static final String ACTION_PLAY = "PLAY";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();

        Intent serviceIntent = new Intent(context, MusicService.class);
        serviceIntent.putExtra("myActionName", action);

        switch (action) {

            case ACTION_PLAY:
            case ACTION_NEXT:
            case ACTION_PREV:

                ContextCompat.startForegroundService(context, serviceIntent);
                break;
        }
    }
}