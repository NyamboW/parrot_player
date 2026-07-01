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
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    LinearLayout miniPlayer;
    TextView miniSongName;
    ImageButton miniPlayPause;
    ImageButton miniNext;

    BroadcastReceiver miniReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        miniPlayer = findViewById(R.id.miniPlayer);
        miniSongName = findViewById(R.id.miniSongName);
        miniPlayPause = findViewById(R.id.miniPlayPause);
        miniNext = findViewById(R.id.miniNext);

        miniPlayer.setVisibility(LinearLayout.GONE);

        miniPlayPause.setOnClickListener(v -> {
            Intent intent = new Intent(this, MusicService.class);
            intent.setAction(MusicService.ACTION_PLAY_PAUSE);
            startService(intent);
        });

        miniNext.setOnClickListener(v -> {
            Intent intent = new Intent(this, MusicService.class);
            intent.setAction(MusicService.ACTION_NEXT);
            startService(intent);
        });

        miniReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && intent.getAction().equals(MusicService.BROADCAST_UPDATE_UI)) {
                    String song = intent.getStringExtra("song");
                    boolean isPlaying = intent.getBooleanExtra("isPlaying", false);

                    miniPlayer.setVisibility(LinearLayout.VISIBLE);
                    miniSongName.setText(song);

                    miniPlayPause.setImageResource(
                            isPlaying ? R.drawable.ic_pause : R.drawable.ic_play
                    );
                }
            }
        };

        IntentFilter filter = new IntentFilter(MusicService.BROADCAST_UPDATE_UI);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(miniReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(miniReceiver, filter);
        }

        miniPlayer.setOnClickListener(v ->
                startActivity(new Intent(this, PlayerActivity.class))
        );

        ImageView imageMenu = findViewById(R.id.imageMenu);
        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);
        
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.navHostFragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(navigationView, navController);
            
            // Close drawer when an item is selected
            navigationView.setNavigationItemSelectedListener(item -> {
                boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
                if (handled) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                return handled;
            });
        }
        
        imageMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_REQUEST_UPDATE);
        startService(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniReceiver != null) unregisterReceiver(miniReceiver);
    }
}