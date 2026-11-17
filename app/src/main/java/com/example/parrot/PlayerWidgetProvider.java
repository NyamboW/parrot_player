package com.example.parrot;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import java.io.File;

public class PlayerWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
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

        // Open PlayerActivity when clicking the widget background, album art, or song title
        Intent openPlayerIntent = new Intent(context, PlayerActivity.class);
        openPlayerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPlayerPendingIntent = PendingIntent.getActivity(
                context,
                3,
                openPlayerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widgetRoot, openPlayerPendingIntent);
        views.setOnClickPendingIntent(R.id.widgetAlbumArt, openPlayerPendingIntent);
        views.setOnClickPendingIntent(R.id.widgetSongName, openPlayerPendingIntent);

        return views;
    }

    private static PendingIntent getSafePendingIntent(Context context, int requestCode, Intent intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            return PendingIntent.getService(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = getWidgetViews(context);

        // Pre-populate from saved player state so the widget doesn't reset to "No song playing"
        PlayerStateManager.RestoredState restored = new PlayerStateManager(context).restore();
        if (restored.hasValidSong()) {
            String path = restored.songs.get(restored.position);
            String name = new File(path).getName().replaceAll("(?i)\\.(mp3|wav|m4a|flac|ogg|aac)$", "");
            views.setTextViewText(R.id.widgetSongName, name);
            Bitmap cached = AlbumArtLoader.getInstance().getBestCachedArt(path);
            if (cached != null) {
                views.setImageViewBitmap(R.id.widgetAlbumArt, cached);
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);

        // Request live state update if MusicService can be started/queried
        Intent updateIntent = new Intent(context, MusicService.class);
        updateIntent.setAction(MusicService.ACTION_REQUEST_UPDATE);
        try {
            context.startService(updateIntent);
        } catch (Exception ignored) {
        }
    }
}
