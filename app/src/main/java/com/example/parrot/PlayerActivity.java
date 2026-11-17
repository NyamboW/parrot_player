package com.example.parrot;

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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity {

    private static final int COLOR_WHITE = Color.WHITE;
    private static final long SEEKBAR_UPDATE_INTERVAL_MS = 200L;

    ArrayList<String> mySongs;
    int position = 0;

    TextView txtSong, txtStart, txtEnd;
    SeekBar seekBar;
    ImageView imgView;
    CardView imgViewCard;

    ImageButton btnPlay, btnNext, btnPrev, btnRepeat, btnShuffle, btnFav, btnEq, btnBack, btnQueue, btnMic;

    FavouritesManager favManager;
    String currentSongPath;

    boolean isShuffle = false;
    boolean isRepeat = false;
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
                if (intent.hasExtra("isRepeat") && !repeatToggledBeforeBind) {
                    isRepeat = intent.getBooleanExtra("isRepeat", false);
                    updateToggleButton(btnRepeat, isRepeat);
                }

                if (songPath != null && !songPath.equals(imgView.getTag())) {
                    imgView.setTag(songPath);
                    loadAlbumArt(songPath, true);
                }

                if (songPath != null) {
                    currentSongPath = songPath;
                    updateFavIcon(false);
                }
            } else if (intent.getAction().equals("com.example.parrot.CLOSE_APP")) {
                finishAffinity();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        favManager = new FavouritesManager(this);

        txtSong = findViewById(R.id.txtSong);
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

        txtSong.setSelected(true);
        applyAccentTheme();

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
                btnMic);
        setupAlbumArtSwipe();

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
            isRepeat = !isRepeat;
            if (isBound && musicService != null) {
                musicService.setRepeat(isRepeat);
                repeatToggledBeforeBind = false;
            } else {
                repeatToggledBeforeBind = true;
            }
            updateToggleButton(btnRepeat, isRepeat);
            popView(btnRepeat);
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
            musicService.setRepeat(isRepeat);
            repeatToggledBeforeBind = false;
        } else {
            isRepeat = musicService.isRepeat();
        }

        updateToggleButton(btnShuffle, isShuffle);
        updateToggleButton(btnRepeat, isRepeat);

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

    private void applyAccentTheme() {
        int accent = AccentColorHelper.getAccentColor(this);
        if (seekBar != null) {
            seekBar.setProgressTintList(ColorStateList.valueOf(accent));
        }
        updateToggleButton(btnShuffle, isShuffle);
        updateToggleButton(btnRepeat, isRepeat);
        updateFavIcon(false);
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
                }
                handler.postDelayed(this, SEEKBAR_UPDATE_INTERVAL_MS);
            }
        };
        handler.post(updateSeekBar);
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

        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_UPDATE_UI);
        filter.addAction("com.example.parrot.CLOSE_APP");

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

    private void navigateBackFromPlayer() {
        if (isTaskRoot()) {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        navigateBackFromPlayer();
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
