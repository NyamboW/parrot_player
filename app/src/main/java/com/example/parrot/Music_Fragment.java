package com.example.parrot;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.LruCache;
import java.io.File;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.Set;

import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Music_Fragment extends Fragment {

    ListView listView;
    String[] items;
    String[] artists;
    ArrayList<String> songList = new ArrayList<>();
    FavouritesManager favManager;
    
    // Search
    EditText editSearch;
    ImageButton btnClearSearch;
    ArrayList<String> filteredSongList = new ArrayList<>();
    String[] filteredItems;
    String[] filteredArtists;
    
    String currentSortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";
    Spinner spinnerSort;
    boolean isSpinnerInitialized = false;
    
    private ExecutorService executorService;
    private LruCache<String, Bitmap> memoryCache;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        View view = inflater.inflate(R.layout.fragment_music_, container, false);

        listView = view.findViewById(R.id.listView);
        favManager = new FavouritesManager(requireContext());
        
        // Search
        editSearch = view.findViewById(R.id.editSearch);
        btnClearSearch = view.findViewById(R.id.btnClearSearch);
        
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });
        
        btnClearSearch.setOnClickListener(v -> {
            editSearch.setText("");
            editSearch.clearFocus();
        });
        
        spinnerSort = view.findViewById(R.id.spinnerSort);
        String[] sortOptions = {"Date Added", "A-Z", "By Folder"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, sortOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(spinnerAdapter);

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    currentSortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";
                } else if (position == 1) {
                    currentSortOrder = MediaStore.Audio.Media.DISPLAY_NAME + " ASC";
                } else if (position == 2) {
                    currentSortOrder = MediaStore.Audio.Media.DATA + " ASC";
                }
                
                if (isSpinnerInitialized) {
                    requestPermission();
                } else {
                    isSpinnerInitialized = true;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        SwipeRefreshLayout refreshLayout = view.findViewById(R.id.pullToRefresh);
        refreshLayout.setOnRefreshListener(() -> {
            requestPermission();
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
        ArrayList<String> artistList = new ArrayList<>();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        
        SharedPreferences prefs = requireContext().getSharedPreferences("parrot_settings", Context.MODE_PRIVATE);
        Set<String> savedFolders = prefs.getStringSet(SettingsFragment.KEY_MUSIC_FOLDERS, null);

        Cursor cursor = requireContext().getContentResolver().query(
                uri,
                null,
                selection,
                null,
                currentSortOrder
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {

                String name = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                );

                String path = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                );
                
                File file = new File(path);
                String parent = file.getParent();
                if (savedFolders != null && !savedFolders.isEmpty()) {
                    if (parent == null || !savedFolders.contains(parent)) {
                        continue;
                    }
                }
                
                String artist = "<Unknown>";
                try {
                    artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                } catch (Exception e) {}

                songList.add(path);
                artistList.add(artist);
            }
            cursor.close();
        }

        items = new String[songList.size()];
        artists = new String[songList.size()];

        for (int i = 0; i < songList.size(); i++) {
            String fullPath = songList.get(i);
            String name = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            items[i] = name.replace(".mp3", "").replace(".wav", "");
            artists[i] = artistList.get(i);
        }
        
        // Apply current search filter
        String currentQuery = editSearch != null ? editSearch.getText().toString() : "";
        if (currentQuery.isEmpty()) {
            filteredSongList = new ArrayList<>(songList);
            filteredItems = items.clone();
            filteredArtists = artists.clone();
        } else {
            filterSongs(currentQuery);
            return; // filterSongs calls setAdapter
        }

        setAdapter();
    }
    
    private void filterSongs(String query) {
        filteredSongList.clear();
        ArrayList<String> tempItems = new ArrayList<>();
        ArrayList<String> tempArtists = new ArrayList<>();
        
        if (query.isEmpty()) {
            filteredSongList.addAll(songList);
            for (String item : items) tempItems.add(item);
            for (String artist : artists) tempArtists.add(artist);
        } else {
            String lowerQuery = query.toLowerCase();
            for (int i = 0; i < songList.size(); i++) {
                boolean matchName = items[i].toLowerCase().contains(lowerQuery);
                boolean matchArtist = artists[i].toLowerCase().contains(lowerQuery);
                if (matchName || matchArtist) {
                    filteredSongList.add(songList.get(i));
                    tempItems.add(items[i]);
                    tempArtists.add(artists[i]);
                }
            }
        }
        
        filteredItems = tempItems.toArray(new String[0]);
        filteredArtists = tempArtists.toArray(new String[0]);
        
        setAdapter();
    }
    
    private void setAdapter() {
        listView.setAdapter(new BaseAdapter() {

            @Override
            public int getCount() {
                return filteredItems.length;
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
                TextView txtArtist = v.findViewById(R.id.txtartist);
                ImageView img = v.findViewById(R.id.songImg);
                ImageView btnMore = v.findViewById(R.id.btnMore);
                
                txt.setText(filteredItems[i]);
                if (txtArtist != null) {
                    txtArtist.setText(filteredArtists[i]);
                }
                
                String path = filteredSongList.get(i);
                img.setTag(path);
                
                if (btnMore != null) {
                    btnMore.setOnClickListener(view1 -> {
                        PopupMenu popupMenu = new PopupMenu(getContext(), view1);
                        popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());
                        
                        if (favManager.isFavourite(path)) {
                            popupMenu.getMenu().findItem(R.id.action_fav).setTitle("Remove from Favourites");
                        }
                        
                        popupMenu.setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == R.id.action_fav) {
                                favManager.toggleFavourite(path);
                                Toast.makeText(getContext(), favManager.isFavourite(path) ? "Added to Favourites" : "Removed from Favourites", Toast.LENGTH_SHORT).show();
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
        });

        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            // Pass the full song list but find the correct position for the tapped filtered song
            String tappedPath = filteredSongList.get(position);
            int actualPosition = songList.indexOf(tappedPath);
            if (actualPosition == -1) actualPosition = position;

            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putStringArrayListExtra("songs", songList);
            intent.putExtra("pos", actualPosition);
            startActivity(intent);
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
                
                loadSongs();
            } else {
                Toast.makeText(getContext(), "Failed to delete. May require permission.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}