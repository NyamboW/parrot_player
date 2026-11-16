package com.example.parrot;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.bullhead.equalizer.DialogEqualizerFragment;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int COLOR_ACTIVE_ICON = Color.parseColor("#4CAF50");
    private static final int COLOR_ACTIVE_TEXT = Color.WHITE;
    private static final int COLOR_INACTIVE_ITEM = Color.parseColor("#E0E3E8");
    private static final int COLOR_BOTTOM_ICON = Color.parseColor("#E6E8EC");

    LinearLayout miniPlayer;
    TextView miniSongName;
    ImageButton miniPlayPause;
    ImageButton miniNext;

    DrawerLayout drawerLayout;
    LinearLayout navRowHome, navRowSoundEffects, navRowFavorites, navRowQueue;
    ImageView imgNavHome, imgNavFavorites;
    TextView txtNavHome, txtNavFavorites, txtNavFavCount, txtNavQueueCount;
    ImageButton btnDrawerAddPlaylist, btnDrawerExit, btnDrawerSettings, btnDrawerAbout;
    ListView drawerPlaylistsListView;

    private PlaylistManager playlistManager;
    private FavouritesManager favManager;
    private final ArrayList<String> drawerPlaylists = new ArrayList<>();
    private BaseAdapter drawerPlaylistsAdapter;
    private String selectedDrawerPlaylist = null;

    private MusicService musicService;
    private boolean isBound = false;

    BroadcastReceiver miniReceiver;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MyBinder binder = (MusicService.MyBinder) service;
            musicService = binder.getService();
            isBound = true;
            refreshDrawerCounts();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playlistManager = new PlaylistManager(this);
        favManager = new FavouritesManager(this);

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
                if (intent.getAction() == null) return;
                switch (intent.getAction()) {
                    case MusicService.BROADCAST_UPDATE_UI: {
                        String song = intent.getStringExtra("song");
                        boolean isPlaying = intent.getBooleanExtra("isPlaying", false);

                        miniPlayer.setVisibility(LinearLayout.VISIBLE);
                        miniSongName.setText(song);
                        miniPlayPause.setImageResource(
                                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play
                        );
                        break;
                    }
                    case MusicService.BROADCAST_QUEUE_UPDATED:
                        refreshDrawerCounts();
                        break;
                    case "com.example.parrot.CLOSE_APP":
                        finishAffinity();
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_UPDATE_UI);
        filter.addAction(MusicService.BROADCAST_QUEUE_UPDATED);
        filter.addAction("com.example.parrot.CLOSE_APP");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(miniReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(miniReceiver, filter);
        }

        miniPlayer.setOnClickListener(v ->
                startActivity(new Intent(this, PlayerActivity.class))
        );

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.layoutToolBar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);
        setupCustomDrawer();

        Intent bindIntent = new Intent(this, MusicService.class);
        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setupCustomDrawer() {
        navRowHome = findViewById(R.id.navRowHome);
        navRowSoundEffects = findViewById(R.id.navRowSoundEffects);
        navRowFavorites = findViewById(R.id.navRowFavorites);
        navRowQueue = findViewById(R.id.navRowQueue);

        imgNavHome = findViewById(R.id.imgNavHome);
        imgNavFavorites = findViewById(R.id.imgNavFavorites);
        txtNavHome = findViewById(R.id.txtNavHome);
        txtNavFavorites = findViewById(R.id.txtNavFavorites);
        txtNavFavCount = findViewById(R.id.txtNavFavCount);
        txtNavQueueCount = findViewById(R.id.txtNavQueueCount);

        btnDrawerAddPlaylist = findViewById(R.id.btnDrawerAddPlaylist);
        drawerPlaylistsListView = findViewById(R.id.drawerPlaylistsListView);
        btnDrawerExit = findViewById(R.id.btnDrawerExit);
        btnDrawerSettings = findViewById(R.id.btnDrawerSettings);
        btnDrawerAbout = findViewById(R.id.btnDrawerAbout);

        setupDrawerPlaylistsAdapter();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.navHostFragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();

            AppBarConfiguration appBarConfiguration =
                    new AppBarConfiguration.Builder(navController.getGraph())
                            .setOpenableLayout(drawerLayout)
                            .build();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

            navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                    updateDrawerSelection(destination.getId())
            );

            navRowHome.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                if (navController.getCurrentDestination() == null
                        || navController.getCurrentDestination().getId() != R.id.homeFragment) {
                    navController.popBackStack(R.id.homeFragment, false);
                }
            });

            navRowFavorites.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToDestination(navController, R.id.menuFavourite);
            });

            btnDrawerSettings.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToDestination(navController, R.id.MenuSettings);
            });

            btnDrawerAbout.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToDestination(navController, R.id.MenuAboutUs);
            });
        }

        navRowSoundEffects.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            openSoundEffectsDialog();
        });

        navRowQueue.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            new QueueBottomSheetFragment().show(getSupportFragmentManager(), "queue");
        });

        btnDrawerAddPlaylist.setOnClickListener(v -> showCreatePlaylistDialogFromDrawer());

        btnDrawerExit.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            Intent closeIntent = new Intent(this, MusicService.class);
            closeIntent.setAction(MusicService.ACTION_CLOSE);
            startService(closeIntent);
            finishAffinity();
        });

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                refreshDrawerPlaylists();
                refreshDrawerCounts();
            }
        });

        refreshDrawerPlaylists();
        refreshDrawerCounts();
    }

    private void navigateToDestination(NavController navController, int destinationId) {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == destinationId) {
            return;
        }
        navController.navigate(destinationId);
    }

    private void updateDrawerSelection(int destinationId) {
        boolean isHome = (destinationId == R.id.homeFragment);
        boolean isFav = (destinationId == R.id.menuFavourite);
        boolean isSettings = (destinationId == R.id.MenuSettings);
        boolean isAbout = (destinationId == R.id.MenuAboutUs);

        int selectableBg = getSelectableItemBackgroundResId();

        navRowHome.setBackgroundResource(isHome ? R.drawable.drawer_active_item_bg : selectableBg);
        ImageViewCompat.setImageTintList(imgNavHome,
                ColorStateList.valueOf(isHome ? COLOR_ACTIVE_ICON : COLOR_INACTIVE_ITEM));
        txtNavHome.setTextColor(isHome ? COLOR_ACTIVE_TEXT : COLOR_INACTIVE_ITEM);
        txtNavHome.setTypeface(null, isHome ? Typeface.BOLD : Typeface.NORMAL);

        navRowFavorites.setBackgroundResource(isFav ? R.drawable.drawer_active_item_bg : selectableBg);
        ImageViewCompat.setImageTintList(imgNavFavorites,
                ColorStateList.valueOf(isFav ? COLOR_ACTIVE_ICON : COLOR_INACTIVE_ITEM));
        txtNavFavorites.setTextColor(isFav ? COLOR_ACTIVE_TEXT : COLOR_INACTIVE_ITEM);
        txtNavFavorites.setTypeface(null, isFav ? Typeface.BOLD : Typeface.NORMAL);

        ImageViewCompat.setImageTintList(btnDrawerSettings,
                ColorStateList.valueOf(isSettings ? COLOR_ACTIVE_ICON : COLOR_BOTTOM_ICON));
        ImageViewCompat.setImageTintList(btnDrawerAbout,
                ColorStateList.valueOf(isAbout ? COLOR_ACTIVE_ICON : COLOR_BOTTOM_ICON));
    }

    private int getSelectableItemBackgroundResId() {
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        return outValue.resourceId;
    }

    private void setupDrawerPlaylistsAdapter() {
        drawerPlaylistsAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return drawerPlaylists.size();
            }

            @Override
            public Object getItem(int position) {
                return drawerPlaylists.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView != null
                        ? convertView
                        : getLayoutInflater().inflate(R.layout.item_drawer_playlist, parent, false);

                TextView txtName = v.findViewById(R.id.txtDrawerPlaylistName);
                ImageButton btnDelete = v.findViewById(R.id.btnDeleteDrawerPlaylist);

                String playlistName = drawerPlaylists.get(position);
                txtName.setText(playlistName);
                boolean isSelected = playlistName.equals(selectedDrawerPlaylist);
                txtName.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
                txtName.setTextColor(isSelected ? Color.WHITE : COLOR_INACTIVE_ITEM);

                btnDelete.setOnClickListener(view -> confirmDeletePlaylist(playlistName));

                return v;
            }
        };

        drawerPlaylistsListView.setAdapter(drawerPlaylistsAdapter);
        drawerPlaylistsListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < drawerPlaylists.size()) {
                String playlistName = drawerPlaylists.get(position);
                selectedDrawerPlaylist = playlistName;
                drawerPlaylistsAdapter.notifyDataSetChanged();
                drawerLayout.closeDrawer(GravityCompat.START);

                Intent intent = new Intent(MainActivity.this, GroupSongsActivity.class);
                intent.putExtra("type", "playlist");
                intent.putExtra("name", playlistName);
                startActivity(intent);
            }
        });
    }

    private void refreshDrawerPlaylists() {
        if (playlistManager == null || drawerPlaylistsAdapter == null) return;
        drawerPlaylists.clear();
        drawerPlaylists.addAll(playlistManager.getPlaylists());
        drawerPlaylistsAdapter.notifyDataSetChanged();
    }

    private void refreshDrawerCounts() {
        if (favManager != null && txtNavFavCount != null) {
            int validFavCount = 0;
            for (String path : favManager.getFavourites()) {
                if (path != null && new File(path).exists()) {
                    validFavCount++;
                }
            }
            txtNavFavCount.setText(validFavCount > 0 ? String.valueOf(validFavCount) : "");
        }

        if (txtNavQueueCount != null) {
            int queueCount = (isBound && musicService != null && musicService.getQueueList() != null)
                    ? musicService.getQueueList().size()
                    : 0;
            txtNavQueueCount.setText(queueCount > 0 ? String.valueOf(queueCount) : "");
        }
    }

    private void showCreatePlaylistDialogFromDrawer() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create New Playlist");

        final EditText input = new EditText(this);
        input.setHint("Playlist name");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                playlistManager.createPlaylist(name);
                selectedDrawerPlaylist = name;
                refreshDrawerPlaylists();
                Toast.makeText(this, "Playlist '" + name + "' created", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void confirmDeletePlaylist(String playlistName) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Playlist")
                .setMessage("Are you sure you want to delete '" + playlistName + "'?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    playlistManager.deletePlaylist(playlistName);
                    if (playlistName.equals(selectedDrawerPlaylist)) {
                        selectedDrawerPlaylist = null;
                    }
                    refreshDrawerPlaylists();
                    Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void openSoundEffectsDialog() {
        if (isBound && musicService != null) {
            int sessionId = musicService.getAudioSessionId();
            if (sessionId != 0) {
                DialogEqualizerFragment fragment = DialogEqualizerFragment.newBuilder()
                        .setAudioSessionId(sessionId)
                        .themeColor(Color.parseColor("#0B0B0F"))
                        .textColor(Color.WHITE)
                        .accentAlpha(Color.parseColor("#444CAF50"))
                        .darkColor(Color.parseColor("#0B0B0F"))
                        .setAccentColor(COLOR_ACTIVE_ICON)
                        .build();

                getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                        new FragmentManager.FragmentLifecycleCallbacks() {
                            @Override
                            public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                                if (f == fragment) {
                                    fm.unregisterFragmentLifecycleCallbacks(this);
                                    saveAndReloadEqualizer();
                                }
                            }
                        }, false);

                fragment.show(getSupportFragmentManager(), "eq_drawer");
                return;
            }
        }
        Toast.makeText(this, "Play a song first to use Sound Effects", Toast.LENGTH_SHORT).show();
    }

    private void saveAndReloadEqualizer() {
        SharedPreferences eqPrefs = getSharedPreferences("parrot_eq", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = eqPrefs.edit();

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

        Intent reloadIntent = new Intent(this, MusicService.class);
        reloadIntent.setAction(MusicService.ACTION_RELOAD_EQUALIZER);
        startService(reloadIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDrawerPlaylists();
        refreshDrawerCounts();

        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_REQUEST_UPDATE);
        try {
            startService(intent);
        } catch (IllegalStateException ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniReceiver != null) unregisterReceiver(miniReceiver);
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.navHostFragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            AppBarConfiguration appBarConfiguration =
                    new AppBarConfiguration.Builder(navController.getGraph())
                            .setOpenableLayout(drawerLayout)
                            .build();
            return NavigationUI.navigateUp(navController, appBarConfiguration)
                    || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }
}
