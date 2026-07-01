package com.example.parrot;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FavFragment extends Fragment {

    private ListView favListView;
    private TextView txtEmpty;
    private FavouritesManager favManager;
    private ArrayList<String> favSongs = new ArrayList<>();
    private String[] displayNames;
    private BaseAdapter adapter;

    private ExecutorService executorService;
    private LruCache<String, Bitmap> memoryCache;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        favManager = new FavouritesManager(requireContext());
        executorService = Executors.newFixedThreadPool(4);

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;

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
        View view = inflater.inflate(R.layout.fragment_fav, container, false);

        favListView = view.findViewById(R.id.favListView);
        txtEmpty = view.findViewById(R.id.txtEmpty);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFavourites();
    }

    private void loadFavourites() {
        favSongs.clear();

        // Only keep songs that still exist on disk
        ArrayList<String> allFavs = favManager.getFavourites();
        for (String path : allFavs) {
            File f = new File(path);
            if (f.exists()) {
                favSongs.add(path);
            }
        }

        if (favSongs.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            favListView.setVisibility(View.GONE);
            return;
        }

        txtEmpty.setVisibility(View.GONE);
        favListView.setVisibility(View.VISIBLE);

        displayNames = new String[favSongs.size()];
        for (int i = 0; i < favSongs.size(); i++) {
            String fullPath = favSongs.get(i);
            String name = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            displayNames[i] = name.replace(".mp3", "").replace(".wav", "").replace(".m4a", "").replace(".flac", "");
        }

        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return displayNames.length;
            }

            @Override
            public Object getItem(int i) {
                return favSongs.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convertView, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_items, null);
                TextView txt = v.findViewById(R.id.txtSong);
                ImageView img = v.findViewById(R.id.songImg);

                txt.setText(displayNames[i]);

                String path = favSongs.get(i);
                img.setTag(path);

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
                        if (finalBitmap != null) {
                            memoryCache.put(path, finalBitmap);
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (path.equals(img.getTag())) {
                                        img.setImageBitmap(finalBitmap);
                                    }
                                });
                            }
                        }
                    });
                }

                return v;
            }
        };

        favListView.setAdapter(adapter);

        favListView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putStringArrayListExtra("songs", favSongs);
            intent.putExtra("pos", position);
            startActivity(intent);
        });

        favListView.setOnItemLongClickListener((parent, view, position, id) -> {
            String path = favSongs.get(position);
            favManager.removeFavourite(path);
            loadFavourites();
            return true;
        });
    }
}