package com.attomaticsystems.parrotplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Tab fragment for smart track categories: Most Played, Recently Played, and Recently Added.
 */
public class SmartCategoryFragment extends Fragment {

    private static final String ARG_SMART_ID = "arg_smart_id";

    private String smartId = ListeningStatsManager.SMART_MOST_PLAYED;
    private ListView listView;
    private TextView txtEmpty;
    private FavouritesManager favManager;
    private SongDeletionHelper songDeletionHelper;

    private final ArrayList<String> allSongs = new ArrayList<>();
    private String[] allDisplayNames = new String[0];
    private final ArrayList<String> filteredSongs = new ArrayList<>();
    private String[] filteredDisplayNames = new String[0];
    private String currentQuery = "";
    private BaseAdapter adapter;
    private boolean isMultiSelectMode = false;

    public static SmartCategoryFragment newInstance(String smartId) {
        SmartCategoryFragment fragment = new SmartCategoryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SMART_ID, smartId);
        fragment.setArguments(args);
        return fragment;
    }

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        private String lastSongPath = null;
        private boolean lastPlaying = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isAdded() || intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (PermissionHelper.ACTION_PERMISSIONS_GRANTED.equals(action)
                    || MusicFolderHelper.ACTION_FOLDERS_UPDATED.equals(action)) {
                loadCategorySongs();
            } else if (AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED.equals(action)) {
                if (adapter != null) adapter.notifyDataSetChanged();
            } else if (MusicService.BROADCAST_UPDATE_UI.equals(action)) {
                String currentPath = MusicService.getActiveSongPath();
                boolean currentPlaying = MusicService.isActivelyPlaying();
                boolean pathChanged = (currentPath == null) ? (lastSongPath != null) : !currentPath.equals(lastSongPath);
                if (pathChanged) {
                    lastSongPath = currentPath;
                    lastPlaying = currentPlaying;
                    loadCategorySongs();
                } else if (currentPlaying != lastPlaying) {
                    lastPlaying = currentPlaying;
                    if (adapter != null) adapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            smartId = getArguments().getString(ARG_SMART_ID, ListeningStatsManager.SMART_MOST_PLAYED);
        }
        favManager = new FavouritesManager(requireContext());
        songDeletionHelper = SongDeletionHelper.forFragment(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fav, container, false);
        listView = view.findViewById(R.id.favListView);
        listView.setFastScrollEnabled(true);
        txtEmpty = view.findViewById(R.id.txtEmpty);
        setupMultiSelect();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ToolbarSearchHelper.attachFragmentSearchMenu(
                this,
                R.menu.menu_search,
                "Search " + getCategoryTitle().toLowerCase(Locale.ROOT) + "...",
                query -> {
                    String next = query != null ? query : "";
                    if (next.equals(currentQuery)) return;
                    currentQuery = next;
                    applyFilter();
                },
                null
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_UPDATE_UI);
        filter.addAction(AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED);
        filter.addAction(PermissionHelper.ACTION_PERMISSIONS_GRANTED);
        filter.addAction(MusicFolderHelper.ACTION_FOLDERS_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(updateReceiver, filter);
        }
        loadCategorySongs();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(updateReceiver);
        } catch (Exception ignored) {}
    }

    private String getCategoryTitle() {
        if (ListeningStatsManager.SMART_MOST_PLAYED.equals(smartId)) {
            return "Most Played";
        } else if (ListeningStatsManager.SMART_RECENTLY_PLAYED.equals(smartId)) {
            return "Recently Played";
        } else {
            return "Recently Added";
        }
    }

    private String getEmptyMessage() {
        if (ListeningStatsManager.SMART_MOST_PLAYED.equals(smartId)) {
            return "No most-played tracks yet. Play some songs and your top tracks will appear here.";
        } else if (ListeningStatsManager.SMART_RECENTLY_PLAYED.equals(smartId)) {
            return "No recently played tracks yet. Songs you listen to will appear here.";
        } else {
            return "No recently added tracks found in your selected music folders.";
        }
    }

    private void loadCategorySongs() {
        if (!isAdded()) return;
        allSongs.clear();
        allSongs.addAll(ListeningStatsManager.getSmartPlaylistSongs(requireContext(), smartId));

        allDisplayNames = new String[allSongs.size()];
        for (int i = 0; i < allSongs.size(); i++) {
            String fullPath = allSongs.get(i);
            String name = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            allDisplayNames[i] = name.replaceAll("(?i)\\.(mp3|wav|m4a|flac|ogg|aac)$", "");
        }

        applyFilter();
    }

    private void applyFilter() {
        filteredSongs.clear();
        ArrayList<String> names = new ArrayList<>();
        String q = currentQuery.trim().toLowerCase(Locale.ROOT);

        for (int i = 0; i < allSongs.size(); i++) {
            String path = allSongs.get(i);
            String name = i < allDisplayNames.length ? allDisplayNames[i] : "";
            if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q)
                    || path.toLowerCase(Locale.ROOT).contains(q)) {
                filteredSongs.add(path);
                names.add(name);
            }
        }
        filteredDisplayNames = names.toArray(new String[0]);

        if (filteredSongs.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            txtEmpty.setText(q.isEmpty() ? getEmptyMessage() : "No results found");
            listView.setVisibility(View.GONE);
            return;
        }

        txtEmpty.setVisibility(View.GONE);
        listView.setVisibility(View.VISIBLE);
        bindAdapter();
    }

    private abstract static class IndexedCategoryAdapter extends BaseAdapter implements SectionIndexer {}

    private void bindAdapter() {
        final AlphabetSectionIndexer indexer = new AlphabetSectionIndexer(filteredDisplayNames);
        adapter = new IndexedCategoryAdapter() {
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
            public Object getItem(int position) {
                return filteredSongs.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView != null
                        ? convertView
                        : getLayoutInflater().inflate(R.layout.list_items, parent, false);

                TextView txt = v.findViewById(R.id.txtSong);
                TextView txtArtist = v.findViewById(R.id.txtartist);
                TextView txtDuration = v.findViewById(R.id.txtSongDuration);
                ImageView img = v.findViewById(R.id.songImg);
                ImageView btnMore = v.findViewById(R.id.btnMore);
                ImageView imgCheckMark = v.findViewById(R.id.imgCheckMark);

                String path = filteredSongs.get(position);
                txt.setText(filteredDisplayNames[position]);

                boolean isChecked = isMultiSelectMode && listView.isItemChecked(position);
                if (isMultiSelectMode) {
                    if (btnMore != null) btnMore.setVisibility(View.GONE);
                    if (imgCheckMark != null) {
                        imgCheckMark.setVisibility(View.VISIBLE);
                        imgCheckMark.setImageResource(isChecked
                                ? R.drawable.ic_check_circle_filled
                                : R.drawable.ic_check_circle_outline);
                        imgCheckMark.setImageTintList(isChecked
                                ? ColorStateList.valueOf(AccentColorHelper.getAccentColor(requireContext()))
                                : null);
                    }
                } else {
                    if (btnMore != null) btnMore.setVisibility(View.VISIBLE);
                    if (imgCheckMark != null) imgCheckMark.setVisibility(View.GONE);
                }

                img.setTag(path);
                NowPlayingBarsView.bindRow(v, path, txt);
                TrackMetadataHelper.bindDuration(txtDuration, path);

                final int playCount = ListeningStatsManager.SMART_MOST_PLAYED.equals(smartId)
                        ? ListeningStatsManager.getPlayCount(requireContext(), path)
                        : 0;

                if (txtArtist != null) {
                    txtArtist.setText(playCount > 0
                            ? (playCount + (playCount == 1 ? " play" : " plays"))
                            : "Unknown Artist");
                }
                TrackMetadataHelper.loadTrackInfoAsync(path, info -> {
                    if (isAdded() && path.equals(img.getTag()) && info != null) {
                        bindSubtitle(txtArtist, info, playCount);
                    }
                });

                if (btnMore != null) {
                    btnMore.setOnClickListener(anchor -> showSongPopupMenu(anchor, path));
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

                return v;
            }
        };

        listView.setAdapter(adapter);
        SongRowSwipeHelper.attach(listView, requireContext(), new SongRowSwipeHelper.SongPathProvider() {
            @Override
            public String getSongPathAt(int position) {
                return (position >= 0 && position < filteredSongs.size()) ? filteredSongs.get(position) : null;
            }

            @Override
            public boolean isSwipeAllowed() {
                return !isMultiSelectMode;
            }
        }, null);

        AutoHideScrollHelper.attach(listView, getActivity(), new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                    hideKeyboard(view);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            hideKeyboard(listView);
            if (position < 0 || position >= filteredSongs.size()) return;
            String tappedPath = filteredSongs.get(position);
            int actualPosition = allSongs.indexOf(tappedPath);
            if (actualPosition == -1) actualPosition = position;
            MusicService.startPlaybackList(requireActivity(), allSongs, actualPosition);
        });
    }

    private void bindSubtitle(TextView txtArtist, TrackMetadataHelper.TrackInfo info, int playCount) {
        if (txtArtist != null) {
            String base = info.artist != null && !info.artist.isEmpty() ? info.artist : "Unknown Artist";
            if (playCount > 0) {
                base = base + " • " + playCount + (playCount == 1 ? " play" : " plays");
            }
            txtArtist.setText(base);
        }
    }

    private void showSongPopupMenu(View anchor, final String path) {
        PopupMenu popupMenu = new PopupMenu(getContext(), anchor);
        popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());
        if (favManager.isFavourite(path)) {
            popupMenu.getMenu().findItem(R.id.action_fav).setTitle("Remove from Favourites");
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_fav) {
                if (favManager.isFavourite(path)) {
                    favManager.removeFavourite(path);
                    Toast.makeText(getContext(), "Removed from Favourites", Toast.LENGTH_SHORT).show();
                } else {
                    favManager.addFavourite(path);
                    Toast.makeText(getContext(), "Added to Favourites", Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (id == R.id.action_delete) {
                if (songDeletionHelper != null) {
                    songDeletionHelper.deleteSong(path, () -> {
                        if (isAdded()) loadCategorySongs();
                    });
                }
                return true;
            } else if (id == R.id.action_play_next) {
                Intent intent = new Intent(getContext(), MusicService.class);
                intent.setAction(MusicService.ACTION_PLAY_NEXT);
                intent.putExtra("songPath", path);
                requireContext().startService(intent);
                Toast.makeText(getContext(), "Will play next", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.action_add_to_queue) {
                Intent intent = new Intent(getContext(), MusicService.class);
                intent.setAction(MusicService.ACTION_ADD_TO_QUEUE);
                intent.putExtra("songPath", path);
                requireContext().startService(intent);
                Toast.makeText(getContext(), "Added to queue", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.action_add_to_playlist) {
                ArrayList<String> single = new ArrayList<>();
                single.add(path);
                ParrotModalHelper.showAddToPlaylistDialog(requireContext(), single, null);
                return true;
            } else if (SongMenuActionsHelper.handleCommonMenuAction(requireContext(), id, path)) {
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void setupMultiSelect() {
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                mode.setTitle(listView.getCheckedItemCount() + " selected");
                if (adapter != null) adapter.notifyDataSetChanged();
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_cab_songs, menu);
                isMultiSelectMode = true;
                if (adapter != null) adapter.notifyDataSetChanged();
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
                    if (adapter != null) adapter.notifyDataSetChanged();
                    return true;
                }

                SparseBooleanArray checked = listView.getCheckedItemPositions();
                ArrayList<String> selectedPaths = new ArrayList<>();
                for (int i = 0; i < checked.size(); i++) {
                    int pos = checked.keyAt(i);
                    if (checked.valueAt(i) && pos >= 0 && pos < filteredSongs.size()) {
                        selectedPaths.add(filteredSongs.get(pos));
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
                    ParrotModalHelper.showAddToPlaylistDialog(requireContext(), selectedPaths, null);
                    mode.finish();
                    return true;
                } else if (id == R.id.action_fav) {
                    favManager.addFavourites(selectedPaths);
                    Toast.makeText(getContext(), selectedPaths.size() + " added to Favourites", Toast.LENGTH_SHORT).show();
                    mode.finish();
                    return true;
                } else if (id == R.id.action_delete) {
                    mode.finish();
                    if (songDeletionHelper != null) {
                        songDeletionHelper.deleteSongs(selectedPaths, () -> {
                            if (isAdded()) loadCategorySongs();
                        });
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                isMultiSelectMode = false;
                if (adapter != null) adapter.notifyDataSetChanged();
            }
        });
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
}
