package com.example.parrot;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.ArrayList;

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
    private final ArrayList<PlaylistRowItem> rowItems = new ArrayList<>();
    private PlaylistManager playlistManager;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded()) {
                loadPlaylists();
            }
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
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED);
        filter.addAction(PermissionHelper.ACTION_PERMISSIONS_GRANTED);
        filter.addAction(MusicFolderHelper.ACTION_FOLDERS_UPDATED);
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
        rowItems.clear();
        rowItems.add(new PlaylistRowItem(true, false, null, "Create New Playlist", "Group your favourite tracks"));

        // 3 Smart Auto-Playlists
        rowItems.add(new PlaylistRowItem(false, true,
                ListeningStatsManager.SMART_RECENTLY_ADDED, "Recently Added", "Auto Playlist • "));
        rowItems.add(new PlaylistRowItem(false, true,
                ListeningStatsManager.SMART_MOST_PLAYED, "Most Played", "Auto Playlist • "));
        rowItems.add(new PlaylistRowItem(false, true,
                ListeningStatsManager.SMART_RECENTLY_PLAYED, "Recently Played", "Auto Playlist • "));

        // User-created custom playlists
        for (String plName : playlistManager.getPlaylists()) {
            rowItems.add(new PlaylistRowItem(false, false, null, plName, ""));
        }

        listView.setAdapter(new BaseAdapter() {
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
        });

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
            if (position <= 0 || position >= rowItems.size()) return false;
            PlaylistRowItem item = rowItems.get(position);
            if (item.isSmartPlaylist) {
                if (ListeningStatsManager.SMART_MOST_PLAYED.equals(item.smartId)
                        || ListeningStatsManager.SMART_RECENTLY_PLAYED.equals(item.smartId)) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Reset " + item.displayName)
                            .setMessage("Clear all tracked history for '" + item.displayName + "'?")
                            .setPositiveButton("Reset", (dialog, which) -> {
                                ListeningStatsManager.clearSmartPlaylistStats(requireContext(), item.smartId);
                                loadPlaylists();
                                Toast.makeText(getContext(), item.displayName + " reset", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                }
                return false;
            }

            String playlistName = item.displayName;
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Playlist")
                    .setMessage("Are you sure you want to delete '" + playlistName + "'?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        playlistManager.deletePlaylist(playlistName);
                        loadPlaylists();
                        Toast.makeText(getContext(), "Playlist deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("No", null)
                    .show();
            return true;
        });
    }

    private void setDefaultPlaylistIcon(ImageView imgCover, int padPx, int tintColor) {
        imgCover.setPadding(padPx, padPx, padPx, padPx);
        imgCover.setImageResource(R.drawable.ic_nav_playlist_item);
        imgCover.setImageTintList(ColorStateList.valueOf(tintColor));
    }

    private void showCreatePlaylistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Create New Playlist");

        final EditText input = new EditText(requireContext());
        input.setHint("Playlist name");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                playlistManager.createPlaylist(name);
                loadPlaylists();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
