package com.attomaticsystems.parrotplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

public class PlaylistsFragment extends Fragment {

    private static class PlaylistRowItem {
        final boolean isCreateCard;
        final boolean isSmartPlaylist;
        final String smartId;
        final String displayName;
        final String subtitlePrefix;

        PlaylistRowItem(boolean isCreateCard, boolean isSmartPlaylist,
                        String smartId, String displayName, String subtitlePrefix) {
            this.isCreateCard = isCreateCard;
            this.isSmartPlaylist = isSmartPlaylist;
            this.smartId = smartId;
            this.displayName = displayName;
            this.subtitlePrefix = subtitlePrefix;
        }
    }

    private ListView listView;
    private final ArrayList<PlaylistRowItem> allRowItems = new ArrayList<>();
    private final ArrayList<PlaylistRowItem> rowItems = new ArrayList<>();
    private BaseAdapter adapter;
    private String currentQuery = "";
    private PlaylistManager playlistManager;
    private ActivityResultLauncher<Intent> m3uImportLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        m3uImportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null && isAdded()) {
                            importM3uFromUri(uri);
                        }
                    }
                }
        );
    }

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        private String lastSeenSongPath = null;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isAdded() || intent == null || intent.getAction() == null) return;
            if (MusicService.BROADCAST_UPDATE_UI.equals(intent.getAction())) {
                String songPath = intent.getStringExtra("songPath");
                if (songPath != null && songPath.equals(lastSeenSongPath)) {
                    return;
                }
                lastSeenSongPath = songPath;
            }
            loadPlaylists();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        listView = view.findViewById(R.id.listView);
        playlistManager = new PlaylistManager(requireContext());
        loadPlaylists();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ToolbarSearchHelper.attachFragmentSearchMenu(
                this,
                R.menu.menu_search,
                "Search playlists...",
                this::filterPlaylists,
                null
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED);
        filter.addAction(PermissionHelper.ACTION_PERMISSIONS_GRANTED);
        filter.addAction(MusicFolderHelper.ACTION_FOLDERS_UPDATED);
        filter.addAction(MusicService.BROADCAST_UPDATE_UI);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(updateReceiver, filter);
        }
        loadPlaylists();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(updateReceiver);
        } catch (Exception ignored) {}
    }

    private void loadPlaylists() {
        allRowItems.clear();
        allRowItems.add(new PlaylistRowItem(true, false, null, "Create New Playlist", "Group your favourite tracks"));

        // User-created custom playlists
        for (String plName : playlistManager.getPlaylists()) {
            allRowItems.add(new PlaylistRowItem(false, false, null, plName, ""));
        }

        applyPlaylistFilter();

        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return rowItems.size();
            }

            @Override
            public Object getItem(int i) {
                return rowItems.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convertView, ViewGroup parent) {
                View v = convertView != null
                        ? convertView
                        : getLayoutInflater().inflate(R.layout.item_playlist_row, parent, false);

                LinearLayout card = v.findViewById(R.id.playlistCard);
                CardView coverCard = v.findViewById(R.id.playlistCoverCard);
                ImageView imgCover = v.findViewById(R.id.imgPlaylistCover);
                TextView txtName = v.findViewById(R.id.txtPlaylistName);
                TextView txtCount = v.findViewById(R.id.txtPlaylistCount);
                ImageView imgArrow = v.findViewById(R.id.imgPlaylistArrow);

                float density = getResources().getDisplayMetrics().density;
                int padPx = (int) (12 * density);
                int accent = AccentColorHelper.getAccentColor(requireContext());

                PlaylistRowItem item = rowItems.get(i);

                if (item.isCreateCard) {
                    card.setBackground(AccentColorHelper.createRoundedRect(
                            AccentColorHelper.withAlpha(accent, 0x1A),
                            AccentColorHelper.withAlpha(accent, 0x40),
                            (int) (1 * density),
                            14f * density));
                    coverCard.setCardBackgroundColor(AccentColorHelper.withAlpha(accent, 0x24));
                    imgCover.setTag(null);
                    imgCover.setPadding(padPx, padPx, padPx, padPx);
                    imgCover.setImageResource(R.drawable.ic_nav_playlist_add);
                    imgCover.setImageTintList(ColorStateList.valueOf(accent));
                    txtName.setText(item.displayName);
                    txtName.setTextColor(accent);
                    txtCount.setText(item.subtitlePrefix);
                    imgArrow.setVisibility(View.GONE);
                } else {
                    ArrayList<String> songs = item.isSmartPlaylist
                            ? ListeningStatsManager.getSmartPlaylistSongs(requireContext(), item.smartId)
                            : playlistManager.getSongsInPlaylist(item.displayName);

                    int validCount = 0;
                    String firstValidPath = null;
                    for (String path : songs) {
                        if (path != null && new File(path).exists()) {
                            if (firstValidPath == null) firstValidPath = path;
                            validCount++;
                        }
                    }

                    card.setBackgroundResource(R.drawable.card_surface_bg);
                    coverCard.setCardBackgroundColor(item.isSmartPlaylist
                            ? AccentColorHelper.withAlpha(accent, 0x1E)
                            : Color.parseColor("#1E2430"));
                    txtName.setText(item.displayName);
                    txtName.setTextColor(Color.parseColor("#F5F7FA"));
                    txtCount.setText(item.subtitlePrefix + validCount + (validCount == 1 ? " track" : " tracks"));
                    imgArrow.setVisibility(View.VISIBLE);

                    imgCover.setTag(firstValidPath);
                    if (firstValidPath != null) {
                        AlbumArtLoader loader = AlbumArtLoader.getInstance();
                        Bitmap cached = loader.getCachedThumb(firstValidPath);
                        if (cached != null) {
                            imgCover.setPadding(0, 0, 0, 0);
                            imgCover.setImageTintList(null);
                            imgCover.setImageBitmap(cached);
                        } else {
                            setDefaultPlaylistIcon(imgCover, padPx, item.isSmartPlaylist ? accent : Color.parseColor("#9AA3B2"));
                            if (!loader.hasNoArt(firstValidPath)) {
                                final String coverPath = firstValidPath;
                                loader.loadThumbArt(coverPath, bitmap -> {
                                    if (bitmap != null && isAdded() && coverPath.equals(imgCover.getTag())) {
                                        imgCover.setPadding(0, 0, 0, 0);
                                        imgCover.setImageTintList(null);
                                        imgCover.setImageBitmap(bitmap);
                                    }
                                });
                            }
                        }
                    } else {
                        setDefaultPlaylistIcon(imgCover, padPx, item.isSmartPlaylist ? accent : Color.parseColor("#9AA3B2"));
                    }
                }

                return v;
            }
        };
        listView.setAdapter(adapter);

        AutoHideScrollHelper.attach(listView, getActivity(), null);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= rowItems.size()) return;
            PlaylistRowItem item = rowItems.get(position);
            if (item.isCreateCard) {
                showCreatePlaylistDialog();
            } else if (item.isSmartPlaylist) {
                Intent intent = new Intent(getContext(), GroupSongsActivity.class);
                intent.putExtra("type", "smart_playlist");
                intent.putExtra("smart_id", item.smartId);
                intent.putExtra("name", item.displayName);
                startActivity(intent);
            } else {
                Intent intent = new Intent(getContext(), GroupSongsActivity.class);
                intent.putExtra("type", "playlist");
                intent.putExtra("name", item.displayName);
                startActivity(intent);
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= rowItems.size()) return false;
            PlaylistRowItem item = rowItems.get(position);
            if (item.isCreateCard) return false;
            if (item.isSmartPlaylist) {
                if (ListeningStatsManager.SMART_MOST_PLAYED.equals(item.smartId)
                        || ListeningStatsManager.SMART_RECENTLY_PLAYED.equals(item.smartId)) {
                    ParrotModalHelper.showConfirmDialog(
                            requireContext(),
                            "Reset " + item.displayName,
                            "Clear all tracked history for '" + item.displayName + "'?",
                            "Reset",
                            "Cancel",
                            true,
                            () -> {
                                ListeningStatsManager.clearSmartPlaylistStats(requireContext(), item.smartId);
                                loadPlaylists();
                                Toast.makeText(getContext(), item.displayName + " reset", Toast.LENGTH_SHORT).show();
                            },
                            null,
                            null
                    );
                    return true;
                }
                return false;
            }

            String playlistName = item.displayName;
            String[] options = new String[]{
                    "Export Playlist (.M3U)",
                    "Rename Playlist",
                    "Delete Playlist"
            };
            ParrotModalHelper.showSingleChoiceDialog(
                    requireContext(),
                    playlistName,
                    options,
                    -1,
                    which -> {
                        if (which == 0) {
                            File out = playlistManager.exportPlaylistToM3u(requireContext(), playlistName);
                            if (out != null) {
                                Toast.makeText(getContext(), "Exported to " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(), "Failed to export .m3u playlist", Toast.LENGTH_SHORT).show();
                            }
                        } else if (which == 1) {
                            showRenamePlaylistDialog(playlistName);
                        } else if (which == 2) {
                            ParrotModalHelper.showConfirmDialog(
                                    requireContext(),
                                    "Delete Playlist",
                                    "Are you sure you want to delete '" + playlistName + "'?",
                                    "Delete",
                                    "Cancel",
                                    true,
                                    () -> {
                                        playlistManager.deletePlaylist(playlistName);
                                        loadPlaylists();
                                        Toast.makeText(getContext(), "Playlist deleted", Toast.LENGTH_SHORT).show();
                                    },
                                    null,
                                    null
                            );
                        }
                    }
            );
            return true;
        });
    }

    private void filterPlaylists(String query) {
        String next = query != null ? query : "";
        if (next.equals(currentQuery)) return;
        currentQuery = next;
        applyPlaylistFilter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void applyPlaylistFilter() {
        rowItems.clear();
        String q = currentQuery.trim().toLowerCase(Locale.ROOT);
        for (PlaylistRowItem item : allRowItems) {
            if (q.isEmpty()) {
                rowItems.add(item);
            } else if (!item.isCreateCard && item.displayName.toLowerCase(Locale.ROOT).contains(q)) {
                rowItems.add(item);
            }
        }
    }

    private void showRenamePlaylistDialog(final String oldName) {
        ParrotModalHelper.showTextInputDialog(
                requireContext(),
                "Rename Playlist",
                "Update name for '" + oldName + "'",
                "Playlist name",
                oldName,
                "Rename",
                null,
                null,
                newName -> {
                    if (playlistManager.renamePlaylist(oldName, newName)) {
                        loadPlaylists();
                        Toast.makeText(getContext(), "Renamed to '" + newName + "'", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setDefaultPlaylistIcon(ImageView imgCover, int padPx, int tintColor) {
        imgCover.setPadding(padPx, padPx, padPx, padPx);
        imgCover.setImageResource(R.drawable.ic_nav_playlist_item);
        imgCover.setImageTintList(ColorStateList.valueOf(tintColor));
    }

    private void showCreatePlaylistDialog() {
        ParrotModalHelper.showTextInputDialog(
                requireContext(),
                "Create New Playlist",
                "Enter a name or import an existing .M3U playlist",
                "Playlist name",
                null,
                "Create",
                "Import .M3U File",
                this::launchM3uFilePicker,
                name -> {
                    playlistManager.createPlaylist(name);
                    loadPlaylists();
                }
        );
    }

    private void launchM3uFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            m3uImportLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Unable to open file picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void importM3uFromUri(Uri uri) {
        if (!isAdded()) return;
        String fileName = "Imported Playlist";
        try {
            Cursor c = requireContext().getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        int nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIdx >= 0) {
                            String display = c.getString(nameIdx);
                            if (display != null && !display.isEmpty()) {
                                fileName = display.replaceAll("(?i)\\.(m3u8?|txt)$", "");
                            }
                        }
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {}

        ArrayList<String> allLibrarySongs = new ArrayList<>();
        if (PermissionHelper.hasAudioPermission(requireContext())) {
            try {
                Cursor cursor = requireContext().getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Audio.Media.DATA},
                        MediaStore.Audio.Media.IS_MUSIC + "!=0",
                        null,
                        null
                );
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String path = cursor.getString(0);
                        if (path != null) allLibrarySongs.add(path);
                    }
                    cursor.close();
                }
            } catch (Exception ignored) {}
        }

        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            int added = playlistManager.importPlaylistFromM3uStream(fileName, is, allLibrarySongs);
            loadPlaylists();
            Toast.makeText(getContext(),
                    "Imported '" + fileName + "' (" + added + (added == 1 ? " track)" : " tracks)"),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to import .m3u file", Toast.LENGTH_SHORT).show();
        }
    }
}
