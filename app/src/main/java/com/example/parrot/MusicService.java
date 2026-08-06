package com.example.parrot;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
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

    private final IBinder binder = new MyBinder();
    
    private MediaPlayer mediaPlayer;
    private ArrayList<String> mySongs = new ArrayList<>();
    private int position = 0;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    
    private QueueManager queueManager = new QueueManager();
    
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
        
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                if (mediaPlayer == null) return;
                
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                            updateMediaSession();
                            showNotification(R.drawable.ic_pause);
                            broadcastUIUpdate();
                        }
                        mediaPlayer.setVolume(1.0f, 1.0f);
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            updateMediaSession();
                            showNotification(R.drawable.ic_play);
                            broadcastUIUpdate();
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
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
                if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                    togglePlay();
                }
            }

            @Override
            public void onPause() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
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
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo((int) pos);
                    updateMediaSession();
                }
            }
            
            @Override
            public void onCustomAction(String action, Bundle extras) {
                if (ACTION_CLOSE.equals(action)) {
                    safeRelease();
                    stopForeground(true);
                    stopSelf();
                    sendBroadcast(new Intent("com.example.parrot.CLOSE_APP"));
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
                    safeRelease();
                    stopForeground(true);
                    stopSelf();
                    
                    // Exit the app completely
                    Intent closeIntent = new Intent("com.example.parrot.CLOSE_APP");
                    sendBroadcast(closeIntent);
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    public void playSong(int pos) {
        safeRelease();
        position = pos;

        if (mySongs.isEmpty()) return;

        Uri uri = Uri.parse(mySongs.get(position));
        mediaPlayer = MediaPlayer.create(this, uri);

        if (mediaPlayer == null) return;

        setupEqualizer();

        int result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mediaPlayer.start();
            updateMediaSession();
            showNotification(R.drawable.ic_pause);
            broadcastUIUpdate();
        }

        mediaPlayer.setOnCompletionListener(mp -> {
            if (isRepeat) {
                playSong(position);
            } else if (!queueManager.isEmpty()) {
                // Play next song from queue
                playNextFromQueue();
            } else {
                nextSong();
            }
        });
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
            
            safeRelease();
            
            Uri uri = Uri.parse(nextPath);
            mediaPlayer = MediaPlayer.create(this, uri);
            if (mediaPlayer == null) return;
            
            setupEqualizer();
            
            int result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer.start();
                updateMediaSession();
                showNotification(R.drawable.ic_pause);
                broadcastUIUpdate();
            }
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (isRepeat) {
                    playSong(position);
                } else if (!queueManager.isEmpty()) {
                    playNextFromQueue();
                } else {
                    nextSong();
                }
            });
            
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
            
            safeRelease();
            
            Uri uri = Uri.parse(path);
            mediaPlayer = MediaPlayer.create(this, uri);
            if (mediaPlayer == null) return;
            
            setupEqualizer();
            
            int result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer.start();
                updateMediaSession();
                showNotification(R.drawable.ic_pause);
                broadcastUIUpdate();
            }
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (isRepeat) {
                    playSong(position);
                } else if (!queueManager.isEmpty()) {
                    playNextFromQueue();
                } else {
                    nextSong();
                }
            });
            
            broadcastQueueUpdate();
        }
    }

    public void togglePlay() {
        if (mediaPlayer == null) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            updateMediaSession();
            showNotification(R.drawable.ic_play);
        } else {
            int result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer.start();
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
            position = new Random().nextInt(mySongs.size());
        } else {
            position = (position + 1) % mySongs.size();
        }
        playSong(position);
    }

    public void prevSong() {
        if (mySongs.isEmpty()) return;

        if (isShuffle) {
            position = new Random().nextInt(mySongs.size());
        } else {
            position = (position - 1 < 0) ? mySongs.size() - 1 : position - 1;
        }
        playSong(position);
    }

    private void safeRelease() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
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

        Bitmap icon = getAlbumArt(songPath);

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
                        .setShowActionsInCompactView(0, 1, 2))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        startForeground(1, notification);
    }
    
    private void broadcastUIUpdate() {
        if (mediaPlayer == null || mySongs.isEmpty()) return;
        
        Intent intent = new Intent(BROADCAST_UPDATE_UI);
        intent.setPackage(getPackageName()); // CRITICAL for Android 14 RECEIVER_NOT_EXPORTED
        intent.putExtra("song", getFileName(mySongs.get(position)));
        intent.putExtra("songPath", mySongs.get(position));
        intent.putExtra("isPlaying", mediaPlayer.isPlaying());
        intent.putExtra("duration", mediaPlayer.getDuration());
        sendBroadcast(intent);
        
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName widgetComponent = new ComponentName(this, PlayerWidgetProvider.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);
        if (widgetIds != null && widgetIds.length > 0) {
            RemoteViews views = PlayerWidgetProvider.getWidgetViews(this);
            views.setTextViewText(R.id.widgetSongName, getFileName(mySongs.get(position)));
            if (mediaPlayer.isPlaying()) {
                views.setImageViewResource(R.id.widgetPlayPause, R.drawable.ic_pause);
            } else {
                views.setImageViewResource(R.id.widgetPlayPause, R.drawable.ic_play);
            }
            
            // Set rounded album art
            Bitmap albumArt = getAlbumArt(mySongs.get(position));
            if (albumArt != null) {
                // Scale bitmap for widget to avoid memory issues and apply rounding
                Bitmap scaled = Bitmap.createScaledBitmap(albumArt, 100, 100, true);
                Bitmap rounded = getRoundedCornerBitmap(scaled, 16);
                views.setImageViewBitmap(R.id.widgetAlbumArt, rounded);
            }
            
            appWidgetManager.updateAppWidget(widgetIds, views);
        }
    }
    
    private void broadcastQueueUpdate() {
        Intent intent = new Intent(BROADCAST_QUEUE_UPDATED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
    
    private void updateMediaSession() {
        if (mediaPlayer == null) return;
        
        long currentPosition = mediaPlayer.getCurrentPosition();
        int state = mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        
        PlaybackStateCompat.CustomAction closeAction = new PlaybackStateCompat.CustomAction.Builder(
                ACTION_CLOSE,
                "Close",
                R.drawable.ic_close
        ).build();
        
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                            PlaybackStateCompat.ACTION_PAUSE |
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                            PlaybackStateCompat.ACTION_SEEK_TO)
                .addCustomAction(closeAction)
                .setState(state, currentPosition, 1.0f)
                .build());
                
        String songPath = mySongs.get(this.position);
        String songName = getFileName(songPath);
        Bitmap icon = getAlbumArt(songPath);
        
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, songName)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, icon)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration())
                .build());
    }

    private Bitmap getAlbumArt(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            byte[] art = retriever.getEmbeddedPicture();
            retriever.release();
            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return BitmapFactory.decodeResource(getResources(), R.drawable.ic_music);
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
        return (index != -1) ? path.substring(index + 1) : path;
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public String getCurrentSongPath() {
        if (mySongs != null && !mySongs.isEmpty() && position >= 0 && position < mySongs.size()) {
            return mySongs.get(position);
        }
        return null;
    }
    
    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }
    
    public int getAudioSessionId() {
        if (mediaPlayer != null) {
            return mediaPlayer.getAudioSessionId();
        }
        return 0;
    }
    
    public void seekTo(int pos) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(pos);
            updateMediaSession();
        }
    }
    
    public void setShuffle(boolean shuffle) {
        isShuffle = shuffle;
    }
    
    public void setRepeat(boolean repeat) {
        isRepeat = repeat;
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

    @Override
    public void onDestroy() {
        super.onDestroy();
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