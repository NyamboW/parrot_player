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
import java.io.File;
import java.util.ArrayList;

public class FoldersFragment extends Fragment {

    private ListView listView;
    private ArrayList<String> folders = new ArrayList<>();

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
        loadFolders();
    }

    private void loadFolders() {
        folders.clear();
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
                    if (parent != null
                            && MusicFolderHelper.isFolderAllowed(requireContext(), parent)
                            && !folders.contains(parent)) {
                        folders.add(parent);
                    }
                }
            }
            cursor.close();
        }

        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return folders.size(); }
            @Override
            public Object getItem(int i) { return null; }
            @Override
            public long getItemId(int i) { return 0; }
            @Override
            public View getView(int i, View view, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_item_group, null);
                TextView txt = v.findViewById(R.id.txtGroupName);
                // Just display the folder name instead of full path
                String fullPath = folders.get(i);
                String folderName = fullPath.substring(fullPath.lastIndexOf("/") + 1);
                txt.setText(folderName);
                return v;
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(getContext(), GroupSongsActivity.class);
            intent.putExtra("type", "folder");
            intent.putExtra("name", folders.get(position)); // Pass full path
            startActivity(intent);
        });
    }
}
