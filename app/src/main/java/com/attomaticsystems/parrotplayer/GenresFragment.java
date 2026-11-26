package com.attomaticsystems.parrotplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Displays audio genres from MediaStore filtered by the user's allowed music folders.
 */
public class GenresFragment extends Fragment {

    private static class GenreItem {
        final long genreId;
        final String name;
        final String sampleSongPath;
        final int trackCount;

        GenreItem(long genreId, String name, String sampleSongPath, int trackCount) {
            this.genreId = genreId;
            this.name = name;
            this.sampleSongPath = sampleSongPath;
            this.trackCount = trackCount;
        }
    }

    private ListView listView;
    private final ArrayList<GenreItem> allGenres = new ArrayList<>();
    private final ArrayList<GenreItem> genres = new ArrayList<>();
    private BaseAdapter adapter;
    private String currentQuery = "";

    private final BroadcastReceiver libraryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded()) {
                loadGenres();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        listView = view.findViewById(R.id.listView);
        loadGenres();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ToolbarSearchHelper.attachFragmentSearchMenu(
                this,
                R.menu.menu_search,
                "Search genres...",
                this::filterGenres,
                null
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(PermissionHelper.ACTION_PERMISSIONS_GRANTED);
        filter.addAction(MusicFolderHelper.ACTION_FOLDERS_UPDATED);
        filter.addAction(AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(libraryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(libraryReceiver, filter);
        }
        loadGenres();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(libraryReceiver);
        } catch (Exception ignored) {}
    }

    private void loadGenres() {
        allGenres.clear();
        genres.clear();
        if (!isAdded() || !PermissionHelper.hasAudioPermission(requireContext())) return;

        try (Cursor genreCursor = requireContext().getContentResolver().query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME},
                null,
                null,
                MediaStore.Audio.Genres.NAME + " ASC"
        )) {
            if (genreCursor != null) {
                while (genreCursor.moveToNext()) {
                    long genreId = genreCursor.getLong(0);
                    String genreName = genreCursor.getString(1);
                    if (genreName == null || genreName.trim().isEmpty()) continue;

                    Uri membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId);
                    int validCount = 0;
                    String samplePath = null;
                    try (Cursor memberCursor = requireContext().getContentResolver().query(
                            membersUri,
                            new String[]{MediaStore.Audio.Media.DATA},
                            MediaStore.Audio.Media.IS_MUSIC + "!=0",
                            null,
                            null
                    )) {
                        if (memberCursor != null) {
                            while (memberCursor.moveToNext()) {
                                String path = memberCursor.getString(0);
                                if (path != null && MusicFolderHelper.isPathAllowed(requireContext(), path)
                                        && new File(path).exists()) {
                                    if (samplePath == null) samplePath = path;
                                    validCount++;
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    if (validCount > 0) {
                        allGenres.add(new GenreItem(genreId, genreName.trim(), samplePath, validCount));
                    }
                }
            }
        } catch (Exception ignored) {}

        applyGenreFilter();

        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return genres.size();
            }

            @Override
            public Object getItem(int position) {
                return genres.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView != null
                        ? convertView
                        : getLayoutInflater().inflate(R.layout.item_artist_row, parent, false);

                ImageView imgAvatar = v.findViewById(R.id.imgArtistAvatar);
                TextView txtName = v.findViewById(R.id.txtArtistName);
                TextView txtInfo = v.findViewById(R.id.txtArtistInfo);

                GenreItem item = genres.get(position);
                txtName.setText(item.name);
                txtInfo.setText(item.trackCount + (item.trackCount == 1 ? " track" : " tracks"));

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
                    imgAvatar.setImageResource(R.drawable.ic_music);
                    imgAvatar.setImageTintList(ColorStateList.valueOf(
                            AccentColorHelper.getAccentColor(requireContext())));
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
            if (position < 0 || position >= genres.size()) return;
            GenreItem selected = genres.get(position);
            Intent intent = new Intent(getContext(), GroupSongsActivity.class);
            intent.putExtra("type", "genre");
            intent.putExtra("genre_id", selected.genreId);
            intent.putExtra("name", selected.name);
            startActivity(intent);
        });
    }

    private void filterGenres(String query) {
        String next = query != null ? query : "";
        if (next.equals(currentQuery)) return;
        currentQuery = next;
        applyGenreFilter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void applyGenreFilter() {
        genres.clear();
        String q = currentQuery.trim().toLowerCase(Locale.ROOT);
        for (GenreItem item : allGenres) {
            if (q.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains(q)) {
                genres.add(item);
            }
        }
    }
}
