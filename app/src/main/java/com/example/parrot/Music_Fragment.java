package com.example.parrot;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.ArrayList;

public class Music_Fragment extends Fragment {

    ListView listView;
    String[] items;
    ArrayList<String> songList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_music_, container, false);

        listView = view.findViewById(R.id.listView);

        SwipeRefreshLayout refreshLayout = view.findViewById(R.id.pullToRefresh);
        refreshLayout.setOnRefreshListener(() -> {
            loadSongs();
            refreshLayout.setRefreshing(false);
        });

        requestPermission();

        return view;
    }

    private void requestPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            Dexter.withContext(requireContext())
                    .withPermissions(Manifest.permission.READ_MEDIA_AUDIO)
                    .withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport report) {
                            loadSongs();
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(
                                java.util.List<PermissionRequest> list,
                                PermissionToken token) {
                            token.continuePermissionRequest();
                        }
                    }).check();

        } else {

            Dexter.withContext(requireContext())
                    .withPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
                    .withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport report) {
                            loadSongs();
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(
                                java.util.List<PermissionRequest> list,
                                PermissionToken token) {
                            token.continuePermissionRequest();
                        }
                    }).check();
        }
    }

    private void loadSongs() {

        songList.clear();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";

        Cursor cursor = requireContext().getContentResolver().query(
                uri,
                null,
                selection,
                null,
                MediaStore.Audio.Media.DATE_ADDED + " DESC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {

                String name = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                );

                String path = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                );

                songList.add(path);
            }
            cursor.close();
        }

        items = new String[songList.size()];

        for (int i = 0; i < songList.size(); i++) {
            String fullPath = songList.get(i);
            String name = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            items[i] = name.replace(".mp3", "").replace(".wav", "");
        }

        listView.setAdapter(new BaseAdapter() {

            @Override
            public int getCount() {
                return items.length;
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @Override
            public View getView(int i, View view, ViewGroup parent) {

                View v = getLayoutInflater().inflate(R.layout.list_items, null);
                TextView txt = v.findViewById(R.id.txtSong);
                txt.setText(items[i]);
                return v;
            }
        });

        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {

            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putStringArrayListExtra("songs", songList);
            intent.putExtra("pos", position);
            startActivity(intent);
        });
    }
}