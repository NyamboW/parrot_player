package com.attomaticsystems.parrotplayer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
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
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements AutoHideScrollHelper.ScrollBarController {

    public static final String ACTION_SHORTCUT_SHUFFLE_ALL = "com.attomaticsystems.parrotplayer.SHORTCUT_SHUFFLE_ALL";
    public static final String ACTION_SHORTCUT_SEARCH = "com.attomaticsystems.parrotplayer.SHORTCUT_SEARCH";
    public static final String ACTION_SHORTCUT_FAVORITES = "com.attomaticsystems.parrotplayer.SHORTCUT_FAVORITES";
    public static final String ACTION_SHORTCUT_RECENTLY_PLAYED = "com.attomaticsystems.parrotplayer.SHORTCUT_RECENTLY_PLAYED";

    private static final int COLOR_ACTIVE_TEXT = Color.WHITE;
    private static final int COLOR_INACTIVE_ITEM = Color.parseColor("#E0E3E8");
    private static final int COLOR_BOTTOM_ICON = Color.parseColor("#E6E8EC");

    LinearLayout miniPlayer;
    LinearLayout miniTextContainer;
    ProgressBar miniProgressBar;
    TextView miniSongName;
    TextView miniArtistName;
    ImageView imgMiniMusic;
    ImageButton miniPlayPause;
    ImageButton miniNext;
    private String miniLoadedSongPath = null;
    private boolean miniHasActualArt = false;
    private long lastPlayerOpenElapsedMs = 0L;
    private final Handler miniProgressHandler = new Handler(Looper.getMainLooper());
    private final Runnable miniProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isBound && musicService != null && miniPlayer != null && miniPlayer.getVisibility() == View.VISIBLE) {
                int dur = musicService.getDuration();
                int pos = musicService.getCurrentPosition();
                updateMiniProgressBar(pos, dur);
            }
            miniProgressHandler.postDelayed(this, 400L);
        }
    };
    private androidx.appcompat.widget.Toolbar toolbar;
    private boolean areBarsVisible = true;

    DrawerLayout drawerLayout;
    LinearLayout navRowHome, navRowSoundEffects, navRowFavorites, navRowQueue;
    ImageView imgNavHome, imgNavFavorites;
    TextView txtNavHome, txtNavFavorites, txtNavFavCount, txtNavQueueCount;
    ImageButton btnDrawerAddPlaylist, btnDrawerExit, btnDrawerSettings, btnDrawerAbout;
    ListView drawerPlaylistsListView;
    private int currentDestinationId = R.id.homeFragment;

    private static class DrawerPlaylistEntry {
        final boolean isSmart;
        final String smartId;
        final String name;

        DrawerPlaylistEntry(boolean isSmart, String smartId, String name) {
            this.isSmart = isSmart;
            this.smartId = smartId;
            this.name = name;
        }
    }

    private PlaylistManager playlistManager;
    private FavouritesManager favManager;
    private final ArrayList<DrawerPlaylistEntry> drawerPlaylists = new ArrayList<>();
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
        miniTextContainer = findViewById(R.id.miniTextContainer);
        miniProgressBar = findViewById(R.id.miniProgressBar);
        miniSongName = findViewById(R.id.miniSongName);
        miniArtistName = findViewById(R.id.miniArtistName);
        imgMiniMusic = findViewById(R.id.imgMiniMusic);
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
                        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                            refreshDrawerCounts();
                        }
                        String song = intent.getStringExtra("song");
                        String songPath = intent.getStringExtra("songPath");
                        boolean isPlaying = intent.getBooleanExtra("isPlaying", false);
                        int duration = intent.getIntExtra("duration", 0);
                        int currentPos = intent.getIntExtra("currentPosition", 0);

                        if (miniPlayer.getVisibility() != View.VISIBLE) {
                            miniPlayer.setVisibility(View.VISIBLE);
                            if (areBarsVisible) {
                                miniPlayer.setAlpha(0f);
                                miniPlayer.setTranslationY(24f);
                                miniPlayer.animate().alpha(1f).translationY(0f).setDuration(200).start();
                            } else {
                                miniPlayer.setAlpha(0f);
                                miniPlayer.setTranslationY(220f);
                            }
                        }
                        miniSongName.setText(song);
                        miniPlayPause.setImageResource(
                                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play
                        );
                        updateMiniProgressBar(currentPos, duration);
                        updateMiniAlbumArtAndArtist(songPath);
                        break;
                    }
                    case MusicService.BROADCAST_QUEUE_UPDATED:
                        refreshDrawerCounts();
                        break;
                    case AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED:
                        applyAccentTheme();
                        break;
                    case "com.attomaticsystems.parrotplayer.CLOSE_APP":
                        finishAffinity();
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_UPDATE_UI);
        filter.addAction(MusicService.BROADCAST_QUEUE_UPDATED);
        filter.addAction(AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED);
        filter.addAction("com.attomaticsystems.parrotplayer.CLOSE_APP");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(miniReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(miniReceiver, filter);
        }

        setupMiniPlayerGestures();

        toolbar = findViewById(R.id.layoutToolBar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);
        setupCustomDrawer();

        Intent bindIntent = new Intent(this, MusicService.class);
        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        if (savedInstanceState == null) {
            handleShortcutIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShortcutIntent(intent);
    }

    private void handleShortcutIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!ACTION_SHORTCUT_SHUFFLE_ALL.equals(action)
                && !ACTION_SHORTCUT_SEARCH.equals(action)
                && !ACTION_SHORTCUT_FAVORITES.equals(action)
                && !ACTION_SHORTCUT_RECENTLY_PLAYED.equals(action)) {
            return;
        }
        // Clear handled shortcut action so screen rotations do not re-trigger it
        intent.setAction(Intent.ACTION_MAIN);
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }

        switch (action) {
            case ACTION_SHORTCUT_SHUFFLE_ALL:
                triggerShuffleAllShortcut();
                break;
            case ACTION_SHORTCUT_SEARCH:
                openSearchShortcut();
                break;
            case ACTION_SHORTCUT_FAVORITES:
                openFavoritesShortcut();
                break;
            case ACTION_SHORTCUT_RECENTLY_PLAYED:
                openRecentlyPlayedShortcut();
                break;
        }
    }

    private NavHostFragment getMainNavHostFragment() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.navHostFragment);
        return (f instanceof NavHostFragment) ? (NavHostFragment) f : null;
    }

    private void ensureHomeDestination() {
        NavHostFragment navHost = getMainNavHostFragment();
        if (navHost == null) return;
        NavController navController = navHost.getNavController();
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() != R.id.homeFragment) {
            navController.popBackStack(R.id.homeFragment, false);
        }
    }

    private void triggerShuffleAllShortcut() {
        ensureHomeDestination();
        if (!PermissionHelper.hasAudioPermission(this)) {
            Toast.makeText(this, "Please allow audio permission first", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            ArrayList<String> songs = MusicFolderHelper.getAllAllowedSongs(MainActivity.this);
            if (songs.isEmpty()) {
                songs = new PlayerStateManager(MainActivity.this).restore().songs;
            }
            final ArrayList<String> finalSongs = songs;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalSongs.isEmpty()) {
                    Toast.makeText(MainActivity.this, "No tracks found to shuffle", Toast.LENGTH_SHORT).show();
                    return;
                }
                int startPos = new Random().nextInt(finalSongs.size());
                MusicService.startPlaybackShuffled(MainActivity.this, finalSongs, startPos);
                Toast.makeText(MainActivity.this,
                        "Shuffling " + finalSongs.size() + " tracks", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void openSearchShortcut() {
        ensureHomeDestination();
        setBarsVisible(true);
        NavHostFragment navHost = getMainNavHostFragment();
        if (navHost != null) {
            for (Fragment child : navHost.getChildFragmentManager().getFragments()) {
                if (child instanceof HomeFragment) {
                    ((HomeFragment) child).selectTab(1);
                    break;
                }
            }
        }
        expandToolbarSearchWithRetry(0);
    }

    private void expandToolbarSearchWithRetry(final int attempt) {
        if (isFinishing() || isDestroyed() || toolbar == null) return;
        toolbar.postDelayed(() -> {
            if (isFinishing() || isDestroyed() || toolbar == null) return;
            android.view.Menu menu = toolbar.getMenu();
            android.view.MenuItem searchItem = menu != null ? menu.findItem(R.id.item_search) : null;
            if (searchItem != null) {
                searchItem.expandActionView();
            } else if (attempt < 5) {
                expandToolbarSearchWithRetry(attempt + 1);
            }
        }, attempt == 0 ? 180L : 140L);
    }

    private void openFavoritesShortcut() {
        NavHostFragment navHost = getMainNavHostFragment();
        if (navHost != null) {
            navigateToDestination(navHost.getNavController(), R.id.menuFavourite);
        }
    }

    private void openRecentlyPlayedShortcut() {
        ensureHomeDestination();
        setBarsVisible(true);
        NavHostFragment navHost = getMainNavHostFragment();
        if (navHost != null) {
            for (Fragment child : navHost.getChildFragmentManager().getFragments()) {
                if (child instanceof HomeFragment) {
                    ((HomeFragment) child).selectTab(HomeFragment.TAB_RECENTLY_PLAYED);
                    return;
                }
            }
        }
        toolbar.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            NavHostFragment delayedHost = getMainNavHostFragment();
            if (delayedHost != null) {
                for (Fragment child : delayedHost.getChildFragmentManager().getFragments()) {
                    if (child instanceof HomeFragment) {
                        ((HomeFragment) child).selectTab(HomeFragment.TAB_RECENTLY_PLAYED);
                        break;
                    }
                }
            }
        }, 120L);
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

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                setBarsVisible(true);
                updateDrawerSelection(destination.getId());
            });

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
                hideSoftKeyboard();
                refreshDrawerPlaylists();
                refreshDrawerCounts();
            }
        });

        refreshDrawerPlaylists();
        refreshDrawerCounts();
    }

    private void hideSoftKeyboard() {
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
            currentFocus.clearFocus();
        }
    }

    private void navigateToDestination(NavController navController, int destinationId) {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == destinationId) {
            return;
        }
        NavOptions options = new NavOptions.Builder()
                .setEnterAnim(R.anim.nav_enter)
                .setExitAnim(R.anim.nav_exit)
                .setPopEnterAnim(R.anim.nav_enter)
                .setPopExitAnim(R.anim.nav_exit)
                .build();
        navController.navigate(destinationId, null, options);
    }

    private void updateDrawerSelection(int destinationId) {
        currentDestinationId = destinationId;
        int accent = AccentColorHelper.getAccentColor(this);
        float density = getResources().getDisplayMetrics().density;

        boolean isHome = (destinationId == R.id.homeFragment);
        boolean isFav = (destinationId == R.id.menuFavourite);
        boolean isSettings = (destinationId == R.id.MenuSettings);
        boolean isAbout = (destinationId == R.id.MenuAboutUs);

        int selectableBg = getSelectableItemBackgroundResId();

        if (isHome) {
            navRowHome.setBackground(AccentColorHelper.createRightRoundedPill(
                    AccentColorHelper.withAlpha(accent, 0x26), 28f * density));
        } else {
            navRowHome.setBackgroundResource(selectableBg);
        }
        ImageViewCompat.setImageTintList(imgNavHome,
                ColorStateList.valueOf(isHome ? accent : COLOR_INACTIVE_ITEM));
        txtNavHome.setTextColor(isHome ? COLOR_ACTIVE_TEXT : COLOR_INACTIVE_ITEM);
        txtNavHome.setTypeface(null, isHome ? Typeface.BOLD : Typeface.NORMAL);

        if (isFav) {
            navRowFavorites.setBackground(AccentColorHelper.createRightRoundedPill(
                    AccentColorHelper.withAlpha(accent, 0x26), 28f * density));
        } else {
            navRowFavorites.setBackgroundResource(selectableBg);
        }
        ImageViewCompat.setImageTintList(imgNavFavorites,
                ColorStateList.valueOf(isFav ? accent : COLOR_INACTIVE_ITEM));
        txtNavFavorites.setTextColor(isFav ? COLOR_ACTIVE_TEXT : COLOR_INACTIVE_ITEM);
        txtNavFavorites.setTypeface(null, isFav ? Typeface.BOLD : Typeface.NORMAL);

        ImageViewCompat.setImageTintList(btnDrawerSettings,
                ColorStateList.valueOf(isSettings ? accent : COLOR_BOTTOM_ICON));
        ImageViewCompat.setImageTintList(btnDrawerAbout,
                ColorStateList.valueOf(isAbout ? accent : COLOR_BOTTOM_ICON));

        if (imgMiniMusic != null && !miniHasActualArt) {
            ImageViewCompat.setImageTintList(imgMiniMusic, ColorStateList.valueOf(accent));
        }
        if (miniProgressBar != null) {
            miniProgressBar.setProgressTintList(ColorStateList.valueOf(accent));
        }
    }

    private void applyAccentTheme() {
        updateDrawerSelection(currentDestinationId);
        if (drawerPlaylistsAdapter != null) {
            drawerPlaylistsAdapter.notifyDataSetChanged();
        }
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

                ImageView imgIcon = v.findViewById(R.id.imgDrawerPlaylistIcon);
                TextView txtName = v.findViewById(R.id.txtDrawerPlaylistName);
                ImageButton btnDelete = v.findViewById(R.id.btnDeleteDrawerPlaylist);

                DrawerPlaylistEntry entry = drawerPlaylists.get(position);
                txtName.setText(entry.name);
                boolean isSelected = entry.name.equals(selectedDrawerPlaylist);
                txtName.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
                txtName.setTextColor(isSelected ? Color.WHITE : COLOR_INACTIVE_ITEM);

                if (imgIcon != null) {
                    int iconColor = entry.isSmart
                            ? AccentColorHelper.getAccentColor(MainActivity.this)
                            : Color.parseColor("#E0E3E8");
                    ImageViewCompat.setImageTintList(imgIcon, ColorStateList.valueOf(iconColor));
                }

                if (entry.isSmart) {
                    btnDelete.setVisibility(View.GONE);
                    btnDelete.setOnClickListener(null);
                } else {
                    btnDelete.setVisibility(View.VISIBLE);
                    btnDelete.setOnClickListener(view -> confirmDeletePlaylist(entry.name));
                }

                return v;
            }
        };

        drawerPlaylistsListView.setAdapter(drawerPlaylistsAdapter);
        drawerPlaylistsListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < drawerPlaylists.size()) {
                DrawerPlaylistEntry entry = drawerPlaylists.get(position);
                selectedDrawerPlaylist = entry.name;
                drawerPlaylistsAdapter.notifyDataSetChanged();
                drawerLayout.closeDrawer(GravityCompat.START);

                Intent intent = new Intent(MainActivity.this, GroupSongsActivity.class);
                if (entry.isSmart) {
                    intent.putExtra("type", "smart_playlist");
                    intent.putExtra("smart_id", entry.smartId);
                } else {
                    intent.putExtra("type", "playlist");
                }
                intent.putExtra("name", entry.name);
                startActivity(intent);
            }
        });
    }

    private void refreshDrawerPlaylists() {
        if (playlistManager == null || drawerPlaylistsAdapter == null) return;
        drawerPlaylists.clear();
        for (String pl : playlistManager.getPlaylists()) {
            drawerPlaylists.add(new DrawerPlaylistEntry(false, null, pl));
        }
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
        ParrotModalHelper.showTextInputDialog(
                this,
                "Create New Playlist",
                "Enter a name for your new playlist",
                "Playlist name",
                null,
                "Create",
                null,
                null,
                name -> {
                    playlistManager.createPlaylist(name);
                    selectedDrawerPlaylist = name;
                    refreshDrawerPlaylists();
                    Toast.makeText(this, "Playlist '" + name + "' created", Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void confirmDeletePlaylist(String playlistName) {
        ParrotModalHelper.showConfirmDialog(
                this,
                "Delete Playlist",
                "Are you sure you want to delete '" + playlistName + "'?",
                "Delete",
                "Cancel",
                true,
                () -> {
                    playlistManager.deletePlaylist(playlistName);
                    if (playlistName.equals(selectedDrawerPlaylist)) {
                        selectedDrawerPlaylist = null;
                    }
                    refreshDrawerPlaylists();
                    Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
                },
                null,
                null
        );
    }

    private void openSoundEffectsDialog() {
        new ParrotEqualizerDialogFragment().show(getSupportFragmentManager(), "eq_drawer");
    }

    private void updateMiniProgressBar(int currentPos, int duration) {
        if (miniProgressBar == null) return;
        if (duration > 0) {
            miniProgressBar.setMax(duration);
            miniProgressBar.setProgress(Math.max(0, Math.min(duration, currentPos)));
        } else {
            miniProgressBar.setMax(1000);
            miniProgressBar.setProgress(0);
        }
    }

    private void updateMiniAlbumArtAndArtist(final String songPath) {
        if (songPath == null || songPath.isEmpty() || songPath.equals(miniLoadedSongPath)) {
            return;
        }
        miniLoadedSongPath = songPath;

        TrackMetadataHelper.loadTrackInfoAsync(songPath, info -> {
            if (!isFinishing() && !isDestroyed() && songPath.equals(miniLoadedSongPath) && miniArtistName != null) {
                if (info != null) {
                    miniArtistName.setText(info.getArtistAlbumSubtitle());
                } else {
                    miniArtistName.setText("Unknown Artist");
                }
            }
        });

        if (imgMiniMusic == null) return;
        imgMiniMusic.setTag(songPath);
        AlbumArtLoader loader = AlbumArtLoader.getInstance();
        Bitmap cached = loader.getBestCachedArt(songPath);
        if (cached != null) {
            applyMiniAlbumArtBitmap(cached);
        } else {
            applyMiniDefaultArtIcon();
            if (!loader.hasNoArt(songPath)) {
                loader.loadThumbArt(songPath, bitmap -> {
                    if (bitmap != null && !isFinishing() && !isDestroyed() && songPath.equals(imgMiniMusic.getTag())) {
                        applyMiniAlbumArtBitmap(bitmap);
                    }
                });
            }
        }
    }

    private void applyMiniAlbumArtBitmap(Bitmap bitmap) {
        if (imgMiniMusic == null) return;
        miniHasActualArt = true;
        imgMiniMusic.setPadding(0, 0, 0, 0);
        ImageViewCompat.setImageTintList(imgMiniMusic, null);
        imgMiniMusic.setImageBitmap(bitmap);
    }

    private void applyMiniDefaultArtIcon() {
        if (imgMiniMusic == null) return;
        miniHasActualArt = false;
        int pad = (int) (10 * getResources().getDisplayMetrics().density);
        imgMiniMusic.setPadding(pad, pad, pad, pad);
        imgMiniMusic.setImageResource(R.drawable.ic_music);
        ImageViewCompat.setImageTintList(imgMiniMusic,
                ColorStateList.valueOf(AccentColorHelper.getAccentColor(this)));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupMiniPlayerGestures() {
        if (miniPlayer == null) return;
        final GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 75;
            private static final int SWIPE_VELOCITY_THRESHOLD = 120;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastPlayerOpenElapsedMs < 450L) {
                    return true;
                }
                lastPlayerOpenElapsedMs = now;
                Intent playerIntent = new Intent(MainActivity.this, PlayerActivity.class);
                playerIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(playerIntent);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)
                        && Math.abs(diffX) > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    boolean next = diffX < 0;
                    animateMiniSwipeAndSkip(next);
                    return true;
                }
                return false;
            }
        });
        miniPlayer.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    private void animateMiniSwipeAndSkip(final boolean next) {
        final View target = miniTextContainer != null ? miniTextContainer : miniSongName;
        if (target == null) {
            Intent intent = new Intent(this, MusicService.class);
            intent.setAction(next ? MusicService.ACTION_NEXT : MusicService.ACTION_PREV);
            startService(intent);
            return;
        }
        final float offset = next ? -36f : 36f;
        target.animate().cancel();
        target.animate()
                .translationX(offset)
                .alpha(0.35f)
                .setDuration(90)
                .withEndAction(() -> {
                    Intent intent = new Intent(MainActivity.this, MusicService.class);
                    intent.setAction(next ? MusicService.ACTION_NEXT : MusicService.ACTION_PREV);
                    startService(intent);
                    target.setTranslationX(-offset);
                    target.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(140)
                            .start();
                })
                .start();
    }

    @Override
    public void setBarsVisible(boolean visible) {
        if (areBarsVisible == visible) return;
        areBarsVisible = visible;

        View homeTopContainer = findViewById(R.id.homeTopContainer);
        if (homeTopContainer != null) {
            AutoHideScrollHelper.animateTopBarCollapse(homeTopContainer, visible);
        }

        if (miniPlayer != null && miniPlayer.getVisibility() == View.VISIBLE) {
            int slideDist = miniPlayer.getHeight() > 0 ? miniPlayer.getHeight() + 36 : 220;
            miniPlayer.animate().cancel();
            miniPlayer.animate()
                    .translationY(visible ? 0f : slideDist)
                    .alpha(visible ? 1f : 0f)
                    .setDuration(220)
                    .start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setBarsVisible(true);
        applyAccentTheme();
        refreshDrawerPlaylists();
        refreshDrawerCounts();

        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_REQUEST_UPDATE);
        try {
            startService(intent);
        } catch (IllegalStateException ignored) {}

        miniProgressHandler.removeCallbacks(miniProgressRunnable);
        miniProgressHandler.post(miniProgressRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        miniProgressHandler.removeCallbacks(miniProgressRunnable);
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
