package com.attomaticsystems.parrotplayer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.RemoteViews;

import java.io.File;

public class PlayerWidgetProvider extends AppWidgetProvider {

    private static final int PROGRESS_MAX = 1000;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static RemoteViews getWidgetViews(Context context, boolean hasTrack) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_player);

        Intent tapIntent = hasTrack
                ? new Intent(context, PlayerActivity.class)
                : new Intent(context, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tapPendingIntent = PendingIntent.getActivity(
                context,
                hasTrack ? 3 : 4,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(R.id.widgetRoot, tapPendingIntent);
        views.setOnClickPendingIntent(R.id.widgetAlbumArt, tapPendingIntent);
        views.setOnClickPendingIntent(R.id.widgetSongName, tapPendingIntent);
        views.setOnClickPendingIntent(R.id.widgetArtistName, tapPendingIntent);

        if (hasTrack) {
            views.setOnClickPendingIntent(R.id.widgetPlayPause,
                    getSafePendingIntent(context, 0, new Intent(context, MusicService.class).setAction(MusicService.ACTION_PLAY_PAUSE)));
            views.setOnClickPendingIntent(R.id.widgetNext,
                    getSafePendingIntent(context, 1, new Intent(context, MusicService.class).setAction(MusicService.ACTION_NEXT)));
            views.setOnClickPendingIntent(R.id.widgetPrev,
                    getSafePendingIntent(context, 2, new Intent(context, MusicService.class).setAction(MusicService.ACTION_PREV)));
        } else {
            views.setOnClickPendingIntent(R.id.widgetPlayPause, tapPendingIntent);
            views.setOnClickPendingIntent(R.id.widgetNext, tapPendingIntent);
            views.setOnClickPendingIntent(R.id.widgetPrev, tapPendingIntent);
        }

        return views;
    }

    public static void pushLiveUpdate(Context context,
                                      String songPath,
                                      boolean isPlaying,
                                      int currentPosMs,
                                      int durationMs) {
        if (context == null) return;
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName widgetComponent = new ComponentName(context, PlayerWidgetProvider.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);
        if (widgetIds == null || widgetIds.length == 0) return;

        boolean hasTrack = songPath != null && !songPath.isEmpty();
        RemoteViews views = getWidgetViews(context, hasTrack);
        bindWidgetContent(context, views, songPath, isPlaying, currentPosMs, durationMs);
        appWidgetManager.updateAppWidget(widgetIds, views);
    }

    private static void bindWidgetContent(Context context,
                                          RemoteViews views,
                                          String songPath,
                                          boolean isPlaying,
                                          int currentPosMs,
                                          int durationMs) {
        if (songPath == null || songPath.isEmpty()) {
            views.setTextViewText(R.id.widgetSongName, "Tap to browse music");
            views.setTextViewText(R.id.widgetArtistName, "Parrot Audio Player");
            views.setImageViewResource(R.id.widgetPlayPause, R.drawable.ic_play);
            views.setProgressBar(R.id.widgetProgressBar, PROGRESS_MAX, 0, false);
            applyPlaceholderArt(context, views);
            return;
        }

        String fileName = new File(songPath).getName().replaceAll("(?i)\\.(mp3|wav|m4a|flac|ogg|aac)$", "");
        TrackMetadataHelper.TrackInfo cachedInfo = TrackMetadataHelper.getCached(songPath);
        String title = (cachedInfo != null && !cachedInfo.title.isEmpty()) ? cachedInfo.title : fileName;
        String subtitle = (cachedInfo != null && cachedInfo.hasKnownArtist())
                ? cachedInfo.getArtistAlbumSubtitle()
                : "Parrot Audio Player";

        views.setTextViewText(R.id.widgetSongName, title);
        views.setTextViewText(R.id.widgetArtistName, subtitle);
        views.setImageViewResource(R.id.widgetPlayPause,
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

        int progress = 0;
        if (durationMs > 0 && currentPosMs > 0) {
            progress = Math.max(0, Math.min(PROGRESS_MAX, (int) ((currentPosMs * (long) PROGRESS_MAX) / durationMs)));
        }
        views.setProgressBar(R.id.widgetProgressBar, PROGRESS_MAX, progress, false);

        Bitmap albumArt = AlbumArtLoader.getInstance().getBestCachedArt(songPath);
        if (albumArt != null) {
            Bitmap scaled = Bitmap.createScaledBitmap(albumArt, 160, 160, true);
            Bitmap rounded = getRoundedCornerBitmap(scaled, 28);
            views.setViewPadding(R.id.widgetAlbumArt, 0, 0, 0, 0);
            views.setInt(R.id.widgetAlbumArt, "setColorFilter", 0);
            views.setImageViewBitmap(R.id.widgetAlbumArt, rounded);
        } else {
            applyPlaceholderArt(context, views);
        }
    }

    private static void applyPlaceholderArt(Context context, RemoteViews views) {
        int pad = Math.round(13f * context.getResources().getDisplayMetrics().density);
        views.setViewPadding(R.id.widgetAlbumArt, pad, pad, pad, pad);
        views.setInt(R.id.widgetAlbumArt, "setColorFilter", Color.parseColor("#8A93A2"));
        views.setImageViewResource(R.id.widgetAlbumArt, R.drawable.ic_music);
    }

    private static Bitmap getRoundedCornerBitmap(Bitmap bitmap, int radiusPx) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);

        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(0xFF424242);
        canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
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
        PlayerStateManager.RestoredState restored = new PlayerStateManager(context).restore();
        String path = null;
        if (restored.hasValidSong()) {
            path = restored.songs.get(restored.position);
        } else if (MusicService.getActiveSongPath() != null) {
            path = MusicService.getActiveSongPath();
        }

        boolean isPlaying = MusicService.isActivelyPlaying();
        RemoteViews views = getWidgetViews(context, path != null && !path.isEmpty());
        bindWidgetContent(context, views, path, isPlaying, restored.seekPosition, restored.duration);
        appWidgetManager.updateAppWidget(appWidgetId, views);

        if (path != null && !path.isEmpty()) {
            final String targetPath = path;
            final boolean playingSnapshot = isPlaying;
            final int posSnapshot = restored.seekPosition;
            final int durSnapshot = restored.duration;

            AlbumArtLoader loader = AlbumArtLoader.getInstance();
            if (loader.getBestCachedArt(targetPath) == null && !loader.hasNoArt(targetPath)) {
                loader.loadThumbArt(targetPath, bitmap -> {
                    if (bitmap != null) {
                        pushLiveUpdate(context, targetPath, MusicService.isActivelyPlaying() || playingSnapshot, posSnapshot, durSnapshot);
                    }
                });
            }
            if (TrackMetadataHelper.getCached(targetPath) == null) {
                TrackMetadataHelper.loadTrackInfoAsync(targetPath, info -> {
                    if (info != null) {
                        pushLiveUpdate(context, targetPath, MusicService.isActivelyPlaying() || playingSnapshot, posSnapshot, durSnapshot);
                    }
                });
            }
        }

        if (MusicService.getActiveSongPath() != null) {
            Intent updateIntent = new Intent(context, MusicService.class);
            updateIntent.setAction(MusicService.ACTION_REQUEST_UPDATE);
            try {
                context.startService(updateIntent);
            } catch (Exception ignored) {
            }
        }
    }
}
