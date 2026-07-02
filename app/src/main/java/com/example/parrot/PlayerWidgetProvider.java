package com.example.parrot;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class PlayerWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static RemoteViews getWidgetViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_player);

        // Intent for Play/Pause
        Intent playPauseIntent = new Intent(context, MusicService.class);
        playPauseIntent.setAction(MusicService.ACTION_PLAY_PAUSE);
        PendingIntent playPausePendingIntent = getSafePendingIntent(context, 0, playPauseIntent);
        views.setOnClickPendingIntent(R.id.widgetPlayPause, playPausePendingIntent);

        // Intent for Next
        Intent nextIntent = new Intent(context, MusicService.class);
        nextIntent.setAction(MusicService.ACTION_NEXT);
        PendingIntent nextPendingIntent = getSafePendingIntent(context, 1, nextIntent);
        views.setOnClickPendingIntent(R.id.widgetNext, nextPendingIntent);

        // Intent for Previous
        Intent prevIntent = new Intent(context, MusicService.class);
        prevIntent.setAction(MusicService.ACTION_PREV);
        PendingIntent prevPendingIntent = getSafePendingIntent(context, 2, prevIntent);
        views.setOnClickPendingIntent(R.id.widgetPrev, prevPendingIntent);

        // Open App on click of background or song name
        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 3, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetSongName, openAppPendingIntent);
        
        return views;
    }
    
    private static PendingIntent getSafePendingIntent(Context context, int requestCode, Intent intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            return PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {

        RemoteViews views = getWidgetViews(context);

        // Request current state from service so widget is populated
        Intent updateIntent = new Intent(context, MusicService.class);
        updateIntent.setAction(MusicService.ACTION_REQUEST_UPDATE);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(updateIntent);
            } else {
                context.startService(updateIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
