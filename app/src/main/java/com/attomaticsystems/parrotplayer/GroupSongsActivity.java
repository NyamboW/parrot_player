package com.attomaticsystems.parrotplayer;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupSongsActivity extends AppCompatActivity implements AutoHideScrollHelper.ScrollBarController {

    private ListView listView;
    private Toolbar toolbar;
    private boolean areBarsVisible = true;
    private LinearLayout layoutPlaylistTopActions;
    private LinearLayout layoutPlaylistEmpty;
    private LinearLayout btnTopImportFolder, btnTopAddSongs;
    private LinearLayout btnEmptyImportFolder, btnEmptyAddSongs;
    private CardView emptyPlaylistIconCard;
    private ImageView imgEmptyPlaylistIcon, imgEmptyImportFolderBtn, imgTopImportFolder;
    private TextView txtEmptyImportFolderBtn, txtTopImportFolder;

    private final ArrayList<String> allSongList = new ArrayList<>();
    private String[] allItems = new String[0];
    private String[] allArtists = new String[0];
    private final ArrayList<String> songList = new ArrayList<>();
    private String[] items = new String[0];
    private String[] artists = new String[0];
    private String currentQuery = "";
    private FavouritesManager favManager;
    private BaseAdapter songsAdapter;
    private boolean isMultiSelectMode = false;

    private String groupType;
    private String groupName;
    private ActivityResultLauncher<Intent> documentTreeLauncher;
    private SongDeletionHelper songDeletionHelper;

    private final BroadcastReceiver playbackStateReceiver = new BroadcastReceiver() {
        private String lastSongPath = null;
        private boolean lastPlaying = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (isFinishing() || isDestroyed() || intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED.equals(action)) {
                applyAccentStyle();
                if (songsAdapter != null) songsAdapter.notifyDataSetChanged();
            } else if (PermissionHelper.ACTION_PERMISSIONS_GRANTED.equals(action)) {
                loadGroupSongs(groupType, groupName);
            } else if (MusicService.BROADCAST_UPDATE_UI.equals(action) && songsAdapter != null) {
                String currentPath = MusicService.getActiveSongPath();
                boolean currentPlaying = MusicService.isActivelyPlaying();
                boolean pathChanged = (currentPath == null) ? (lastSongPath != null) : !currentPath.equals(lastSongPath);
                if (pathChanged || currentPlaying != lastPlaying) {
                    lastSongPath = currentPath;
                    lastPlaying = currentPlaying;
                    songsAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_songs);

        toolbar = findViewById(R.id.toolbarGroup);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        listView = findViewById(R.id.listViewGroupSongs);
        listView.setFastScrollEnabled(true);
        layoutPlaylistTopActions = findViewById(R.id.layoutPlaylistTopActions);
        layoutPlaylistEmpty = findViewById(R.id.layoutPlaylistEmpty);
        btnTopImportFolder = findViewById(R.id.btnTopImportFolder);
        btnTopAddSongs = findViewById(R.id.btnTopAddSongs);
        btnEmptyImportFolder = findViewById(R.id.btnEmptyImportFolder);
        btnEmptyAddSongs = findViewById(R.id.btnEmptyAddSongs);
        emptyPlaylistIconCard = findViewById(R.id.emptyPlaylistIconCard);
        imgEmptyPlaylistIcon = findViewById(R.id.imgEmptyPlaylistIcon);
        imgEmptyImportFolderBtn = findViewById(R.id.imgEmptyImportFolderBtn);
        txtEmptyImportFolderBtn = findViewById(R.id.txtEmptyImportFolderBtn);
        imgTopImportFolder = findViewById(R.id.imgTopImportFolder);
        txtTopImportFolder = findViewById(R.id.txtTopImportFolder);

        favManager = new FavouritesManager(this);

        groupType = getIntent().getStringExtra("type");
        groupName = getIntent().getStringExtra("name");

        if (getSupportActionBar() != null && groupName != null) {
            if ("folder".equals(groupType)) {
                String folderTitle = new File(groupName).getName();
                getSupportActionBar().setTitle(folderTitle.isEmpty() ? groupName : folderTitle);
            } else {
                getSupportActionBar().setTitle(groupName);
            }
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        documentTreeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            importSongsFromDocumentTreeUri(treeUri);
                        }
                    }
                }
        );
        songDeletionHelper = SongDeletionHelper.forActivity(this);

        View.OnClickListener importFolderListener = v -> PermissionHelper.ensurePermissions(
                GroupSongsActivity.this,
                this::showImportFromFolderDialog
        );
        View.OnClickListener addSongsListener = v -> PermissionHelper.ensurePermissions(
                GroupSongsActivity.this,
                this::showSelectSongsToImportDialog
        );

        if (btnEmptyImportFolder != null) btnEmptyImportFolder.setOnClickListener(importFolderListener);
        if (btnTopImportFolder != null) btnTopImportFolder.setOnClickListener(importFolderListener);
        if (btnEmptyAddSongs != null) btnEmptyAddSongs.setOnClickListener(addSongsListener);
        if (btnTopAddSongs != null) btnTopAddSongs.setOnClickListener(addSongsListener);

        View btnTopExportM3u = findViewById(R.id.btnTopExportM3u);
        if (btnTopExportM3u != null) {
            btnTopExportM3u.setOnClickListener(v -> {
                if ("playlist".equals(groupType) && groupName != null) {
                    PlaylistManager pm = new PlaylistManager(GroupSongsActivity.this);
                    File out = pm.exportPlaylistToM3u(GroupSongsActivity.this, groupName);
                    if (out != null) {
                        Toast.makeText(GroupSongsActivity.this, "Exported to " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(GroupSongsActivity.this, "Failed to export .m3u playlist", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        applyAccentStyle();
        setupMultiSelect();

        PermissionHelper.ensurePermissions(this, () -> loadGroupSongs(groupType, groupName));
    }

    private void applyAccentStyle() {
        int accent = AccentColorHelper.getAccentColor(this);
        float density = getResources().getDisplayMetrics().density;

        if (emptyPlaylistIconCard != null) {
            emptyPlaylistIconCard.setCardBackgroundColor(AccentColorHelper.withAlpha(accent, 0x22));
        }
        if (imgEmptyPlaylistIcon != null) {
            imgEmptyPlaylistIcon.setImageTintList(ColorStateList.valueOf(accent));
        }
        if (btnEmptyImportFolder != null) {
            btnEmptyImportFolder.setBackground(AccentColorHelper.createRoundedRect(
                    AccentColorHelper.withAlpha(accent, 0x20),
                    AccentColorHelper.withAlpha(accent, 0x55),
                    (int) (1 * density),
                    14f * density
            ));
        }
        if (imgEmptyImportFolderBtn != null) {
            imgEmptyImportFolderBtn.setImageTintList(ColorStateList.valueOf(accent));
        }
        if (txtEmptyImportFolderBtn != null) {
            txtEmptyImportFolderBtn.setTextColor(accent);
        }
        if (btnTopImportFolder != null) {
            btnTopImportFolder.setBackground(AccentColorHelper.createRoundedRect(
                    AccentColorHelper.withAlpha(accent, 0x20),
                    AccentColorHelper.withAlpha(accent, 0x55),
                    (int) (1 * density),
                    12f * density
            ));
        }
        if (imgTopImportFolder != null) {
            imgTopImportFolder.setImageTintList(ColorStateList.valueOf(accent));
        }
        if (txtTopImportFolder != null) {
            txtTopImportFolder.setTextColor(accent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_UPDATE_UI);
        filter.addAction(AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED);
        filter.addAction(PermissionHelper.ACTION_PERMISSIONS_GRANTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(playbackStateReceiver, filter);
        }
        applyAccentStyle();
        if (songsAdapter != null) {
            songsAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(playbackStateReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        ToolbarSearchHelper.setupSearchMenu(
                this,
                menu,
                getMenuInflater(),
                R.menu.menu_search,
                "Search tracks in list...",
                this::filterSongs
        );
        return true;
    }

    private void setupMultiSelect() {
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                int count = listView.getCheckedItemCount();
                mode.setTitle(count + " selected");
                if (songsAdapter != null) {
                    songsAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_cab_songs, menu);
                isMultiSelectMode = true;
                if (songsAdapter != null) {
                    songsAdapter.notifyDataSetChanged();
                }
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_select_all) {
                    int total = listView.getCount();
                    boolean selectAll = listView.getCheckedItemCount() < total;
                    for (int idx = 0; idx < total; idx++) {
                        listView.setItemChecked(idx, selectAll);
                    }
                    if (songsAdapter != null) {
                        songsAdapter.notifyDataSetChanged();
                    }
                    return true;
                }

                SparseBooleanArray checked = listView.getCheckedItemPositions();
                ArrayList<String> selectedPaths = new ArrayList<>();
                for (int i = 0; i < checked.size(); i++) {
                    int pos = checked.keyAt(i);
                    if (checked.valueAt(i) && pos >= 0 && pos < songList.size()) {
                        selectedPaths.add(songList.get(pos));
                    }
                }
                if (selectedPaths.isEmpty()) {
                    mode.finish();
                    return true;
                }

                if (id == R.id.action_play_selected) {
                    MusicService.startPlaybackList(GroupSongsActivity.this, selectedPaths, 0);
                    mode.finish();
                    return true;
                } else if (id == R.id.action_add_to_queue) {
                    Intent intent = new Intent(GroupSongsActivity.this, MusicService.class);
                    intent.setAction(MusicService.ACTION_ADD_MULTIPLE_TO_QUEUE);
                    intent.putStringArrayListExtra("songPaths", selectedPaths);
                    startService(intent);
                    Toast.makeText(GroupSongsActivity.this, selectedPaths.size() + " added to queue", Toast.LENGTH_SHORT).show();
                    mode.finish();
                    return true;
                } else if (id == R.id.action_play_next) {
                    Intent intent = new Intent(GroupSongsActivity.this, MusicService.class);
                    intent.setAction(MusicService.ACTION_PLAY_NEXT_MULTIPLE);
                    intent.putStringArrayListExtra("songPaths", selectedPaths);
                    startService(intent);
                    Toast.makeText(GroupSongsActivity.this, selectedPaths.size() + " will play next", Toast.LENGTH_SHORT).show();
                    mode.finish();
                    return true;
                } else if (id == R.id.action_add_to_playlist) {
                    showAddMultipleToPlaylistDialog(selectedPaths);
                    mode.finish();
                    return true;
                } else if (id == R.id.action_fav) {
                    favManager.addFavourites(selectedPaths);
                    Toast.makeText(GroupSongsActivity.this, selectedPaths.size() + " added to Favourites", Toast.LENGTH_SHORT).show();
                    mode.finish();
                    return true;
                } else if (id == R.id.action_delete) {
                    if ("playlist".equals(groupType)) {
                        PlaylistManager pm = new PlaylistManager(GroupSongsActivity.this);
                        for (String path : selectedPaths) {
                            pm.removeSongFromPlaylist(groupName, path);
                        }
                        Toast.makeText(GroupSongsActivity.this, selectedPaths.size() + " removed from playlist", Toast.LENGTH_SHORT).show();
                        loadGroupSongs(groupType, groupName);
                        mode.finish();
                        return true;
                    }
                    mode.finish();
                    if (songDeletionHelper != null) {
                        songDeletionHelper.deleteSongs(selectedPaths, () -> {
                            if (!isFinishing() && !isDestroyed()) {
                                loadGroupSongs(groupType, groupName);
                            }
                        });
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                isMultiSelectMode = false;
                if (songsAdapter != null) {
                    songsAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void loadGroupSongs(String type, String name) {
        allSongList.clear();
        songList.clear();
        ArrayList<String> artistList = new ArrayList<>();

        if ("playlist".equals(type)) {
            Map<String, String> mediaStoreArtists = queryArtistLookupMap();
            PlaylistManager pm = new PlaylistManager(this);
            ArrayList<String> playlistSongs = pm.getSongsInPlaylist(name);
            for (String path : playlistSongs) {
                File file = new File(path);
                if (file.exists()) {
                    allSongList.add(path);
                    String artist = mediaStoreArtists.get(path);
                    artistList.add((artist != null && !artist.isEmpty()) ? artist : "Unknown Artist");
                }
            }
        } else if ("smart_playlist".equals(type)) {
            String smartId = getIntent().getStringExtra("smart_id");
            Map<String, String> mediaStoreArtists = queryArtistLookupMap();
            ArrayList<String> smartSongs = ListeningStatsManager.getSmartPlaylistSongs(this, smartId);
            boolean isMostPlayed = ListeningStatsManager.SMART_MOST_PLAYED.equals(smartId);
            for (String path : smartSongs) {
                File file = new File(path);
                if (file.exists()) {
                    allSongList.add(path);
                    String artist = mediaStoreArtists.get(path);
                    String cleanArtist = (artist != null && !artist.isEmpty()) ? artist : "Unknown Artist";
                    if (isMostPlayed) {
                        int plays = ListeningStatsManager.getPlayCount(this, path);
                        cleanArtist += "  •  " + plays + (plays == 1 ? " play" : " plays");
                    }
                    artistList.add(cleanArtist);
                }
            }
        } else if (PermissionHelper.hasAudioPermission(this)) {
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
            String[] selectionArgs = null;

            if ("artist".equals(type)) {
                selection += " AND " + MediaStore.Audio.Media.ARTIST + "=?";
                selectionArgs = new String[]{name};
            } else if ("album".equals(type)) {
                selection += " AND " + MediaStore.Audio.Media.ALBUM + "=?";
                selectionArgs = new String[]{name};
            }

            try {
                Cursor cursor = getContentResolver().query(
                        uri, null, selection, selectionArgs, MediaStore.Audio.Media.TITLE + " ASC"
                );

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));

                        if ("folder".equals(type)) {
                            File file = new File(path);
                            if (file.getParent() == null || !file.getParent().equals(name)) {
                                continue;
                            }
                        } else if (!MusicFolderHelper.isPathAllowed(this, path)) {
                            continue;
                        }

                        String artist = "Unknown Artist";
                        try {
                            String rawArtist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                            if (rawArtist != null && !rawArtist.equals("<unknown>")) {
                                artist = rawArtist;
                            }
                        } catch (Exception ignored) {}

                        try {
                            long durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                            TrackMetadataHelper.cacheDuration(path, durationMs);
                        } catch (Exception ignored) {}

                        allSongList.add(path);
                        artistList.add(artist);
                    }
                    cursor.close();
                }
            } catch (Exception ignored) {}
        }

        allItems = new String[allSongList.size()];
        allArtists = new String[allSongList.size()];

        for (int i = 0; i < allSongList.size(); i++) {
            String fullPath = allSongList.get(i);
            String songName = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            allItems[i] = songName.replaceAll("(?i)\\.(mp3|wav|m4a|flac|ogg|aac)$", "");
            allArtists[i] = artistList.get(i);
        }

        applySongFilter();
        updatePlaylistViewsVisibility();
        setAdapter();
    }

    private void filterSongs(String query) {
        currentQuery = query != null ? query : "";
        applySongFilter();
        updatePlaylistViewsVisibility();
        setAdapter();
    }

    private void applySongFilter() {
        songList.clear();
        String q = currentQuery.trim().toLowerCase(Locale.ROOT);
        ArrayList<String> filteredItems = new ArrayList<>();
        ArrayList<String> filteredArtists = new ArrayList<>();
        for (int i = 0; i < allSongList.size(); i++) {
            String title = allItems[i] != null ? allItems[i] : "";
            String artist = allArtists[i] != null ? allArtists[i] : "";
            if (q.isEmpty()
                    || title.toLowerCase(Locale.ROOT).contains(q)
                    || artist.toLowerCase(Locale.ROOT).contains(q)) {
                songList.add(allSongList.get(i));
                filteredItems.add(title);
                filteredArtists.add(artist);
            }
        }
        items = filteredItems.toArray(new String[0]);
        artists = filteredArtists.toArray(new String[0]);
    }

    private void updatePlaylistViewsVisibility() {
        boolean isPlaylist = "playlist".equals(groupType);
        boolean isEmpty = allSongList.isEmpty();

        if (isPlaylist && isEmpty) {
            if (layoutPlaylistEmpty != null) layoutPlaylistEmpty.setVisibility(View.VISIBLE);
            if (layoutPlaylistTopActions != null) layoutPlaylistTopActions.setVisibility(View.GONE);
            listView.setVisibility(View.GONE);
        } else {
            if (layoutPlaylistEmpty != null) layoutPlaylistEmpty.setVisibility(View.GONE);
            if (layoutPlaylistTopActions != null) {
                layoutPlaylistTopActions.setVisibility(isPlaylist ? View.VISIBLE : View.GONE);
            }
            listView.setVisibility(View.VISIBLE);
        }
    }

    private Map<String, String> queryArtistLookupMap() {
        Map<String, String> map = new HashMap<>();
        if (!PermissionHelper.hasAudioPermission(this)) return map;
        try {
            Cursor cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ARTIST},
                    MediaStore.Audio.Media.IS_MUSIC + "!=0",
                    null,
                    null
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(0);
                    String artist = cursor.getString(1);
                    if (path != null && artist != null && !artist.equals("<unknown>")) {
                        map.put(path, artist);
                    }
                }
                cursor.close();
            }
        } catch (Exception ignored) {}
        return map;
    }

    /**
     * AIMP-style "Import from Folder" dialog: lets the user pick one or more folders
     * on the device (or launch the system folder browser) and imports all songs inside them.
     */
    private void showImportFromFolderDialog() {
        if (!PermissionHelper.hasAudioPermission(this)) return;

        Map<String, ArrayList<String>> folderToSongs = new LinkedHashMap<>();
        try {
            Cursor cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media.DATA},
                    MediaStore.Audio.Media.IS_MUSIC + "!=0",
                    null,
                    MediaStore.Audio.Media.TITLE + " ASC"
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(0);
                    if (path != null) {
                        File f = new File(path);
                        String parent = f.getParent();
                        if (parent != null && f.exists()) {
                            ArrayList<String> list = folderToSongs.get(parent);
                            if (list == null) {
                                list = new ArrayList<>();
                                folderToSongs.put(parent, list);
                            }
                            list.add(path);
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception ignored) {}

        List<String> folderList = new ArrayList<>(folderToSongs.keySet());
        Collections.sort(folderList, (a, b) -> {
            boolean aAllowed = MusicFolderHelper.isFolderAllowed(this, a);
            boolean bAllowed = MusicFolderHelper.isFolderAllowed(this, b);
            if (aAllowed != bAllowed) return aAllowed ? -1 : 1;
            return a.compareToIgnoreCase(b);
        });

        if (folderList.isEmpty()) {
            launchSystemFolderPicker();
            return;
        }

        String[] folderPaths = folderList.toArray(new String[0]);
        String[] labels = new String[folderPaths.length];
        boolean[] checked = new boolean[folderPaths.length];

        for (int i = 0; i < folderPaths.length; i++) {
            String path = folderPaths[i];
            String folderName = new File(path).getName();
            int count = folderToSongs.get(path).size();
            String shortPath = path.replaceFirst("^/storage/emulated/0", "/Internal");
            labels[i] = folderName + " (" + count + (count == 1 ? " track)\n" : " tracks)\n") + shortPath;
        }

        new AlertDialog.Builder(this)
                .setTitle("Import Songs from Folder")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Import Selected", (dialog, which) -> {
                    ArrayList<String> toImport = new ArrayList<>();
                    for (int i = 0; i < folderPaths.length; i++) {
                        if (checked[i]) {
                            ArrayList<String> songsInFolder = folderToSongs.get(folderPaths[i]);
                            if (songsInFolder != null) {
                                toImport.addAll(songsInFolder);
                            }
                        }
                    }
                    if (toImport.isEmpty()) {
                        Toast.makeText(this, "No folder selected", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    PlaylistManager pm = new PlaylistManager(this);
                    pm.addSongsToPlaylist(groupName, toImport);
                    loadGroupSongs(groupType, groupName);
                    Toast.makeText(this, "Imported " + toImport.size() + " tracks into " + groupName, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Browse Storage...", (dialog, which) -> launchSystemFolderPicker())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchSystemFolderPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            documentTreeLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Folder browser not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void importSongsFromDocumentTreeUri(Uri treeUri) {
        String resolvedDirPath = resolveTreeUriToFilePath(treeUri);
        ArrayList<String> imported = new ArrayList<>();

        if (resolvedDirPath != null) {
            File dir = new File(resolvedDirPath);
            if (dir.exists() && dir.isDirectory()) {
                collectAudioFilesFromDirectory(dir, imported);
            }
        }

        if (imported.isEmpty() && PermissionHelper.hasAudioPermission(this)) {
            // Fallback: match folder name against MediaStore paths
            String lastSegment = treeUri.getLastPathSegment();
            if (lastSegment != null) {
                int colon = lastSegment.indexOf(':');
                String relPath = (colon >= 0 && colon + 1 < lastSegment.length())
                        ? lastSegment.substring(colon + 1)
                        : lastSegment;
                try {
                    Cursor cursor = getContentResolver().query(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Audio.Media.DATA},
                            MediaStore.Audio.Media.IS_MUSIC + "!=0",
                            null,
                            MediaStore.Audio.Media.TITLE + " ASC"
                    );
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            String path = cursor.getString(0);
                            if (path != null && path.contains(relPath)) {
                                imported.add(path);
                            }
                        }
                        cursor.close();
                    }
                } catch (Exception ignored) {}
            }
        }

        if (imported.isEmpty()) {
            Toast.makeText(this, "No audio tracks found in selected folder", Toast.LENGTH_SHORT).show();
            return;
        }

        PlaylistManager pm = new PlaylistManager(this);
        pm.addSongsToPlaylist(groupName, imported);
        loadGroupSongs(groupType, groupName);
        Toast.makeText(this, "Imported " + imported.size() + " tracks into " + groupName, Toast.LENGTH_SHORT).show();
    }

    private String resolveTreeUriToFilePath(Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            if (docId != null) {
                String[] split = docId.split(":");
                if (split.length >= 2) {
                    String type = split[0];
                    String relative = split[1];
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + relative;
                    } else {
                        File candidate = new File("/storage/" + type + "/" + relative);
                        if (candidate.exists()) {
                            return candidate.getAbsolutePath();
                        }
                    }
                } else if ("primary".equalsIgnoreCase(docId.replace(":", ""))) {
                    return Environment.getExternalStorageDirectory().getAbsolutePath();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void collectAudioFilesFromDirectory(File dir, ArrayList<String> outPaths) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectAudioFilesFromDirectory(f, outPaths);
            } else if (isAudioFileName(f.getName())) {
                outPaths.add(f.getAbsolutePath());
            }
        }
    }

    private boolean isAudioFileName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3")
                || lower.endsWith(".flac")
                || lower.endsWith(".wav")
                || lower.endsWith(".m4a")
                || lower.endsWith(".aac")
                || lower.endsWith(".ogg");
    }

    private void showSelectSongsToImportDialog() {
        if (!PermissionHelper.hasAudioPermission(this)) return;

        ArrayList<String> allPaths = new ArrayList<>();
        ArrayList<String> allLabels = new ArrayList<>();

        try {
            Cursor cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ARTIST},
                    MediaStore.Audio.Media.IS_MUSIC + "!=0",
                    null,
                    MediaStore.Audio.Media.TITLE + " ASC"
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(0);
                    String artist = cursor.getString(1);
                    if (path != null && MusicFolderHelper.isPathAllowed(this, path)) {
                        allPaths.add(path);
                        String title = path.substring(path.lastIndexOf("/") + 1)
                                .replaceAll("(?i)\\.(mp3|wav|m4a|flac|ogg|aac)$", "");
                        String cleanArtist = (artist != null && !artist.equals("<unknown>")) ? artist : "Unknown Artist";
                        allLabels.add(title + "\n" + cleanArtist);
                    }
                }
                cursor.close();
            }
        } catch (Exception ignored) {}

        if (allPaths.isEmpty()) {
            Toast.makeText(this, "No tracks found in your library", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labelArray = allLabels.toArray(new String[0]);
        boolean[] checked = new boolean[labelArray.length];

        new AlertDialog.Builder(this)
                .setTitle("Add Songs to " + groupName)
                .setMultiChoiceItems(labelArray, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Add Selected", (dialog, which) -> {
                    ArrayList<String> selected = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            selected.add(allPaths.get(i));
                        }
                    }
                    if (!selected.isEmpty()) {
                        PlaylistManager pm = new PlaylistManager(this);
                        pm.addSongsToPlaylist(groupName, selected);
                        loadGroupSongs(groupType, groupName);
                        Toast.makeText(this, "Added " + selected.size() + " tracks to " + groupName, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private abstract static class IndexedGroupAdapter extends BaseAdapter implements SectionIndexer {}

    private void setAdapter() {
        final AlphabetSectionIndexer indexer = new AlphabetSectionIndexer(items);
        songsAdapter = new IndexedGroupAdapter() {
            @Override
            public Object[] getSections() { return indexer.getSections(); }
            @Override
            public int getPositionForSection(int sectionIndex) { return indexer.getPositionForSection(sectionIndex); }
            @Override
            public int getSectionForPosition(int position) { return indexer.getSectionForPosition(position); }
            @Override
            public int getCount() { return items.length; }
            @Override
            public Object getItem(int i) { return null; }
            @Override
            public long getItemId(int i) { return 0; }
            @Override
            public View getView(int i, View view, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_items, null);
                TextView txt = v.findViewById(R.id.txtSong);
                TextView txtArtist = v.findViewById(R.id.txtartist);
                ImageView img = v.findViewById(R.id.songImg);
                ImageView btnMore = v.findViewById(R.id.btnMore);
                ImageView imgCheckMark = v.findViewById(R.id.imgCheckMark);

                txt.setText(items[i]);
                if (txtArtist != null) txtArtist.setText(artists[i]);

                boolean isChecked = isMultiSelectMode && listView.isItemChecked(i);
                if (isMultiSelectMode) {
                    if (btnMore != null) btnMore.setVisibility(View.GONE);
                    if (imgCheckMark != null) {
                        imgCheckMark.setVisibility(View.VISIBLE);
                        imgCheckMark.setImageResource(isChecked
                                ? R.drawable.ic_check_circle_filled
                                : R.drawable.ic_check_circle_outline);
                        imgCheckMark.setImageTintList(isChecked
                                ? ColorStateList.valueOf(AccentColorHelper.getAccentColor(GroupSongsActivity.this))
                                : null);
                    }
                } else {
                    if (btnMore != null) btnMore.setVisibility(View.VISIBLE);
                    if (imgCheckMark != null) imgCheckMark.setVisibility(View.GONE);
                }

                String path = songList.get(i);
                img.setTag(path);
                NowPlayingBarsView.bindRow(v, path, txt);

                if (btnMore != null) {
                    btnMore.setOnClickListener(view1 -> {
                        PopupMenu popupMenu = new PopupMenu(GroupSongsActivity.this, view1);
                        popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());
                        if (favManager.isFavourite(path)) {
                            popupMenu.getMenu().findItem(R.id.action_fav).setTitle("Remove from Favourites");
                        }
                        final int MENU_MOVE_UP = 9001;
                        final int MENU_MOVE_DOWN = 9002;
                        if ("playlist".equals(groupType)) {
                            if (currentQuery.trim().isEmpty()) {
                                if (i > 0) {
                                    popupMenu.getMenu().add(0, MENU_MOVE_UP, 0, "Move Up in Playlist");
                                }
                                if (i < songList.size() - 1) {
                                    popupMenu.getMenu().add(0, MENU_MOVE_DOWN, 1, "Move Down in Playlist");
                                }
                            }
                            MenuItem deleteItem = popupMenu.getMenu().findItem(R.id.action_delete);
                            if (deleteItem != null) {
                                deleteItem.setTitle("Remove from Playlist");
                            }
                        }
                        popupMenu.setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == MENU_MOVE_UP) {
                                PlaylistManager pm = new PlaylistManager(GroupSongsActivity.this);
                                pm.moveSongInPlaylist(groupName, i, i - 1);
                                loadGroupSongs(groupType, groupName);
                                return true;
                            } else if (item.getItemId() == MENU_MOVE_DOWN) {
                                PlaylistManager pm = new PlaylistManager(GroupSongsActivity.this);
                                pm.moveSongInPlaylist(groupName, i, i + 1);
                                loadGroupSongs(groupType, groupName);
                                return true;
                            } else if (item.getItemId() == R.id.action_fav) {
                                favManager.toggleFavourite(path);
                                Toast.makeText(GroupSongsActivity.this, favManager.isFavourite(path) ? "Added to Favourites" : "Removed from Favourites", Toast.LENGTH_SHORT).show();
                                return true;
                            } else if (item.getItemId() == R.id.action_play_next) {
                                Intent intent = new Intent(GroupSongsActivity.this, MusicService.class);
                                intent.setAction(MusicService.ACTION_PLAY_NEXT);
                                intent.putExtra("songPath", path);
                                startService(intent);
                                Toast.makeText(GroupSongsActivity.this, "Will play next", Toast.LENGTH_SHORT).show();
                                return true;
                            } else if (item.getItemId() == R.id.action_add_to_queue) {
                                Intent intent = new Intent(GroupSongsActivity.this, MusicService.class);
                                intent.setAction(MusicService.ACTION_ADD_TO_QUEUE);
                                intent.putExtra("songPath", path);
                                startService(intent);
                                Toast.makeText(GroupSongsActivity.this, "Added to queue", Toast.LENGTH_SHORT).show();
                                return true;
                            } else if (item.getItemId() == R.id.action_add_to_playlist) {
                                showAddToPlaylistDialog(path);
                                return true;
                            } else if (SongMenuActionsHelper.handleCommonMenuAction(GroupSongsActivity.this, item.getItemId(), path)) {
                                return true;
                            } else if (item.getItemId() == R.id.action_delete) {
                                if ("playlist".equals(groupType)) {
                                    PlaylistManager pm = new PlaylistManager(GroupSongsActivity.this);
                                    pm.removeSongFromPlaylist(groupName, path);
                                    loadGroupSongs(groupType, groupName);
                                    Toast.makeText(GroupSongsActivity.this, "Removed from " + groupName, Toast.LENGTH_SHORT).show();
                                } else if (songDeletionHelper != null) {
                                    songDeletionHelper.deleteSong(path, () -> {
                                        if (!isFinishing() && !isDestroyed()) {
                                            loadGroupSongs(groupType, groupName);
                                        }
                                    });
                                }
                                return true;
                            }
                            return false;
                        });
                        popupMenu.show();
                    });
                }

                AlbumArtLoader loader = AlbumArtLoader.getInstance();
                Bitmap cachedBitmap = loader.getCachedThumb(path);
                if (cachedBitmap != null) {
                    img.setImageBitmap(cachedBitmap);
                } else {
                    img.setImageResource(R.drawable.ic_music);
                    if (!loader.hasNoArt(path)) {
                        loader.loadThumbArt(path, bitmap -> {
                            if (bitmap != null && !isFinishing() && !isDestroyed() && path.equals(img.getTag())) {
                                img.setImageBitmap(bitmap);
                            }
                        });
                    }
                }

                return v;
            }
        };
        listView.setAdapter(songsAdapter);
        AutoHideScrollHelper.attach(listView, this, null);

        listView.setOnItemClickListener((parent, view, position, id) ->
                MusicService.startPlaybackList(this, songList, position));
    }

    @Override
    public void setBarsVisible(boolean visible) {
        if (areBarsVisible == visible) return;
        areBarsVisible = visible;
        AutoHideScrollHelper.animateTopBarCollapse(toolbar, visible);
        if (layoutPlaylistTopActions != null && "playlist".equals(groupType) && !allSongList.isEmpty()) {
            AutoHideScrollHelper.animateTopBarCollapse(layoutPlaylistTopActions, visible);
        }
    }

    private void showAddToPlaylistDialog(String songPath) {
        ArrayList<String> single = new ArrayList<>();
        single.add(songPath);
        showAddMultipleToPlaylistDialog(single);
    }

    private void showAddMultipleToPlaylistDialog(ArrayList<String> songPaths) {
        PlaylistManager playlistManager = new PlaylistManager(this);
        ArrayList<String> playlists = playlistManager.getPlaylists();
        ArrayList<String> options = new ArrayList<>();
        options.add("+ Create New Playlist");
        options.addAll(playlists);

        String[] playlistArray = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Add to Playlist")
                .setItems(playlistArray, (dialog, which) -> {
                    if (which == 0) {
                        final EditText input = new EditText(this);
                        input.setHint("Playlist name");
                        new AlertDialog.Builder(this)
                                .setTitle("Create New Playlist")
                                .setView(input)
                                .setPositiveButton("Create & Add", (d, w) -> {
                                    String name = input.getText().toString().trim();
                                    if (!name.isEmpty()) {
                                        playlistManager.createPlaylist(name);
                                        playlistManager.addSongsToPlaylist(name, songPaths);
                                        Toast.makeText(this, songPaths.size() + " added to " + name, Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else {
                        String selectedPlaylist = playlistArray[which];
                        playlistManager.addSongsToPlaylist(selectedPlaylist, songPaths);
                        Toast.makeText(this, songPaths.size() + " added to " + selectedPlaylist, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
