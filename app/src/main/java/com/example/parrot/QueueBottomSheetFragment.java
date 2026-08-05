package com.example.parrot;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.IBinder;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QueueBottomSheetFragment extends BottomSheetDialogFragment {

    private ListView queueListView;
    private TextView txtQueueEmpty;
    private TextView txtQueueCount;
    private TextView btnClearQueue;

    private MusicService musicService;
    private boolean isBound = false;

    private ExecutorService executorService;
    private LruCache<String, Bitmap> memoryCache;

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executorService = Executors.newFixedThreadPool(2);

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 16;

        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_queue_bottom_sheet, container, false);

        queueListView = view.findViewById(R.id.queueListView);
        txtQueueEmpty = view.findViewById(R.id.txtQueueEmpty);
        txtQueueCount = view.findViewById(R.id.txtQueueCount);
        btnClearQueue = view.findViewById(R.id.btnClearQueue);

        btnClearQueue.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.clearQueue();
                refreshQueue();
                Toast.makeText(getContext(), "Queue cleared", Toast.LENGTH_SHORT).show();
            }
        });

        // Bind to MusicService
        Intent intent = new Intent(getContext(), MusicService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        return view;
    }

    private void refreshQueue() {
        if (!isBound || musicService == null) return;

        ArrayList<String> queue = musicService.getQueueList();

        if (queue == null || queue.isEmpty()) {
            txtQueueEmpty.setVisibility(View.VISIBLE);
            queueListView.setVisibility(View.GONE);
            txtQueueCount.setText("0 songs");
            return;
        }

        txtQueueEmpty.setVisibility(View.GONE);
        queueListView.setVisibility(View.VISIBLE);
        txtQueueCount.setText(queue.size() + (queue.size() == 1 ? " song" : " songs"));

        String[] displayNames = new String[queue.size()];
        for (int i = 0; i < queue.size(); i++) {
            String fullPath = queue.get(i);
            String name = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            displayNames[i] = name.replace(".mp3", "").replace(".wav", "")
                                  .replace(".m4a", "").replace(".flac", "");
        }

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
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_items, null);
                TextView txt = v.findViewById(R.id.txtSong);
                TextView txtArtist = v.findViewById(R.id.txtartist);
                ImageView img = v.findViewById(R.id.songImg);
                ImageView btnMore = v.findViewById(R.id.btnMore);

                txt.setText(displayNames[position]);
                if (txtArtist != null) {
                    txtArtist.setText("Queued");
                }

                String path = queue.get(position);
                img.setTag(path);

                // Replace the "more" button with a remove action
                if (btnMore != null) {
                    btnMore.setImageResource(R.drawable.ic_clear);
                    btnMore.setOnClickListener(view -> {
                        if (isBound && musicService != null) {
                            musicService.removeFromQueue(position);
                            refreshQueue();
                            Toast.makeText(getContext(), "Removed from queue", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                // Load album art
                Bitmap cachedBitmap = memoryCache.get(path);
                if (cachedBitmap != null) {
                    img.setImageBitmap(cachedBitmap);
                } else {
                    img.setImageResource(R.drawable.ic_music);
                    executorService.execute(() -> {
                        Bitmap bitmap = null;
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(path);
                            byte[] art = retriever.getEmbeddedPicture();
                            retriever.release();
                            if (art != null) {
                                bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                            }
                        } catch (Exception ignored) {}

                        final Bitmap finalBitmap = bitmap;
                        if (finalBitmap != null && getActivity() != null) {
                            memoryCache.put(path, finalBitmap);
                            getActivity().runOnUiThread(() -> {
                                if (path.equals(img.getTag())) {
                                    img.setImageBitmap(finalBitmap);
                                }
                            });
                        }
                    });
                }

                return v;
            }
        });

        // Tap a queued song to play it immediately
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
