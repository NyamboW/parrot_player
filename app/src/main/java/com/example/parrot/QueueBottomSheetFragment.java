package com.example.parrot;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;

public class QueueBottomSheetFragment extends BottomSheetDialogFragment {

    private ListView queueListView;
    private TextView txtQueueEmpty;
    private TextView txtQueueCount;
    private TextView btnClearQueue;
    private TextView btnSaveQueueAsPlaylist;

    private MusicService musicService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MyBinder binder = (MusicService.MyBinder) service;
            musicService = binder.getService();
            isBound = true;
            refreshQueue();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_queue_bottom_sheet, container, false);

        queueListView = view.findViewById(R.id.queueListView);
        txtQueueEmpty = view.findViewById(R.id.txtQueueEmpty);
        txtQueueCount = view.findViewById(R.id.txtQueueCount);
        btnClearQueue = view.findViewById(R.id.btnClearQueue);
        btnSaveQueueAsPlaylist = view.findViewById(R.id.btnSaveQueueAsPlaylist);

        int accent = AccentColorHelper.getAccentColor(requireContext());
        btnClearQueue.setTextColor(accent);

        btnClearQueue.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.clearQueue();
                refreshQueue();
                Toast.makeText(getContext(), "Queue cleared", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnSaveQueueAsPlaylist != null) {
            btnSaveQueueAsPlaylist.setOnClickListener(v -> promptSaveQueueAsPlaylist());
        }

        Intent intent = new Intent(getContext(), MusicService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        return view;
    }

    private void promptSaveQueueAsPlaylist() {
        if (!isBound || musicService == null) return;
        ArrayList<String> queue = musicService.getQueueList();
        if (queue == null || queue.isEmpty()) {
            Toast.makeText(getContext(), "Queue is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(requireContext());
        input.setHint("Playlist name");
        new AlertDialog.Builder(requireContext())
                .setTitle("Save Queue as Playlist")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        PlaylistManager pm = new PlaylistManager(requireContext());
                        pm.createPlaylist(name);
                        pm.addSongsToPlaylist(name, queue);
                        Toast.makeText(getContext(), "Saved " + queue.size() + " tracks to '" + name + "'", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshQueue() {
        if (!isBound || musicService == null) return;

        final ArrayList<String> queue = musicService.getQueueList();

        if (queue == null || queue.isEmpty()) {
            txtQueueEmpty.setVisibility(View.VISIBLE);
            queueListView.setVisibility(View.GONE);
            txtQueueCount.setText("0 songs");
            return;
        }

        txtQueueEmpty.setVisibility(View.GONE);
        queueListView.setVisibility(View.VISIBLE);
        txtQueueCount.setText(queue.size() + (queue.size() == 1 ? " song" : " songs"));

        final String[] displayNames = new String[queue.size()];
        for (int i = 0; i < queue.size(); i++) {
            String fullPath = queue.get(i);
            String name = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            displayNames[i] = name.replaceAll("(?i)\\.(mp3|wav|m4a|flac|ogg|aac)$", "");
        }

        final float density = getResources().getDisplayMetrics().density;

        queueListView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return displayNames.length;
            }

            @Override
            public Object getItem(int position) {
                return queue.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_items, null);
                TextView txt = v.findViewById(R.id.txtSong);
                TextView txtArtist = v.findViewById(R.id.txtartist);
                ImageView img = v.findViewById(R.id.songImg);
                ImageView btnMore = v.findViewById(R.id.btnMore);
                FrameLayout rightActionContainer = v.findViewById(R.id.rightActionContainer);

                txt.setText(displayNames[position]);
                if (txtArtist != null) {
                    txtArtist.setText("#" + (position + 1) + " in queue • Tap to play");
                }

                String path = queue.get(position);
                img.setTag(path);

                if (rightActionContainer != null) {
                    rightActionContainer.removeAllViews();
                    LinearLayout controlsRow = new LinearLayout(requireContext());
                    controlsRow.setOrientation(LinearLayout.HORIZONTAL);
                    controlsRow.setGravity(Gravity.CENTER_VERTICAL);

                    int btnSize = (int) (32 * density);

                    // Move Up (▲)
                    TextView btnUp = new TextView(requireContext());
                    btnUp.setText("▲");
                    btnUp.setTextSize(12f);
                    btnUp.setGravity(Gravity.CENTER);
                    btnUp.setTextColor(position > 0 ? Color.parseColor("#C8D0DC") : Color.parseColor("#3E4554"));
                    btnUp.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));
                    btnUp.setEnabled(position > 0);
                    btnUp.setOnClickListener(view -> {
                        if (isBound && musicService != null && position > 0) {
                            musicService.moveQueueItem(position, position - 1);
                            refreshQueue();
                        }
                    });

                    // Move Down (▼)
                    TextView btnDown = new TextView(requireContext());
                    btnDown.setText("▼");
                    btnDown.setTextSize(12f);
                    btnDown.setGravity(Gravity.CENTER);
                    btnDown.setTextColor(position < queue.size() - 1 ? Color.parseColor("#C8D0DC") : Color.parseColor("#3E4554"));
                    btnDown.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));
                    btnDown.setEnabled(position < queue.size() - 1);
                    btnDown.setOnClickListener(view -> {
                        if (isBound && musicService != null && position < queue.size() - 1) {
                            musicService.moveQueueItem(position, position + 1);
                            refreshQueue();
                        }
                    });

                    // Remove (✕)
                    ImageView btnRemove = new ImageView(requireContext());
                    int pad = (int) (7 * density);
                    btnRemove.setPadding(pad, pad, pad, pad);
                    btnRemove.setImageResource(R.drawable.ic_clear);
                    btnRemove.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));
                    btnRemove.setOnClickListener(view -> {
                        if (isBound && musicService != null) {
                            musicService.removeFromQueue(position);
                            refreshQueue();
                            Toast.makeText(getContext(), "Removed from queue", Toast.LENGTH_SHORT).show();
                        }
                    });

                    controlsRow.addView(btnUp);
                    controlsRow.addView(btnDown);
                    controlsRow.addView(btnRemove);
                    rightActionContainer.addView(controlsRow);
                } else if (btnMore != null) {
                    btnMore.setImageResource(R.drawable.ic_clear);
                    btnMore.setOnClickListener(view -> {
                        if (isBound && musicService != null) {
                            musicService.removeFromQueue(position);
                            refreshQueue();
                        }
                    });
                }

                AlbumArtLoader loader = AlbumArtLoader.getInstance();
                Bitmap cachedBitmap = loader.getCachedThumb(path);
                if (cachedBitmap != null) {
                    img.setImageBitmap(cachedBitmap);
                } else {
                    img.setImageResource(R.drawable.ic_music);
                    if (!loader.hasNoArt(path)) {
                        loader.loadThumbArt(path, bitmap -> {
                            if (bitmap != null && isAdded() && path.equals(img.getTag())) {
                                img.setImageBitmap(bitmap);
                            }
                        });
                    }
                }

                return v;
            }
        });

        queueListView.setOnItemClickListener((parent, view, position, id) -> {
            if (isBound && musicService != null) {
                musicService.playFromQueue(position);
                refreshQueue();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isBound) {
            requireContext().unbindService(serviceConnection);
            isBound = false;
        }
    }

    @Override
    public int getTheme() {
        return com.google.android.material.R.style.Theme_Design_BottomSheetDialog;
    }
}
