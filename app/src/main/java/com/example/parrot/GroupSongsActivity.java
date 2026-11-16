package com.example.parrot;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.AbsListView;
import android.view.ActionMode;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.util.ArrayList;

public class GroupSongsActivity extends AppCompatActivity {

    private ListView listView;
    private ArrayList<String> songList = new ArrayList<>();
    private String[] items;
    private String[] artists;
    private FavouritesManager favManager;
    private BaseAdapter songsAdapter;
    private boolean isMultiSelectMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_songs);

        Toolbar toolbar = findViewById(R.id.toolbarGroup);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        listView = findViewById(R.id.listViewGroupSongs);
        favManager = new FavouritesManager(this);

        String type = getIntent().getStringExtra("type");
        String name = getIntent().getStringExtra("name");

        if (getSupportActionBar() != null && name != null) {
            getSupportActionBar().setTitle(name);
        }
        
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        setupMultiSelect();

        loadGroupSongs(type, name);
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
                    AlbumArtLoader.getInstance().preloadFullArt(selectedPaths.get(0));
                    Intent intent = new Intent(GroupSongsActivity.this, PlayerActivity.class);
                    intent.putStringArrayListExtra("songs", selectedPaths);
                    intent.putExtra("pos", 0);
                    startActivity(intent);
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
                    new AlertDialog.Builder(GroupSongsActivity.this)
                        .setTitle("Delete Songs")
                        .setMessage("Are you sure you want to delete " + selectedPaths.size() + " songs?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            int deletedCount = 0;
                            for (String path : selectedPaths) {
                                File file = new File(path);
                                if (file.exists() && file.delete()) {
                                    getContentResolver().delete(
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, 
                                        MediaStore.Audio.Media.DATA + "=?", new String[]{path});
                                    if (favManager.isFavourite(path)) {
                                        favManager.removeFavourite(path);
                                    }
                                    deletedCount++;
                                }
                            }
                            Toast.makeText(GroupSongsActivity.this, deletedCount + " songs deleted", Toast.LENGTH_SHORT).show();
                            loadGroupSongs(getIntent().getStringExtra("type"), getIntent().getStringExtra("name"));
                            mode.finish();
                        })
                        .setNegativeButton("No", null)
                        .show();
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
        songList.clear();
        ArrayList<String> artistList = new ArrayList<>();

        if ("playlist".equals(type)) {
            PlaylistManager pm = new PlaylistManager(this);
            ArrayList<String> playlistSongs = pm.getSongsInPlaylist(name);
            for (String path : playlistSongs) {
                File file = new File(path);
                if (file.exists()) {
                    songList.add(path);
                    artistList.add("<Unknown>"); // Can refine later
                }
            }
        } else {
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

                    String artist = "<Unknown>";
                    try {
                        artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                    } catch (Exception e) {}

                    songList.add(path);
                    artistList.add(artist);
                }
                cursor.close();
            }
        }

        items = new String[songList.size()];
        artists = new String[songList.size()];

        for (int i = 0; i < songList.size(); i++) {
            String fullPath = songList.get(i);
            String songName = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            items[i] = songName.replace(".mp3", "").replace(".wav", "");
            artists[i] = artistList.get(i);
        }

        setAdapter();
    }

    private void setAdapter() {
        songsAdapter = new BaseAdapter() {
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
                    }
                } else {
                    if (btnMore != null) btnMore.setVisibility(View.VISIBLE);
                    if (imgCheckMark != null) imgCheckMark.setVisibility(View.GONE);
                }
                
                String path = songList.get(i);
                img.setTag(path);
                
                if (btnMore != null) {
                    btnMore.setOnClickListener(view1 -> {
                        PopupMenu popupMenu = new PopupMenu(GroupSongsActivity.this, view1);
                        popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());
                        if (favManager.isFavourite(path)) {
                            popupMenu.getMenu().findItem(R.id.action_fav).setTitle("Remove from Favourites");
                        }
                        popupMenu.setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == R.id.action_fav) {
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

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < songList.size()) {
                AlbumArtLoader.getInstance().preloadFullArt(songList.get(position));
            }
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putStringArrayListExtra("songs", songList);
            intent.putExtra("pos", position);
            startActivity(intent);
        });
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
        new android.app.AlertDialog.Builder(this)
            .setTitle("Add to Playlist")
            .setItems(playlistArray, (dialog, which) -> {
                if (which == 0) {
                    final EditText input = new EditText(this);
                    input.setHint("Playlist name");
                    new android.app.AlertDialog.Builder(this)
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
