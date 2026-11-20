package com.attomaticsystems.parrotplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks play counts and listening history to power Smart Auto-Playlists:
 * - Recently Added
 * - Most Played
 * - Recently Played
 */
public final class ListeningStatsManager {

    public static final String SMART_RECENTLY_ADDED = "__smart_recently_added__";
    public static final String SMART_MOST_PLAYED = "__smart_most_played__";
    public static final String SMART_RECENTLY_PLAYED = "__smart_recently_played__";

    private static final String PREFS_NAME = "parrot_listening_stats";
    private static final String KEY_HISTORY_JSON = "history_json";
    private static final String KEY_COUNTS_JSON = "counts_json";
    private static final int MAX_HISTORY_SIZE = 80;
    private static final int MAX_SMART_PLAYLIST_SIZE = 50;

    private ListeningStatsManager() {}

    /**
     * Records a track play in both Recently Played history and Most Played counts.
     */
    public static void recordSongPlayed(Context context, String songPath) {
        if (context == null || songPath == null || songPath.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        try {
            // 1. Update Recently Played history (most recent at index 0)
            ArrayList<String> history = loadHistoryList(prefs);
            history.remove(songPath);
            history.add(0, songPath);
            while (history.size() > MAX_HISTORY_SIZE) {
                history.remove(history.size() - 1);
            }
            JSONArray historyArr = new JSONArray();
            for (String p : history) {
                historyArr.put(p);
            }

            // 2. Increment play count
            String countsRaw = prefs.getString(KEY_COUNTS_JSON, "{}");
            JSONObject countsObj = new JSONObject(countsRaw);
            int currentCount = countsObj.optInt(songPath, 0);
            countsObj.put(songPath, currentCount + 1);

            prefs.edit()
                    .putString(KEY_HISTORY_JSON, historyArr.toString())
                    .putString(KEY_COUNTS_JSON, countsObj.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static int getPlayCount(Context context, String songPath) {
        if (context == null || songPath == null) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            JSONObject countsObj = new JSONObject(prefs.getString(KEY_COUNTS_JSON, "{}"));
            return countsObj.optInt(songPath, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public static ArrayList<String> getSmartPlaylistSongs(Context context, String smartId) {
        if (SMART_RECENTLY_ADDED.equals(smartId)) {
            return getRecentlyAddedSongs(context);
        } else if (SMART_MOST_PLAYED.equals(smartId)) {
            return getMostPlayedSongs(context);
        } else if (SMART_RECENTLY_PLAYED.equals(smartId)) {
            return getRecentlyPlayedSongs(context);
        }
        return new ArrayList<>();
    }

    public static ArrayList<String> getRecentlyAddedSongs(Context context) {
        ArrayList<String> result = new ArrayList<>();
        if (context == null || !PermissionHelper.hasAudioPermission(context)) return result;

        try {
            Cursor cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media.DATA},
                    MediaStore.Audio.Media.IS_MUSIC + "!=0",
                    null,
                    MediaStore.Audio.Media.DATE_ADDED + " DESC"
            );
            if (cursor != null) {
                while (cursor.moveToNext() && result.size() < MAX_SMART_PLAYLIST_SIZE) {
                    String path = cursor.getString(0);
                    if (path != null && MusicFolderHelper.isPathAllowed(context, path) && new File(path).exists()) {
                        result.add(path);
                    }
                }
                cursor.close();
            }
        } catch (Exception ignored) {}
        return result;
    }

    public static ArrayList<String> getRecentlyPlayedSongs(Context context) {
        ArrayList<String> result = new ArrayList<>();
        if (context == null) return result;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ArrayList<String> history = loadHistoryList(prefs);
        for (String path : history) {
            if (path != null && new File(path).exists() && MusicFolderHelper.isPathAllowed(context, path)) {
                result.add(path);
                if (result.size() >= MAX_SMART_PLAYLIST_SIZE) break;
            }
        }
        return result;
    }

    public static ArrayList<String> getMostPlayedSongs(Context context) {
        ArrayList<String> result = new ArrayList<>();
        if (context == null) return result;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        try {
            JSONObject countsObj = new JSONObject(prefs.getString(KEY_COUNTS_JSON, "{}"));
            List<PlayedEntry> entries = new ArrayList<>();
            Iterator<String> keys = countsObj.keys();
            while (keys.hasNext()) {
                String path = keys.next();
                int count = countsObj.optInt(path, 0);
                if (count > 0 && path != null && new File(path).exists()
                        && MusicFolderHelper.isPathAllowed(context, path)) {
                    entries.add(new PlayedEntry(path, count));
                }
            }
            Collections.sort(entries, (a, b) -> Integer.compare(b.count, a.count));
            for (int i = 0; i < Math.min(MAX_SMART_PLAYLIST_SIZE, entries.size()); i++) {
                result.add(entries.get(i).path);
            }
        } catch (Exception ignored) {}
        return result;
    }

    public static void clearSmartPlaylistStats(Context context, String smartId) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (SMART_RECENTLY_PLAYED.equals(smartId)) {
            prefs.edit().remove(KEY_HISTORY_JSON).apply();
        } else if (SMART_MOST_PLAYED.equals(smartId)) {
            prefs.edit().remove(KEY_COUNTS_JSON).apply();
        }
    }

    private static ArrayList<String> loadHistoryList(SharedPreferences prefs) {
        ArrayList<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_HISTORY_JSON, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String val = arr.optString(i, null);
                if (val != null && !val.isEmpty()) {
                    list.add(val);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static final class PlayedEntry {
        final String path;
        final int count;

        PlayedEntry(String path, int count) {
            this.path = path;
            this.count = count;
        }
    }
}
