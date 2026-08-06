package com.example.parrot;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;

public class PlaylistsFragment extends Fragment {

    private ListView listView;
    private ArrayList<String> playlists = new ArrayList<>();
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
        playlists.add("+ Create New Playlist"); // First item is always Create
        playlists.addAll(playlistManager.getPlaylists());

        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return playlists.size(); }
            @Override
            public Object getItem(int i) { return null; }
            @Override
            public long getItemId(int i) { return 0; }
            @Override
            public View getView(int i, View view, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_item_group, null);
                TextView txt = v.findViewById(R.id.txtGroupName);
                txt.setText(playlists.get(i));
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
