package com.example.parrot;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.HashSet;
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
        return new ArrayList<>(names);
    }

    public void createPlaylist(String name) {
        Set<String> names = new HashSet<>(prefs.getStringSet(KEY_PLAYLISTS, new HashSet<>()));
        names.add(name);
        prefs.edit().putStringSet(KEY_PLAYLISTS, names).apply();
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

    public void removeSongFromPlaylist(String playlistName, String songPath) {
        ArrayList<String> songs = getSongsInPlaylist(playlistName);
        if (songs.contains(songPath)) {
            songs.remove(songPath);
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
}
