package com.example.parrot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
        SharedPreferences prefs = getSharedPreferences("parrot_settings", MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", true);
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate
                    .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate
                    .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }

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
                if (intent.getAction() != null) {
                    if (intent.getAction().equals(MusicService.BROADCAST_UPDATE_UI)) {
                        String song = intent.getStringExtra("song");
                        boolean isPlaying = intent.getBooleanExtra("isPlaying", false);

                        miniPlayer.setVisibility(LinearLayout.VISIBLE);
                        miniSongName.setText(song);

                        miniPlayPause.setImageResource(
                                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                    } else if (intent.getAction().equals("com.example.parrot.CLOSE_APP")) {
                        finishAffinity();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_UPDATE_UI);
        filter.addAction("com.example.parrot.CLOSE_APP");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(miniReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(miniReceiver, filter);
        }

        miniPlayer.setOnClickListener(v -> startActivity(new Intent(this, PlayerActivity.class)));

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.layoutToolBar);
        setSupportActionBar(toolbar);

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.navHostFragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(navigationView, navController);

            androidx.navigation.ui.AppBarConfiguration appBarConfiguration = new androidx.navigation.ui.AppBarConfiguration.Builder(
                    navController.getGraph())
                    .setOpenableLayout(drawerLayout)
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

            // Close drawer when an item is selected
            navigationView.setNavigationItemSelectedListener(item -> {
                boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
                if (handled) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                return handled;
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_REQUEST_UPDATE);
        try {
            startService(intent);
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniReceiver != null)
            unregisterReceiver(miniReceiver);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.navHostFragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
            androidx.navigation.ui.AppBarConfiguration appBarConfiguration = new androidx.navigation.ui.AppBarConfiguration.Builder(
                    navController.getGraph())
                    .setOpenableLayout(drawerLayout)
                    .build();
            return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }
}