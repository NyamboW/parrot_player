package com.example.parrot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
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
import java.util.LinkedHashMap;
import java.util.Map;

public class FoldersFragment extends Fragment {

    private static class FolderItem {
        final String fullPath;
        final String name;
        int trackCount = 1;

        FolderItem(String fullPath, String name) {
            this.fullPath = fullPath;
            this.name = name;
        }
    }

    private ListView listView;
    private final ArrayList<FolderItem> folders = new ArrayList<>();

    private final BroadcastReceiver libraryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded()) {
                loadFolders();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        listView = view.findViewById(R.id.listView);
        loadFolders();
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
        loadFolders();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(libraryReceiver);
        } catch (Exception ignored) {}
    }

    private void loadFolders() {
        folders.clear();
        if (!isAdded() || !PermissionHelper.hasAudioPermission(requireContext())) return;
        Map<String, FolderItem> folderMap = new LinkedHashMap<>();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{MediaStore.Audio.Media.DATA};
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";

        Cursor cursor = requireContext().getContentResolver().query(
                uri, projection, selection, null, MediaStore.Audio.Media.DATA + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String path = cursor.getString(0);
                if (path != null) {
                    File file = new File(path);
                    String parent = file.getParent();
                    if (parent != null && MusicFolderHelper.isFolderAllowed(requireContext(), parent)) {
                        FolderItem existing = folderMap.get(parent);
                        if (existing == null) {
                            String folderName = new File(parent).getName();
                            if (folderName.isEmpty()) folderName = parent;
                            folderMap.put(parent, new FolderItem(parent, folderName));
                        } else {
                            existing.trackCount++;
                        }
                    }
                }
            }
            cursor.close();
        }

        folders.addAll(folderMap.values());

        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return folders.size();
            }

            @Override
            public Object getItem(int i) {
                return folders.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convertView, ViewGroup parent) {
                View v = convertView != null
                        ? convertView
                        : getLayoutInflater().inflate(R.layout.item_folder_row, parent, false);

                ImageView imgFolderIcon = v.findViewById(R.id.imgFolderIcon);
                TextView txtName = v.findViewById(R.id.txtFolderName);
                TextView txtPath = v.findViewById(R.id.txtFolderPath);

                if (imgFolderIcon != null) {
                    imgFolderIcon.setImageTintList(ColorStateList.valueOf(
                            AccentColorHelper.getAccentColor(requireContext())));
                }

                FolderItem item = folders.get(i);
                txtName.setText(item.name);

                String shortPath = item.fullPath.replaceFirst("^/storage/emulated/0", "/Internal");
                txtPath.setText(item.trackCount + (item.trackCount == 1 ? " track • " : " tracks • ") + shortPath);

                return v;
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(getContext(), GroupSongsActivity.class);
            intent.putExtra("type", "folder");
            intent.putExtra("name", folders.get(position).fullPath);
            startActivity(intent);
        });
    }
}
