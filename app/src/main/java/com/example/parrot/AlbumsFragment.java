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

public class AlbumsFragment extends Fragment {

    private ListView listView;
    private ArrayList<String> albums = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        listView = view.findViewById(R.id.listView);
        loadAlbums();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAlbums();
    }

    private void loadAlbums() {
        albums.clear();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";

        Cursor cursor = requireContext().getContentResolver().query(
                uri, projection, selection, null, MediaStore.Audio.Media.ALBUM + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String album = cursor.getString(0);
                String path = cursor.getString(1);
                if (!MusicFolderHelper.isPathAllowed(requireContext(), path)) {
                    continue;
                }
                if (album != null && !album.equals("<unknown>") && !albums.contains(album)) {
                    albums.add(album);
                }
            }
            cursor.close();
        }

        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return albums.size(); }
            @Override
            public Object getItem(int i) { return null; }
            @Override
            public long getItemId(int i) { return 0; }
            @Override
            public View getView(int i, View view, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_item_group, null);
                TextView txt = v.findViewById(R.id.txtGroupName);
                txt.setText(albums.get(i));
                return v;
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(getContext(), GroupSongsActivity.class);
            intent.putExtra("type", "album");
            intent.putExtra("name", albums.get(position));
            startActivity(intent);
        });
    }
}
