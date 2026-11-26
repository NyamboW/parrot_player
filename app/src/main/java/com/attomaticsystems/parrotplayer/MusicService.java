package com.attomaticsystems.parrotplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
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
import android.view.KeyEvent;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.media.audiofx.Equalizer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.PresetReverb;
import android.content.SharedPreferences;
import android.content.Context;
import android.media.AudioManager;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Random;

public class MusicService extends Service {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREV = "ACTION_PREV";
    public static final String ACTION_REQUEST_UPDATE = "ACTION_REQUEST_UPDATE";
    public static final String ACTION_RELOAD_EQUALIZER = "ACTION_RELOAD_EQUALIZER";
    public static final String ACTION_CLOSE = "ACTION_CLOSE";
    public static final String ACTION_DISMISS_NOTIFICATION = "ACTION_DISMISS_NOTIFICATION";
    public static final String ACTION_ADD_TO_QUEUE = "ACTION_ADD_TO_QUEUE";
    public static final String ACTION_ADD_MULTIPLE_TO_QUEUE = "ACTION_ADD_MULTIPLE_TO_QUEUE";
    public static final String ACTION_PLAY_NEXT = "ACTION_PLAY_NEXT";
    public static final String ACTION_PLAY_NEXT_MULTIPLE = "ACTION_PLAY_NEXT_MULTIPLE";
    
    public static final String BROADCAST_UPDATE_UI = "com.attomaticsystems.parrotplayer.UPDATE_UI";
    public static final String BROADCAST_QUEUE_UPDATED = "com.attomaticsystems.parrotplayer.QUEUE_UPDATED";

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static volatile String activeSongPath = null;
    private static volatile boolean activeIsPlaying = false;
    private static volatile MusicService activeInstance = null;

    public static MusicService getActiveInstance() {
        return activeInstance;
    }

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
    private int sessionTrackOrder = 1;
    private final ArrayList<Integer> shuffleOrder = new ArrayList<>();
    private int shuffleCursor = -1;
    private final Random shuffleRandom = new Random();
    private boolean isShuffle = false;
    private int repeatMode = REPEAT_OFF;
    private int abLoopStartMs = -1;
    private int abLoopEndMs = -1;
    
    private QueueManager queueManager = new QueueManager();
    private PlayerStateManager stateManager;
    private int restoredSeekPosition = 0;
    private int restoredDuration = 0;

    private float playbackSpeed = 1.0f;
    private int pitchSemitones = 0;
    private int stereoBalance = 0; // -100 (Full Left) .. 0 (Center) .. +100 (Full Right)
    private float preampGainScalar = 1.0f;
    private float activeVolumeEnvelope = 1.0f;
    private int crossfadeSeconds = 0;
    private boolean skipSilence = false;
    private boolean stopAfterCurrentTrack = false;
    private int headsetClickCount = 0;
    private long sleepTimerEndElapsedMs = 0L;
    private MediaPlayer fadingOutPlayer;
    private boolean crossfadeTriggered = false;
    private boolean incomingFadeIn = false;
    private MediaPlayer gaplessNextPlayer;
    private int gaplessNextToken = 0;
    private boolean isGaplessNextPrepared = false;
    private String gaplessNextPath = null;
    private boolean gaplessNextFromQueue = false;
    private boolean gaplessNextRepeatOne = false;
    private int gaplessTargetPosition = -1;
    private int gaplessTargetShuffleCursor = -1;
    private BroadcastReceiver audioDeviceReceiver;
    private AudioDeviceCallback audioDeviceCallback;
    private boolean audioDeviceCallbackReady = false;
    private long lastAutoResumeElapsedMs = 0L;
    private boolean pausedByAudioFocus = false;
    private volatile boolean isClosing = false;

