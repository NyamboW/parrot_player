package com.example.parrot;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
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

    private ListView listView;
    private final ArrayList<String> playlists = new ArrayList<>();
    private PlaylistManager playlistManager;

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
        loadPlaylists();
    }

    private void loadPlaylists() {
        playlists.clear();
        playlists.add("+ Create New Playlist");
        playlists.addAll(playlistManager.getPlaylists());

        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return playlists.size();
            }

            @Override
            public Object getItem(int i) {
                return playlists.get(i);
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

                int padPx = (int) (12 * getResources().getDisplayMetrics().density);

                if (i == 0) {
                    card.setBackgroundResource(R.drawable.card_create_playlist_bg);
                    coverCard.setCardBackgroundColor(Color.parseColor("#244CAF50"));
                    imgCover.setTag(null);
                    imgCover.setPadding(padPx, padPx, padPx, padPx);
                    imgCover.setImageResource(R.drawable.ic_nav_playlist_add);
                    imgCover.setImageTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
                    txtName.setText("Create New Playlist");
                    txtName.setTextColor(Color.parseColor("#4CAF50"));
                    txtCount.setText("Group your favourite tracks");
                    imgArrow.setVisibility(View.GONE);
                } else {
                    String playlistName = playlists.get(i);
                    ArrayList<String> songs = playlistManager.getSongsInPlaylist(playlistName);
                    int validCount = 0;
                    String firstValidPath = null;
                    for (String path : songs) {
                        if (path != null && new File(path).exists()) {
                            if (firstValidPath == null) firstValidPath = path;
                            validCount++;
                        }
                    }

                    card.setBackgroundResource(R.drawable.card_surface_bg);
                    coverCard.setCardBackgroundColor(Color.parseColor("#1E2430"));
                    txtName.setText(playlistName);
                    txtName.setTextColor(Color.parseColor("#F5F7FA"));
                    txtCount.setText(validCount + (validCount == 1 ? " track" : " tracks"));
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
                            setDefaultPlaylistIcon(imgCover, padPx);
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
                        setDefaultPlaylistIcon(imgCover, padPx);
                    }
                }

                return v;
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                showCreatePlaylistDialog();
            } else {
                Intent intent = new Intent(getContext(), GroupSongsActivity.class);
                intent.putExtra("type", "playlist");
                intent.putExtra("name", playlists.get(position));
                startActivity(intent);
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position > 0) {
                String playlistName = playlists.get(position);
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
            }
            return false;
        });
    }

    private void setDefaultPlaylistIcon(ImageView imgCover, int padPx) {
        imgCover.setPadding(padPx, padPx, padPx, padPx);
        imgCover.setImageResource(R.drawable.ic_nav_playlist_item);
        imgCover.setImageTintList(ColorStateList.valueOf(Color.parseColor("#9AA3B2")));
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
