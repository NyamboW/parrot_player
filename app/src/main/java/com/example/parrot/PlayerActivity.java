package com.example.parrot;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bullhead.equalizer.DialogEqualizerFragment;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends AppCompatActivity {

    ArrayList<String> mySongs;
    int position = 0;

    TextView txtSong, txtStart, txtEnd;
    SeekBar seekBar;
    ImageView imgView;

    ImageButton btnPlay, btnNext, btnPrev, btnRepeat, btnShuffle, btnFav, btnEq, btnBack;

    FavouritesManager favManager;
    String currentSongPath;

    boolean isShuffle = false;
    boolean isRepeat = false;

    Handler handler = new Handler();
    Runnable updateSeekBar;
    ExecutorService executor = Executors.newSingleThreadExecutor();

    MusicService musicService;
    boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MyBinder binder = (MusicService.MyBinder) service;
            musicService = binder.getService();
            isBound = true;
            
            // Sync with current state
            if (musicService.isPlaying()) {
                btnPlay.setImageResource(R.drawable.ic_pause);
            } else {
                btnPlay.setImageResource(R.drawable.ic_play);
            }
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
            if (intent.getAction() != null) {
                if (intent.getAction().equals(MusicService.BROADCAST_UPDATE_UI)) {
                    String songName = intent.getStringExtra("song");
                    String songPath = intent.getStringExtra("songPath");
                    boolean isPlaying = intent.getBooleanExtra("isPlaying", false);
                    int duration = intent.getIntExtra("duration", 0);

                    txtSong.setText(songName);
                    btnPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

                    seekBar.setMax(duration);
                    txtEnd.setText(formatTime(duration));
                    
                    if (songPath != null && !songPath.equals(imgView.getTag())) {
                        imgView.setTag(songPath);
                        loadAlbumArt(songPath);
                    }
                    
                    // Update favourite icon
                    if (songPath != null) {
                        currentSongPath = songPath;
                        updateFavIcon();
                    }
                } else if (intent.getAction().equals("com.example.parrot.CLOSE_APP")) {
                    finishAffinity();
                }
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

        seekBar = findViewById(R.id.seekBar);

        btnPlay = findViewById(R.id.btnPlay);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrevious);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnFav = findViewById(R.id.btnfav);
        btnEq = findViewById(R.id.btnEq);
        btnBack = findViewById(R.id.btnback);

        mySongs = getIntent().getStringArrayListExtra("songs");
        position = getIntent().getIntExtra("pos", 0);

        if (mySongs != null && !mySongs.isEmpty()) {
            if (position >= 0 && position < mySongs.size()) {
                String initialSongPath = mySongs.get(position);
                imgView.setTag(initialSongPath);
                loadAlbumArt(initialSongPath);
                
                java.io.File file = new java.io.File(initialSongPath);
                txtSong.setText(file.getName().replace(".mp3", ""));
                currentSongPath = initialSongPath;
                updateFavIcon();
            }

            Intent serviceIntent = new Intent(this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_START);
            serviceIntent.putStringArrayListExtra("songs", mySongs);
            serviceIntent.putExtra("pos", position);
            
            // Ensure service is started as foreground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
        
        Intent bindIntent = new Intent(this, MusicService.class);
        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        btnPlay.setOnClickListener(v -> sendActionToService(MusicService.ACTION_PLAY_PAUSE));
        btnNext.setOnClickListener(v -> sendActionToService(MusicService.ACTION_NEXT));
        btnPrev.setOnClickListener(v -> sendActionToService(MusicService.ACTION_PREV));

        btnShuffle.setOnClickListener(v -> {
            isShuffle = !isShuffle;
            if (isBound) musicService.setShuffle(isShuffle);
            btnShuffle.setAlpha(isShuffle ? 1.0f : 0.5f);
        });
        
        btnRepeat.setOnClickListener(v -> {
            isRepeat = !isRepeat;
            if (isBound) musicService.setRepeat(isRepeat);
            btnRepeat.setAlpha(isRepeat ? 1.0f : 0.5f);
        });

        btnFav.setOnClickListener(v -> {
            if (currentSongPath != null) {
                favManager.toggleFavourite(currentSongPath);
                updateFavIcon();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isBound && musicService != null) {
                    musicService.seekTo(progress);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        startSeekBarUpdate();

        btnBack.setOnClickListener(v -> finish());

        btnEq.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                int sessionId = musicService.getAudioSessionId();
                if (sessionId != 0) {
                    DialogEqualizerFragment fragment = DialogEqualizerFragment.newBuilder()
                            .setAudioSessionId(sessionId)
                            .themeColor(android.graphics.Color.parseColor("#0B0B0F"))
                            .textColor(android.graphics.Color.WHITE)
                            .accentAlpha(android.graphics.Color.parseColor("#444CAF50"))
                            .darkColor(android.graphics.Color.parseColor("#0B0B0F"))
                            .setAccentColor(android.graphics.Color.parseColor("#4CAF50"))
                            .build();
                    fragment.show(getSupportFragmentManager(), "eq");
                    
                    getSupportFragmentManager().executePendingTransactions();
                    if (fragment.getDialog() != null) {
                        fragment.getDialog().setOnDismissListener(dialog -> {
                            saveAndReloadEqualizer();
                        });
                    }
                } else {
                    Toast.makeText(this, "Play a song first", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Play a song first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveAndReloadEqualizer() {
        android.content.SharedPreferences prefs = getSharedPreferences("parrot_eq", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        editor.putBoolean("isEqualizerEnabled", com.bullhead.equalizer.Settings.isEqualizerEnabled);
        editor.putInt("presetPos", com.bullhead.equalizer.Settings.presetPos);
        editor.putInt("reverbPreset", com.bullhead.equalizer.Settings.reverbPreset);
        editor.putInt("bassStrength", com.bullhead.equalizer.Settings.bassStrength);
        
        if (com.bullhead.equalizer.Settings.seekbarpos != null) {
            for (int i = 0; i < com.bullhead.equalizer.Settings.seekbarpos.length; i++) {
                editor.putInt("band" + i, com.bullhead.equalizer.Settings.seekbarpos[i]);
            }
        }
        editor.apply();
        
        sendActionToService(MusicService.ACTION_RELOAD_EQUALIZER);
    }

    private void loadAlbumArt(String path) {
        executor.execute(() -> {
            Bitmap bitmap = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(path);
                byte[] art = retriever.getEmbeddedPicture();
                retriever.release();
                if (art != null) {
                    bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                }
            } catch (Exception ignored) {}

            final Bitmap finalBitmap = bitmap;
            runOnUiThread(() -> {
                if (path.equals(imgView.getTag())) {
                    if (finalBitmap != null) {
                        imgView.setImageBitmap(finalBitmap);
                    } else {
                        imgView.setImageResource(R.drawable.ic_music);
                    }
                }
            });
        });
    }

    private void sendActionToService(String action) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        startService(intent);
    }
    
    private void updateFavIcon() {
        if (currentSongPath != null && favManager != null) {
            if (favManager.isFavourite(currentSongPath)) {
                btnFav.setImageResource(R.drawable.ic_fav);
                btnFav.setAlpha(1.0f);
            } else {
                btnFav.setImageResource(R.drawable.ic_fav);
                btnFav.setAlpha(0.5f);
            }
        }
    }

    private void startSeekBarUpdate() {
        handler.removeCallbacksAndMessages(null);

        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (isBound && musicService.getMediaPlayer() != null) {
                    try {
                        int current = musicService.getMediaPlayer().getCurrentPosition();
                        seekBar.setProgress(current);
                        txtStart.setText(formatTime(current));
                    } catch (Exception ignored) {}
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.post(updateSeekBar);
    }

    private String formatTime(int millis) {
        int min = (millis / 1000) / 60;
        int sec = (millis / 1000) % 60;
        return String.format("%02d:%02d", min, sec);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_UPDATE_UI);
        filter.addAction("com.example.parrot.CLOSE_APP");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiver, filter);
        }
        
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_REQUEST_UPDATE);
        startService(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(updateReceiver);
    }

    @Override
    public void onBackPressed() {
        finish();
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