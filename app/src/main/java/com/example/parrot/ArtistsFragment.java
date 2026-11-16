package com.example.parrot;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;

public class ArtistsFragment extends Fragment {

    private ListView listView;
    private ArrayList<String> artists = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        listView = view.findViewById(R.id.listView);
        loadArtists();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadArtists();
    }

    private void loadArtists() {
        artists.clear();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";

        Cursor cursor = requireContext().getContentResolver().query(
                uri, projection, selection, null, MediaStore.Audio.Media.ARTIST + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String artist = cursor.getString(0);
                String path = cursor.getString(1);
                if (!MusicFolderHelper.isPathAllowed(requireContext(), path)) {
                    continue;
                }
                if (artist != null && !artist.equals("<unknown>") && !artists.contains(artist)) {
                    artists.add(artist);
                }
            }
            cursor.close();
        }

        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return artists.size(); }
            @Override
            public Object getItem(int i) { return null; }
            @Override
            public long getItemId(int i) { return 0; }
            @Override
            public View getView(int i, View view, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_item_group, null);
                TextView txt = v.findViewById(R.id.txtGroupName);
                txt.setText(artists.get(i));
                return v;
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(getContext(), GroupSongsActivity.class);
            intent.putExtra("type", "artist");
            intent.putExtra("name", artists.get(position));
            startActivity(intent);
        });
    }
}
