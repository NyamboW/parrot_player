package com.example.parrot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import androidx.appcompat.widget.SearchView;

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
    private boolean isMultiSelectMode = false;
    private SongDeletionHelper songDeletionHelper;

    private final BroadcastReceiver playbackStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (adapter != null && isAdded()) {
                adapter.notifyDataSetChanged();
            }
        }
    };

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
        songDeletionHelper = SongDeletionHelper.forFragment(this);
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
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_cab_songs, menu);
                MenuItem favItem = menu.findItem(R.id.action_fav);
                if (favItem != null) {
                    favItem.setTitle("Remove from Favourites");
                }
                isMultiSelectMode = true;
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
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
                    int total = favListView.getCount();
                    boolean selectAll = favListView.getCheckedItemCount() < total;
                    for (int idx = 0; idx < total; idx++) {
                        favListView.setItemChecked(idx, selectAll);
                    }
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    return true;
                }

                SparseBooleanArray checked = favListView.getCheckedItemPositions();
                ArrayList<String> selectedPaths = new ArrayList<>();
                for (int i = 0; i < checked.size(); i++) {
                    int pos = checked.keyAt(i);
                    if (checked.valueAt(i) && pos >= 0 && pos < filteredFavSongs.size()) {
                        selectedPaths.add(filteredFavSongs.get(pos));
                    }
                }
                if (selectedPaths.isEmpty()) {
                    mode.finish();
                    return true;
                }

                if (id == R.id.action_play_selected) {
                    AlbumArtLoader.getInstance().preloadFullArt(selectedPaths.get(0));
                    Intent intent = new Intent(getContext(), PlayerActivity.class);
                    intent.putStringArrayListExtra("songs", selectedPaths);
                    intent.putExtra("pos", 0);
                    startActivity(intent);
                    mode.finish();
                    return true;
                } else if (id == R.id.action_add_to_queue) {
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
                    for (String p : selectedPaths) {
                        favManager.removeFavourite(p);
                    }
                    Toast.makeText(getContext(), selectedPaths.size() + " removed from Favourites", Toast.LENGTH_SHORT).show();
                    loadFavourites();
                    mode.finish();
                    return true;
                } else if (id == R.id.action_delete) {
                    mode.finish();
                    if (songDeletionHelper != null) {
                        songDeletionHelper.deleteSongs(selectedPaths, () -> {
                            if (isAdded()) loadFavourites();
                        });
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                isMultiSelectMode = false;
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_UPDATE_UI);
        filter.addAction(AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(playbackStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(playbackStateReceiver, filter);
        }
        loadFavourites();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(playbackStateReceiver);
        } catch (Exception ignored) {}
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
                ImageView imgCheckMark = v.findViewById(R.id.imgCheckMark);

                txt.setText(filteredDisplayNames[i]);

                boolean isChecked = isMultiSelectMode && favListView.isItemChecked(i);
                if (isMultiSelectMode) {
                    if (btnMore != null) btnMore.setVisibility(View.GONE);
                    if (imgCheckMark != null) {
                        imgCheckMark.setVisibility(View.VISIBLE);
                        imgCheckMark.setImageResource(isChecked
                                ? R.drawable.ic_check_circle_filled
                                : R.drawable.ic_check_circle_outline);
                        imgCheckMark.setImageTintList(isChecked
                                ? android.content.res.ColorStateList.valueOf(
                                        AccentColorHelper.getAccentColor(requireContext()))
                                : null);
                    }
                } else {
                    if (btnMore != null) btnMore.setVisibility(View.VISIBLE);
                    if (imgCheckMark != null) imgCheckMark.setVisibility(View.GONE);
                }

                String path = filteredFavSongs.get(i);
                img.setTag(path);
                NowPlayingBarsView.bindRow(v, path, txt);
                
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

        favListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                    hideKeyboard(view);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}
        });

        favListView.setOnItemClickListener((parent, view, position, id) -> {
            hideKeyboard(favListView);
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
        if (songDeletionHelper != null) {
            songDeletionHelper.deleteSong(path, () -> {
                if (isAdded()) loadFavourites();
            });
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu, menu);
        MenuItem searchItem = menu.findItem(R.id.item_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setIconifiedByDefault(true);
                searchView.setMaxWidth(Integer.MAX_VALUE);
                searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
                searchView.setQueryHint("Search favourites...");

                EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
                if (searchEditText != null) {
                    searchEditText.setTextColor(android.graphics.Color.WHITE);
                    searchEditText.setHintTextColor(android.graphics.Color.parseColor("#8A93A2"));
                }
                View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
                if (searchPlate != null) {
                    searchPlate.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }

                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        searchView.postDelayed(() -> {
                            if (!isAdded()) return;
                            searchView.requestFocus();
                            View target = searchEditText != null ? searchEditText : searchView;
                            target.requestFocus();
                            InputMethodManager imm = (InputMethodManager) requireContext()
                                    .getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
                            }
                        }, 120);
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        currentQuery = "";
                        searchView.setQuery("", false);
                        filterFavSongs("");
                        searchView.clearFocus();
                        hideKeyboard(searchView);
                        return true;
                    }
                });

                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        searchView.clearFocus();
                        hideKeyboard(searchView);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        currentQuery = newText != null ? newText : "";
                        filterFavSongs(currentQuery);
                        return true;
                    }
                });
            }
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void hideKeyboard(View anchor) {
        if (!isAdded()) return;
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View focused = getActivity() != null ? getActivity().getCurrentFocus() : null;
            View target = focused != null ? focused : anchor;
            if (target != null) {
                imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
                target.clearFocus();
            }
        }
    }

    private void showAddToPlaylistDialog(String songPath) {
        ArrayList<String> single = new ArrayList<>();
        single.add(songPath);
        showAddMultipleToPlaylistDialog(single);
    }

    private void showAddMultipleToPlaylistDialog(ArrayList<String> songPaths) {
        PlaylistManager playlistManager = new PlaylistManager(requireContext());
        ArrayList<String> playlists = playlistManager.getPlaylists();
        ArrayList<String> options = new ArrayList<>();
        options.add("+ Create New Playlist");
        options.addAll(playlists);

        String[] playlistArray = options.toArray(new String[0]);
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Add to Playlist")
            .setItems(playlistArray, (dialog, which) -> {
                if (which == 0) {
                    final EditText input = new EditText(requireContext());
                    input.setHint("Playlist name");
                    new android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Create New Playlist")
                        .setView(input)
                        .setPositiveButton("Create & Add", (d, w) -> {
                            String name = input.getText().toString().trim();
                            if (!name.isEmpty()) {
                                playlistManager.createPlaylist(name);
                                playlistManager.addSongsToPlaylist(name, songPaths);
                                Toast.makeText(getContext(), songPaths.size() + " added to " + name, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    String selectedPlaylist = playlistArray[which];
                    playlistManager.addSongsToPlaylist(selectedPlaylist, songPaths);
                    Toast.makeText(getContext(), songPaths.size() + " added to " + selectedPlaylist, Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }
}