package com.example.parrot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlaylistManager {

    private static final String PREFS_NAME = "parrot_playlists";
    private static final String KEY_PLAYLISTS = "playlists_names";
    private final SharedPreferences prefs;

    public PlaylistManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public ArrayList<String> getPlaylists() {
        Set<String> names = prefs.getStringSet(KEY_PLAYLISTS, new HashSet<>());
        ArrayList<String> list = new ArrayList<>(names);
        Collections.sort(list, String::compareToIgnoreCase);
        return list;
    }

    public void createPlaylist(String name) {
        Set<String> names = new HashSet<>(prefs.getStringSet(KEY_PLAYLISTS, new HashSet<>()));
        names.add(name);
        prefs.edit().putStringSet(KEY_PLAYLISTS, names).apply();
    }

    public boolean renamePlaylist(String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        String cleanNew = newName.trim();
        if (cleanNew.isEmpty() || cleanNew.equals(oldName)) return false;

        Set<String> names = new HashSet<>(prefs.getStringSet(KEY_PLAYLISTS, new HashSet<>()));
        if (!names.contains(oldName)) return false;

        ArrayList<String> songs = getSongsInPlaylist(oldName);
        names.remove(oldName);
        names.add(cleanNew);

        prefs.edit()
                .putStringSet(KEY_PLAYLISTS, names)
                .remove("playlist_" + oldName)
                .apply();
        saveSongsToPlaylist(cleanNew, songs);
        return true;
    }

    public void deletePlaylist(String name) {
        Set<String> names = new HashSet<>(prefs.getStringSet(KEY_PLAYLISTS, new HashSet<>()));
        names.remove(name);
        prefs.edit()
             .putStringSet(KEY_PLAYLISTS, names)
             .remove("playlist_" + name)
             .apply();
    }

    public void addSongToPlaylist(String playlistName, String songPath) {
        ArrayList<String> songs = getSongsInPlaylist(playlistName);
        if (!songs.contains(songPath)) {
            songs.add(songPath);
            saveSongsToPlaylist(playlistName, songs);
        }
    }

    public void addSongsToPlaylist(String playlistName, ArrayList<String> songPaths) {
        ArrayList<String> songs = getSongsInPlaylist(playlistName);
        boolean changed = false;
        for (String songPath : songPaths) {
            if (!songs.contains(songPath)) {
                songs.add(songPath);
                changed = true;
            }
        }
        if (changed) {
            saveSongsToPlaylist(playlistName, songs);
        }
    }

    public void removeSongFromPlaylist(String playlistName, String songPath) {
        ArrayList<String> songs = getSongsInPlaylist(playlistName);
        if (songs.contains(songPath)) {
            songs.remove(songPath);
            saveSongsToPlaylist(playlistName, songs);
        }
    }

    public void moveSongInPlaylist(String playlistName, int fromIndex, int toIndex) {
        ArrayList<String> songs = getSongsInPlaylist(playlistName);
        if (fromIndex >= 0 && fromIndex < songs.size() && toIndex >= 0 && toIndex < songs.size()) {
            String moved = songs.remove(fromIndex);
            songs.add(toIndex, moved);
            saveSongsToPlaylist(playlistName, songs);
        }
    }

    public ArrayList<String> getSongsInPlaylist(String playlistName) {
        String jsonStr = prefs.getString("playlist_" + playlistName, "[]");
        ArrayList<String> songs = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                songs.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return songs;
    }

    private void saveSongsToPlaylist(String playlistName, ArrayList<String> songs) {
        JSONArray jsonArray = new JSONArray();
        for (String song : songs) {
            jsonArray.put(song);
        }
        prefs.edit().putString("playlist_" + playlistName, jsonArray.toString()).apply();
    }

    /**
     * Exports a playlist to a standard .m3u file in /Music/ParrotPlaylists (or app external storage fallback).
     */
    public File exportPlaylistToM3u(Context context, String playlistName) {
        ArrayList<String> songs = getSongsInPlaylist(playlistName);
        String safeFileName = playlistName.replaceAll("[\\\\/:*?\"<>|]", "_") + ".m3u";

        File targetDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "ParrotPlaylists"
        );
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            targetDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        }
        if (targetDir == null) {
            targetDir = context.getFilesDir();
        }

        File outFile = new File(targetDir, safeFileName);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            StringBuilder sb = new StringBuilder("#EXTM3U\n");
            sb.append("#PLAYLIST:").append(playlistName).append("\n");
            for (String path : songs) {
                if (path != null && !path.isEmpty()) {
                    String title = new File(path).getName().replaceAll("(?i)\\.(mp3|wav|m4a|flac|ogg|aac)$", "");
                    sb.append("#EXTINF:-1,").append(title).append("\n");
                    sb.append(path).append("\n");
                }
            }
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
            return outFile;
        } catch (Exception e) {
            // Fallback to app external files dir if public Music directory is restricted by Scoped Storage
            try {
                File fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
                if (fallbackDir != null) {
                    File fallbackFile = new File(fallbackDir, safeFileName);
                    try (FileOutputStream fos = new FileOutputStream(fallbackFile)) {
                        StringBuilder sb = new StringBuilder("#EXTM3U\n");
                        for (String path : songs) {
                            if (path != null && !path.isEmpty()) {
                                sb.append(path).append("\n");
                            }
                        }
                        fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                        return fallbackFile;
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }
    }

    /**
     * Imports songs from an .m3u / .m3u8 InputStream into a playlist and returns the number of valid songs added.
     */
    public int importPlaylistFromM3uStream(String playlistName, InputStream inputStream, List<String> allLibrarySongs) {
        if (inputStream == null || playlistName == null) return 0;
        createPlaylist(playlistName);
        ArrayList<String> toAdd = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                File directFile = new File(trimmed);
                if (directFile.exists() && directFile.isFile()) {
                    toAdd.add(directFile.getAbsolutePath());
                } else if (allLibrarySongs != null) {
                    // Match by filename if the .m3u uses relative paths
                    String targetName = new File(trimmed.replace('\\', '/')).getName();
                    for (String libPath : allLibrarySongs) {
                        if (new File(libPath).getName().equalsIgnoreCase(targetName)) {
                            toAdd.add(libPath);
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (!toAdd.isEmpty()) {
            addSongsToPlaylist(playlistName, toAdd);
        }
        return toAdd.size();
    }
}
