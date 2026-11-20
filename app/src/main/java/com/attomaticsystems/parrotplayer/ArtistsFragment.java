package com.attomaticsystems.parrotplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ArtistsFragment extends Fragment {

    private static class ArtistItem {
        final String name;
        final String sampleSongPath;
        int trackCount = 1;
        final Set<String> albums = new HashSet<>();

        ArtistItem(String name, String album, String sampleSongPath) {
            this.name = name;
            this.sampleSongPath = sampleSongPath;
            if (album != null && !album.equals("<unknown>")) {
                albums.add(album);
            }
        }
    }

    private ListView listView;
    private final ArrayList<ArtistItem> allArtists = new ArrayList<>();
    private final ArrayList<ArtistItem> artists = new ArrayList<>();
    private BaseAdapter adapter;
    private String currentQuery = "";

    private final BroadcastReceiver libraryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded()) {
                loadArtists();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        listView = view.findViewById(R.id.listView);
        loadArtists();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ToolbarSearchHelper.attachFragmentSearchMenu(
                this,
                R.menu.menu_search,
                "Search artists...",
                this::filterArtists,
                null
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(PermissionHelper.ACTION_PERMISSIONS_GRANTED);
        filter.addAction(MusicFolderHelper.ACTION_FOLDERS_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(libraryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(libraryReceiver, filter);
        }
        loadArtists();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(libraryReceiver);
        } catch (Exception ignored) {}
    }

    private void loadArtists() {
        allArtists.clear();
        artists.clear();
        if (!isAdded() || !PermissionHelper.hasAudioPermission(requireContext())) return;
        Map<String, ArtistItem> artistMap = new LinkedHashMap<>();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";

        Cursor cursor = requireContext().getContentResolver().query(
                uri, projection, selection, null, MediaStore.Audio.Media.ARTIST + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String artist = cursor.getString(0);
                String album = cursor.getString(1);
                String path = cursor.getString(2);

                if (!MusicFolderHelper.isPathAllowed(requireContext(), path)) {
                    continue;
                }
                if (artist == null || artist.equals("<unknown>")) {
                    continue;
                }

                ArtistItem existing = artistMap.get(artist);
                if (existing == null) {
                    artistMap.put(artist, new ArtistItem(artist, album, path));
                } else {
                    existing.trackCount++;
                    if (album != null && !album.equals("<unknown>")) {
                        existing.albums.add(album);
                    }
                }
            }
            cursor.close();
        }

        allArtists.addAll(artistMap.values());
        applyArtistFilter();

        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return artists.size();
            }

            @Override
            public Object getItem(int i) {
                return artists.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convertView, ViewGroup parent) {
                View v = convertView != null
                        ? convertView
                        : getLayoutInflater().inflate(R.layout.item_artist_row, parent, false);

                ImageView imgAvatar = v.findViewById(R.id.imgArtistAvatar);
                TextView txtName = v.findViewById(R.id.txtArtistName);
                TextView txtInfo = v.findViewById(R.id.txtArtistInfo);

                ArtistItem item = artists.get(i);
                txtName.setText(item.name);

                String info = item.trackCount + (item.trackCount == 1 ? " track" : " tracks");
                if (!item.albums.isEmpty()) {
                    info += " • " + item.albums.size() + (item.albums.size() == 1 ? " album" : " albums");
                }
                txtInfo.setText(info);

                String artPath = item.sampleSongPath;
                imgAvatar.setTag(artPath);

                AlbumArtLoader loader = AlbumArtLoader.getInstance();
                Bitmap cached = loader.getCachedThumb(artPath);
                if (cached != null) {
                    imgAvatar.setPadding(0, 0, 0, 0);
                    imgAvatar.setImageTintList(null);
                    imgAvatar.setImageBitmap(cached);
                } else {
                    int padPx = (int) (11 * getResources().getDisplayMetrics().density);
                    imgAvatar.setPadding(padPx, padPx, padPx, padPx);
                    imgAvatar.setImageResource(R.drawable.ic_artist_person);
                    imgAvatar.setImageTintList(android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#9AA3B2")));
                    if (artPath != null && !loader.hasNoArt(artPath)) {
                        loader.loadThumbArt(artPath, bitmap -> {
                            if (bitmap != null && isAdded() && artPath.equals(imgAvatar.getTag())) {
                                imgAvatar.setPadding(0, 0, 0, 0);
                                imgAvatar.setImageTintList(null);
                                imgAvatar.setImageBitmap(bitmap);
                            }
                        });
                    }
                }

                return v;
            }
        };
        listView.setAdapter(adapter);

        AutoHideScrollHelper.attach(listView, getActivity(), null);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= artists.size()) return;
            Intent intent = new Intent(getContext(), GroupSongsActivity.class);
            intent.putExtra("type", "artist");
            intent.putExtra("name", artists.get(position).name);
            startActivity(intent);
        });
    }

    private void filterArtists(String query) {
        String next = query != null ? query : "";
        if (next.equals(currentQuery)) return;
        currentQuery = next;
        applyArtistFilter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void applyArtistFilter() {
        artists.clear();
        String q = currentQuery.trim().toLowerCase(Locale.ROOT);
        for (ArtistItem item : allArtists) {
            if (q.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains(q)) {
                artists.add(item);
            }
        }
    }
}
