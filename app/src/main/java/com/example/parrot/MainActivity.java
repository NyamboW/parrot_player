package com.example.parrot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    LinearLayout miniPlayer;
    TextView miniSongName;
    ImageButton miniPlayPause;

    BroadcastReceiver miniReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        miniPlayer = findViewById(R.id.miniPlayer);
        miniSongName = findViewById(R.id.miniSongName);
        miniPlayPause = findViewById(R.id.miniPlayPause);

        miniPlayer.setVisibility(LinearLayout.GONE);

        miniPlayPause.setOnClickListener(v -> {
            if (MusicController.isPlaying()) {
                MusicController.pause();
                miniPlayPause.setImageResource(R.drawable.ic_play);
            } else {
                MusicController.play();
                miniPlayPause.setImageResource(R.drawable.ic_pause);
            }
        });

        miniReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String song = intent.getStringExtra("song");

                miniPlayer.setVisibility(LinearLayout.VISIBLE);
                miniSongName.setText(song);

                miniPlayPause.setImageResource(
                        MusicController.isPlaying()
                                ? R.drawable.ic_pause
                                : R.drawable.ic_play
                );
            }
        };

        IntentFilter filter = new IntentFilter("SHOW_MINI_PLAYER");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(miniReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(miniReceiver, filter);
        }

        miniPlayer.setOnClickListener(v ->
                startActivity(new Intent(this, PlayerActivity.class))
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniReceiver != null) unregisterReceiver(miniReceiver);
    }
}