package com.example.parrot;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.provider.MediaStore;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.widget.AbsListView;
import android.view.ActionMode;
import android.util.SparseBooleanArray;
import android.app.AlertDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FavFragment extends Fragment {

    private ListView favListView;
    private TextView txtEmpty;
    private FavouritesManager favManager;
    private ArrayList<String> favSongs = new ArrayList<>();
    private String[] displayNames;
    private BaseAdapter adapter;

    private ExecutorService executorService;
    private HashMap<String, String> artistCache = new HashMap<>();

    private int currentFavSortMode = 0; // 0: Date, 1: A-Z, 2: Folder
    
    // Search
    private ArrayList<String> filteredFavSongs = new ArrayList<>();
    private String[] filteredDisplayNames;
    
    // Maintain current search query to apply when loading songs
    private String currentQuery = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        favManager = new FavouritesManager(requireContext());
        executorService = Executors.newFixedThreadPool(2);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fav, container, false);

        favListView = view.findViewById(R.id.favListView);
        txtEmpty = view.findViewById(R.id.txtEmpty);
        
        // Search views removed from layout, handled via OptionsMenu
        
        setupMultiSelect();
        
        return view;
    }

    private void setupMultiSelect() {
        favListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        favListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                int count = favListView.getCheckedItemCount();
                mode.setTitle(count + " selected");
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_cab_songs, menu);
                // "Add to Favourites" action doesn't make sense in FavFragment, maybe rename to Remove?
                // Let's just leave it as it works toggling it, but it's redundant.
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                SparseBooleanArray checked = favListView.getCheckedItemPositions();
                ArrayList<String> selectedPaths = new ArrayList<>();
                for (int i = 0; i < checked.size(); i++) {
                    int pos = checked.keyAt(i);
                    if (checked.valueAt(i)) {
                        selectedPaths.add(filteredFavSongs.get(pos));
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
                    // For Favourites tab, maybe remove from favourites instead?
                    for (String p : selectedPaths) {
                        favManager.removeFavourite(p);
                    }
                    Toast.makeText(getContext(), selectedPaths.size() + " removed from Favourites", Toast.LENGTH_SHORT).show();
                    loadFavourites();
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
                            loadFavourites();
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

    @Override
    public void onResume() {
        super.onResume();
        loadFavourites();
    }

    private void loadFavourites() {
        favSongs.clear();

        // Only keep songs that still exist on disk
        ArrayList<String> allFavs = favManager.getFavourites();
        for (String path : allFavs) {
            File f = new File(path);
            if (f.exists()) {
                favSongs.add(path);
            }
        }
        
        Collections.sort(favSongs, new Comparator<String>() {
            @Override
            public int compare(String path1, String path2) {
                if (currentFavSortMode == 1) { // A-Z
                    String name1 = path1.substring(path1.lastIndexOf("/") + 1).toLowerCase();
                    String name2 = path2.substring(path2.lastIndexOf("/") + 1).toLowerCase();
                    return name1.compareTo(name2);
                } else if (currentFavSortMode == 2) { // By Folder
                    return path1.compareToIgnoreCase(path2);
                } else { // Date Added
                    long time1 = new File(path1).lastModified();
                    long time2 = new File(path2).lastModified();
                    return Long.compare(time2, time1); // Descending
                }
            }
        });

        if (favSongs.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            favListView.setVisibility(View.GONE);
            return;
        }

        txtEmpty.setVisibility(View.GONE);
        favListView.setVisibility(View.VISIBLE);

        displayNames = new String[favSongs.size()];
        for (int i = 0; i < favSongs.size(); i++) {
            String fullPath = favSongs.get(i);
            String name = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            displayNames[i] = name.replace(".mp3", "").replace(".wav", "").replace(".m4a", "").replace(".flac", "");
        }
        
        // Apply current search filter
        if (currentQuery.isEmpty()) {
            filteredFavSongs = new ArrayList<>(favSongs);
            filteredDisplayNames = displayNames.clone();
        } else {
            filterFavSongs(currentQuery);
            return; // filterFavSongs calls setFavAdapter
        }

        setFavAdapter();
    }
    
    private void filterFavSongs(String query) {
        filteredFavSongs.clear();
        ArrayList<String> tempNames = new ArrayList<>();
        
        if (query.isEmpty()) {
            filteredFavSongs.addAll(favSongs);
            if (displayNames != null) {
                for (String name : displayNames) tempNames.add(name);
            }
        } else {
            String lowerQuery = query.toLowerCase();
            for (int i = 0; i < favSongs.size(); i++) {
                if (displayNames == null || i >= displayNames.length) break;
                boolean matchName = displayNames[i].toLowerCase().contains(lowerQuery);
                String artist = artistCache.get(favSongs.get(i));
                boolean matchArtist = artist != null && artist.toLowerCase().contains(lowerQuery);
                if (matchName || matchArtist) {
                    filteredFavSongs.add(favSongs.get(i));
                    tempNames.add(displayNames[i]);
                }
            }
        }
        
        filteredDisplayNames = tempNames.toArray(new String[0]);
        
        if (filteredFavSongs.isEmpty() && !query.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            txtEmpty.setText("No results found");
            favListView.setVisibility(View.GONE);
        } else if (favSongs.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            txtEmpty.setText("No favourites yet. Tap the heart icon on a song to add it here.");
            favListView.setVisibility(View.GONE);
        } else {
            txtEmpty.setVisibility(View.GONE);
            favListView.setVisibility(View.VISIBLE);
            setFavAdapter();
        }
    }
    
    private void setFavAdapter() {
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return filteredDisplayNames.length;
            }

            @Override
            public Object getItem(int i) {
                return filteredFavSongs.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convertView, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_items, null);
                TextView txt = v.findViewById(R.id.txtSong);
                TextView txtArtist = v.findViewById(R.id.txtartist);
                ImageView img = v.findViewById(R.id.songImg);
                ImageView btnMore = v.findViewById(R.id.btnMore);

                txt.setText(filteredDisplayNames[i]);

                String path = filteredFavSongs.get(i);
                img.setTag(path);
                
                if (artistCache.containsKey(path)) {
                    if (txtArtist != null) txtArtist.setText(artistCache.get(path));
                }
                
                if (btnMore != null) {
                    btnMore.setOnClickListener(view1 -> {
                        PopupMenu popupMenu = new PopupMenu(getContext(), view1);
                        popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());
                        
                        popupMenu.getMenu().findItem(R.id.action_fav).setTitle("Remove from Favourites");
                        
                        popupMenu.setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == R.id.action_fav) {
                                favManager.removeFavourite(path);
                                loadFavourites();
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

                AlbumArtLoader loader = AlbumArtLoader.getInstance();
                Bitmap cachedBitmap = loader.getCachedThumb(path);
                if (cachedBitmap != null) {
                    img.setImageBitmap(cachedBitmap);
                } else {
                    img.setImageResource(R.drawable.ic_music);
                    if (!loader.hasNoArt(path)) {
                        loader.loadThumbArt(path, bitmap -> {
                            if (bitmap != null && isAdded() && path.equals(img.getTag())) {
                                img.setImageBitmap(bitmap);
                            }
                        });
                    }
                }

                if (!artistCache.containsKey(path)) {
                    executorService.execute(() -> {
                        String artist = "Unknown Artist";
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(path);
                            String extracted = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                            if (extracted != null && !extracted.isEmpty()) {
                                artist = extracted;
                            }
                        } catch (Exception ignored) {
                        } finally {
                            try {
                                retriever.release();
                            } catch (Exception ignored) {}
                        }

                        final String finalArtist = artist;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                artistCache.put(path, finalArtist);
                                if (path.equals(img.getTag()) && txtArtist != null) {
                                    txtArtist.setText(finalArtist);
                                }
                            });
                        }
                    });
                }

                return v;
            }
        };

        favListView.setAdapter(adapter);

        favListView.setOnItemClickListener((parent, view, position, id) -> {
            // Use filtered list for playback but pass correct position
            String tappedPath = filteredFavSongs.get(position);
            int actualPosition = favSongs.indexOf(tappedPath);
            if (actualPosition == -1) actualPosition = position;
            
            AlbumArtLoader.getInstance().preloadFullArt(tappedPath);
            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putStringArrayListExtra("songs", favSongs);
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
                
                loadFavourites();
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
                searchView.setIconifiedByDefault(false);
                searchView.setFocusable(true);
                searchView.requestFocusFromTouch();
                searchView.setQueryHint("Search favourites...");
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        currentQuery = newText;
                        filterFavSongs(newText);
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