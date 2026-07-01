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
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Random;

public class MusicService extends Service {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREV = "ACTION_PREV";
    public static final String ACTION_REQUEST_UPDATE = "ACTION_REQUEST_UPDATE";
    
    public static final String BROADCAST_UPDATE_UI = "com.example.parrot.UPDATE_UI";

    private final IBinder binder = new MyBinder();
    
    private MediaPlayer mediaPlayer;
    private ArrayList<String> mySongs = new ArrayList<>();
    private int position = 0;
    private boolean isShuffle = false;
    private boolean isRepeat = false;

    private MediaSessionCompat mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
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

        mediaPlayer.start();
        updateMediaSession();
        showNotification(R.drawable.ic_pause);
        broadcastUIUpdate();

        mediaPlayer.setOnCompletionListener(mp -> {
            if (isRepeat) {
                playSong(position);
            } else {
                nextSong();
            }
        });
    }

    public void togglePlay() {
        if (mediaPlayer == null) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            updateMediaSession();
            showNotification(R.drawable.ic_play);
        } else {
            mediaPlayer.start();
            updateMediaSession();
            showNotification(R.drawable.ic_pause);
        }
        broadcastUIUpdate();
    }

    public void nextSong() {
        if (mySongs.isEmpty()) return;

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
    }
    
    private void updateMediaSession() {
        if (mediaPlayer == null) return;
        
        long currentPosition = mediaPlayer.getCurrentPosition();
        int state = mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                            PlaybackStateCompat.ACTION_PAUSE |
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                            PlaybackStateCompat.ACTION_SEEK_TO)
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        safeRelease();
        if (mediaSession != null) {
            mediaSession.release();
        }
    }
}