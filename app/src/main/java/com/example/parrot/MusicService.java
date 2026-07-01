package com.example.parrot;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class MusicService extends Service {

    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREV = "PREVIOUS";
    public static final String ACTION_PLAY = "PLAY";

    private final IBinder binder = new MyBinder();
    private ActionPlaying callback;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class MyBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) return START_STICKY;

        String action = intent.getStringExtra("myActionName");

        if (callback == null || action == null) return START_STICKY;

        switch (action) {
            case ACTION_PLAY:
                callback.playClicked();
                break;
            case ACTION_NEXT:
                callback.nextClicked();
                break;
            case ACTION_PREV:
                callback.prevClicked();
                break;
        }

        return START_STICKY;
    }

    public void setCallBack(ActionPlaying callback) {
        this.callback = callback;
    }
}