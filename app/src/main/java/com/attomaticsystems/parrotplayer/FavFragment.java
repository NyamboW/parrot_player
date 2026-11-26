package com.attomaticsystems.parrotplayer;

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
import android.widget.SectionIndexer;
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
        private String lastSongPath = null;
        private boolean lastPlaying = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (adapter == null || !isAdded() || intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED.equals(action)) {
                adapter.notifyDataSetChanged();
            } else if (MusicService.BROADCAST_UPDATE_UI.equals(action)) {
                String currentPath = MusicService.getActiveSongPath();
                boolean currentPlaying = MusicService.isActivelyPlaying();
                boolean pathChanged = (currentPath == null) ? (lastSongPath != null) : !currentPath.equals(lastSongPath);
                if (pathChanged || currentPlaying != lastPlaying) {
                    lastSongPath = currentPath;
                    lastPlaying = currentPlaying;
                    adapter.notifyDataSetChanged();
                }
            }
        }
    };

    private ExecutorService executorService;
    private HashMap<String, String> artistCache = new HashMap<>();

    private static final String KEY_FAV_SORT_MODE = "fav_sort_mode";
    private static final String[] FAV_SORT_LABELS = new String[]{
            "Date Added (Newest First)",
            "Title (A – Z)",
            "Title (Z – A)",
            "Folder Path (A – Z)"
    };
    private int currentFavSortMode = 0; // 0: Date, 1: A-Z, 2: Z-A, 3: Folder
    
    // Search
    private ArrayList<String> filteredFavSongs = new ArrayList<>();
    private String[] filteredDisplayNames;
    
    // Maintain current search query to apply when loading songs
    private String currentQuery = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        favManager = new FavouritesManager(requireContext());
        executorService = Executors.newFixedThreadPool(2);
        songDeletionHelper = SongDeletionHelper.forFragment(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fav, container, false);

        favListView = view.findViewById(R.id.favListView);
        favListView.setFastScrollEnabled(true);
        txtEmpty = view.findViewById(R.id.txtEmpty);
        currentFavSortMode = requireContext()
                .getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_FAV_SORT_MODE, 0);
        
        // Search views removed from layout, handled via OptionsMenu
        
        setupMultiSelect();
        
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ToolbarSearchHelper.attachFragmentSearchMenu(
                this,
                R.menu.menu,
                "Search favourites...",
                query -> {
                    String next = query != null ? query : "";
                    if (next.equals(currentQuery)) return;
                    currentQuery = next;
                    filterFavSongs(currentQuery);
                },
                this::showFavSortDialog
        );
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
                    MusicService.startPlaybackList(requireActivity(), selectedPaths, 0);
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
                if (currentFavSortMode == 1 || currentFavSortMode == 2) { // A-Z or Z-A
                    String name1 = path1.substring(path1.lastIndexOf("/") + 1).toLowerCase();
                    String name2 = path2.substring(path2.lastIndexOf("/") + 1).toLowerCase();
                    int cmp = name1.compareTo(name2);
                    return currentFavSortMode == 2 ? -cmp : cmp;
                } else if (currentFavSortMode == 3) { // By Folder
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
    
    private abstract static class IndexedFavAdapter extends BaseAdapter implements SectionIndexer {}

    private void setFavAdapter() {
        final AlphabetSectionIndexer indexer = new AlphabetSectionIndexer(filteredDisplayNames);
        adapter = new IndexedFavAdapter() {
            @Override
            public Object[] getSections() {
                return indexer.getSections();
            }

            @Override
            public int getPositionForSection(int sectionIndex) {
                return indexer.getPositionForSection(sectionIndex);
            }

            @Override
            public int getSectionForPosition(int position) {
                return indexer.getSectionForPosition(position);
            }

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
                            } else if (SongMenuActionsHelper.handleCommonMenuAction(requireContext(), item.getItemId(), path)) {
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
        SongRowSwipeHelper.attach(favListView, requireContext(), new SongRowSwipeHelper.SongPathProvider() {
            @Override
            public String getSongPathAt(int position) {
                return (position >= 0 && position < filteredFavSongs.size()) ? filteredFavSongs.get(position) : null;
            }

            @Override
            public boolean isSwipeAllowed() {
                return !isMultiSelectMode;
            }
        }, this::loadFavourites);

        AutoHideScrollHelper.attach(favListView, getActivity(), new AbsListView.OnScrollListener() {
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

            MusicService.startPlaybackList(requireActivity(), favSongs, actualPosition);
        });
    }

    private void deleteSong(String path) {
        if (songDeletionHelper != null) {
            songDeletionHelper.deleteSong(path, () -> {
                if (isAdded()) loadFavourites();
            });
        }
    }

    private void showFavSortDialog() {
        if (!isAdded()) return;
        ParrotModalHelper.showSingleChoiceDialog(
                requireContext(),
                "Sort Favourites By",
                FAV_SORT_LABELS,
                currentFavSortMode,
                which -> {
                    currentFavSortMode = which;
                    requireContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putInt(KEY_FAV_SORT_MODE, currentFavSortMode)
                            .apply();
                    loadFavourites();
                }
        );
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
        if (!isAdded()) return;
        ParrotModalHelper.showAddToPlaylistDialog(requireContext(), songPaths, null);
    }
}