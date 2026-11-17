package com.example.parrot;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.media.audiofx.Equalizer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.PresetReverb;
import android.content.SharedPreferences;
import android.content.Context;
import android.media.AudioManager;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Random;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.widget.RemoteViews;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

public class MusicService extends Service {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREV = "ACTION_PREV";
    public static final String ACTION_REQUEST_UPDATE = "ACTION_REQUEST_UPDATE";
    public static final String ACTION_RELOAD_EQUALIZER = "ACTION_RELOAD_EQUALIZER";
    public static final String ACTION_CLOSE = "ACTION_CLOSE";
    public static final String ACTION_ADD_TO_QUEUE = "ACTION_ADD_TO_QUEUE";
    public static final String ACTION_ADD_MULTIPLE_TO_QUEUE = "ACTION_ADD_MULTIPLE_TO_QUEUE";
    public static final String ACTION_PLAY_NEXT = "ACTION_PLAY_NEXT";
    public static final String ACTION_PLAY_NEXT_MULTIPLE = "ACTION_PLAY_NEXT_MULTIPLE";
    
    public static final String BROADCAST_UPDATE_UI = "com.example.parrot.UPDATE_UI";
    public static final String BROADCAST_QUEUE_UPDATED = "com.example.parrot.QUEUE_UPDATED";

    private static volatile String activeSongPath = null;
    private static volatile boolean activeIsPlaying = false;

    public static String getActiveSongPath() {
        return activeSongPath;
    }

    public static boolean isActivelyPlaying() {
        return activeIsPlaying;
    }

    private final IBinder binder = new MyBinder();
    
    private MediaPlayer mediaPlayer;
    private boolean isPrepared = false;
    private int playToken = 0;
    private ArrayList<String> mySongs = new ArrayList<>();
    private int position = 0;
    private int preselectedShufflePos = -1;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    
    private QueueManager queueManager = new QueueManager();
    private PlayerStateManager stateManager;
    private int restoredSeekPosition = 0;
    private int restoredDuration = 0;

    private float playbackSpeed = 1.0f;
    private int crossfadeSeconds = 0;
    private boolean skipSilence = false;
    private long sleepTimerEndElapsedMs = 0L;
    private MediaPlayer fadingOutPlayer;
    private boolean crossfadeTriggered = false;
    private boolean incomingFadeIn = false;
    private BroadcastReceiver audioDeviceReceiver;

