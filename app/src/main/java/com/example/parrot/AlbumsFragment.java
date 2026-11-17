package com.example.parrot;

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
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class AlbumsFragment extends Fragment {

    private static class AlbumItem {
        final String name;
        String artist;
        String sampleSongPath;
        int trackCount;

        AlbumItem(String name, String artist, String sampleSongPath) {
            this.name = name;
            this.artist = artist;
            this.sampleSongPath = sampleSongPath;
            this.trackCount = 1;
        }
    }

    private GridView gridView;
    private final ArrayList<AlbumItem> albums = new ArrayList<>();

    private final BroadcastReceiver libraryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded()) {
                loadAlbums();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_albums_grid, container, false);
        gridView = view.findViewById(R.id.gridViewAlbums);
        loadAlbums();
        return view;
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
        loadAlbums();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(libraryReceiver);
        } catch (Exception ignored) {}
    }

    private void loadAlbums() {
        albums.clear();
        if (!isAdded() || !PermissionHelper.hasAudioPermission(requireContext())) return;
        Map<String, AlbumItem> albumMap = new LinkedHashMap<>();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";

        Cursor cursor = requireContext().getContentResolver().query(
                uri, projection, selection, null, MediaStore.Audio.Media.ALBUM + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String album = cursor.getString(0);
                String artist = cursor.getString(1);
                String path = cursor.getString(2);

                if (!MusicFolderHelper.isPathAllowed(requireContext(), path)) {
                    continue;
                }
                if (album == null || album.equals("<unknown>")) {
                    continue;
                }

                String cleanArtist = (artist != null && !artist.equals("<unknown>")) ? artist : "Unknown Artist";
                AlbumItem existing = albumMap.get(album);
                if (existing == null) {
                    albumMap.put(album, new AlbumItem(album, cleanArtist, path));
                } else {
                    existing.trackCount++;
                }
            }
            cursor.close();
        }

        albums.addAll(albumMap.values());

        gridView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return albums.size();
            }

            @Override
            public Object getItem(int i) {
                return albums.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convertView, ViewGroup parent) {
                View v = convertView != null
                        ? convertView
                        : getLayoutInflater().inflate(R.layout.item_album_grid, parent, false);

                ImageView imgCover = v.findViewById(R.id.imgAlbumCover);
                TextView txtTitle = v.findViewById(R.id.txtAlbumTitle);
                TextView txtSubtitle = v.findViewById(R.id.txtAlbumSubtitle);

                AlbumItem item = albums.get(i);
                txtTitle.setText(item.name);
                txtSubtitle.setText(item.artist + " • " + item.trackCount
                        + (item.trackCount == 1 ? " track" : " tracks"));

                String artPath = item.sampleSongPath;
                imgCover.setTag(artPath);

                AlbumArtLoader loader = AlbumArtLoader.getInstance();
                Bitmap cached = loader.getBestCachedArt(artPath);
                if (cached != null) {
                    imgCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgCover.setImageBitmap(cached);
                } else {
                    imgCover.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    imgCover.setImageResource(R.drawable.ic_music);
                    if (artPath != null && !loader.hasNoArt(artPath)) {
                        loader.loadThumbArt(artPath, bitmap -> {
                            if (bitmap != null && isAdded() && artPath.equals(imgCover.getTag())) {
                                imgCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                imgCover.setImageBitmap(bitmap);
                            }
                        });
                    }
                }

                return v;
            }
        });

        AutoHideScrollHelper.attach(gridView, getActivity(), null);

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(getContext(), GroupSongsActivity.class);
            intent.putExtra("type", "album");
            intent.putExtra("name", albums.get(position).name);
            startActivity(intent);
        });
    }
}
