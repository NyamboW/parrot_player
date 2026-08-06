package com.example.parrot;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.LruCache;
import java.io.File;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.Set;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.widget.AbsListView;
import android.view.ActionMode;
import android.util.SparseBooleanArray;
import android.app.AlertDialog;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Music_Fragment extends Fragment {

    ListView listView;
    String[] items;
    String[] artists;
    ArrayList<String> songList = new ArrayList<>();
    FavouritesManager favManager;
    
    // Search
    ArrayList<String> filteredSongList = new ArrayList<>();
    String[] filteredItems;
    String[] filteredArtists;
    
    // Maintain current search query to apply when loading songs
    String currentQuery = "";
    
    String currentSortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";
    
    private ExecutorService executorService;
    private LruCache<String, Bitmap> memoryCache;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        executorService = Executors.newFixedThreadPool(4);
        
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_music_, container, false);

        listView = view.findViewById(R.id.listView);
        favManager = new FavouritesManager(requireContext());
        
        setupMultiSelect();
        
        // Search views removed from layout, handled via OptionsMenu

        SwipeRefreshLayout refreshLayout = view.findViewById(R.id.pullToRefresh);
        refreshLayout.setOnRefreshListener(() -> {
            requestPermission();
            refreshLayout.setRefreshing(false);
        });

        requestPermission();

        return view;
    }

    private void setupMultiSelect() {
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                int count = listView.getCheckedItemCount();
                mode.setTitle(count + " selected");
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_cab_songs, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                SparseBooleanArray checked = listView.getCheckedItemPositions();
                ArrayList<String> selectedPaths = new ArrayList<>();
                for (int i = 0; i < checked.size(); i++) {
                    int pos = checked.keyAt(i);
                    if (checked.valueAt(i)) {
                        selectedPaths.add(filteredSongList.get(pos));
                    }
                }

                int id = item.getItemId();
                if (id == R.id.action_add_to_queue) {
                    Intent intent = new Intent(getContext(), MusicService.class);
                    intent.setAction(MusicService.ACTION_ADD_MULTIPLE_TO_QUEUE);
                    intent.putStringArrayListExtra("songPaths", selectedPaths);
                    requireContext().startService(intent);
                    Toast.makeText(getContext(), selectedPaths.size() + " added to queue", Toast.LENGTH_SHORT).show();
                    mode.finish();
                    return true;
                } else if (id == R.id.action_play_next) {
                    Intent intent = new Intent(getContext(), MusicService.class);
                    intent.setAction(MusicService.ACTION_PLAY_NEXT_MULTIPLE);
                    intent.putStringArrayListExtra("songPaths", selectedPaths);
                    requireContext().startService(intent);
                    Toast.makeText(getContext(), selectedPaths.size() + " will play next", Toast.LENGTH_SHORT).show();
                    mode.finish();
                    return true;
                } else if (id == R.id.action_add_to_playlist) {
                    showAddMultipleToPlaylistDialog(selectedPaths);
                    mode.finish();
                    return true;
                } else if (id == R.id.action_fav) {
                    favManager.addFavourites(selectedPaths);
                    Toast.makeText(getContext(), selectedPaths.size() + " added to Favourites", Toast.LENGTH_SHORT).show();
                    mode.finish();
                    return true;
                } else if (id == R.id.action_delete) {
                    new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Songs")
                        .setMessage("Are you sure you want to delete " + selectedPaths.size() + " songs?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            int deletedCount = 0;
                            for (String path : selectedPaths) {
                                File file = new File(path);
                                if (file.exists() && file.delete()) {
                                    requireContext().getContentResolver().delete(
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, 
                                        MediaStore.Audio.Media.DATA + "=?", new String[]{path});
                                    if (favManager.isFavourite(path)) {
                                        favManager.removeFavourite(path);
                                    }
                                    deletedCount++;
                                }
                            }
                            Toast.makeText(getContext(), deletedCount + " songs deleted", Toast.LENGTH_SHORT).show();
                            loadSongs();
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
            }
        });
    }

    private void requestPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            Dexter.withContext(requireContext())
                    .withPermissions(Manifest.permission.READ_MEDIA_AUDIO)
                    .withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport report) {
                            loadSongs();
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(
                                java.util.List<PermissionRequest> list,
                                PermissionToken token) {
                            token.continuePermissionRequest();
                        }
                    }).check();

        } else {

            Dexter.withContext(requireContext())
                    .withPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
                    .withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport report) {
                            loadSongs();
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(
                                java.util.List<PermissionRequest> list,
                                PermissionToken token) {
                            token.continuePermissionRequest();
                        }
                    }).check();
        }
    }

    private void loadSongs() {

        songList.clear();
        ArrayList<String> artistList = new ArrayList<>();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        
        SharedPreferences prefs = requireContext().getSharedPreferences("parrot_settings", Context.MODE_PRIVATE);
        Set<String> savedFolders = prefs.getStringSet(SettingsFragment.KEY_MUSIC_FOLDERS, null);

        Cursor cursor = requireContext().getContentResolver().query(
                uri,
                null,
                selection,
                null,
                currentSortOrder
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {

                String name = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                );

                String path = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                );
                
                File file = new File(path);
                String parent = file.getParent();
                if (savedFolders != null && !savedFolders.isEmpty()) {
                    if (parent == null || !savedFolders.contains(parent)) {
                        continue;
                    }
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

        items = new String[songList.size()];
        artists = new String[songList.size()];

        for (int i = 0; i < songList.size(); i++) {
            String fullPath = songList.get(i);
            String name = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            items[i] = name.replace(".mp3", "").replace(".wav", "");
            artists[i] = artistList.get(i);
        }
        
        // Apply current search filter
        if (currentQuery.isEmpty()) {
            filteredSongList = new ArrayList<>(songList);
            filteredItems = items.clone();
            filteredArtists = artists.clone();
        } else {
            filterSongs(currentQuery);
            return; // filterSongs calls setAdapter
        }

        setAdapter();
    }
    
    private void filterSongs(String query) {
        filteredSongList.clear();
        ArrayList<String> tempItems = new ArrayList<>();
        ArrayList<String> tempArtists = new ArrayList<>();
        
        if (query.isEmpty()) {
            filteredSongList.addAll(songList);
            for (String item : items) tempItems.add(item);
            for (String artist : artists) tempArtists.add(artist);
        } else {
            String lowerQuery = query.toLowerCase();
            for (int i = 0; i < songList.size(); i++) {
                boolean matchName = items[i].toLowerCase().contains(lowerQuery);
                boolean matchArtist = artists[i].toLowerCase().contains(lowerQuery);
                if (matchName || matchArtist) {
                    filteredSongList.add(songList.get(i));
                    tempItems.add(items[i]);
                    tempArtists.add(artists[i]);
                }
            }
        }
        
        filteredItems = tempItems.toArray(new String[0]);
        filteredArtists = tempArtists.toArray(new String[0]);
        
        setAdapter();
    }
    
    private void setAdapter() {
        listView.setAdapter(new BaseAdapter() {

            @Override
            public int getCount() {
                return filteredItems.length;
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @Override
            public View getView(int i, View view, ViewGroup parent) {

                View v = getLayoutInflater().inflate(R.layout.list_items, null);
                TextView txt = v.findViewById(R.id.txtSong);
                TextView txtArtist = v.findViewById(R.id.txtartist);
                ImageView img = v.findViewById(R.id.songImg);
                ImageView btnMore = v.findViewById(R.id.btnMore);
                
                txt.setText(filteredItems[i]);
                if (txtArtist != null) {
                    txtArtist.setText(filteredArtists[i]);
                }
                
                String path = filteredSongList.get(i);
                img.setTag(path);
                
                if (btnMore != null) {
                    btnMore.setOnClickListener(view1 -> {
                        PopupMenu popupMenu = new PopupMenu(getContext(), view1);
                        popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());
                        
                        if (favManager.isFavourite(path)) {
                            popupMenu.getMenu().findItem(R.id.action_fav).setTitle("Remove from Favourites");
                        }
                        
                        popupMenu.setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == R.id.action_fav) {
                                favManager.toggleFavourite(path);
                                Toast.makeText(getContext(), favManager.isFavourite(path) ? "Added to Favourites" : "Removed from Favourites", Toast.LENGTH_SHORT).show();
                                return true;
                            } else if (item.getItemId() == R.id.action_delete) {
                                deleteSong(path);
                                return true;
                            } else if (item.getItemId() == R.id.action_play_next) {
                                Intent intent = new Intent(getContext(), MusicService.class);
                                intent.setAction(MusicService.ACTION_PLAY_NEXT);
                                intent.putExtra("songPath", path);
                                requireContext().startService(intent);
                                Toast.makeText(getContext(), "Will play next", Toast.LENGTH_SHORT).show();
                                return true;
                            } else if (item.getItemId() == R.id.action_add_to_queue) {
                                Intent intent = new Intent(getContext(), MusicService.class);
                                intent.setAction(MusicService.ACTION_ADD_TO_QUEUE);
                                intent.putExtra("songPath", path);
                                requireContext().startService(intent);
                                Toast.makeText(getContext(), "Added to queue", Toast.LENGTH_SHORT).show();
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
                
                Bitmap cachedBitmap = memoryCache.get(path);
                if (cachedBitmap != null) {
                    img.setImageBitmap(cachedBitmap);
                } else {
                    img.setImageResource(R.drawable.ic_music);
                    executorService.execute(() -> {
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
                        if (finalBitmap != null) {
                            memoryCache.put(path, finalBitmap);
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (path.equals(img.getTag())) {
                                        img.setImageBitmap(finalBitmap);
                                    }
                                });
                            }
                        }
                    });
                }
                
                return v;
            }
        });

        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            // Pass the full song list but find the correct position for the tapped filtered song
            String tappedPath = filteredSongList.get(position);
            int actualPosition = songList.indexOf(tappedPath);
            if (actualPosition == -1) actualPosition = position;

            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putStringArrayListExtra("songs", songList);
            intent.putExtra("pos", actualPosition);
            startActivity(intent);
        });
    }

    private void deleteSong(String path) {
        File file = new File(path);
        try {
            if (file.exists() && file.delete()) {
                requireContext().getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, 
                    MediaStore.Audio.Media.DATA + "=?", new String[]{path});
                Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                
                if (favManager.isFavourite(path)) {
                    favManager.removeFavourite(path);
                }
                
                loadSongs();
            } else {
                Toast.makeText(getContext(), "Failed to delete. May require permission.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu, menu);
        MenuItem searchItem = menu.findItem(R.id.item_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint("Search songs...");
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        currentQuery = newText;
                        filterSongs(newText);
                        return true;
                    }
                });
            }
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void showAddToPlaylistDialog(String songPath) {
        PlaylistManager playlistManager = new PlaylistManager(requireContext());
        ArrayList<String> playlists = playlistManager.getPlaylists();
        if (playlists.isEmpty()) {
            Toast.makeText(getContext(), "No playlists available. Create one first.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] playlistArray = playlists.toArray(new String[0]);
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Add to Playlist")
            .setItems(playlistArray, (dialog, which) -> {
                String selectedPlaylist = playlistArray[which];
                playlistManager.addSongToPlaylist(selectedPlaylist, songPath);
                Toast.makeText(getContext(), "Added to " + selectedPlaylist, Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void showAddMultipleToPlaylistDialog(ArrayList<String> songPaths) {
        PlaylistManager playlistManager = new PlaylistManager(requireContext());
        ArrayList<String> playlists = playlistManager.getPlaylists();
        if (playlists.isEmpty()) {
            Toast.makeText(getContext(), "No playlists available. Create one first.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] playlistArray = playlists.toArray(new String[0]);
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Add to Playlist")
            .setItems(playlistArray, (dialog, which) -> {
                String selectedPlaylist = playlistArray[which];
                playlistManager.addSongsToPlaylist(selectedPlaylist, songPaths);
                Toast.makeText(getContext(), songPaths.size() + " songs added to " + selectedPlaylist, Toast.LENGTH_SHORT).show();
            })
            .show();
    }
}