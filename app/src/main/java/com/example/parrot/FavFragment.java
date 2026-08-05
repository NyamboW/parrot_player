package com.example.parrot;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.provider.MediaStore;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;

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
    private HashMap<String, String> artistCache = new HashMap<>();

    private int currentFavSortMode = 0; // 0: Date, 1: A-Z, 2: Folder
    private Spinner spinnerSortFav;
    private boolean isFavSpinnerInitialized = false;
    
    // Search
    private EditText editSearchFav;
    private ImageButton btnClearSearchFav;
    private ArrayList<String> filteredFavSongs = new ArrayList<>();
    private String[] filteredDisplayNames;

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
        
        // Search
        editSearchFav = view.findViewById(R.id.editSearchFav);
        btnClearSearchFav = view.findViewById(R.id.btnClearSearchFav);
        
        editSearchFav.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFavSongs(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                btnClearSearchFav.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });
        
        btnClearSearchFav.setOnClickListener(v -> {
            editSearchFav.setText("");
            editSearchFav.clearFocus();
        });
        
        spinnerSortFav = view.findViewById(R.id.spinnerSortFav);
        if (spinnerSortFav != null) {
            String[] sortOptions = {"Date Added", "A-Z", "By Folder"};
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, sortOptions);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerSortFav.setAdapter(spinnerAdapter);

            spinnerSortFav.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    currentFavSortMode = position;
                    if (isFavSpinnerInitialized) {
                        loadFavourites();
                    } else {
                        isFavSpinnerInitialized = true;
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

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
        
        Collections.sort(favSongs, new Comparator<String>() {
            @Override
            public int compare(String path1, String path2) {
                if (currentFavSortMode == 1) { // A-Z
                    String name1 = path1.substring(path1.lastIndexOf("/") + 1).toLowerCase();
                    String name2 = path2.substring(path2.lastIndexOf("/") + 1).toLowerCase();
                    return name1.compareTo(name2);
                } else if (currentFavSortMode == 2) { // By Folder
                    return path1.compareToIgnoreCase(path2);
                } else { // Date Added
                    long time1 = new File(path1).lastModified();
                    long time2 = new File(path2).lastModified();
                    return Long.compare(time2, time1); // Descending
                }
            }
        });

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
        
        // Apply current search filter
        String currentQuery = editSearchFav != null ? editSearchFav.getText().toString() : "";
        if (currentQuery.isEmpty()) {
            filteredFavSongs = new ArrayList<>(favSongs);
            filteredDisplayNames = displayNames.clone();
        } else {
            filterFavSongs(currentQuery);
            return; // filterFavSongs calls setFavAdapter
        }

        setFavAdapter();
    }
    
    private void filterFavSongs(String query) {
        filteredFavSongs.clear();
        ArrayList<String> tempNames = new ArrayList<>();
        
        if (query.isEmpty()) {
            filteredFavSongs.addAll(favSongs);
            if (displayNames != null) {
                for (String name : displayNames) tempNames.add(name);
            }
        } else {
            String lowerQuery = query.toLowerCase();
            for (int i = 0; i < favSongs.size(); i++) {
                if (displayNames == null || i >= displayNames.length) break;
                boolean matchName = displayNames[i].toLowerCase().contains(lowerQuery);
                String artist = artistCache.get(favSongs.get(i));
                boolean matchArtist = artist != null && artist.toLowerCase().contains(lowerQuery);
                if (matchName || matchArtist) {
                    filteredFavSongs.add(favSongs.get(i));
                    tempNames.add(displayNames[i]);
                }
            }
        }
        
        filteredDisplayNames = tempNames.toArray(new String[0]);
        
        if (filteredFavSongs.isEmpty() && !query.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            txtEmpty.setText("No results found");
            favListView.setVisibility(View.GONE);
        } else if (favSongs.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            txtEmpty.setText("No favourites yet. Tap the heart icon on a song to add it here.");
            favListView.setVisibility(View.GONE);
        } else {
            txtEmpty.setVisibility(View.GONE);
            favListView.setVisibility(View.VISIBLE);
            setFavAdapter();
        }
    }
    
    private void setFavAdapter() {
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return filteredDisplayNames.length;
            }

            @Override
            public Object getItem(int i) {
                return filteredFavSongs.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convertView, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_items, null);
                TextView txt = v.findViewById(R.id.txtSong);
                TextView txtArtist = v.findViewById(R.id.txtartist);
                ImageView img = v.findViewById(R.id.songImg);
                ImageView btnMore = v.findViewById(R.id.btnMore);

                txt.setText(filteredDisplayNames[i]);

                String path = filteredFavSongs.get(i);
                img.setTag(path);
                
                if (artistCache.containsKey(path)) {
                    if (txtArtist != null) txtArtist.setText(artistCache.get(path));
                }
                
                if (btnMore != null) {
                    btnMore.setOnClickListener(view1 -> {
                        PopupMenu popupMenu = new PopupMenu(getContext(), view1);
                        popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());
                        
                        popupMenu.getMenu().findItem(R.id.action_fav).setTitle("Remove from Favourites");
                        
                        popupMenu.setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == R.id.action_fav) {
                                favManager.removeFavourite(path);
                                loadFavourites();
                                return true;
                            } else if (item.getItemId() == R.id.action_delete) {
                                deleteSong(path);
                                return true;
                            } else if (item.getItemId() == R.id.action_play_next) {
                                Intent intent = new Intent(getContext(), MusicService.class);
                                intent.setAction(MusicService.ACTION_PLAY_NEXT);
                                intent.putExtra("songPath", path);
                                requireContext().startService(intent);
                                Toast.makeText(getContext(), "Will play next", Toast.LENGTH_SHORT).show();
                                return true;
                            } else if (item.getItemId() == R.id.action_add_to_queue) {
                                Intent intent = new Intent(getContext(), MusicService.class);
                                intent.setAction(MusicService.ACTION_ADD_TO_QUEUE);
                                intent.putExtra("songPath", path);
                                requireContext().startService(intent);
                                Toast.makeText(getContext(), "Added to queue", Toast.LENGTH_SHORT).show();
                                return true;
                            }
                            return false;
                        });
                        popupMenu.show();
                    });
                }

                Bitmap cachedBitmap = memoryCache.get(path);
                if (cachedBitmap != null) {
                    img.setImageBitmap(cachedBitmap);
                } else {
                    img.setImageResource(R.drawable.ic_music);
                    executorService.execute(() -> {
                        Bitmap bitmap = null;
                        String artist = "Unknown Artist";
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(path);
                            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                            if (artist == null) artist = "Unknown Artist";
                            
                            byte[] art = retriever.getEmbeddedPicture();
                            retriever.release();
                            if (art != null) {
                                bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                            }
                        } catch (Exception ignored) {}

                        final Bitmap finalBitmap = bitmap;
                        final String finalArtist = artist;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                artistCache.put(path, finalArtist);
                                if (path.equals(img.getTag())) {
                                    if (finalBitmap != null) {
                                        memoryCache.put(path, finalBitmap);
                                        img.setImageBitmap(finalBitmap);
                                    }
                                    if (txtArtist != null) {
                                        txtArtist.setText(finalArtist);
                                    }
                                }
                            });
                        }
                    });
                }

                return v;
            }
        };

        favListView.setAdapter(adapter);

        favListView.setOnItemClickListener((parent, view, position, id) -> {
            // Use filtered list for playback but pass correct position
            String tappedPath = filteredFavSongs.get(position);
            int actualPosition = favSongs.indexOf(tappedPath);
            if (actualPosition == -1) actualPosition = position;
            
            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putStringArrayListExtra("songs", favSongs);
            intent.putExtra("pos", actualPosition);
            startActivity(intent);
        });

        favListView.setOnItemLongClickListener((parent, view, position, id) -> {
            String path = filteredFavSongs.get(position);
            favManager.removeFavourite(path);
            loadFavourites();
            return true;
        });
    }

    private void deleteSong(String path) {
        File file = new File(path);
        try {
            if (file.exists() && file.delete()) {
                requireContext().getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, 
                    MediaStore.Audio.Media.DATA + "=?", new String[]{path});
                Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                
                if (favManager.isFavourite(path)) {
                    favManager.removeFavourite(path);
                }
                
                loadFavourites();
            } else {
                Toast.makeText(getContext(), "Failed to delete. May require permission.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}