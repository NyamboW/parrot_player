package com.example.parrot;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;

/**
 * Persists and restores player state (playlist, track index, seek position,
 * duration, shuffle/repeat flags, and queue) across app restarts.
 */
public class PlayerStateManager {

    private static final String PREFS_NAME = "parrot_player_state";
    private static final String KEY_SONGS = "songs_json";
    private static final String KEY_POSITION = "track_position";
    private static final String KEY_SONG_PATH = "current_song_path";
    private static final String KEY_SEEK_POSITION = "seek_position_ms";
    private static final String KEY_DURATION = "duration_ms";
    private static final String KEY_SHUFFLE = "is_shuffle";
    private static final String KEY_REPEAT = "is_repeat";
    private static final String KEY_REPEAT_MODE = "repeat_mode";
    private static final String KEY_QUEUE = "queue_json";

    private final SharedPreferences prefs;

    public static class RestoredState {
        public final ArrayList<String> songs = new ArrayList<>();
        public final ArrayList<String> queue = new ArrayList<>();
        public int position = 0;
        public int seekPosition = 0;
        public int duration = 0;
        public boolean isShuffle = false;
        public boolean isRepeat = false;
        public int repeatMode = 0;

        public boolean hasValidSong() {
            return !songs.isEmpty() && position >= 0 && position < songs.size();
        }
    }

    public PlayerStateManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void savePlaylist(ArrayList<String> songs) {
        prefs.edit().putString(KEY_SONGS, toJsonArray(songs)).apply();
    }

    public void saveQueue(ArrayList<String> queue) {
        prefs.edit().putString(KEY_QUEUE, toJsonArray(queue)).apply();
    }

    public void savePlaybackState(int position, String songPath, int seekPosition, int duration,
                                  boolean isShuffle, int repeatMode, boolean synchronous) {
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(KEY_POSITION, position)
                .putString(KEY_SONG_PATH, songPath)
                .putInt(KEY_SEEK_POSITION, Math.max(0, seekPosition))
                .putInt(KEY_DURATION, Math.max(0, duration))
                .putBoolean(KEY_SHUFFLE, isShuffle)
                .putBoolean(KEY_REPEAT, repeatMode == 2)
                .putInt(KEY_REPEAT_MODE, repeatMode);
        if (synchronous) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    public void saveSeekPosition(int seekPosition, int duration) {
        prefs.edit()
                .putInt(KEY_SEEK_POSITION, Math.max(0, seekPosition))
                .putInt(KEY_DURATION, Math.max(0, duration))
                .apply();
    }

    public void saveModes(boolean isShuffle, int repeatMode) {
        prefs.edit()
                .putBoolean(KEY_SHUFFLE, isShuffle)
                .putBoolean(KEY_REPEAT, repeatMode == 2)
                .putInt(KEY_REPEAT_MODE, repeatMode)
                .apply();
    }

    public RestoredState restore() {
        RestoredState state = new RestoredState();
        state.isShuffle = prefs.getBoolean(KEY_SHUFFLE, false);
        state.isRepeat = prefs.getBoolean(KEY_REPEAT, false);
        state.repeatMode = prefs.getInt(KEY_REPEAT_MODE, state.isRepeat ? 2 : 0);
        state.seekPosition = Math.max(0, prefs.getInt(KEY_SEEK_POSITION, 0));
        state.duration = Math.max(0, prefs.getInt(KEY_DURATION, 0));

        ArrayList<String> rawSongs = fromJsonArray(prefs.getString(KEY_SONGS, "[]"));
        for (String path : rawSongs) {
            if (path != null && new File(path).exists()) {
                state.songs.add(path);
            }
        }

        ArrayList<String> rawQueue = fromJsonArray(prefs.getString(KEY_QUEUE, "[]"));
        for (String path : rawQueue) {
            if (path != null && new File(path).exists()) {
                state.queue.add(path);
            }
        }

        if (state.songs.isEmpty()) {
            state.position = 0;
            state.seekPosition = 0;
            state.duration = 0;
            return state;
        }

        String savedPath = prefs.getString(KEY_SONG_PATH, null);
        int savedPos = prefs.getInt(KEY_POSITION, 0);

        if (savedPath != null) {
            int exactIndex = state.songs.indexOf(savedPath);
            if (exactIndex >= 0) {
                state.position = exactIndex;
            } else {
                state.position = Math.min(Math.max(0, savedPos), state.songs.size() - 1);
                state.seekPosition = 0;
                state.duration = 0;
            }
        } else {
            state.position = Math.min(Math.max(0, savedPos), state.songs.size() - 1);
        }

        if (state.duration > 0 && state.seekPosition > state.duration) {
            state.seekPosition = 0;
        }

        return state;
    }

    private static String toJsonArray(ArrayList<String> items) {
        JSONArray array = new JSONArray();
        if (items != null) {
            for (String item : items) {
                array.put(item);
            }
        }
        return array.toString();
    }

    private static ArrayList<String> fromJsonArray(String jsonStr) {
        ArrayList<String> result = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, null);
                if (value != null && !value.isEmpty()) {
                    result.add(value);
                }
            }
        } catch (JSONException ignored) {
        }
        return result;
    }
}