    private static final long STATE_SAVE_INTERVAL_MS = 3000L;
    private static final long TRANSITION_POLL_INTERVAL_MS = 250L;
    private final Handler stateHandler = new Handler(Looper.getMainLooper());
    private final Runnable periodicStateSaveRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying()) {
                saveCurrentState(false);
                updateWidget(getCurrentSongPath());
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
    private final Runnable headsetClickRunnable = new Runnable() {
        @Override
        public void run() {
            int clicks = headsetClickCount;
            headsetClickCount = 0;
            if (clicks == 1) {
                togglePlay();
            } else if (clicks == 2) {
                nextSong();
            } else if (clicks >= 3) {
                prevSong();
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
                    setPlayerVolumeEnvelope(1.0f);
                }
            } else {
                if (remaining <= 3000L && mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
                    float vol = Math.max(0.08f, remaining / 3000f);
                    setPlayerVolumeEnvelope(vol);
                }
                stateHandler.postDelayed(this, 500L);
            }
        }
    };
    
    private Equalizer mEqualizer;
    private BassBoost mBassBoost;
    private PresetReverb mPresetReverb;
    private LoudnessEnhancer mLoudnessEnhancer;

    private MediaSessionCompat mediaSession;
    
    private AudioManager audioManager;
    private AudioFocusRequestCompat audioFocusRequest;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        stateManager = new PlayerStateManager(this);
        loadPlaybackPreferences();
        restoreSavedState();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        registerAudioDeviceReceiver();
        audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                if (mediaPlayer == null || !isPrepared) return;

                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        boolean shouldResume = pausedByAudioFocus;
                        pausedByAudioFocus = false;
                        if (shouldResume && !mediaPlayer.isPlaying()) {
                            setPlayerVolumeEnvelope(1.0f);
                            mediaPlayer.start();
                            applyPlaybackSpeed();
                            startPeriodicStateSave();
                            updateMediaSession();
                            showNotification(R.drawable.ic_pause);
                            broadcastUIUpdate();
                        } else {
                            setPlayerVolumeEnvelope(1.0f);
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        pausedByAudioFocus = false;
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
                            pausedByAudioFocus = true;
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
                            boolean duckOnNotification = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
                                    .getBoolean(SettingsFragment.KEY_DUCK_ON_NOTIFICATION, true);
                            if (duckOnNotification) {
                                pausedByAudioFocus = false;
                                setPlayerVolumeEnvelope(0.2f);
                            } else {
                                pausedByAudioFocus = true;
                                mediaPlayer.pause();
                                stopPeriodicStateSave();
                                saveCurrentState(false);
                                updateMediaSession();
                                showNotification(R.drawable.ic_play);
                                broadcastUIUpdate();
                            }
                        }
                        break;
                }
            }
        };

        audioFocusRequest = buildAudioFocusRequest();
                
        mediaSession = new MediaSessionCompat(this, "MusicService");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                if (mediaButtonEvent != null) {
                    KeyEvent event = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        int keyCode = event.getKeyCode();
                        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                            headsetClickCount++;
                            stateHandler.removeCallbacks(headsetClickRunnable);
                            if (headsetClickCount >= 3) {
                                stateHandler.post(headsetClickRunnable);
                            } else {
                                stateHandler.postDelayed(headsetClickRunnable, 340L);
                            }
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                            int dur = getDuration();
                            if (dur > 0) {
                                seekTo(Math.min(dur, getCurrentPosition() + 10_000));
                            }
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                            int dur = getDuration();
                            if (dur > 0) {
                                seekTo(Math.max(0, getCurrentPosition() - 10_000));
                            }
                            return true;
                        }
                    }
                }
                return super.onMediaButtonEvent(mediaButtonEvent);
            }

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
            public void onStop() {
                dismissNotificationAndStopService();
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
                    isClosing = false;
                    if (intent.hasExtra("shuffle")) {
                        isShuffle = intent.getBooleanExtra("shuffle", isShuffle);
                        if (stateManager != null) {
                            stateManager.saveModes(isShuffle, repeatMode);
                        }
                    }
                    ArrayList<String> newSongs = intent.getStringArrayListExtra("songs");
                    if (newSongs != null) {
                        mySongs = newSongs;
                        position = intent.getIntExtra("pos", 0);
                        sessionTrackOrder = 1;
                        restoredSeekPosition = 0;
                        restoredDuration = 0;
                        rebuildShuffleOrder(position);
                        stateManager.savePlaylist(mySongs);
                        playSong(position);
                    }
                    break;
                case ACTION_PLAY_PAUSE:
                    isClosing = false;
                    if (mySongs.isEmpty()) {
                        stopEmptyForegroundActionSafely();
                    } else {
                        togglePlay();
                    }
                    break;
                case ACTION_NEXT:
                    isClosing = false;
                    if (mySongs.isEmpty()) {
                        stopEmptyForegroundActionSafely();
                    } else {
                        nextSong();
                    }
                    break;
                case ACTION_PREV:
                    isClosing = false;
                    if (mySongs.isEmpty()) {
                        stopEmptyForegroundActionSafely();
                    } else {
                        prevSong();
                    }
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
                case ACTION_DISMISS_NOTIFICATION:
                    dismissNotificationAndStopService();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    /**
     * Starts playing a track list directly in the service without opening full-screen PlayerActivity.
     */
    public static void startPlaybackList(Context context, ArrayList<String> songs, int pos) {
        startPlaybackList(context, songs, pos, null);
    }

    /**
     * Starts playing a track list directly in the service with shuffle enabled.
     */
    public static void startPlaybackShuffled(Context context, ArrayList<String> songs, int pos) {
        startPlaybackList(context, songs, pos, Boolean.TRUE);
    }

    private static void startPlaybackList(Context context, ArrayList<String> songs, int pos, Boolean shuffleOverride) {
        if (context == null || songs == null || songs.isEmpty()) return;
        int safePos = Math.max(0, Math.min(pos, songs.size() - 1));
        String targetPath = songs.get(safePos);
        if (targetPath != null) {
            AlbumArtLoader.getInstance().preloadFullArt(targetPath);
        }
        Intent serviceIntent = new Intent(context, MusicService.class);
        serviceIntent.setAction(ACTION_START);
        serviceIntent.putStringArrayListExtra("songs", songs);
        serviceIntent.putExtra("pos", safePos);
        if (shuffleOverride != null) {
            serviceIntent.putExtra("shuffle", shuffleOverride);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
        if (context instanceof AutoHideScrollHelper.ScrollBarController) {
            ((AutoHideScrollHelper.ScrollBarController) context).setBarsVisible(true);
        }
    }

    public void playSong(int pos) {
        position = pos;
        if (mySongs.isEmpty() || position < 0 || position >= mySongs.size()) return;
        if (isShuffle) {
            ensureShuffleDeckSyncedToPosition(position);
        }
        restoredSeekPosition = 0;
        restoredDuration = 0;
        startPlayback(mySongs.get(position), 0);
    }

    private void startPlayback(String songPath, int initialSeekMs) {
        safeRelease();
        isClosing = false;
        final int token = ++playToken;
        isPrepared = false;
        pausedByAudioFocus = false;
        crossfadeTriggered = false;
        if (repeatMode != REPEAT_ONE) {
            abLoopStartMs = -1;
            abLoopEndMs = -1;
        }
        ListeningStatsManager.recordSongPlayed(this, songPath);
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
                if (targetSeek == 0) {
                    int longTrackResume = BookmarkManager.getLongTrackResumePosition(MusicService.this, songPath, duration);
                    if (longTrackResume > 0) {
                        targetSeek = longTrackResume;
                    } else if (skipSilence && duration > 15000) {
                        targetSeek = 350;
                    }
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
                prepareNextGaplessPlayer();
            });
            attachPlaybackListeners(mp, token);

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

    private void attachPlaybackListeners(MediaPlayer mp, int token) {
        mp.setOnCompletionListener(completedMp -> {
            if (token != playToken || completedMp != mediaPlayer) return;
            stopPeriodicStateSave();
            if (stopAfterCurrentTrack) {
                stopAfterCurrentTrack = false;
                try {
                    completedMp.seekTo(0);
                } catch (Exception ignored) {}
                updateMediaSession();
                showNotification(R.drawable.ic_play);
                broadcastUIUpdate();
                saveCurrentState(false);
                return;
            }
            if (gaplessNextPlayer != null && isGaplessNextPrepared && gaplessNextPath != null
                    && crossfadeSeconds == 0 && !skipSilence
                    && !(abLoopStartMs >= 0 && abLoopEndMs > abLoopStartMs)) {
                promoteGaplessNextPlayer();
                return;
            }
            if (repeatMode == REPEAT_ONE) {
                playSong(position);
            } else if (!queueManager.isEmpty()) {
                playNextFromQueue();
            } else if (repeatMode == REPEAT_OFF && isAtEndOfPlaylistCycle()) {
                try {
                    completedMp.seekTo(0);
                } catch (Exception ignored) {}
                updateMediaSession();
                showNotification(R.drawable.ic_play);
                broadcastUIUpdate();
                saveCurrentState(false);
            } else {
                nextSong();
            }
        });
        mp.setOnErrorListener((errorMp, what, extra) -> {
            if (token != playToken || errorMp != mediaPlayer) return true;
            isPrepared = false;
            stopPeriodicStateSave();
            releaseGaplessNextPlayer();
            return true;
        });
    }

    /**
     * Pre-prepares the upcoming track on a standby MediaPlayer and links it via
     * {@link MediaPlayer#setNextMediaPlayer(MediaPlayer)} for zero-gap transitions.
     */
    private void prepareNextGaplessPlayer() {
        if (mediaPlayer == null || !isPrepared || mySongs == null || mySongs.isEmpty()) {
            releaseGaplessNextPlayer();
            return;
        }
        if (crossfadeSeconds > 0 || skipSilence || stopAfterCurrentTrack
                || (abLoopStartMs >= 0 && abLoopEndMs > abLoopStartMs)) {
            releaseGaplessNextPlayer();
            return;
        }

        String nextPath = null;
        boolean fromQueue = false;
        boolean repeatOne = false;
        int targetPos = -1;
        int targetCursor = -1;

        if (repeatMode == REPEAT_ONE) {
            if (position >= 0 && position < mySongs.size()) {
                nextPath = mySongs.get(position);
                repeatOne = true;
                targetPos = position;
                targetCursor = shuffleCursor;
            }
        } else if (!queueManager.isEmpty()) {
            ArrayList<String> queue = queueManager.getQueue();
            if (!queue.isEmpty()) {
                nextPath = queue.get(0);
                fromQueue = true;
                targetPos = Math.min(position + 1, mySongs.size());
            }
        } else if (repeatMode == REPEAT_OFF && isAtEndOfPlaylistCycle()) {
            releaseGaplessNextPlayer();
            return;
        } else if (isShuffle) {
            ensureShuffleDeckValid();
            if (shuffleOrder.size() <= 1) {
                targetPos = 0;
                targetCursor = 0;
                nextPath = mySongs.get(0);
            } else if (shuffleCursor + 1 < shuffleOrder.size()) {
                targetCursor = shuffleCursor + 1;
                targetPos = shuffleOrder.get(targetCursor);
                nextPath = mySongs.get(targetPos);
            } else {
                // End of current shuffle cycle: let nextSong() build a fresh cycle without duplicate titles.
                releaseGaplessNextPlayer();
                return;
            }
        } else {
            targetPos = (position + 1) % mySongs.size();
            nextPath = mySongs.get(targetPos);
        }

        if (nextPath == null || nextPath.isEmpty()) {
            releaseGaplessNextPlayer();
            return;
        }

        if (gaplessNextPlayer != null && isGaplessNextPrepared
                && nextPath.equals(gaplessNextPath)
                && gaplessNextFromQueue == fromQueue
                && gaplessNextRepeatOne == repeatOne
                && gaplessTargetPosition == targetPos
                && gaplessTargetShuffleCursor == targetCursor) {
            return;
        }

        releaseGaplessNextPlayer();
        final int nextToken = ++gaplessNextToken;
        gaplessNextPath = nextPath;
        gaplessNextFromQueue = fromQueue;
        gaplessNextRepeatOne = repeatOne;
        gaplessTargetPosition = targetPos;
        gaplessTargetShuffleCursor = targetCursor;

        MediaPlayer nextMp = new MediaPlayer();
        gaplessNextPlayer = nextMp;
        try {
            nextMp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            nextMp.setDataSource(this, Uri.parse(nextPath));
            nextMp.setOnPreparedListener(preparedNext -> {
                if (nextToken != gaplessNextToken || preparedNext != gaplessNextPlayer
                        || mediaPlayer == null || !isPrepared) {
                    try {
                        preparedNext.setOnPreparedListener(null);
                        preparedNext.setOnErrorListener(null);
                        preparedNext.release();
                    } catch (Exception ignored) {}
                    return;
                }
                try {
                    preparedNext.setVolume(1.0f, 1.0f);
                    mediaPlayer.setNextMediaPlayer(preparedNext);
                    isGaplessNextPrepared = true;
                } catch (Exception e) {
                    releaseGaplessNextPlayer();
                }
            });
            nextMp.setOnErrorListener((errorMp, what, extra) -> {
                if (nextToken == gaplessNextToken) {
                    releaseGaplessNextPlayer();
                }
                return true;
            });
            nextMp.prepareAsync();
        } catch (Exception e) {
            releaseGaplessNextPlayer();
        }
    }

    /**
     * Promotes the already-started gapless next MediaPlayer when the outgoing track completes.
     */
    private void promoteGaplessNextPlayer() {
        MediaPlayer oldMp = mediaPlayer;
        MediaPlayer promotedMp = gaplessNextPlayer;
        String nextPath = gaplessNextPath;
        boolean wasFromQueue = gaplessNextFromQueue;
        boolean wasRepeatOne = gaplessNextRepeatOne;
        int targetPos = gaplessTargetPosition;
        int targetCursor = gaplessTargetShuffleCursor;

        gaplessNextToken++;
        gaplessNextPlayer = null;
        isGaplessNextPrepared = false;
        gaplessNextPath = null;
        gaplessNextFromQueue = false;
        gaplessNextRepeatOne = false;
        gaplessTargetPosition = -1;
        gaplessTargetShuffleCursor = -1;

        if (oldMp != null) {
            try {
                oldMp.setOnPreparedListener(null);
                oldMp.setOnCompletionListener(null);
                oldMp.setOnErrorListener(null);
                oldMp.release();
            } catch (Exception ignored) {}
        }

        final int newToken = ++playToken;
        mediaPlayer = promotedMp;
        isPrepared = true;
        pausedByAudioFocus = false;
        crossfadeTriggered = false;
        incomingFadeIn = false;
        restoredSeekPosition = 0;

        if (!wasRepeatOne) {
            abLoopStartMs = -1;
            abLoopEndMs = -1;
            if (wasFromQueue) {
                String dequeued = queueManager.pollNext();
                if (dequeued == null) {
                    dequeued = nextPath;
                }
                int insertIndex = position + 1;
                if (insertIndex > mySongs.size()) insertIndex = mySongs.size();
                mySongs.add(insertIndex, dequeued);
                position = insertIndex;
                if (isShuffle) {
                    ensureShuffleDeckSyncedToPosition(position);
                }
                stateManager.savePlaylist(mySongs);
                sessionTrackOrder++;
            } else {
                if (targetPos >= 0 && targetPos < mySongs.size()) {
                    position = targetPos;
                }
                if (isShuffle && targetCursor >= 0) {
                    shuffleCursor = targetCursor;
                }
                sessionTrackOrder = (mySongs.size() <= 1 || sessionTrackOrder >= mySongs.size()) ? 1 : (sessionTrackOrder + 1);
            }
        }

        try {
            if (!promotedMp.isPlaying()) {
                int result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    promotedMp.start();
                }
            }
        } catch (Exception ignored) {}

        ListeningStatsManager.recordSongPlayed(this, nextPath);
        setupEqualizer();
        int dur = 0;
        try {
            dur = promotedMp.getDuration();
        } catch (Exception ignored) {}
        restoredDuration = Math.max(0, dur);
        if (playbackSpeed != 1.0f) {
            applyPlaybackSpeed();
        }
        attachPlaybackListeners(promotedMp, newToken);
        startPeriodicStateSave();
        ensureAlbumArtLoaded(nextPath);
        updateMediaSession();
        showNotification(promotedMp.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        broadcastUIUpdate();
        preloadNextSongArt();
        saveCurrentState(false);
        if (wasFromQueue) {
            broadcastQueueUpdate();
        } else {
            prepareNextGaplessPlayer();
        }
    }

    private void releaseGaplessNextPlayer() {
        gaplessNextToken++;
        boolean wasPrepared = isGaplessNextPrepared;
        isGaplessNextPrepared = false;
        gaplessNextPath = null;
        gaplessNextFromQueue = false;
        gaplessNextRepeatOne = false;
        gaplessTargetPosition = -1;
        gaplessTargetShuffleCursor = -1;
        if (mediaPlayer != null && isPrepared && wasPrepared) {
            try {
                mediaPlayer.setNextMediaPlayer(null);
            } catch (Exception ignored) {}
        }
        if (gaplessNextPlayer != null) {
            try {
                gaplessNextPlayer.setOnPreparedListener(null);
                gaplessNextPlayer.setOnErrorListener(null);
                gaplessNextPlayer.release();
            } catch (Exception ignored) {}
            gaplessNextPlayer = null;
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
            sessionTrackOrder++;
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
            sessionTrackOrder++;
            restoredSeekPosition = 0;
            restoredDuration = 0;
            startPlayback(path, 0);
            broadcastQueueUpdate();
        }
    }

    public void togglePlay() {
        isClosing = false;
        pausedByAudioFocus = false;
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
                setPlayerVolumeEnvelope(1.0f);
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
            ensureShuffleDeckValid();
            if (shuffleOrder.size() <= 1) {
                position = 0;
            } else {
                shuffleCursor++;
                if (shuffleCursor >= shuffleOrder.size()) {
                    // Completed full non-repeating cycle of all songs; build a new cycle starting after current
                    rebuildShuffleOrder(position);
                    shuffleCursor = Math.min(1, shuffleOrder.size() - 1);
                }
                position = shuffleOrder.get(shuffleCursor);
            }
        } else {
            position = (position + 1) % mySongs.size();
        }
        sessionTrackOrder = (mySongs.size() <= 1 || sessionTrackOrder >= mySongs.size()) ? 1 : (sessionTrackOrder + 1);
        playSong(position);
    }

    public void prevSong() {
        if (mySongs.isEmpty()) return;

        if (isShuffle) {
            ensureShuffleDeckValid();
            if (shuffleOrder.size() <= 1) {
                position = 0;
            } else if (shuffleCursor > 0) {
                shuffleCursor--;
                position = shuffleOrder.get(shuffleCursor);
            } else {
                shuffleCursor = shuffleOrder.size() - 1;
                position = shuffleOrder.get(shuffleCursor);
            }
        } else {
            position = (position - 1 < 0) ? mySongs.size() - 1 : position - 1;
        }
        sessionTrackOrder = (sessionTrackOrder > 1) ? (sessionTrackOrder - 1) : mySongs.size();
        playSong(position);
    }

    /**
     * Builds a Fisher-Yates shuffled permutation of all songs with currentPos at index 0,
     * ensuring every song plays once before any song repeats and preventing duplicate titles back-to-back.
     */
    private void rebuildShuffleOrder(int currentPos) {
        shuffleOrder.clear();
        if (mySongs == null || mySongs.isEmpty()) {
            shuffleCursor = -1;
            return;
        }
        int safeCurrent = Math.max(0, Math.min(currentPos, mySongs.size() - 1));
        shuffleOrder.add(safeCurrent);
        shuffleCursor = 0;

        if (mySongs.size() == 1) {
            return;
        }

        ArrayList<Integer> remaining = new ArrayList<>(mySongs.size() - 1);
        for (int i = 0; i < mySongs.size(); i++) {
            if (i != safeCurrent) {
                remaining.add(i);
            }
        }
        Collections.shuffle(remaining, shuffleRandom);

        // Spread out any duplicate song titles so identical tracks never play back-to-back
        String prevTitle = normalizeTitleForShuffle(mySongs.get(safeCurrent));
        for (int i = 0; i < remaining.size(); i++) {
            String candidateTitle = normalizeTitleForShuffle(mySongs.get(remaining.get(i)));
            if (candidateTitle.equals(prevTitle)) {
                for (int j = i + 1; j < remaining.size(); j++) {
                    String swapTitle = normalizeTitleForShuffle(mySongs.get(remaining.get(j)));
                    if (!swapTitle.equals(prevTitle)) {
                        Collections.swap(remaining, i, j);
                        candidateTitle = swapTitle;
                        break;
                    }
                }
            }
            prevTitle = candidateTitle;
        }

        shuffleOrder.addAll(remaining);
    }

    private String normalizeTitleForShuffle(String path) {
        if (path == null) return "";
        return getFileName(path).trim().toLowerCase(Locale.ROOT);
    }

    private void ensureShuffleDeckValid() {
        if (mySongs == null || mySongs.isEmpty()) {
            shuffleOrder.clear();
            shuffleCursor = -1;
            return;
        }
        if (shuffleOrder.size() != mySongs.size()
                || shuffleCursor < 0
                || shuffleCursor >= shuffleOrder.size()) {
            rebuildShuffleOrder(position);
        }
    }

    private void ensureShuffleDeckSyncedToPosition(int targetPos) {
        ensureShuffleDeckValid();
        if (shuffleOrder.isEmpty()) return;
        if (shuffleCursor >= 0 && shuffleCursor < shuffleOrder.size()
                && shuffleOrder.get(shuffleCursor) == targetPos) {
            return;
        }
        int existingIdx = shuffleOrder.indexOf(targetPos);
        if (existingIdx >= 0) {
            shuffleCursor = existingIdx;
        } else {
            rebuildShuffleOrder(targetPos);
        }
    }

    private void safeRelease() {
        releaseGaplessNextPlayer();
        pausedByAudioFocus = false;
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
        if (isClosing || mySongs.isEmpty() || position < 0 || position >= mySongs.size()) return;
        
        String songPath = mySongs.get(position);
        String songName = getFileName(songPath);
        TrackMetadataHelper.TrackInfo cachedInfo = TrackMetadataHelper.getCached(songPath);
        String contentText = (cachedInfo != null && cachedInfo.hasKnownArtist())
                ? cachedInfo.getArtistAlbumSubtitle()
                : "Parrot Audio Player";

        Intent prevIntent = new Intent(this, MusicService.class).setAction(ACTION_PREV);
        PendingIntent prevPendingIntent = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent playIntent = new Intent(this, MusicService.class).setAction(ACTION_PLAY_PAUSE);
        PendingIntent playPendingIntent = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent nextIntent = new Intent(this, MusicService.class).setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent closeIntent = new Intent(this, MusicService.class).setAction(ACTION_CLOSE);
        PendingIntent closePendingIntent = PendingIntent.getService(this, 4, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(this, MusicService.class).setAction(ACTION_DISMISS_NOTIFICATION);
        PendingIntent dismissPendingIntent = PendingIntent.getService(this, 10, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap icon = AlbumArtLoader.getInstance().getBestCachedArt(songPath);
        boolean isPlayingState = (playPauseBtn == R.drawable.ic_pause);
        String subText = String.format(Locale.US, "%d/%d", getPlayOrderNumber(), mySongs.size());

        Notification notification = new NotificationCompat.Builder(this, ApplicationClass.CHANNEL_ID_1)
                .setSmallIcon(R.drawable.ic_music)
                .setLargeIcon(icon)
                .setContentTitle(songName)
                .setContentText(contentText)
                .setSubText(subText)
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
                .setDeleteIntent(dismissPendingIntent)
                .setOngoing(isPlayingState)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (isPlayingState || pausedByAudioFocus) {
            try {
                startForeground(1, notification);
            } catch (Exception e) {
                if (nm != null) {
                    nm.notify(1, notification);
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH);
            } else {
                stopForeground(false);
            }
            if (nm != null) {
                nm.notify(1, notification);
            }
        }
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
        intent.putExtra("isRepeat", repeatMode == REPEAT_ONE);
        intent.putExtra("repeatMode", repeatMode);
        intent.putExtra("playOrder", getPlayOrderNumber());
        intent.putExtra("trackIndex", position);
        intent.putExtra("totalTracks", mySongs.size());
        sendBroadcast(intent);
        
        updateWidget(songPath);
    }
    
    private void broadcastQueueUpdate() {
        if (stateManager != null) {
            stateManager.saveQueue(queueManager.getQueue());
        }
        prepareNextGaplessPlayer();
        Intent intent = new Intent(BROADCAST_QUEUE_UPDATED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
    
    private void updateMediaSession() {
        if (isClosing || mediaPlayer == null || mySongs.isEmpty() || position < 0 || position >= mySongs.size()) return;
        if (!mediaSession.isActive()) {
            mediaSession.setActive(true);
        }
        
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
                            PlaybackStateCompat.ACTION_STOP |
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                            PlaybackStateCompat.ACTION_SEEK_TO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ MediaControlPanel maps CustomAction #1 to the left slot (action0)
            // and CustomAction #2 to the right slot (action1).
            stateBuilder.addCustomAction(leftSpacerAction);
        }
        stateBuilder.addCustomAction(closeAction)
                .setState(state, currentPosition, playbackSpeed);
        mediaSession.setPlaybackState(stateBuilder.build());

        final String songPath = mySongs.get(this.position);
        String songName = getFileName(songPath);
        Bitmap icon = AlbumArtLoader.getInstance().getBestCachedArt(songPath);
        TrackMetadataHelper.TrackInfo cachedInfo = TrackMetadataHelper.getCached(songPath);

        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                        (cachedInfo != null && !cachedInfo.title.isEmpty()) ? cachedInfo.title : songName)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        if (cachedInfo != null) {
            if (cachedInfo.hasKnownArtist()) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, cachedInfo.artist);
            }
            if (cachedInfo.hasKnownAlbum()) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, cachedInfo.album);
            }
        } else {
            TrackMetadataHelper.loadTrackInfoAsync(songPath, info -> {
                if (!isClosing && mediaPlayer != null && songPath.equals(getCurrentSongPath())) {
                    updateMediaSession();
                    showNotification((!isPrepared || isPlaying()) ? R.drawable.ic_pause : R.drawable.ic_play);
                    updateWidget(songPath);
                }
            });
        }
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
            if (!isClosing && mediaPlayer != null && songPath.equals(getCurrentSongPath())) {
                if (bitmap != null) {
                    updateMediaSession();
                    showNotification((!isPrepared || isPlaying()) ? R.drawable.ic_pause : R.drawable.ic_play);
                    updateWidget(songPath);
                }
            }
            preloadNextSongArt();
        });
    }

    private void stopEmptyForegroundActionSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Notification placeholder = new NotificationCompat.Builder(this, ApplicationClass.CHANNEL_ID_1)
                        .setSmallIcon(R.drawable.ic_music)
                        .setContentTitle("Parrot Audio Player")
                        .setContentText("No music queued")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build();
                startForeground(1, placeholder);
            } catch (Exception ignored) {}
        }
        stopForeground(true);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(1);
        }
        PlayerWidgetProvider.pushLiveUpdate(this, null, false, 0, 0);
        stopSelf();
    }

    private void updateWidget(String songPath) {
        boolean showPause = !isClosing && ((mediaPlayer != null && !isPrepared) || isPlaying());
        PlayerWidgetProvider.pushLiveUpdate(this, songPath, showPause, getCurrentPosition(), getDuration());
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
                ensureShuffleDeckValid();
                if (shuffleCursor + 1 < shuffleOrder.size()) {
                    loader.preloadFullArt(mySongs.get(shuffleOrder.get(shuffleCursor + 1)));
                }
                if (shuffleCursor - 1 >= 0 && shuffleCursor - 1 < shuffleOrder.size()) {
                    loader.preloadFullArt(mySongs.get(shuffleOrder.get(shuffleCursor - 1)));
                }
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

    private String getFileName(String path) {
        if (path == null) return "";
        TrackTagEditorHelper.TagOverride override = TrackTagEditorHelper.getOverride(this, path);
        if (override != null && override.title != null && !override.title.isEmpty()) {
            return override.title;
        }
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

    public int getCurrentTrackIndex() {
        return position;
    }

    public int getPlayOrderNumber() {
        return Math.max(1, sessionTrackOrder);
    }

    public int getTotalTrackCount() {
        return mySongs != null ? mySongs.size() : 0;
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
            updateWidget(getCurrentSongPath());
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
        if (isShuffle) {
            rebuildShuffleOrder(position);
        } else {
            shuffleOrder.clear();
            shuffleCursor = -1;
        }
        preloadNextSongArt();
        prepareNextGaplessPlayer();
        if (stateManager != null) {
            stateManager.saveModes(isShuffle, repeatMode);
        }
    }

    public boolean isShuffle() {
        return isShuffle;
    }

    public void setRepeatMode(int mode) {
        if (mode < REPEAT_OFF || mode > REPEAT_ONE) {
            mode = REPEAT_OFF;
        }
        repeatMode = mode;
        prepareNextGaplessPlayer();
        if (stateManager != null) {
            stateManager.saveModes(isShuffle, repeatMode);
        }
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void setRepeat(boolean repeat) {
        setRepeatMode(repeat ? REPEAT_ONE : REPEAT_OFF);
    }

    public boolean isRepeat() {
        return repeatMode == REPEAT_ONE;
    }

    public void setAbLoopStart(int startMs) {
        abLoopStartMs = Math.max(0, startMs);
        abLoopEndMs = -1;
    }

    public void setAbLoopEnd(int endMs) {
        if (abLoopStartMs >= 0 && endMs > abLoopStartMs + 500) {
            abLoopEndMs = endMs;
            prepareNextGaplessPlayer();
        }
    }

    public void clearAbLoop() {
        abLoopStartMs = -1;
        abLoopEndMs = -1;
        prepareNextGaplessPlayer();
    }

    public int getAbLoopStartMs() {
        return abLoopStartMs;
    }

    public int getAbLoopEndMs() {
        return abLoopEndMs;
    }

    private boolean isAtEndOfPlaylistCycle() {
        if (mySongs == null || mySongs.isEmpty()) return true;
        if (isShuffle) {
            ensureShuffleDeckValid();
            return shuffleCursor >= shuffleOrder.size() - 1;
        }
        return position >= mySongs.size() - 1;
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

    public void moveQueueItem(int fromIndex, int toIndex) {
        queueManager.moveItem(fromIndex, toIndex);
        broadcastQueueUpdate();
    }
    
    public void clearQueue() {
        queueManager.clearQueue();
        broadcastQueueUpdate();
    }

    public void onSongsDeletedFromStorage(java.util.List<String> deletedPaths) {
        if (deletedPaths == null || deletedPaths.isEmpty()) return;

        // Remove from Up Next queue
        ArrayList<String> queue = queueManager.getQueue();
        boolean queueChanged = false;
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (deletedPaths.contains(queue.get(i))) {
                queueManager.removeFromQueue(i);
                queueChanged = true;
            }
        }
        if (queueChanged) {
            broadcastQueueUpdate();
        }

        // Remove from active playlist
        if (mySongs != null && !mySongs.isEmpty()) {
            String currentPath = getCurrentSongPath();
            boolean currentDeleted = currentPath != null && deletedPaths.contains(currentPath);
            boolean wasPlaying = isPlaying();

            for (int i = mySongs.size() - 1; i >= 0; i--) {
                if (deletedPaths.contains(mySongs.get(i))) {
                    mySongs.remove(i);
                    if (i < position) {
                        position = Math.max(0, position - 1);
                    }
                }
            }

            if (stateManager != null) {
                stateManager.savePlaylist(mySongs);
            }

            if (mySongs.isEmpty()) {
                activeSongPath = null;
                activeIsPlaying = false;
                safeRelease();
                stopForeground(true);
            } else if (currentDeleted) {
                if (position >= mySongs.size()) {
                    position = 0;
                }
                if (wasPlaying) {
                    playSong(position);
                } else {
                    safeRelease();
                    activeSongPath = getCurrentSongPath();
                    activeIsPlaying = false;
                    broadcastUIUpdate();
                }
            } else {
                prepareNextGaplessPlayer();
            }
        }
    }

    private void restoreSavedState() {
        if (stateManager == null) return;
        PlayerStateManager.RestoredState restored = stateManager.restore();
        isShuffle = restored.isShuffle;
        repeatMode = restored.repeatMode;
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
        int curPos = getCurrentPosition();
        int curDur = getDuration();
        BookmarkManager.saveLongTrackPosition(this, currentPath, curPos, curDur);
        stateManager.savePlaybackState(
                position,
                currentPath,
                curPos,
                curDur,
                isShuffle,
                repeatMode,
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
        pitchSemitones = Math.max(-12, Math.min(12, prefs.getInt(SettingsFragment.KEY_PLAYBACK_PITCH_SEMITONES, 0)));
        crossfadeSeconds = Math.max(0, Math.min(12, prefs.getInt(SettingsFragment.KEY_CROSSFADE_SECONDS, 0)));
        skipSilence = prefs.getBoolean(SettingsFragment.KEY_SKIP_SILENCE, false);
    }

    public void setPlaybackSpeed(float speed) {
        playbackSpeed = Math.max(0.5f, Math.min(2.0f, speed));
        if (isPlaying()) {
            applyPlaybackSpeed();
        }
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void setPitchSemitones(int semitones) {
        pitchSemitones = Math.max(-12, Math.min(12, semitones));
        if (isPlaying()) {
            applyPlaybackSpeed();
        }
    }

    public int getPitchSemitones() {
        return pitchSemitones;
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
                    float pitchFactor = (float) Math.pow(2.0, pitchSemitones / 12.0);
                    params.setPitch(Math.max(0.5f, Math.min(2.0f, pitchFactor)));
                    mediaPlayer.setPlaybackParams(params);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void setPlayerVolumeEnvelope(float envelope) {
        activeVolumeEnvelope = Math.max(0f, Math.min(1f, envelope));
        applyCurrentPlayerVolume();
    }

    private void applyCurrentPlayerVolume() {
        if (mediaPlayer == null || !isPrepared) return;
        float base = Math.max(0f, Math.min(1f, activeVolumeEnvelope * preampGainScalar));
        float leftScale = (stereoBalance > 0) ? Math.max(0f, (100 - stereoBalance) / 100f) : 1.0f;
        float rightScale = (stereoBalance < 0) ? Math.max(0f, (100 + stereoBalance) / 100f) : 1.0f;
        try {
            mediaPlayer.setVolume(base * leftScale, base * rightScale);
        } catch (Exception ignored) {}
    }

    public void setCrossfadeSeconds(int seconds) {
        crossfadeSeconds = Math.max(0, Math.min(12, seconds));
        prepareNextGaplessPlayer();
    }

    public void setSkipSilence(boolean enabled) {
        skipSilence = enabled;
        prepareNextGaplessPlayer();
    }

    public void setStopAfterCurrentTrack(boolean enabled) {
        stopAfterCurrentTrack = enabled;
        prepareNextGaplessPlayer();
        broadcastUIUpdate();
    }

    public boolean isStopAfterCurrentTrack() {
        return stopAfterCurrentTrack;
    }

    public void setSleepTimerMinutes(int minutes) {
        stateHandler.removeCallbacks(sleepTimerRunnable);
        if (minutes <= 0) {
            sleepTimerEndElapsedMs = 0L;
            if (mediaPlayer != null && isPrepared) {
                setPlayerVolumeEnvelope(1.0f);
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
        if (mediaPlayer == null || !isPrepared) return;
        int currentPos = getCurrentPosition();
        if (abLoopStartMs >= 0 && abLoopEndMs > abLoopStartMs && currentPos >= abLoopEndMs) {
            seekTo(abLoopStartMs);
            return;
        }
        if (stopAfterCurrentTrack) return;
        if (repeatMode == REPEAT_ONE || crossfadeTriggered) return;
        if (repeatMode == REPEAT_OFF && queueManager.isEmpty() && isAtEndOfPlaylistCycle()) return;
        int duration = getDuration();
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
        releaseGaplessNextPlayer();
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
                setPlayerVolumeEnvelope(vol);
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
                    loadEqualizerSettings();
                } else if (Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                    if (isInitialStickyBroadcast()) return;
                    int state = intent.getIntExtra("state", -1);
                    loadEqualizerSettings();
                    if (state == 1) {
                        maybeAutoResumeOnAudioConnect();
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            audioDeviceCallbackReady = false;
            audioDeviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    if (!audioDeviceCallbackReady || addedDevices == null) return;
                    loadEqualizerSettings();
                    for (AudioDeviceInfo device : addedDevices) {
                        if (isExternalOutputDevice(device)) {
                            maybeAutoResumeOnAudioConnect();
                            break;
                        }
                    }
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    if (!audioDeviceCallbackReady) return;
                    loadEqualizerSettings();
                }
            };
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, stateHandler);
            stateHandler.postDelayed(() -> audioDeviceCallbackReady = true, 650L);
        }
    }

    private void maybeAutoResumeOnAudioConnect() {
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        boolean resumeOnConnect = prefs.getBoolean(SettingsFragment.KEY_RESUME_ON_HEADSET, false);
        if (!resumeOnConnect || mySongs == null || mySongs.isEmpty() || isPlaying()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastAutoResumeElapsedMs < 1500L) {
            return;
        }
        lastAutoResumeElapsedMs = now;
        togglePlay();
    }

    private boolean isExternalOutputDevice(AudioDeviceInfo device) {
        if (device == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !device.isSink()) {
            return false;
        }
        int type = device.getType();
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_DEVICE
                || type == AudioDeviceInfo.TYPE_USB_ACCESSORY) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER;
        }
        return false;
    }

    private AudioFocusRequestCompat buildAudioFocusRequest() {
        boolean duckOnNotification = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SettingsFragment.KEY_DUCK_ON_NOTIFICATION, true);
        return new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setWillPauseWhenDucked(!duckOnNotification)
                .build();
    }

    public void refreshAudioFocusPreference() {
        audioFocusRequest = buildAudioFocusRequest();
    }

    private void dismissNotificationAndStopService() {
        stopServicePlaybackAndClearNotification(false, false);
    }

    private void closePlayerService() {
        stopServicePlaybackAndClearNotification(true, true);
    }

    private void stopServicePlaybackAndClearNotification(boolean clearActiveSongPath, boolean finishAppUi) {
        isClosing = true;
        pausedByAudioFocus = false;
        activeIsPlaying = false;
        if (clearActiveSongPath) {
            activeSongPath = null;
        }
        stopPeriodicStateSave();
        stateHandler.removeCallbacksAndMessages(null);
        restoredSeekPosition = Math.max(0, getCurrentPosition());
        restoredDuration = Math.max(0, getDuration());
        saveCurrentState(true);
        releaseFadingOutPlayer();
        safeRelease();

        if (mediaSession != null) {
            try {
                mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f)
                        .build());
                mediaSession.setActive(false);
            } catch (Exception ignored) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(1);
        }

        if (finishAppUi) {
            updateWidget(getCurrentSongPath());
            stopSelf();
            Intent closeIntent = new Intent("com.attomaticsystems.parrotplayer.CLOSE_APP");
            closeIntent.setPackage(getPackageName());
            sendBroadcast(closeIntent);
        } else {
            broadcastUIUpdate();
            stopSelf();
        }
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
        if (activeInstance == this) {
            activeInstance = null;
        }
        activeIsPlaying = false;
        stopPeriodicStateSave();
        stateHandler.removeCallbacks(sleepTimerRunnable);
        if (audioDeviceReceiver != null) {
            try {
                unregisterReceiver(audioDeviceReceiver);
            } catch (Exception ignored) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null && audioDeviceCallback != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
            } catch (Exception ignored) {}
        }
        saveCurrentState(true);
        releaseFadingOutPlayer();
        safeRelease();
        if (mediaSession != null) {
            mediaSession.release();
        }
    }
    
    public boolean isExternalAudioOutputActive() {
        if (audioManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                if (devices != null) {
                    for (AudioDeviceInfo device : devices) {
                        if (isExternalOutputDevice(device)) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
    }

    public static String resolveEqProfilePrefix(Context context, SharedPreferences eqPrefs) {
        if (eqPrefs == null || !eqPrefs.getBoolean("autoOutputProfile", false)) {
            return "";
        }
        MusicService active = getActiveInstance();
        boolean external = false;
        if (active != null) {
            external = active.isExternalAudioOutputActive();
        } else if (context != null) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                external = am.isWiredHeadsetOn() || am.isBluetoothA2dpOn();
            }
        }
        return external ? "headset_" : "speaker_";
    }

    private void setupEqualizer() {
        if (mediaPlayer == null) return;
        int sessionId = mediaPlayer.getAudioSessionId();
        
        if (mEqualizer != null) {
            try { mEqualizer.release(); } catch (Exception ignored) {}
        }
        if (mBassBoost != null) {
            try { mBassBoost.release(); } catch (Exception ignored) {}
        }
        if (mPresetReverb != null) {
            try { mPresetReverb.release(); } catch (Exception ignored) {}
        }
        if (mLoudnessEnhancer != null) {
            try { mLoudnessEnhancer.release(); } catch (Exception ignored) {}
        }
        
        try {
            mEqualizer = new Equalizer(0, sessionId);
            mBassBoost = new BassBoost(0, sessionId);
            mPresetReverb = new PresetReverb(0, sessionId);
            try {
                mLoudnessEnhancer = new LoudnessEnhancer(sessionId);
            } catch (Exception ignored) {
                mLoudnessEnhancer = null;
            }
            loadEqualizerSettings();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public Equalizer getEqualizerInstance() {
        return mEqualizer;
    }

    public void reloadEqualizerNow() {
        loadEqualizerSettings();
    }

    private void loadEqualizerSettings() {
        if (mEqualizer == null || mBassBoost == null || mPresetReverb == null) return;
        SharedPreferences prefs = getSharedPreferences("parrot_eq", Context.MODE_PRIVATE);
        boolean isEqEnabled = prefs.getBoolean("isEqualizerEnabled", true);
        String pfx = resolveEqProfilePrefix(this, prefs);
        
        try {
            mEqualizer.setEnabled(isEqEnabled);
            mBassBoost.setEnabled(isEqEnabled);
            mPresetReverb.setEnabled(isEqEnabled);
            
            if (isEqEnabled) {
                int bassStrength = prefs.getInt(pfx + "bassStrength", prefs.getInt("bassStrength", 0));
                if (bassStrength >= 0 && mBassBoost.getStrengthSupported()) {
                    mBassBoost.setStrength((short) Math.min(1000, bassStrength));
                }
                
                int reverbPreset = prefs.getInt(pfx + "reverbPreset", prefs.getInt("reverbPreset", 0));
                if (reverbPreset >= 0) {
                    mPresetReverb.setPreset((short) reverbPreset);
                }
                
                short[] range = mEqualizer.getBandLevelRange();
                short minEq = (range != null && range.length >= 2) ? range[0] : -1500;
                short maxEq = (range != null && range.length >= 2) ? range[1] : 1500;

                int maxPositiveBandMb = 0;
                short numBands = mEqualizer.getNumberOfBands();
                for (short i = 0; i < numBands; i++) {
                    int fallbackLevel = prefs.getInt("band_mb_" + i, prefs.getInt("band" + i, 0));
                    int level = prefs.getInt(pfx + "band_mb_" + i, fallbackLevel);
                    short clamped = (short) Math.max(minEq, Math.min(maxEq, level));
                    if (clamped > maxPositiveBandMb) {
                        maxPositiveBandMb = clamped;
                    }
                    mEqualizer.setBandLevel(i, clamped);
                }

                // Preamp (-1200 to +1200 mB), Loudness Enhancer (0 to 1000 mB), Anti-Clipping Limiter & Stereo Balance
                int preampMb = Math.max(-1200, Math.min(1200,
                        prefs.getInt(pfx + "preampMb", prefs.getInt("preampMb", 0))));
                int loudnessMb = Math.max(0, Math.min(1000,
                        prefs.getInt(pfx + "loudnessMb", prefs.getInt("loudnessMb", 0))));
                boolean limiterEnabled = prefs.getBoolean("limiterEnabled", true);
                stereoBalance = Math.max(-100, Math.min(100,
                        prefs.getInt(pfx + "stereoBalance", prefs.getInt("stereoBalance", 0))));

                int limiterCutMb = 0;
                if (limiterEnabled) {
                    int bassBoostMb = (bassStrength * 450) / 1000;
                    int peakBoostMb = maxPositiveBandMb + bassBoostMb + Math.max(0, preampMb);
                    if (peakBoostMb > 250) {
                        limiterCutMb = Math.min(900, (peakBoostMb - 250) / 2);
                    }
                }

                int effectivePreampMb = preampMb - limiterCutMb;
                if (effectivePreampMb < 0) {
                    // Attenuate via software volume scalar (mB to linear amplitude: 10^(mB / 2000))
                    preampGainScalar = (float) Math.pow(10.0, effectivePreampMb / 2000.0);
                    preampGainScalar = Math.max(0.15f, Math.min(1.0f, preampGainScalar));
                } else {
                    preampGainScalar = 1.0f;
                }

                int targetLoudnessGainMb = Math.max(0, effectivePreampMb) + loudnessMb;
                if (mLoudnessEnhancer != null) {
                    try {
                        if (targetLoudnessGainMb > 0) {
                            mLoudnessEnhancer.setTargetGain(targetLoudnessGainMb);
                            mLoudnessEnhancer.setEnabled(true);
                        } else {
                            mLoudnessEnhancer.setTargetGain(0);
                            mLoudnessEnhancer.setEnabled(false);
                        }
                    } catch (Exception ignored) {}
                }
            } else {
                preampGainScalar = 1.0f;
                stereoBalance = 0;
                if (mLoudnessEnhancer != null) {
                    try {
                        mLoudnessEnhancer.setTargetGain(0);
                        mLoudnessEnhancer.setEnabled(false);
                    } catch (Exception ignored) {}
                }
            }
            applyCurrentPlayerVolume();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}