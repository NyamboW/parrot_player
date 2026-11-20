package com.attomaticsystems.parrotplayer;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity {

    private static final int COLOR_WHITE = Color.WHITE;
    private static final long SEEKBAR_UPDATE_INTERVAL_MS = 200L;

    ArrayList<String> mySongs;
    int position = 0;

    TextView txtSong, txtArtistAlbum, txtAudioQualityPill, txtTrackCounter, txtAbLoopPill, txtStart, txtEnd;
    SeekBar seekBar;
    ImageView imgView;
    CardView imgViewCard;
    private View playerRootLayout;
    private boolean isMinimizing = false;
    private boolean isDraggingDown = false;
    private float dragStartRawX = 0f;
    private float dragStartRawY = 0f;
    private boolean dragIgnored = false;
    private VelocityTracker velocityTracker;

    ImageButton btnPlay, btnNext, btnPrev, btnRepeat, btnShuffle, btnFav, btnEq, btnBack, btnQueue, btnMic, btnLyrics;

    private LinearLayout liveLyricBanner, lyricsOverlayContainer, lyricsLinesContainer;
    private ImageView imgLiveLyricIcon;
    private TextView txtLiveLyricLine, txtLiveLyricExpand, txtLyricsBadge, btnCloseLyricsOverlay;
    private ScrollView lyricsScrollView;
    private final ArrayList<TextView> lyricLineViews = new ArrayList<>();
    private LyricsExtractor.LyricsData currentLyricsData = LyricsExtractor.LyricsData.EMPTY;
    private String lyricsLoadedForPath = null;
    private int activeLyricLineIndex = -1;
    private boolean isLyricsOverlayVisible = false;
    private boolean isSmallLyricsVisible = false;

    private TrackMetadataHelper.TrackInfo currentTrackInfo = null;
    private String metadataLoadedForPath = null;
    private LinearLayout btnTimerSpeed;
    private ImageView imgTimerSpeedIcon;
    private TextView txtTimerSpeedStatus;
    private String lastTimerSpeedText = "";
    private boolean lastTimerSpeedActive = false;

    FavouritesManager favManager;
    String currentSongPath;

    boolean isShuffle = false;
    int repeatMode = MusicService.REPEAT_OFF;
    private boolean shuffleToggledBeforeBind = false;
    private boolean repeatToggledBeforeBind = false;
    private boolean isUserSeeking = false;

    final Handler handler = new Handler(Looper.getMainLooper());
    Runnable updateSeekBar;

    MusicService musicService;
    boolean isBound = false;

    private ActivityResultLauncher<Intent> voiceCommandLauncher;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MyBinder binder = (MusicService.MyBinder) service;
            musicService = binder.getService();
            isBound = true;
            syncFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;

            if (intent.getAction().equals(MusicService.BROADCAST_UPDATE_UI)) {
                String songName = intent.getStringExtra("song");
                String songPath = intent.getStringExtra("songPath");
                boolean isPlaying = intent.getBooleanExtra("isPlaying", false);
                int duration = intent.getIntExtra("duration", 0);
                int currentPos = intent.getIntExtra("currentPosition", 0);

                if (songName != null) {
                    setSongTitle(songName);
                }
                btnPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

                if (duration > 0) {
                    seekBar.setMax(duration);
                    txtEnd.setText(formatTime(duration));
                } else {
                    seekBar.setMax(0);
                    txtEnd.setText(formatTime(0));
                }

                if (!isUserSeeking) {
                    seekBar.setProgress(Math.max(0, currentPos));
                    txtStart.setText(formatTime(currentPos));
                }

                if (intent.hasExtra("isShuffle") && !shuffleToggledBeforeBind) {
                    isShuffle = intent.getBooleanExtra("isShuffle", false);
                    updateToggleButton(btnShuffle, isShuffle);
                }
                if (intent.hasExtra("repeatMode") && !repeatToggledBeforeBind) {
                    repeatMode = intent.getIntExtra("repeatMode", MusicService.REPEAT_OFF);
                    updateRepeatButton();
                } else if (intent.hasExtra("isRepeat") && !repeatToggledBeforeBind) {
                    boolean repOne = intent.getBooleanExtra("isRepeat", false);
                    repeatMode = repOne ? MusicService.REPEAT_ONE : MusicService.REPEAT_OFF;
                    updateRepeatButton();
                }

                if (songPath != null && !songPath.equals(imgView.getTag())) {
                    imgView.setTag(songPath);
                    loadAlbumArt(songPath, true);
                }

                if (songPath != null) {
                    currentSongPath = songPath;
                    updateFavIcon(false);
                    loadTrackMetadata(songPath);
                    loadLyricsForSong(songPath);
                    syncLyricsToPosition(currentPos, duration);
                    updateAbLoopPillStyle();
                }

                if (intent.hasExtra("trackIndex") && intent.hasExtra("totalTracks")) {
                    updateTrackCounter(
                            intent.getIntExtra("playOrder", 1),
                            intent.getIntExtra("trackIndex", 0),
                            intent.getIntExtra("totalTracks", 0)
                    );
                }
            } else if (intent.getAction().equals("com.attomaticsystems.parrotplayer.CLOSE_APP")) {
                finishAffinity();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_in_up, R.anim.stay_still);
        setContentView(R.layout.activity_player);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateBackFromPlayer();
            }
        });

        favManager = new FavouritesManager(this);

        playerRootLayout = findViewById(R.id.playerRootLayout);
        txtSong = findViewById(R.id.txtSong);
        txtArtistAlbum = findViewById(R.id.txtArtistAlbum);
        txtAudioQualityPill = findViewById(R.id.txtAudioQualityPill);
        txtTrackCounter = findViewById(R.id.txtTrackCounter);
        txtAbLoopPill = findViewById(R.id.txtAbLoopPill);
        txtStart = findViewById(R.id.txtSongStart);
        txtEnd = findViewById(R.id.txtSongEnd);
        imgView = findViewById(R.id.imgView);
        imgViewCard = findViewById(R.id.imgViewCard);

        seekBar = findViewById(R.id.seekBar);

        btnPlay = findViewById(R.id.btnPlay);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrevious);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnFav = findViewById(R.id.btnfav);
        btnEq = findViewById(R.id.btnEq);
        btnBack = findViewById(R.id.btnback);
        btnQueue = findViewById(R.id.btnQueue);
        btnMic = findViewById(R.id.btnMic);
        btnLyrics = findViewById(R.id.btnLyrics);
        btnTimerSpeed = findViewById(R.id.btnTimerSpeed);
        imgTimerSpeedIcon = findViewById(R.id.imgTimerSpeedIcon);
        txtTimerSpeedStatus = findViewById(R.id.txtTimerSpeedStatus);

        liveLyricBanner = findViewById(R.id.liveLyricBanner);
        imgLiveLyricIcon = findViewById(R.id.imgLiveLyricIcon);
        txtLiveLyricLine = findViewById(R.id.txtLiveLyricLine);
        txtLiveLyricExpand = findViewById(R.id.txtLiveLyricExpand);
        lyricsOverlayContainer = findViewById(R.id.lyricsOverlayContainer);
        txtLyricsBadge = findViewById(R.id.txtLyricsBadge);
        btnCloseLyricsOverlay = findViewById(R.id.btnCloseLyricsOverlay);
        lyricsScrollView = findViewById(R.id.lyricsScrollView);
        lyricsLinesContainer = findViewById(R.id.lyricsLinesContainer);

        txtSong.setSelected(true);
        applyAccentTheme();
        refreshLyricsBannerVisibility();
        updateLyricsButtonState();

        voiceCommandLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            handleVoiceCommand(matches.get(0));
                        }
                    }
                });

        mySongs = getIntent().getStringArrayListExtra("songs");
        position = getIntent().getIntExtra("pos", 0);

        if (mySongs != null && !mySongs.isEmpty()) {
            if (position >= 0 && position < mySongs.size()) {
                String initialSongPath = mySongs.get(position);
                imgView.setTag(initialSongPath);
                loadAlbumArt(initialSongPath, false);

                setSongTitle(cleanFileName(new File(initialSongPath).getName()));
                currentSongPath = initialSongPath;
                updateFavIcon(false);
                updateTrackCounter(1, position, mySongs.size());
                loadTrackMetadata(initialSongPath);
                loadLyricsForSong(initialSongPath);
            }

            Intent serviceIntent = new Intent(this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_START);
            serviceIntent.putStringArrayListExtra("songs", mySongs);
            serviceIntent.putExtra("pos", position);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }

        Intent bindIntent = new Intent(this, MusicService.class);
        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        attachPressAnimation(btnPlay, btnNext, btnPrev, btnShuffle, btnRepeat, btnFav, btnQueue, btnEq, btnBack,
                btnMic, btnLyrics);
        setupAlbumArtSwipe();

        if (btnLyrics != null) {
            btnLyrics.setOnClickListener(v -> {
                popView(btnLyrics);
                if (!currentLyricsData.hasLyrics()) {
                    Toast.makeText(this, "No lyrics found for this song", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isLyricsOverlayVisible) {
                    isSmallLyricsVisible = false;
                    setLyricsOverlayVisible(false);
                } else if (shouldShowSmallLyricsByDefault() || isSmallLyricsVisible) {
                    setLyricsOverlayVisible(true);
                } else {
                    isSmallLyricsVisible = true;
                    refreshLyricsBannerVisibility();
                    updateLyricsButtonState();
                }
            });
        }
        if (liveLyricBanner != null) {
            liveLyricBanner.setOnClickListener(v -> setLyricsOverlayVisible(true));
        }
        if (btnCloseLyricsOverlay != null) {
            btnCloseLyricsOverlay.setOnClickListener(v -> {
                isSmallLyricsVisible = false;
                setLyricsOverlayVisible(false);
            });
        }
        if (txtArtistAlbum != null) {
            txtArtistAlbum.setOnClickListener(v -> onArtistAlbumSubtitleClicked());
        }
        if (txtAudioQualityPill != null) {
            txtAudioQualityPill.setOnClickListener(v -> {
                if (currentTrackInfo != null) {
                    TrackMetadataHelper.showTrackDetailsDialog(this, currentTrackInfo);
                }
            });
        }
        if (txtAbLoopPill != null) {
            txtAbLoopPill.setOnClickListener(v -> cycleAbLoopState());
        }
        if (btnTimerSpeed != null) {
            btnTimerSpeed.setOnClickListener(v -> {
                popView(btnTimerSpeed);
                showQuickTimerSpeedBottomSheet();
            });
        }

        btnPlay.setOnClickListener(v -> triggerPlayPause());
        btnNext.setOnClickListener(v -> triggerNext());
        btnPrev.setOnClickListener(v -> triggerPrev());

        btnShuffle.setOnClickListener(v -> {
            isShuffle = !isShuffle;
            if (isBound && musicService != null) {
                musicService.setShuffle(isShuffle);
                shuffleToggledBeforeBind = false;
            } else {
                shuffleToggledBeforeBind = true;
            }
            updateToggleButton(btnShuffle, isShuffle);
            popView(btnShuffle);
        });

        btnRepeat.setOnClickListener(v -> {
            repeatMode = (repeatMode + 1) % 3;
            if (isBound && musicService != null) {
                musicService.setRepeatMode(repeatMode);
                repeatToggledBeforeBind = false;
            } else {
                repeatToggledBeforeBind = true;
            }
            updateRepeatButton();
            popView(btnRepeat);
            String label = (repeatMode == MusicService.REPEAT_ALL)
                    ? "Repeat All"
                    : (repeatMode == MusicService.REPEAT_ONE)
                    ? "Repeat One"
                    : "Repeat Off";
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
        });

        btnFav.setOnClickListener(v -> {
            if (currentSongPath != null) {
                favManager.toggleFavourite(currentSongPath);
                updateFavIcon(true);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    txtStart.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                int target = seekBar.getProgress();
                if (isBound && musicService != null) {
                    musicService.seekTo(target);
                }
                txtStart.setText(formatTime(target));
                syncLyricsToPosition(target, seekBar.getMax());
            }
        });

        btnBack.setOnClickListener(v -> navigateBackFromPlayer());

        btnQueue.setOnClickListener(v -> {
            QueueBottomSheetFragment queueSheet = new QueueBottomSheetFragment();
            queueSheet.show(getSupportFragmentManager(), "queue");
        });

        btnMic.setOnClickListener(v -> launchVoiceCommand());

        btnEq.setOnClickListener(v -> openEqualizer());
    }

    private void syncFromService() {
        if (!isBound || musicService == null)
            return;

        if (shuffleToggledBeforeBind) {
            musicService.setShuffle(isShuffle);
            shuffleToggledBeforeBind = false;
        } else {
            isShuffle = musicService.isShuffle();
        }

        if (repeatToggledBeforeBind) {
            musicService.setRepeatMode(repeatMode);
            repeatToggledBeforeBind = false;
        } else {
            repeatMode = musicService.getRepeatMode();
        }

        updateToggleButton(btnShuffle, isShuffle);
        updateRepeatButton();

        btnPlay.setImageResource(musicService.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        String songPath = musicService.getCurrentSongPath();
        String songTitle = musicService.getCurrentSongTitle();
        if (songTitle != null) {
            setSongTitle(songTitle);
        }

        int duration = musicService.getDuration();
        int currentPos = musicService.getCurrentPosition();
        if (duration > 0) {
            seekBar.setMax(duration);
            txtEnd.setText(formatTime(duration));
        }
        if (!isUserSeeking) {
            seekBar.setProgress(Math.max(0, currentPos));
            txtStart.setText(formatTime(currentPos));
        }

        if (songPath != null) {
            currentSongPath = songPath;
            updateFavIcon(false);
            updateTrackCounter(
                    musicService.getPlayOrderNumber(),
                    musicService.getCurrentTrackIndex(),
                    musicService.getTotalTrackCount()
            );
            loadTrackMetadata(songPath);
            loadLyricsForSong(songPath);
            syncLyricsToPosition(currentPos, duration);
            updateAbLoopPillStyle();
            updateTimerSpeedBadge();
            if (!songPath.equals(imgView.getTag())) {
                imgView.setTag(songPath);
                loadAlbumArt(songPath, false);
            }
        }
    }

    private void triggerPlayPause() {
        popView(btnPlay);
        if (isBound && musicService != null) {
            musicService.togglePlay();
        } else {
            sendActionToService(MusicService.ACTION_PLAY_PAUSE);
        }
    }

    private void triggerNext() {
        if (isBound && musicService != null) {
            musicService.nextSong();
        } else {
            sendActionToService(MusicService.ACTION_NEXT);
        }
    }

    private void triggerPrev() {
        if (isBound && musicService != null) {
            musicService.prevSong();
        } else {
            sendActionToService(MusicService.ACTION_PREV);
        }
    }

    private void openEqualizer() {
        new ParrotEqualizerDialogFragment().show(getSupportFragmentManager(), "eq");
    }

    private void launchVoiceCommand() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say play, pause, next, previous, or a song name");
        try {
            voiceCommandLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Voice recognition not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleVoiceCommand(String rawCommand) {
        if (rawCommand == null)
            return;
        String cmd = rawCommand.trim().toLowerCase(Locale.ROOT);
        if (cmd.contains("pause") || cmd.contains("stop")) {
            if (isBound && musicService != null && musicService.isPlaying()) {
                musicService.togglePlay();
            }
        } else if (cmd.equals("play") || cmd.contains("resume")) {
            if (isBound && musicService != null && !musicService.isPlaying()) {
                musicService.togglePlay();
            }
        } else if (cmd.contains("next") || cmd.contains("skip")) {
            triggerNext();
        } else if (cmd.contains("previous") || cmd.contains("prev") || cmd.contains("back")) {
            triggerPrev();
        } else if (cmd.contains("shuffle")) {
            btnShuffle.performClick();
        } else if (cmd.contains("repeat")) {
            btnRepeat.performClick();
        } else if (isBound && musicService != null && musicService.playMatchingSong(cmd)) {
            Toast.makeText(this, "Playing: " + rawCommand, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No matching command or song: " + rawCommand, Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupAlbumArtSwipe() {
        if (imgViewCard == null)
            return;
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 90;
            private static final int SWIPE_VELOCITY_THRESHOLD = 150;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null)
                    return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)
                        && Math.abs(diffX) > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX < 0) {
                        animateSwipeAndSkip(true);
                    } else {
                        animateSwipeAndSkip(false);
                    }
                    return true;
                } else if (diffY > Math.abs(diffX)
                        && diffY > SWIPE_THRESHOLD
                        && velocityY > SWIPE_VELOCITY_THRESHOLD) {
                    animateMinimizeToMiniPlayer();
                    return true;
                }
                return false;
            }
        });

        imgViewCard.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    private void animateSwipeAndSkip(boolean next) {
        if (imgViewCard == null) {
            if (next)
                triggerNext();
            else
                triggerPrev();
            return;
        }
        float outX = next ? -90f : 90f;
        float inX = next ? 90f : -90f;
        imgViewCard.animate().cancel();
        imgViewCard.animate()
                .translationX(outX)
                .alpha(0.35f)
                .setDuration(100)
                .withEndAction(() -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    if (next)
                        triggerNext();
                    else
                        triggerPrev();
                    imgViewCard.setTranslationX(inX);
                    imgViewCard.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(180)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachPressAnimation(View... views) {
        View.OnTouchListener listener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().cancel();
                v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(60).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                        .setInterpolator(new OvershootInterpolator(1.6f)).start();
            }
            return false;
        };
        for (View v : views) {
            if (v != null) {
                v.setOnTouchListener(listener);
            }
        }
    }

    private void popView(View view) {
        if (view == null)
            return;
        view.animate().cancel();
        view.setScaleX(0.88f);
        view.setScaleY(0.88f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .setInterpolator(new OvershootInterpolator(2.0f))
                .start();
    }

    private void updateToggleButton(ImageButton button, boolean enabled) {
        if (button == null)
            return;
        int accent = AccentColorHelper.getAccentColor(this);
        button.setAlpha(enabled ? 1.0f : 0.5f);
        ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(enabled ? accent : COLOR_WHITE));
    }

    private void updateRepeatButton() {
        if (btnRepeat == null) return;
        int accent = AccentColorHelper.getAccentColor(this);
        if (repeatMode == MusicService.REPEAT_ONE) {
            btnRepeat.setImageResource(R.drawable.ic_repeat_one);
            btnRepeat.setAlpha(1.0f);
            ImageViewCompat.setImageTintList(btnRepeat, ColorStateList.valueOf(accent));
        } else if (repeatMode == MusicService.REPEAT_ALL) {
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setAlpha(1.0f);
            ImageViewCompat.setImageTintList(btnRepeat, ColorStateList.valueOf(accent));
        } else {
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setAlpha(0.5f);
            ImageViewCompat.setImageTintList(btnRepeat, ColorStateList.valueOf(COLOR_WHITE));
        }
    }

    private void loadTrackMetadata(final String path) {
        if (path == null || path.isEmpty() || path.equals(metadataLoadedForPath)) {
            return;
        }
        metadataLoadedForPath = path;

        TrackMetadataHelper.loadTrackInfoAsync(path, info -> {
            if (isFinishing() || isDestroyed() || !path.equals(currentSongPath)) {
                return;
            }
            currentTrackInfo = info;
            if (txtArtistAlbum != null && info != null) {
                txtArtistAlbum.setText(info.getArtistAlbumSubtitle());
            }
            if (txtAudioQualityPill != null && info != null) {
                txtAudioQualityPill.setText(info.getQualityBadgeText());
            }
        });
    }

    private void onArtistAlbumSubtitleClicked() {
        if (currentTrackInfo == null) return;
        boolean hasArtist = currentTrackInfo.hasKnownArtist();
        boolean hasAlbum = currentTrackInfo.hasKnownAlbum();

        if (hasArtist && hasAlbum) {
            String[] choices = new String[]{
                    "Go to Artist: " + currentTrackInfo.artist,
                    "Go to Album: " + currentTrackInfo.album,
                    "Track Technical Info"
            };
            new android.app.AlertDialog.Builder(this)
                    .setTitle(currentTrackInfo.title)
                    .setItems(choices, (dialog, which) -> {
                        if (which == 0) {
                            openGroupSongs("artist", currentTrackInfo.artist);
                        } else if (which == 1) {
                            openGroupSongs("album", currentTrackInfo.album);
                        } else {
                            TrackMetadataHelper.showTrackDetailsDialog(this, currentTrackInfo);
                        }
                    })
                    .show();
        } else if (hasArtist) {
            openGroupSongs("artist", currentTrackInfo.artist);
        } else if (hasAlbum) {
            openGroupSongs("album", currentTrackInfo.album);
        } else {
            TrackMetadataHelper.showTrackDetailsDialog(this, currentTrackInfo);
        }
    }

    private void openGroupSongs(String type, String name) {
        Intent intent = new Intent(this, GroupSongsActivity.class);
        intent.putExtra("type", type);
        intent.putExtra("name", name);
        startActivity(intent);
    }

    private void cycleAbLoopState() {
        if (!isBound || musicService == null) return;
        int startMs = musicService.getAbLoopStartMs();
        int endMs = musicService.getAbLoopEndMs();
        int currentPos = musicService.getCurrentPosition();

        if (startMs < 0) {
            musicService.setAbLoopStart(currentPos);
            Toast.makeText(this, "Point A set at " + formatTime(currentPos) + " — tap again for B", Toast.LENGTH_SHORT).show();
        } else if (endMs < 0) {
            if (currentPos <= startMs + 500) {
                Toast.makeText(this, "Point B must be after " + formatTime(startMs), Toast.LENGTH_SHORT).show();
                return;
            }
            musicService.setAbLoopEnd(currentPos);
            Toast.makeText(this, "Looping " + formatTime(startMs) + " – " + formatTime(currentPos), Toast.LENGTH_SHORT).show();
        } else {
            musicService.clearAbLoop();
            Toast.makeText(this, "A–B Loop cleared", Toast.LENGTH_SHORT).show();
        }
        updateAbLoopPillStyle();
    }

    private void updateTrackCounter(int playOrder, int trackIndex, int totalTracks) {
        if (txtTrackCounter == null) return;
        if (totalTracks > 0 && trackIndex >= 0 && trackIndex < totalTracks) {
            txtTrackCounter.setText("#" + Math.max(1, playOrder) + " \u2022 " + (trackIndex + 1) + "/" + totalTracks);
            txtTrackCounter.setVisibility(View.VISIBLE);
        } else {
            txtTrackCounter.setVisibility(View.GONE);
        }
    }

    private void updateAbLoopPillStyle() {
        if (txtAbLoopPill == null) return;
        int accent = AccentColorHelper.getAccentColor(this);
        float density = getResources().getDisplayMetrics().density;
        int startMs = (isBound && musicService != null) ? musicService.getAbLoopStartMs() : -1;
        int endMs = (isBound && musicService != null) ? musicService.getAbLoopEndMs() : -1;

        if (startMs >= 0 && endMs > startMs) {
            txtAbLoopPill.setText("A–B: " + formatTime(startMs) + "–" + formatTime(endMs) + " ✕");
            txtAbLoopPill.setTextColor(accent);
            txtAbLoopPill.setBackground(AccentColorHelper.createRoundedRect(
                    AccentColorHelper.withAlpha(accent, 0x26),
                    AccentColorHelper.withAlpha(accent, 0x66),
                    (int) (1 * density),
                    13f * density
            ));
        } else if (startMs >= 0) {
            txtAbLoopPill.setText("A: " + formatTime(startMs) + " • Set B");
            txtAbLoopPill.setTextColor(accent);
            txtAbLoopPill.setBackground(AccentColorHelper.createRoundedRect(
                    AccentColorHelper.withAlpha(accent, 0x1A),
                    AccentColorHelper.withAlpha(accent, 0x50),
                    (int) (1 * density),
                    13f * density
            ));
        } else {
            txtAbLoopPill.setText("A–B Loop");
            txtAbLoopPill.setTextColor(Color.parseColor("#9AA3B2"));
            txtAbLoopPill.setBackgroundResource(R.drawable.card_surface_bg);
        }
    }

    private float getActivePlaybackSpeed() {
        if (isBound && musicService != null) {
            return musicService.getPlaybackSpeed();
        }
        return getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(SettingsFragment.KEY_PLAYBACK_SPEED, 1.0f);
    }

    private void updateTimerSpeedBadge() {
        if (btnTimerSpeed == null) return;
        long remainingMs = (isBound && musicService != null) ? musicService.getSleepTimerRemainingMs() : 0L;
        float speed = getActivePlaybackSpeed();
        boolean hasTimer = remainingMs > 0L;
        boolean hasCustomSpeed = Math.abs(speed - 1.0f) >= 0.04f;
        boolean active = hasTimer || hasCustomSpeed;

        StringBuilder label = new StringBuilder();
        if (hasCustomSpeed) {
            label.append(formatSpeedChip(speed));
        }
        if (hasTimer) {
            if (label.length() > 0) label.append(" • ");
            label.append(formatTime((int) Math.min(Integer.MAX_VALUE, remainingMs)));
        }
        String text = label.toString();

        if (!text.equals(lastTimerSpeedText)) {
            lastTimerSpeedText = text;
            if (txtTimerSpeedStatus != null) {
                txtTimerSpeedStatus.setText(text);
                txtTimerSpeedStatus.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            }
        }
        if (active != lastTimerSpeedActive) {
            lastTimerSpeedActive = active;
            int accent = AccentColorHelper.getAccentColor(this);
            float density = getResources().getDisplayMetrics().density;
            if (active) {
                btnTimerSpeed.setBackground(AccentColorHelper.createRoundedRect(
                        AccentColorHelper.withAlpha(accent, 0x24),
                        AccentColorHelper.withAlpha(accent, 0x60),
                        (int) (1 * density),
                        16f * density
                ));
                if (imgTimerSpeedIcon != null) {
                    ImageViewCompat.setImageTintList(imgTimerSpeedIcon, ColorStateList.valueOf(accent));
                }
                if (txtTimerSpeedStatus != null) {
                    txtTimerSpeedStatus.setTextColor(accent);
                }
            } else {
                btnTimerSpeed.setBackgroundResource(R.drawable.card_surface_bg);
                if (imgTimerSpeedIcon != null) {
                    ImageViewCompat.setImageTintList(imgTimerSpeedIcon, ColorStateList.valueOf(COLOR_WHITE));
                }
            }
        }
    }

    private String formatSpeedChip(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.04f) {
            return Math.round(speed) + "×";
        }
        String s = String.format(Locale.US, "%.2f", speed).replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s + "×";
    }

    private void showQuickTimerSpeedBottomSheet() {
        final float density = getResources().getDisplayMetrics().density;
        final int accent = AccentColorHelper.getAccentColor(this);

        final BottomSheetDialog sheet = new BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        float r = 22f * density;
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.parseColor("#0F1219"));
        sheetBg.setCornerRadii(new float[]{r, r, r, r, 0f, 0f, 0f, 0f});
        root.setBackground(sheetBg);
        int sidePad = (int) (20 * density);
        root.setPadding(sidePad, (int) (12 * density), sidePad, (int) (24 * density));

        // Top drag handle pill
        View dragHandle = new View(this);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(Color.parseColor("#2A303D"));
        handleBg.setCornerRadius(2f * density);
        dragHandle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                (int) (38 * density), (int) (4 * density));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = (int) (14 * density);
        dragHandle.setLayoutParams(handleLp);
        root.addView(dragHandle);

        // Header
        TextView titleView = new TextView(this);
        titleView.setText("Timer & Speed");
        titleView.setTextColor(Color.parseColor("#F5F7FA"));
        titleView.setTextSize(18f);
        titleView.setTypeface(null, Typeface.BOLD);
        root.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setTextColor(Color.parseColor("#8A93A2"));
        subtitleView.setTextSize(12.5f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) (2 * density);
        subLp.bottomMargin = (int) (18 * density);
        subtitleView.setLayoutParams(subLp);
        root.addView(subtitleView);

        // Sleep Timer section
        TextView timerHeader = new TextView(this);
        timerHeader.setTextColor(accent);
        timerHeader.setTextSize(11f);
        timerHeader.setLetterSpacing(0.08f);
        timerHeader.setTypeface(null, Typeface.BOLD);
        root.addView(timerHeader);

        final int[] timerMinutes = new int[]{0, 15, 30, 45, 60, 90};
        final String[] timerLabels = new String[]{"Off", "15m", "30m", "45m", "60m", "90m"};
        final TextView[] timerChips = new TextView[timerMinutes.length];

        Runnable refreshSheetStatus = () -> {
            long remMs = (isBound && musicService != null) ? musicService.getSleepTimerRemainingMs() : 0L;
            float spd = getActivePlaybackSpeed();
            if (remMs > 0L) {
                timerHeader.setText("SLEEP TIMER  •  " + formatTime((int) Math.min(Integer.MAX_VALUE, remMs)) + " LEFT");
                subtitleView.setText("Stops playback in " + formatTime((int) Math.min(Integer.MAX_VALUE, remMs))
                        + " • " + formatSpeedChip(spd) + " speed");
            } else {
                timerHeader.setText("SLEEP TIMER");
                subtitleView.setText(Math.abs(spd - 1.0f) >= 0.04f
                        ? "Playing at " + formatSpeedChip(spd) + " speed"
                        : "Auto-pause gently or adjust playback speed");
            }
            if (timerChips[0] != null && remMs <= 0L) {
                for (int k = 0; k < timerChips.length; k++) {
                    if (timerChips[k] != null) styleOptionChip(timerChips[k], k == 0, accent, density);
                }
            }
        };

        long initialRemMs = (isBound && musicService != null) ? musicService.getSleepTimerRemainingMs() : 0L;
        addChipGrid(root, density, 3, timerLabels.length, index -> {
            final int mins = timerMinutes[index];
            boolean isSelected = (mins == 0 && initialRemMs <= 0L);
            TextView chip = buildOptionChip(timerLabels[index], isSelected, accent, density, v -> {
                if (isBound && musicService != null) {
                    musicService.setSleepTimerMinutes(mins);
                }
                for (int k = 0; k < timerChips.length; k++) {
                    if (timerChips[k] != null) {
                        styleOptionChip(timerChips[k], k == index, accent, density);
                    }
                }
                lastTimerSpeedActive = !lastTimerSpeedActive;
                updateTimerSpeedBadge();
                refreshSheetStatus.run();
                Toast.makeText(this,
                        mins == 0 ? "Sleep timer cancelled" : "Sleep timer set for " + mins + " min",
                        Toast.LENGTH_SHORT).show();
            });
            timerChips[index] = chip;
            return chip;
        });

        // Playback Speed section
        TextView speedHeader = new TextView(this);
        float currentSpeed = getActivePlaybackSpeed();
        speedHeader.setText("PLAYBACK SPEED  •  " + formatSpeedChip(currentSpeed));
        speedHeader.setTextColor(accent);
        speedHeader.setTextSize(11f);
        speedHeader.setLetterSpacing(0.08f);
        speedHeader.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams shParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shParams.topMargin = (int) (20 * density);
        speedHeader.setLayoutParams(shParams);
        root.addView(speedHeader);

        final float[] speeds = new float[]{0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
        final TextView[] speedChips = new TextView[speeds.length];
        addChipGrid(root, density, 3, speeds.length, index -> {
            final float targetSpeed = speeds[index];
            boolean isSelected = Math.abs(currentSpeed - targetSpeed) < 0.04f;
            TextView chip = buildOptionChip(formatSpeedChip(targetSpeed), isSelected, accent, density, v -> {
                getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putFloat(SettingsFragment.KEY_PLAYBACK_SPEED, targetSpeed)
                        .apply();
                if (isBound && musicService != null) {
                    musicService.setPlaybackSpeed(targetSpeed);
                }
                speedHeader.setText("PLAYBACK SPEED  •  " + formatSpeedChip(targetSpeed));
                for (int k = 0; k < speedChips.length; k++) {
                    if (speedChips[k] != null) {
                        styleOptionChip(speedChips[k], Math.abs(speeds[k] - targetSpeed) < 0.04f, accent, density);
                    }
                }
                lastTimerSpeedActive = !lastTimerSpeedActive;
                updateTimerSpeedBadge();
                refreshSheetStatus.run();
            });
            speedChips[index] = chip;
            return chip;
        });

        refreshSheetStatus.run();

        final Runnable sheetTicker = new Runnable() {
            @Override
            public void run() {
                if (!sheet.isShowing() || isFinishing() || isDestroyed()) return;
                refreshSheetStatus.run();
                handler.postDelayed(this, 500L);
            }
        };

        sheet.setContentView(root);
        sheet.setOnShowListener(dialogInterface -> {
            View bottomSheetInternal = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                bottomSheetInternal.setBackgroundColor(Color.TRANSPARENT);
            }
            handler.postDelayed(sheetTicker, 500L);
        });
        sheet.setOnDismissListener(dialogInterface -> handler.removeCallbacks(sheetTicker));
        sheet.show();
    }

    private interface ChipFactory {
        TextView create(int index);
    }

    private void addChipGrid(LinearLayout parent, float density, int columns, int count, ChipFactory factory) {
        int gap = (int) (8 * density);
        LinearLayout row = null;
        for (int i = 0; i < count; i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = (int) (10 * density);
                row.setLayoutParams(rp);
                parent.addView(row);
            }
            TextView chip = factory.create(i);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
            if (i % columns < columns - 1) cp.rightMargin = gap;
            chip.setLayoutParams(cp);
            row.addView(chip);
        }
    }

    private TextView buildOptionChip(String label, boolean selected, int accent, float density, View.OnClickListener clickListener) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(13f);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setClickable(true);
        chip.setFocusable(true);
        styleOptionChip(chip, selected, accent, density);
        chip.setOnClickListener(clickListener);
        return chip;
    }

    private void styleOptionChip(TextView chip, boolean selected, int accent, float density) {
        if (selected) {
            chip.setTextColor(accent);
            chip.setBackground(AccentColorHelper.createRoundedRect(
                    AccentColorHelper.withAlpha(accent, 0x26),
                    AccentColorHelper.withAlpha(accent, 0x66),
                    (int) (1 * density),
                    12f * density
            ));
        } else {
            chip.setTextColor(Color.parseColor("#D5DCE6"));
            chip.setBackgroundResource(R.drawable.card_surface_bg);
        }
    }

    private void applyAccentTheme() {
        int accent = AccentColorHelper.getAccentColor(this);
        if (seekBar != null) {
            seekBar.setProgressTintList(ColorStateList.valueOf(accent));
        }
        if (imgLiveLyricIcon != null) {
            imgLiveLyricIcon.setImageTintList(ColorStateList.valueOf(accent));
        }
        if (txtLiveLyricExpand != null) {
            txtLiveLyricExpand.setTextColor(accent);
        }
        if (txtLyricsBadge != null) {
            txtLyricsBadge.setTextColor(accent);
        }
        updateToggleButton(btnShuffle, isShuffle);
        updateRepeatButton();
        updateFavIcon(false);
        updateLyricsButtonState();
        updateAbLoopPillStyle();
        lastTimerSpeedActive = !lastTimerSpeedActive; // Force badge re-tint on accent change
        updateTimerSpeedBadge();
    }

    private void setSongTitle(String title) {
        if (title == null)
            return;
        if (!title.equals(txtSong.getText().toString())) {
            txtSong.setText(title);
            txtSong.setSelected(true);
        }
    }

    private String cleanFileName(String name) {
        return name.replaceAll("(?i)\\.(mp3|wav|m4a|flac|ogg|aac)$", "");
    }

    private void loadAlbumArt(String path, boolean animateFade) {
        if (path == null || path.isEmpty()) {
            imgView.setImageResource(R.drawable.ic_music);
            return;
        }

        AlbumArtLoader loader = AlbumArtLoader.getInstance();
        Bitmap cachedFull = loader.getCachedFull(path);
        if (cachedFull != null) {
            setAlbumArtBitmap(cachedFull, animateFade);
            return;
        }

        if (loader.hasNoArt(path)) {
            imgView.setImageResource(R.drawable.ic_music);
            return;
        }

        Bitmap cachedThumb = loader.getCachedThumb(path);
        if (cachedThumb != null) {
            setAlbumArtBitmap(cachedThumb, animateFade);
        }

        loader.loadFullArt(path, bitmap -> {
            if (!isFinishing() && !isDestroyed() && path.equals(imgView.getTag())) {
                if (bitmap != null) {
                    setAlbumArtBitmap(bitmap, animateFade && cachedThumb == null);
                } else {
                    imgView.animate().cancel();
                    imgView.setAlpha(1f);
                    imgView.setImageResource(R.drawable.ic_music);
                }
            }
        });
    }

    private void setAlbumArtBitmap(Bitmap bitmap, boolean animateFade) {
        if (animateFade) {
            imgView.animate().cancel();
            imgView.setAlpha(0.55f);
            imgView.setImageBitmap(bitmap);
            imgView.animate().alpha(1f).setDuration(180).start();
        } else {
            imgView.setAlpha(1f);
            imgView.setImageBitmap(bitmap);
        }
    }

    private void sendActionToService(String action) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void updateFavIcon(boolean animate) {
        if (btnFav == null)
            return;
        int accent = AccentColorHelper.getAccentColor(this);
        boolean fav = currentSongPath != null && favManager != null && favManager.isFavourite(currentSongPath);
        btnFav.setImageResource(fav ? R.drawable.ic_fav_filled : R.drawable.ic_fav);
        btnFav.setAlpha(fav ? 1.0f : 0.5f);
        ImageViewCompat.setImageTintList(btnFav, ColorStateList.valueOf(fav ? accent : COLOR_WHITE));
        if (animate) {
            popView(btnFav);
        }
    }

    private void startSeekBarUpdate() {
        stopSeekBarUpdate();

        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (isBound && musicService != null && !isUserSeeking) {
                    int duration = musicService.getDuration();
                    if (duration > 0 && seekBar.getMax() != duration) {
                        seekBar.setMax(duration);
                        txtEnd.setText(formatTime(duration));
                    }
                    int current = musicService.getCurrentPosition();
                    seekBar.setProgress(current);
                    txtStart.setText(formatTime(current));
                    syncLyricsToPosition(current, duration);
                    updateTimerSpeedBadge();
                }
                handler.postDelayed(this, SEEKBAR_UPDATE_INTERVAL_MS);
            }
        };
        handler.post(updateSeekBar);
    }

    private void loadLyricsForSong(final String path) {
        if (path == null || path.isEmpty() || path.equals(lyricsLoadedForPath)) {
            return;
        }
        lyricsLoadedForPath = path;
        activeLyricLineIndex = -1;

        LyricsExtractor.loadLyricsAsync(path, data -> {
            if (isFinishing() || isDestroyed() || !path.equals(currentSongPath)) {
                return;
            }
            currentLyricsData = (data != null) ? data : LyricsExtractor.LyricsData.EMPTY;
            activeLyricLineIndex = -1;
            isSmallLyricsVisible = false;

            if (currentLyricsData.hasLyrics()) {
                if (txtLyricsBadge != null) {
                    txtLyricsBadge.setText(currentLyricsData.isSynced
                            ? "SYNCED LYRICS • TAP LINE TO SEEK"
                            : "LYRICS");
                }
                buildLyricsViews();
                refreshLyricsBannerVisibility();
                int pos = (isBound && musicService != null) ? musicService.getCurrentPosition() : seekBar.getProgress();
                int dur = (isBound && musicService != null) ? musicService.getDuration() : seekBar.getMax();
                syncLyricsToPosition(pos, dur);
            } else {
                lyricLineViews.clear();
                if (lyricsLinesContainer != null) {
                    lyricsLinesContainer.removeAllViews();
                }
                refreshLyricsBannerVisibility();
                if (isLyricsOverlayVisible) {
                    setLyricsOverlayVisible(false);
                }
            }
            updateLyricsButtonState();
        });
    }

    private void buildLyricsViews() {
        if (lyricsLinesContainer == null) return;
        lyricsLinesContainer.removeAllViews();
        lyricLineViews.clear();

        float density = getResources().getDisplayMetrics().density;
        int vPad = (int) (10 * density);
        int hPad = (int) (8 * density);

        for (int i = 0; i < currentLyricsData.lines.size(); i++) {
            final LyricsExtractor.LyricLine line = currentLyricsData.lines.get(i);
            final int lineIdx = i;

            TextView tv = new TextView(this);
            tv.setText(line.text);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(15.5f);
            tv.setTextColor(Color.parseColor("#8A93A2"));
            tv.setPadding(hPad, vPad, hPad, vPad);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            if (currentLyricsData.isSynced && line.timeMs >= 0) {
                tv.setBackgroundResource(android.R.drawable.list_selector_background);
                tv.setClickable(true);
                tv.setFocusable(true);
                tv.setOnClickListener(v -> {
                    int targetMs = (int) line.timeMs;
                    if (isBound && musicService != null) {
                        musicService.seekTo(targetMs);
                    }
                    seekBar.setProgress(targetMs);
                    txtStart.setText(formatTime(targetMs));
                    highlightActiveLyricLine(lineIdx, true);
                });
            }

            lyricLineViews.add(tv);
            lyricsLinesContainer.addView(tv);
        }
    }

    private boolean shouldShowSmallLyricsByDefault() {
        return getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SettingsFragment.KEY_SHOW_LYRICS_BANNER_DEFAULT, false);
    }

    private void refreshLyricsBannerVisibility() {
        if (liveLyricBanner == null) return;
        boolean hasLyrics = currentLyricsData != null && currentLyricsData.hasLyrics();
        boolean showBanner = hasLyrics && !isLyricsOverlayVisible
                && (shouldShowSmallLyricsByDefault() || isSmallLyricsVisible);
        if (showBanner) {
            if (liveLyricBanner.getVisibility() != View.VISIBLE) {
                liveLyricBanner.setAlpha(0f);
                liveLyricBanner.setVisibility(View.VISIBLE);
                liveLyricBanner.animate().alpha(1f).setDuration(160).start();
            }
        } else {
            liveLyricBanner.animate().cancel();
            liveLyricBanner.setVisibility(View.GONE);
        }
    }

    private void setLyricsOverlayVisible(boolean visible) {
        isLyricsOverlayVisible = visible;
        if (lyricsOverlayContainer != null) {
            if (visible) {
                lyricsOverlayContainer.setAlpha(0f);
                lyricsOverlayContainer.setVisibility(View.VISIBLE);
                lyricsOverlayContainer.animate().alpha(1f).setDuration(180).start();
                if (activeLyricLineIndex >= 0) {
                    scrollToLyricLine(activeLyricLineIndex, false);
                }
            } else {
                lyricsOverlayContainer.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction(() -> lyricsOverlayContainer.setVisibility(View.GONE))
                        .start();
            }
        }
        refreshLyricsBannerVisibility();
        updateLyricsButtonState();
    }

    private void updateLyricsButtonState() {
        if (btnLyrics == null) return;
        int accent = AccentColorHelper.getAccentColor(this);
        boolean hasLyrics = currentLyricsData != null && currentLyricsData.hasLyrics();
        if (isLyricsOverlayVisible || (hasLyrics && isSmallLyricsVisible)) {
            btnLyrics.setAlpha(1.0f);
            ImageViewCompat.setImageTintList(btnLyrics, ColorStateList.valueOf(accent));
        } else if (hasLyrics) {
            btnLyrics.setAlpha(0.95f);
            ImageViewCompat.setImageTintList(btnLyrics, ColorStateList.valueOf(COLOR_WHITE));
        } else {
            btnLyrics.setAlpha(0.38f);
            ImageViewCompat.setImageTintList(btnLyrics, ColorStateList.valueOf(COLOR_WHITE));
        }
    }

    private void syncLyricsToPosition(long positionMs, long durationMs) {
        if (currentLyricsData == null || !currentLyricsData.hasLyrics()) return;
        int newIdx = currentLyricsData.findActiveLineIndex(positionMs, durationMs);
        if (newIdx < 0 || newIdx >= currentLyricsData.lines.size()) return;

        if (newIdx != activeLyricLineIndex) {
            highlightActiveLyricLine(newIdx, true);
        }
    }

    private void highlightActiveLyricLine(int newIdx, boolean smoothScroll) {
        if (newIdx < 0 || newIdx >= lyricLineViews.size()) return;
        int prevIdx = activeLyricLineIndex;
        activeLyricLineIndex = newIdx;

        int accent = AccentColorHelper.getAccentColor(this);

        if (prevIdx >= 0 && prevIdx < lyricLineViews.size()) {
            TextView prevView = lyricLineViews.get(prevIdx);
            prevView.setTextColor(Color.parseColor("#8A93A2"));
            prevView.setTextSize(15.5f);
            prevView.setTypeface(null, Typeface.NORMAL);
        }

        TextView activeView = lyricLineViews.get(newIdx);
        activeView.setTextColor(accent);
        activeView.setTextSize(18.5f);
        activeView.setTypeface(null, Typeface.BOLD);

        if (txtLiveLyricLine != null) {
            txtLiveLyricLine.setText("♪  " + currentLyricsData.lines.get(newIdx).text);
        }

        if (isLyricsOverlayVisible) {
            scrollToLyricLine(newIdx, smoothScroll);
        }
    }

    private void scrollToLyricLine(final int lineIdx, final boolean smooth) {
        if (lyricsScrollView == null || lineIdx < 0 || lineIdx >= lyricLineViews.size()) return;
        final TextView targetView = lyricLineViews.get(lineIdx);
        lyricsScrollView.post(() -> {
            int scrollHeight = lyricsScrollView.getHeight();
            if (scrollHeight <= 0) return;
            int lineCenter = targetView.getTop() + (targetView.getHeight() / 2);
            int targetScrollY = Math.max(0, lineCenter - (scrollHeight / 2) + lyricsScrollView.getPaddingTop());
            if (smooth) {
                lyricsScrollView.smoothScrollTo(0, targetScrollY);
            } else {
                lyricsScrollView.scrollTo(0, targetScrollY);
            }
        });
    }

    private void stopSeekBarUpdate() {
        if (updateSeekBar != null) {
            handler.removeCallbacks(updateSeekBar);
        }
    }

    private String formatTime(int millis) {
        int safeMillis = Math.max(0, millis);
        int totalSec = safeMillis / 1000;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean keepScreenOn = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SettingsFragment.KEY_KEEP_SCREEN_ON, false);
        if (keepScreenOn) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        applyAccentTheme();
        refreshLyricsBannerVisibility();
        updateLyricsButtonState();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_UPDATE_UI);
        filter.addAction("com.attomaticsystems.parrotplayer.CLOSE_APP");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiver, filter);
        }

        if (isBound && musicService != null) {
            syncFromService();
        } else {
            Intent intent = new Intent(this, MusicService.class);
            intent.setAction(MusicService.ACTION_REQUEST_UPDATE);
            startService(intent);
        }

        startSeekBarUpdate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSeekBarUpdate();
        unregisterReceiver(updateReceiver);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isMinimizing) {
            return true;
        }
        int action = ev.getActionMasked();
        float rawX = ev.getRawX();
        float rawY = ev.getRawY();

        if (action == MotionEvent.ACTION_DOWN) {
            dragStartRawX = rawX;
            dragStartRawY = rawY;
            isDraggingDown = false;
            dragIgnored = isTouchInsideExcludedScrollView(rawX, rawY);
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            } else {
                velocityTracker.clear();
            }
            velocityTracker.addMovement(ev);
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (velocityTracker != null) {
                velocityTracker.addMovement(ev);
            }
            if (!dragIgnored && !isUserSeeking) {
                float dx = rawX - dragStartRawX;
                float dy = rawY - dragStartRawY;
                int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

                if (!isDraggingDown) {
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) >= Math.abs(dy)) {
                        // Horizontal swipe (e.g. album art skip or seekbar) — don't intercept
                        dragIgnored = true;
                    } else if (dy > touchSlop * 1.25f && dy > Math.abs(dx) * 1.25f) {
                        isDraggingDown = true;
                        // Cancel any pending button press state in child views
                        MotionEvent cancelEvent = MotionEvent.obtain(ev);
                        cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancelEvent);
                        cancelEvent.recycle();
                    }
                }

                if (isDraggingDown && playerRootLayout != null) {
                    float dragOffset = Math.max(0f, dy);
                    int screenH = Math.max(1, getResources().getDisplayMetrics().heightPixels);
                    float progress = Math.min(1f, dragOffset / (screenH * 0.55f));
                    playerRootLayout.setTranslationY(dragOffset);
                    float scale = 1f - (0.06f * progress);
                    playerRootLayout.setScaleX(scale);
                    playerRootLayout.setScaleY(scale);
                    playerRootLayout.setAlpha(1f - (0.35f * progress));
                    return true;
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            float vy = 0f;
            if (velocityTracker != null) {
                velocityTracker.addMovement(ev);
                velocityTracker.computeCurrentVelocity(1000);
                vy = velocityTracker.getYVelocity();
                velocityTracker.recycle();
                velocityTracker = null;
            }
            if (isDraggingDown && playerRootLayout != null) {
                isDraggingDown = false;
                float dy = Math.max(0f, rawY - dragStartRawY);
                float density = getResources().getDisplayMetrics().density;
                boolean shouldDismiss = (action == MotionEvent.ACTION_UP)
                        && (dy > 95f * density || (vy > 600f && dy > 32f * density));
                if (shouldDismiss) {
                    animateMinimizeToMiniPlayer();
                } else {
                    playerRootLayout.animate()
                            .translationY(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(190)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
                return true;
            }
            isDraggingDown = false;
            dragIgnored = false;
        }

        return super.dispatchTouchEvent(ev);
    }

    private boolean isTouchInsideExcludedScrollView(float rawX, float rawY) {
        if (btnBack != null && isPointInsideView(btnBack, rawX, rawY, 12)) {
            return true;
        }
        if (btnEq != null && isPointInsideView(btnEq, rawX, rawY, 12)) {
            return true;
        }
        if (btnTimerSpeed != null && isPointInsideView(btnTimerSpeed, rawX, rawY, 12)) {
            return true;
        }
        if (seekBar != null && isPointInsideView(seekBar, rawX, rawY, 16)) {
            return true;
        }
        if (isLyricsOverlayVisible && lyricsScrollView != null
                && isPointInsideView(lyricsScrollView, rawX, rawY, 0)
                && lyricsScrollView.canScrollVertically(-1)) {
            return true;
        }
        return false;
    }

    private boolean isPointInsideView(View view, float rawX, float rawY, int slopPx) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return rawX >= loc[0] - slopPx
                && rawX <= loc[0] + view.getWidth() + slopPx
                && rawY >= loc[1] - slopPx
                && rawY <= loc[1] + view.getHeight() + slopPx;
    }

    private void animateMinimizeToMiniPlayer() {
        if (isMinimizing || isFinishing() || isDestroyed()) return;
        isMinimizing = true;

        if (isTaskRoot()) {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(mainIntent);
        }

        boolean animatorsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || ValueAnimator.areAnimatorsEnabled();
        if (playerRootLayout != null && animatorsEnabled) {
            int screenH = getResources().getDisplayMetrics().heightPixels;
            float remainingRatio = Math.max(0.35f, 1f - (playerRootLayout.getTranslationY() / Math.max(1f, screenH)));
            long duration = Math.max(80L, (long) (220 * remainingRatio));

            final Runnable completeMinimize = new Runnable() {
                private boolean completed = false;

                @Override
                public void run() {
                    if (completed) return;
                    completed = true;
                    handler.removeCallbacks(this);
                    if (!isFinishing() && !isDestroyed()) {
                        finish();
                        overridePendingTransition(0, 0);
                    }
                }
            };

            handler.postDelayed(completeMinimize, duration + 60L);
            playerRootLayout.animate().cancel();
            playerRootLayout.animate()
                    .translationY(screenH)
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .alpha(0f)
                    .setDuration(duration)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(completeMinimize)
                    .start();
        } else {
            finish();
            overridePendingTransition(R.anim.stay_still, R.anim.slide_out_down);
        }
    }

    private void navigateBackFromPlayer() {
        animateMinimizeToMiniPlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }
}
