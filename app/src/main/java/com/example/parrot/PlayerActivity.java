package com.example.parrot;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Random;

public class PlayerActivity extends AppCompatActivity {

    MediaPlayer mediaPlayer;

    ArrayList<String> mySongs;
    int position = 0;

    TextView txtSong, txtStart, txtEnd;
    SeekBar seekBar;

    ImageButton btnPlay, btnNext, btnPrev, btnRepeat, btnShuffle;

    boolean isShuffle = false;
    boolean isRepeat = false;
    boolean isPlaying = true;

    Handler handler = new Handler();
    Runnable updateSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        txtSong = findViewById(R.id.txtSong);
        txtStart = findViewById(R.id.txtSongStart);
        txtEnd = findViewById(R.id.txtSongEnd);

        seekBar = findViewById(R.id.seekBar);

        btnPlay = findViewById(R.id.btnPlay);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrevious);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);

        mySongs = getIntent().getStringArrayListExtra("songs");
        position = getIntent().getIntExtra("pos", 0);

        playSong(position);

        btnPlay.setOnClickListener(v -> togglePlay());
        btnNext.setOnClickListener(v -> nextSong());
        btnPrev.setOnClickListener(v -> prevSong());

        btnShuffle.setOnClickListener(v -> isShuffle = !isShuffle);
        btnRepeat.setOnClickListener(v -> isRepeat = !isRepeat);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void playSong(int pos) {

        safeRelease();

        position = pos;

        Uri uri = Uri.parse(mySongs.get(position));
        mediaPlayer = MediaPlayer.create(this, uri);

        if (mediaPlayer == null) return;

        mediaPlayer.start();

        // 🔥 GLOBAL STATE
        MusicController.setPlayer(mediaPlayer);
        MusicController.setSongName(getFileName(mySongs.get(position)));

        isPlaying = true;

        txtSong.setText(getFileName(mySongs.get(position)));

        seekBar.setMax(mediaPlayer.getDuration());
        txtEnd.setText(formatTime(mediaPlayer.getDuration()));

        startSeekBarUpdate();

        // 🔥 SHOW MINI PLAYER
        Intent intent = new Intent("SHOW_MINI_PLAYER");
        intent.putExtra("song", getFileName(mySongs.get(position)));
        sendBroadcast(intent);

        mediaPlayer.setOnCompletionListener(mp -> {
            if (isRepeat) {
                playSong(position);
            } else {
                nextSong();
            }
        });
    }

    private void togglePlay() {
        if (mediaPlayer == null) return;

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                isPlaying = false;
            } else {
                mediaPlayer.start();
                isPlaying = true;
            }
        } catch (Exception e) {
            playSong(position);
        }
    }

    private void nextSong() {
        safeRelease();

        if (isShuffle) {
            position = new Random().nextInt(mySongs.size());
        } else {
            position = (position + 1) % mySongs.size();
        }

        playSong(position);
    }

    private void prevSong() {
        safeRelease();

        if (isShuffle) {
            position = new Random().nextInt(mySongs.size());
        } else {
            position = (position - 1 < 0)
                    ? mySongs.size() - 1
                    : position - 1;
        }

        playSong(position);
    }

    private void startSeekBarUpdate() {

        handler.removeCallbacksAndMessages(null);

        updateSeekBar = new Runnable() {
            @Override
            public void run() {

                if (mediaPlayer != null) {
                    try {
                        int current = mediaPlayer.getCurrentPosition();
                        seekBar.setProgress(current);
                        txtStart.setText(formatTime(current));
                    } catch (Exception ignored) {}
                }

                handler.postDelayed(this, 500);
            }
        };

        handler.post(updateSeekBar);
    }

    private void safeRelease() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {}
    }

    private String formatTime(int millis) {
        int min = (millis / 1000) / 60;
        int sec = (millis / 1000) % 60;
        return String.format("%02d:%02d", min, sec);
    }

    private String getFileName(String path) {
        int index = path.lastIndexOf("/");
        return (index != -1) ? path.substring(index + 1) : path;
    }

    @Override
    public void onBackPressed() {
        // 🔥 IMPORTANT: DO NOT STOP MUSIC
        finish(); // only close UI
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        // ❌ DO NOT release mediaPlayer here (this was your bug)
    }
}