    private static final long STATE_SAVE_INTERVAL_MS = 3000L;
    private static final long TRANSITION_POLL_INTERVAL_MS = 250L;
    private final Handler stateHandler = new Handler(Looper.getMainLooper());
    private final Runnable periodicStateSaveRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying()) {
                saveCurrentState(false);
                stateHandler.postDelayed(this, STATE_SAVE_INTERVAL_MS);
            }
        }
    };
    private final Runnable transitionMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying()) {
                checkTrackTransition();
                stateHandler.postDelayed(this, TRANSITION_POLL_INTERVAL_MS);
            }
        }
    };
    private final Runnable sleepTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (sleepTimerEndElapsedMs <= 0L) return;
            long remaining = sleepTimerEndElapsedMs - SystemClock.elapsedRealtime();
            if (remaining <= 0L) {
                sleepTimerEndElapsedMs = 0L;
                if (mediaPlayer != null && isPrepared) {
                    if (mediaPlayer.isPlaying()) {
                        togglePlay();
                    }
                    try {
                        mediaPlayer.setVolume(1.0f, 1.0f);
                    } catch (Exception ignored) {}
                }
            } else {
                if (remaining <= 3000L && mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
                    float vol = Math.max(0.08f, remaining / 3000f);
                    try {
                        mediaPlayer.setVolume(vol, vol);
                    } catch (Exception ignored) {}
                }
                stateHandler.postDelayed(this, 500L);
            }
        }
    };
    
    private Equalizer mEqualizer;
    private BassBoost mBassBoost;
    private PresetReverb mPresetReverb;

    private MediaSessionCompat mediaSession;
    
    private AudioManager audioManager;
    private AudioFocusRequestCompat audioFocusRequest;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

    @Override
    public void onCreate() {
        super.onCreate();
        stateManager = new PlayerStateManager(this);
        loadPlaybackPreferences();
        restoreSavedState();
        registerAudioDeviceReceiver();
        
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                if (mediaPlayer == null || !isPrepared) return;
                
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                            applyPlaybackSpeed();
                            startPeriodicStateSave();
                            updateMediaSession();
                            showNotification(R.drawable.ic_pause);
                            broadcastUIUpdate();
                        }
                        mediaPlayer.setVolume(1.0f, 1.0f);
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            stopPeriodicStateSave();
                            saveCurrentState(false);
                            updateMediaSession();
                            showNotification(R.drawable.ic_play);
                            broadcastUIUpdate();
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            stopPeriodicStateSave();
                            saveCurrentState(false);
                            updateMediaSession();
                            showNotification(R.drawable.ic_play);
                            broadcastUIUpdate();
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.setVolume(0.1f, 0.1f);
                        }
                        break;
                }
            }
        };
        
        audioFocusRequest = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build();
                
        mediaSession = new MediaSessionCompat(this, "MusicService");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (mediaPlayer == null && !mySongs.isEmpty()) {
                    togglePlay();
                } else if (mediaPlayer != null && isPrepared && !mediaPlayer.isPlaying()) {
                    togglePlay();
                }
            }

            @Override
            public void onPause() {
                if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
                    togglePlay();
                }
            }

            @Override
            public void onSkipToNext() {
                nextSong();
            }

            @Override
            public void onSkipToPrevious() {
                prevSong();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }
            
            @Override
            public void onCustomAction(String action, Bundle extras) {
                if (ACTION_CLOSE.equals(action)) {
                    closePlayerService();
                }
            }
        });
        mediaSession.setActive(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class MyBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();

            switch (action) {
                case ACTION_START:
                    ArrayList<String> newSongs = intent.getStringArrayListExtra("songs");
                    if (newSongs != null) {
                        mySongs = newSongs;
                        position = intent.getIntExtra("pos", 0);
                        restoredSeekPosition = 0;
                        restoredDuration = 0;
                        stateManager.savePlaylist(mySongs);
                        playSong(position);
                    }
                    break;
                case ACTION_PLAY_PAUSE:
                    togglePlay();
                    break;
                case ACTION_NEXT:
                    nextSong();
                    break;
                case ACTION_PREV:
                    prevSong();
                    break;
                case ACTION_REQUEST_UPDATE:
                    broadcastUIUpdate();
                    String currentPath = getCurrentSongPath();
                    if (currentPath != null) {
                        ensureAlbumArtLoaded(currentPath);
                    }
                    break;
                case ACTION_RELOAD_EQUALIZER:
                    loadEqualizerSettings();
                    break;
                case ACTION_ADD_TO_QUEUE:
                    String queuePath = intent.getStringExtra("songPath");
                    if (queuePath != null) {
                        queueManager.addToQueue(queuePath);
                        broadcastQueueUpdate();
                    }
                    break;
                case ACTION_PLAY_NEXT:
                    String nextPath = intent.getStringExtra("songPath");
                    if (nextPath != null) {
                        queueManager.playNext(nextPath);
                        broadcastQueueUpdate();
                    }
                    break;
                case ACTION_ADD_MULTIPLE_TO_QUEUE:
                    ArrayList<String> multiplePaths = intent.getStringArrayListExtra("songPaths");
                    if (multiplePaths != null && !multiplePaths.isEmpty()) {
                        queueManager.addAllToQueue(multiplePaths);
                        broadcastQueueUpdate();
                    }
                    break;
                case ACTION_PLAY_NEXT_MULTIPLE:
                    ArrayList<String> multipleNextPaths = intent.getStringArrayListExtra("songPaths");
                    if (multipleNextPaths != null && !multipleNextPaths.isEmpty()) {
                        queueManager.playNextMultiple(multipleNextPaths);
                        broadcastQueueUpdate();
                    }
                    break;
                case ACTION_CLOSE:
                    closePlayerService();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    public void playSong(int pos) {
        position = pos;
        if (mySongs.isEmpty() || position < 0 || position >= mySongs.size()) return;
        restoredSeekPosition = 0;
        restoredDuration = 0;
        startPlayback(mySongs.get(position), 0);
    }

    private void startPlayback(String songPath, int initialSeekMs) {
        safeRelease();
        final int token = ++playToken;
        isPrepared = false;
        crossfadeTriggered = false;
        final boolean shouldFadeIn = incomingFadeIn && crossfadeSeconds > 0;
        incomingFadeIn = false;
        restoredSeekPosition = Math.max(0, initialSeekMs);

        MediaPlayer mp = new MediaPlayer();
        mediaPlayer = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(this, Uri.parse(songPath));
            mp.setOnPreparedListener(preparedMp -> {
                if (token != playToken || preparedMp != mediaPlayer) return;
                isPrepared = true;
                setupEqualizer();

                int duration = preparedMp.getDuration();
                int targetSeek = initialSeekMs;
                if (targetSeek == 0 && skipSilence && duration > 15000) {
                    targetSeek = 350;
                }
                if (targetSeek > 0 && duration > 0 && targetSeek < duration) {
                    preparedMp.seekTo(targetSeek);
                }
                restoredSeekPosition = 0;
                restoredDuration = Math.max(0, duration);

                if (shouldFadeIn) {
                    preparedMp.setVolume(0.08f, 0.08f);
                } else {
                    preparedMp.setVolume(1.0f, 1.0f);
                }

                int result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    preparedMp.start();
                    applyPlaybackSpeed();
                    if (shouldFadeIn) {
                        startVolumeRampIn(preparedMp, token, crossfadeSeconds * 1000L);
                    }
                    startPeriodicStateSave();
                }
                updateMediaSession();
                showNotification(preparedMp.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
                broadcastUIUpdate();
                saveCurrentState(false);
            });
            mp.setOnCompletionListener(completedMp -> {
                if (token != playToken || completedMp != mediaPlayer) return;
                stopPeriodicStateSave();
                if (isRepeat) {
                    playSong(position);
                } else if (!queueManager.isEmpty()) {
                    playNextFromQueue();
                } else {
                    nextSong();
                }
            });
            mp.setOnErrorListener((errorMp, what, extra) -> {
                if (token != playToken || errorMp != mediaPlayer) return true;
                isPrepared = false;
                stopPeriodicStateSave();
                return true;
            });

            // Satisfy foreground service & update UI immediately without waiting for preparation
            updateMediaSession();
            showNotification(R.drawable.ic_pause);
            broadcastUIUpdate();
            ensureAlbumArtLoaded(songPath);
            saveCurrentState(false);

            mp.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
            safeRelease();
        }
    }

    /**
     * Play the next song from the queue.
     */
    private void playNextFromQueue() {
        String nextPath = queueManager.pollNext();
        if (nextPath != null) {
            // Insert it into the current playlist position context so prev/next still work
            int insertIndex = position + 1;
            if (insertIndex > mySongs.size()) insertIndex = mySongs.size();
            mySongs.add(insertIndex, nextPath);
            position = insertIndex;

            stateManager.savePlaylist(mySongs);
            restoredSeekPosition = 0;
            restoredDuration = 0;
            startPlayback(nextPath, 0);
            broadcastQueueUpdate();
        }
    }

    /**
     * Play a specific song from the queue by index (tap in queue viewer).
     */
    public void playFromQueue(int queueIndex) {
        ArrayList<String> queue = queueManager.getQueue();
        if (queueIndex >= 0 && queueIndex < queue.size()) {
            String path = queue.get(queueIndex);
            queueManager.removeFromQueue(queueIndex);

            // Insert and play
            int insertIndex = position + 1;
            if (insertIndex > mySongs.size()) insertIndex = mySongs.size();
            mySongs.add(insertIndex, path);
            position = insertIndex;

            stateManager.savePlaylist(mySongs);
            restoredSeekPosition = 0;
            restoredDuration = 0;
            startPlayback(path, 0);
            broadcastQueueUpdate();
        }
    }

    public void togglePlay() {
        if (mediaPlayer == null) {
            if (!mySongs.isEmpty() && position >= 0 && position < mySongs.size()) {
                startPlayback(mySongs.get(position), restoredSeekPosition);
            }
            return;
        }
        if (!isPrepared) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            stopPeriodicStateSave();
            saveCurrentState(false);
            updateMediaSession();
            showNotification(R.drawable.ic_play);
        } else {
            int result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer.setVolume(1.0f, 1.0f);
                mediaPlayer.start();
                applyPlaybackSpeed();
                startPeriodicStateSave();
                saveCurrentState(false);
                updateMediaSession();
                showNotification(R.drawable.ic_pause);
            }
        }
        broadcastUIUpdate();
    }

    public void nextSong() {
        if (mySongs.isEmpty()) return;

        // Check queue first
        if (!queueManager.isEmpty()) {
            playNextFromQueue();
            return;
        }

        if (isShuffle) {
            if (preselectedShufflePos >= 0 && preselectedShufflePos < mySongs.size()
                    && preselectedShufflePos != position) {
                position = preselectedShufflePos;
            } else {
                position = pickNextShufflePosition();
            }
            preselectedShufflePos = -1;
        } else {
            position = (position + 1) % mySongs.size();
        }
        playSong(position);
    }

    public void prevSong() {
        if (mySongs.isEmpty()) return;

        if (isShuffle) {
            position = pickNextShufflePosition();
        } else {
            position = (position - 1 < 0) ? mySongs.size() - 1 : position - 1;
        }
        playSong(position);
    }

    private int pickNextShufflePosition() {
        if (mySongs.size() <= 1) return 0;
        Random random = new Random();
        int nextPos = random.nextInt(mySongs.size());
        if (nextPos == position) {
            nextPos = (position + 1 + random.nextInt(mySongs.size() - 1)) % mySongs.size();
        }
        return nextPos;
    }

    private void safeRelease() {
        playToken++;
        boolean wasPrepared = isPrepared;
        isPrepared = false;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setOnPreparedListener(null);
                mediaPlayer.setOnCompletionListener(null);
                mediaPlayer.setOnErrorListener(null);
                if (wasPrepared && mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    mediaPlayer.release();
                } catch (Exception ignored) {}
                mediaPlayer = null;
            }
        }
        if (audioManager != null && audioFocusRequest != null) {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest);
        }
    }

    private void showNotification(int playPauseBtn) {
        if (mySongs.isEmpty()) return;
        
        String songPath = mySongs.get(position);
        String songName = getFileName(songPath);

        Intent prevIntent = new Intent(this, MusicService.class).setAction(ACTION_PREV);
        PendingIntent prevPendingIntent = PendingIntent.getService(this, 0, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent playIntent = new Intent(this, MusicService.class).setAction(ACTION_PLAY_PAUSE);
        PendingIntent playPendingIntent = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent nextIntent = new Intent(this, MusicService.class).setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getService(this, 0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent closeIntent = new Intent(this, MusicService.class).setAction(ACTION_CLOSE);
        PendingIntent closePendingIntent = PendingIntent.getService(this, 0, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent intent = new Intent(this, PlayerActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap icon = AlbumArtLoader.getInstance().getBestCachedArt(songPath);

        Notification notification = new NotificationCompat.Builder(this, ApplicationClass.CHANNEL_ID_1)
                .setSmallIcon(R.drawable.ic_music)
                .setLargeIcon(icon)
                .setContentTitle(songName)
                .setContentText("Parrot Player")
                .addAction(R.drawable.ic_previous, "Previous", prevPendingIntent)
                .addAction(playPauseBtn, "Play", playPendingIntent)
                .addAction(R.drawable.ic_next, "Next", nextPendingIntent)
                .addAction(R.drawable.ic_close, "Close", closePendingIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(closePendingIntent))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        startForeground(1, notification);
    }
    
    private void broadcastUIUpdate() {
        if (mySongs.isEmpty() || position < 0 || position >= mySongs.size()) return;
        
        String songPath = mySongs.get(position);
        boolean playing = (mediaPlayer != null && !isPrepared) || isPlaying();
        activeSongPath = songPath;
        activeIsPlaying = playing;
        int duration = getDuration();
        int currentPos = getCurrentPosition();

        Intent intent = new Intent(BROADCAST_UPDATE_UI);
        intent.setPackage(getPackageName()); // CRITICAL for Android 14 RECEIVER_NOT_EXPORTED
        intent.putExtra("song", getFileName(songPath));
        intent.putExtra("songPath", songPath);
        intent.putExtra("isPlaying", playing);
        intent.putExtra("duration", duration);
        intent.putExtra("currentPosition", currentPos);
        intent.putExtra("isShuffle", isShuffle);
        intent.putExtra("isRepeat", isRepeat);
        sendBroadcast(intent);
        
        updateWidget(songPath);
    }
    
    private void broadcastQueueUpdate() {
        if (stateManager != null) {
            stateManager.saveQueue(queueManager.getQueue());
        }
        Intent intent = new Intent(BROADCAST_QUEUE_UPDATED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
    
    private void updateMediaSession() {
        if (mediaPlayer == null || mySongs.isEmpty() || position < 0 || position >= mySongs.size()) return;
        
        long currentPosition = getCurrentPosition();
        boolean playing = !isPrepared || isPlaying();
        int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        
        PlaybackStateCompat.CustomAction leftSpacerAction = new PlaybackStateCompat.CustomAction.Builder(
                "ACTION_NOOP",
                " ",
                R.drawable.ic_transparent
        ).build();

        PlaybackStateCompat.CustomAction closeAction = new PlaybackStateCompat.CustomAction.Builder(
                ACTION_CLOSE,
                "Close",
                R.drawable.ic_close
        ).build();
        
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                            PlaybackStateCompat.ACTION_PAUSE |
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                            PlaybackStateCompat.ACTION_SEEK_TO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ MediaControlPanel maps CustomAction #1 to the left slot (action0)
            // and CustomAction #2 to the right slot (action1).
            stateBuilder.addCustomAction(leftSpacerAction);
        }
        stateBuilder.addCustomAction(closeAction)
                .setState(state, currentPosition, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
                
        String songPath = mySongs.get(this.position);
        String songName = getFileName(songPath);
        Bitmap icon = AlbumArtLoader.getInstance().getBestCachedArt(songPath);
        
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, songName)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        if (icon != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, icon);
        }
        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void ensureAlbumArtLoaded(String songPath) {
        AlbumArtLoader loader = AlbumArtLoader.getInstance();
        if (loader.getCachedFull(songPath) != null || loader.hasNoArt(songPath)) {
            preloadNextSongArt();
            return;
        }

        loader.loadFullArt(songPath, bitmap -> {
            if (mediaPlayer != null && songPath.equals(getCurrentSongPath())) {
                if (bitmap != null) {
                    updateMediaSession();
                    showNotification((!isPrepared || isPlaying()) ? R.drawable.ic_pause : R.drawable.ic_play);
                    updateWidget(songPath);
                }
            }
            preloadNextSongArt();
        });
    }

    private void updateWidget(String songPath) {
        if (songPath == null) return;
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName widgetComponent = new ComponentName(this, PlayerWidgetProvider.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);
        if (widgetIds == null || widgetIds.length == 0) return;

        RemoteViews views = PlayerWidgetProvider.getWidgetViews(this);
        views.setTextViewText(R.id.widgetSongName, getFileName(songPath));
        boolean showPause = (mediaPlayer != null && !isPrepared) || isPlaying();
        views.setImageViewResource(R.id.widgetPlayPause,
                showPause ? R.drawable.ic_pause : R.drawable.ic_play);

        Bitmap albumArt = AlbumArtLoader.getInstance().getBestCachedArt(songPath);
        if (albumArt != null) {
            Bitmap scaled = Bitmap.createScaledBitmap(albumArt, 100, 100, true);
            Bitmap rounded = getRoundedCornerBitmap(scaled, 16);
            views.setImageViewBitmap(R.id.widgetAlbumArt, rounded);
        } else {
            views.setImageViewResource(R.id.widgetAlbumArt, R.drawable.ic_music);
        }

        appWidgetManager.updateAppWidget(widgetIds, views);
    }

    private void preloadNextSongArt() {
        if (mySongs == null || mySongs.isEmpty()) return;
        AlbumArtLoader loader = AlbumArtLoader.getInstance();

        if (!queueManager.isEmpty()) {
            ArrayList<String> q = queueManager.getQueue();
            if (!q.isEmpty()) {
                loader.preloadFullArt(q.get(0));
            }
        }

        if (mySongs.size() > 1) {
            if (isShuffle) {
                if (preselectedShufflePos < 0 || preselectedShufflePos >= mySongs.size()
                        || preselectedShufflePos == position) {
                    preselectedShufflePos = pickNextShufflePosition();
                }
                loader.preloadFullArt(mySongs.get(preselectedShufflePos));
            } else {
                int next1 = (position + 1) % mySongs.size();
                int prev1 = (position - 1 < 0) ? mySongs.size() - 1 : position - 1;
                loader.preloadFullArt(mySongs.get(next1));
                loader.preloadFullArt(mySongs.get(prev1));
                if (mySongs.size() > 2) {
                    int next2 = (position + 2) % mySongs.size();
                    loader.preloadFullArt(mySongs.get(next2));
                }
            }
        }
    }
    
    private Bitmap getRoundedCornerBitmap(Bitmap bitmap, int pixels) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        final float roundPx = pixels;

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    private String getFileName(String path) {
        int index = path.lastIndexOf("/");
        String name = (index != -1) ? path.substring(index + 1) : path;
        return name.replaceAll("(?i)\\.(mp3|wav|m4a|flac|ogg|aac)$", "");
    }

    public boolean isPlaying() {
        try {
            return mediaPlayer != null && isPrepared && mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public int getCurrentPosition() {
        try {
            if (mediaPlayer != null && isPrepared) {
                return mediaPlayer.getCurrentPosition();
            }
        } catch (IllegalStateException ignored) {}
        return restoredSeekPosition;
    }

    public int getDuration() {
        try {
            if (mediaPlayer != null && isPrepared) {
                return mediaPlayer.getDuration();
            }
        } catch (IllegalStateException ignored) {}
        return restoredDuration;
    }

    public String getCurrentSongPath() {
        if (mySongs != null && !mySongs.isEmpty() && position >= 0 && position < mySongs.size()) {
            return mySongs.get(position);
        }
        return null;
    }

    public String getCurrentSongTitle() {
        String path = getCurrentSongPath();
        return path != null ? getFileName(path) : null;
    }
    
    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }
    
    public int getAudioSessionId() {
        if (mediaPlayer != null && isPrepared) {
            return mediaPlayer.getAudioSessionId();
        }
        return 0;
    }
    
    public void seekTo(int pos) {
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.seekTo(pos);
            updateMediaSession();
            saveCurrentState(false);
        } else if (!mySongs.isEmpty()) {
            restoredSeekPosition = Math.max(0, pos);
            if (stateManager != null) {
                stateManager.saveSeekPosition(restoredSeekPosition, restoredDuration);
            }
            broadcastUIUpdate();
        }
    }
    
    public void setShuffle(boolean shuffle) {
        isShuffle = shuffle;
        if (stateManager != null) {
            stateManager.saveModes(isShuffle, isRepeat);
        }
    }

    public boolean isShuffle() {
        return isShuffle;
    }
    
    public void setRepeat(boolean repeat) {
        isRepeat = repeat;
        if (stateManager != null) {
            stateManager.saveModes(isShuffle, isRepeat);
        }
    }

    public boolean isRepeat() {
        return isRepeat;
    }

    public boolean playMatchingSong(String query) {
        if (query == null || query.trim().isEmpty() || mySongs == null || mySongs.isEmpty()) {
            return false;
        }
        String lower = query.toLowerCase();
        for (int i = 0; i < mySongs.size(); i++) {
            String title = getFileName(mySongs.get(i)).toLowerCase();
            if (title.contains(lower)) {
                playSong(i);
                return true;
            }
        }
        return false;
    }
    
    // Queue accessors
    
    public ArrayList<String> getQueueList() {
        return queueManager.getQueue();
    }
    
    public void addToQueue(String songPath) {
        queueManager.addToQueue(songPath);
        broadcastQueueUpdate();
    }
    
    public void addPlayNext(String songPath) {
        queueManager.playNext(songPath);
        broadcastQueueUpdate();
    }
    
    public void removeFromQueue(int index) {
        queueManager.removeFromQueue(index);
        broadcastQueueUpdate();
    }
    
    public void clearQueue() {
        queueManager.clearQueue();
        broadcastQueueUpdate();
    }

    private void restoreSavedState() {
        if (stateManager == null) return;
        PlayerStateManager.RestoredState restored = stateManager.restore();
        isShuffle = restored.isShuffle;
        isRepeat = restored.isRepeat;
        if (!restored.queue.isEmpty()) {
            queueManager.addAllToQueue(restored.queue);
        }
        if (restored.hasValidSong()) {
            mySongs = restored.songs;
            position = restored.position;
            restoredSeekPosition = restored.seekPosition;
            restoredDuration = restored.duration;
            activeSongPath = getCurrentSongPath();
            activeIsPlaying = false;
            AlbumArtLoader.getInstance().preloadFullArt(getCurrentSongPath());
        }
    }

    private void saveCurrentState(boolean synchronous) {
        if (stateManager == null || mySongs == null || mySongs.isEmpty()) return;
        String currentPath = getCurrentSongPath();
        if (currentPath == null) return;
        stateManager.savePlaybackState(
                position,
                currentPath,
                getCurrentPosition(),
                getDuration(),
                isShuffle,
                isRepeat,
                synchronous
        );
    }

    private void startPeriodicStateSave() {
        stopPeriodicStateSave();
        stateHandler.postDelayed(periodicStateSaveRunnable, STATE_SAVE_INTERVAL_MS);
        stateHandler.postDelayed(transitionMonitorRunnable, TRANSITION_POLL_INTERVAL_MS);
    }

    private void stopPeriodicStateSave() {
        stateHandler.removeCallbacks(periodicStateSaveRunnable);
        stateHandler.removeCallbacks(transitionMonitorRunnable);
    }

    private void loadPlaybackPreferences() {
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        playbackSpeed = Math.max(0.5f, Math.min(2.0f, prefs.getFloat(SettingsFragment.KEY_PLAYBACK_SPEED, 1.0f)));
        crossfadeSeconds = Math.max(0, Math.min(12, prefs.getInt(SettingsFragment.KEY_CROSSFADE_SECONDS, 0)));
        skipSilence = prefs.getBoolean(SettingsFragment.KEY_SKIP_SILENCE, false);
    }

    public void setPlaybackSpeed(float speed) {
        playbackSpeed = Math.max(0.5f, Math.min(2.0f, speed));
        if (isPlaying()) {
            applyPlaybackSpeed();
        }
    }

    private void applyPlaybackSpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null && isPrepared) {
            try {
                if (mediaPlayer.isPlaying()) {
                    PlaybackParams params = mediaPlayer.getPlaybackParams();
                    if (params == null) {
                        params = new PlaybackParams();
                    }
                    params.setSpeed(playbackSpeed);
                    mediaPlayer.setPlaybackParams(params);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void setCrossfadeSeconds(int seconds) {
        crossfadeSeconds = Math.max(0, Math.min(12, seconds));
    }

    public void setSkipSilence(boolean enabled) {
        skipSilence = enabled;
    }

    public void setSleepTimerMinutes(int minutes) {
        stateHandler.removeCallbacks(sleepTimerRunnable);
        if (minutes <= 0) {
            sleepTimerEndElapsedMs = 0L;
            if (mediaPlayer != null && isPrepared) {
                try {
                    mediaPlayer.setVolume(1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
            return;
        }
        sleepTimerEndElapsedMs = SystemClock.elapsedRealtime() + minutes * 60_000L;
        stateHandler.postDelayed(sleepTimerRunnable, 500L);
    }

    public long getSleepTimerRemainingMs() {
        if (sleepTimerEndElapsedMs <= 0L) return 0L;
        long remaining = sleepTimerEndElapsedMs - SystemClock.elapsedRealtime();
        return Math.max(0L, remaining);
    }

    private void checkTrackTransition() {
        if (mediaPlayer == null || !isPrepared || isRepeat || crossfadeTriggered) return;
        int duration = getDuration();
        int currentPos = getCurrentPosition();
        if (duration <= 6000 || currentPos <= 1000) return;

        int remaining = duration - currentPos;
        if (crossfadeSeconds > 0 && duration > (crossfadeSeconds * 1000 + 3000)) {
            if (remaining <= crossfadeSeconds * 1000) {
                crossfadeTriggered = true;
                triggerCrossfadeToNext();
            }
        } else if (skipSilence && remaining <= 550) {
            crossfadeTriggered = true;
            nextSong();
        }
    }

    private void triggerCrossfadeToNext() {
        releaseFadingOutPlayer();
        if (mediaPlayer != null && isPrepared) {
            final MediaPlayer outgoing = mediaPlayer;
            fadingOutPlayer = outgoing;
            mediaPlayer = null;
            isPrepared = false;
            playToken++;
            try {
                outgoing.setOnCompletionListener(null);
                outgoing.setOnErrorListener(null);
            } catch (Exception ignored) {}

            final int steps = 16;
            final long stepDelay = Math.max(40L, (crossfadeSeconds * 1000L) / steps);
            for (int i = 1; i <= steps; i++) {
                final int step = i;
                stateHandler.postDelayed(() -> {
                    if (fadingOutPlayer != outgoing) return;
                    if (step == steps) {
                        releaseFadingOutPlayer();
                    } else {
                        float vol = Math.max(0f, 1f - (step / (float) steps));
                        try {
                            outgoing.setVolume(vol, vol);
                        } catch (Exception ignored) {}
                    }
                }, stepDelay * i);
            }
        }
        incomingFadeIn = true;
        nextSong();
    }

    private void startVolumeRampIn(final MediaPlayer mp, final int token, long durationMs) {
        final int steps = 16;
        final long stepDelay = Math.max(40L, durationMs / steps);
        for (int i = 1; i <= steps; i++) {
            final int step = i;
            stateHandler.postDelayed(() -> {
                if (token != playToken || mp != mediaPlayer || !isPrepared) return;
                float vol = Math.min(1f, step / (float) steps);
                try {
                    mp.setVolume(vol, vol);
                } catch (Exception ignored) {}
            }, stepDelay * i);
        }
    }

    private void releaseFadingOutPlayer() {
        if (fadingOutPlayer != null) {
            try {
                if (fadingOutPlayer.isPlaying()) {
                    fadingOutPlayer.stop();
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    fadingOutPlayer.release();
                } catch (Exception ignored) {}
                fadingOutPlayer = null;
            }
        }
    }

    private void registerAudioDeviceReceiver() {
        audioDeviceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    boolean pauseOnUnplug = prefs.getBoolean(SettingsFragment.KEY_PAUSE_ON_UNPLUG, true);
                    if (pauseOnUnplug && isPlaying()) {
                        togglePlay();
                    }
                } else if (Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                    if (isInitialStickyBroadcast()) return;
                    int state = intent.getIntExtra("state", -1);
                    boolean resumeOnConnect = prefs.getBoolean(SettingsFragment.KEY_RESUME_ON_HEADSET, false);
                    if (state == 1 && resumeOnConnect && mediaPlayer != null && isPrepared && !isPlaying()) {
                        togglePlay();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(audioDeviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(audioDeviceReceiver, filter);
        }
    }

    private void closePlayerService() {
        activeIsPlaying = false;
        stopPeriodicStateSave();
        stateHandler.removeCallbacks(sleepTimerRunnable);
        saveCurrentState(true);
        releaseFadingOutPlayer();
        safeRelease();
        stopForeground(true);
        stopSelf();
        sendBroadcast(new Intent("com.example.parrot.CLOSE_APP"));
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopPeriodicStateSave();
        saveCurrentState(true);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        activeIsPlaying = false;
        stopPeriodicStateSave();
        stateHandler.removeCallbacks(sleepTimerRunnable);
        if (audioDeviceReceiver != null) {
            try {
                unregisterReceiver(audioDeviceReceiver);
            } catch (Exception ignored) {}
        }
        saveCurrentState(true);
        releaseFadingOutPlayer();
        safeRelease();
        if (mediaSession != null) {
            mediaSession.release();
        }
    }
    
    private void setupEqualizer() {
        if (mediaPlayer == null) return;
        int sessionId = mediaPlayer.getAudioSessionId();
        
        if (mEqualizer != null) mEqualizer.release();
        if (mBassBoost != null) mBassBoost.release();
        if (mPresetReverb != null) mPresetReverb.release();
        
        try {
            mEqualizer = new Equalizer(0, sessionId);
            mBassBoost = new BassBoost(0, sessionId);
            mPresetReverb = new PresetReverb(0, sessionId);
            loadEqualizerSettings();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void loadEqualizerSettings() {
        if (mEqualizer == null || mBassBoost == null || mPresetReverb == null) return;
        SharedPreferences prefs = getSharedPreferences("parrot_eq", Context.MODE_PRIVATE);
        boolean isEqEnabled = prefs.getBoolean("isEqualizerEnabled", true);
        
        try {
            mEqualizer.setEnabled(isEqEnabled);
            mBassBoost.setEnabled(isEqEnabled);
            mPresetReverb.setEnabled(isEqEnabled);
            
            if (isEqEnabled) {
                int bassStrength = prefs.getInt("bassStrength", -1);
                if (bassStrength != -1 && mBassBoost.getStrengthSupported()) {
                    mBassBoost.setStrength((short) bassStrength);
                }
                
                int reverbPreset = prefs.getInt("reverbPreset", -1);
                if (reverbPreset != -1) {
                    mPresetReverb.setPreset((short) reverbPreset);
                }
                
                short numBands = mEqualizer.getNumberOfBands();
                for (short i = 0; i < numBands; i++) {
                    int level = prefs.getInt("band" + i, mEqualizer.getBandLevel(i));
                    mEqualizer.setBandLevel(i, (short) level);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}