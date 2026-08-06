package com.example.parrot;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GroupSongsActivity extends AppCompatActivity {

    private ListView listView;
    private ArrayList<String> songList = new ArrayList<>();
    private String[] items;
    private String[] artists;
    private FavouritesManager favManager;
    private ExecutorService executorService;
    private LruCache<String, Bitmap> memoryCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_songs);

        Toolbar toolbar = findViewById(R.id.toolbarGroup);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        listView = findViewById(R.id.listViewGroupSongs);
        favManager = new FavouritesManager(this);

        executorService = Executors.newFixedThreadPool(4);
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        String type = getIntent().getStringExtra("type");
        String name = getIntent().getStringExtra("name");

        if (getSupportActionBar() != null && name != null) {
            getSupportActionBar().setTitle(name);
        }
        
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        loadGroupSongs(type, name);
    }

    private void loadGroupSongs(String type, String name) {
        songList.clear();
        ArrayList<String> artistList = new ArrayList<>();

        if ("playlist".equals(type)) {
            PlaylistManager pm = new PlaylistManager(this);
            ArrayList<String> playlistSongs = pm.getSongsInPlaylist(name);
            for (String path : playlistSongs) {
                File file = new File(path);
                if (file.exists()) {
                    songList.add(path);
                    artistList.add("<Unknown>"); // Can refine later
                }
            }
        } else {
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
            String[] selectionArgs = null;

            if ("artist".equals(type)) {
                selection += " AND " + MediaStore.Audio.Media.ARTIST + "=?";
                selectionArgs = new String[]{name};
            } else if ("album".equals(type)) {
                selection += " AND " + MediaStore.Audio.Media.ALBUM + "=?";
                selectionArgs = new String[]{name};
            }

            Cursor cursor = getContentResolver().query(
                    uri, null, selection, selectionArgs, MediaStore.Audio.Media.TITLE + " ASC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                    
                    if ("folder".equals(type)) {
                        File file = new File(path);
                        if (file.getParent() == null || !file.getParent().equals(name)) {
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
        }

        items = new String[songList.size()];
        artists = new String[songList.size()];

        for (int i = 0; i < songList.size(); i++) {
            String fullPath = songList.get(i);
            String songName = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            items[i] = songName.replace(".mp3", "").replace(".wav", "");
            artists[i] = artistList.get(i);
        }

        setAdapter();
    }

    private void setAdapter() {
        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return items.length; }
            @Override
            public Object getItem(int i) { return null; }
            @Override
            public long getItemId(int i) { return 0; }
            @Override
            public View getView(int i, View view, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.list_items, null);
                TextView txt = v.findViewById(R.id.txtSong);
                TextView txtArtist = v.findViewById(R.id.txtartist);
                ImageView img = v.findViewById(R.id.songImg);
                ImageView btnMore = v.findViewById(R.id.btnMore);
                
                txt.setText(items[i]);
                if (txtArtist != null) txtArtist.setText(artists[i]);
                
                String path = songList.get(i);
                img.setTag(path);
                
                if (btnMore != null) {
                    btnMore.setOnClickListener(view1 -> {
                        PopupMenu popupMenu = new PopupMenu(GroupSongsActivity.this, view1);
                        popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());
                        if (favManager.isFavourite(path)) {
                            popupMenu.getMenu().findItem(R.id.action_fav).setTitle("Remove from Favourites");
                        }
                        popupMenu.setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == R.id.action_fav) {
                                favManager.toggleFavourite(path);
                                Toast.makeText(GroupSongsActivity.this, favManager.isFavourite(path) ? "Added to Favourites" : "Removed from Favourites", Toast.LENGTH_SHORT).show();
                                return true;
                            } else if (item.getItemId() == R.id.action_play_next) {
                                Intent intent = new Intent(GroupSongsActivity.this, MusicService.class);
                                intent.setAction(MusicService.ACTION_PLAY_NEXT);
                                intent.putExtra("songPath", path);
                                startService(intent);
                                Toast.makeText(GroupSongsActivity.this, "Will play next", Toast.LENGTH_SHORT).show();
                                return true;
                            } else if (item.getItemId() == R.id.action_add_to_queue) {
                                Intent intent = new Intent(GroupSongsActivity.this, MusicService.class);
                                intent.setAction(MusicService.ACTION_ADD_TO_QUEUE);
                                intent.putExtra("songPath", path);
                                startService(intent);
                                Toast.makeText(GroupSongsActivity.this, "Added to queue", Toast.LENGTH_SHORT).show();
                                return true;
                            } else if (item.getItemId() == R.id.action_add_to_playlist) {
                                showAddToPlaylistDialog(path);
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
                            runOnUiThread(() -> {
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

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putStringArrayListExtra("songs", songList);
            intent.putExtra("pos", position);
            startActivity(intent);
        });
    }

    private void showAddToPlaylistDialog(String songPath) {
        PlaylistManager playlistManager = new PlaylistManager(this);
        ArrayList<String> playlists = playlistManager.getPlaylists();
        if (playlists.isEmpty()) {
            Toast.makeText(this, "No playlists available. Create one first.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] playlistArray = playlists.toArray(new String[0]);
        new android.app.AlertDialog.Builder(this)
            .setTitle("Add to Playlist")
            .setItems(playlistArray, (dialog, which) -> {
                String selectedPlaylist = playlistArray[which];
                playlistManager.addSongToPlaylist(selectedPlaylist, songPath);
                Toast.makeText(this, "Added to " + selectedPlaylist, Toast.LENGTH_SHORT).show();
            })
            .show();
    }
}
