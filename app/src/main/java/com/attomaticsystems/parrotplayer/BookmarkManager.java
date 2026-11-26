package com.attomaticsystems.parrotplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Manages per-track timestamp bookmarks and automatic resume positions
 * for long tracks (10+ minutes: DJ mixes, podcasts, audiobooks).
 */
public final class BookmarkManager {

    private static final String PREFS_NAME = "parrot_bookmarks";
    private static final String PREFIX_BOOKMARKS = "bm_";
    private static final String PREFIX_RESUME = "resume_";
    public static final int LONG_TRACK_THRESHOLD_MS = 10 * 60 * 1000; // 10 minutes

    public interface BookmarkSelectCallback {
        void onBookmarkSelected(int positionMs);
    }

    private BookmarkManager() {}

    public static ArrayList<Integer> getBookmarks(Context context, String songPath) {
        ArrayList<Integer> list = new ArrayList<>();
        if (context == null || songPath == null || songPath.isEmpty()) return list;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(PREFIX_BOOKMARKS + songPath, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                int ms = arr.optInt(i, -1);
                if (ms >= 0 && !list.contains(ms)) {
                    list.add(ms);
                }
            }
            Collections.sort(list);
        } catch (Exception ignored) {}
        return list;
    }

    public static void addBookmark(Context context, String songPath, int positionMs) {
        if (context == null || songPath == null || songPath.isEmpty() || positionMs < 0) return;
        ArrayList<Integer> list = getBookmarks(context, songPath);
        // Avoid duplicate bookmark within 1 second
        for (int existing : list) {
            if (Math.abs(existing - positionMs) < 1000) {
                return;
            }
        }
        list.add(positionMs);
        Collections.sort(list);
        saveBookmarkList(context, songPath, list);
    }

    public static void clearBookmarks(Context context, String songPath) {
        if (context == null || songPath == null || songPath.isEmpty()) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREFIX_BOOKMARKS + songPath)
                .apply();
    }

    private static void saveBookmarkList(Context context, String songPath, ArrayList<Integer> list) {
        JSONArray arr = new JSONArray();
        for (int ms : list) {
            arr.put(ms);
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREFIX_BOOKMARKS + songPath, arr.toString())
                .apply();
    }

    /**
     * Auto-saves playback position for long tracks (>= 10 min) so mixes/podcasts resume seamlessly.
     */
    public static void saveLongTrackPosition(Context context, String songPath, int positionMs, int durationMs) {
        if (context == null || songPath == null || songPath.isEmpty() || durationMs < LONG_TRACK_THRESHOLD_MS) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (positionMs > 15_000 && positionMs < durationMs - 20_000) {
            prefs.edit().putInt(PREFIX_RESUME + songPath, positionMs).apply();
        } else if (positionMs >= durationMs - 20_000) {
            prefs.edit().remove(PREFIX_RESUME + songPath).apply();
        }
    }

    public static int getLongTrackResumePosition(Context context, String songPath, int durationMs) {
        if (context == null || songPath == null || songPath.isEmpty() || durationMs < LONG_TRACK_THRESHOLD_MS) {
            return 0;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int saved = prefs.getInt(PREFIX_RESUME + songPath, 0);
        return (saved > 15_000 && saved < durationMs - 20_000) ? saved : 0;
    }

    public static void showBookmarksDialog(final Context context,
                                           final String songPath,
                                           final int currentPosMs,
                                           final BookmarkSelectCallback onSelect) {
        if (context == null || songPath == null || songPath.isEmpty()) return;
        final ArrayList<Integer> bookmarks = getBookmarks(context, songPath);
        ArrayList<String> options = new ArrayList<>();
        options.add("+ Save Bookmark at " + formatTimestamp(currentPosMs));
        for (int i = 0; i < bookmarks.size(); i++) {
            options.add("Bookmark #" + (i + 1) + "   •   " + formatTimestamp(bookmarks.get(i)));
        }
        if (!bookmarks.isEmpty()) {
            options.add("Delete All Bookmarks");
        }

        ParrotModalHelper.showSingleChoiceDialog(
                context,
                "Track Bookmarks",
                options.toArray(new String[0]),
                -1,
                which -> {
                    if (which == 0) {
                        addBookmark(context, songPath, currentPosMs);
                        Toast.makeText(context,
                                "Saved bookmark at " + formatTimestamp(currentPosMs),
                                Toast.LENGTH_SHORT).show();
                    } else if (!bookmarks.isEmpty() && which == options.size() - 1) {
                        clearBookmarks(context, songPath);
                        Toast.makeText(context, "Bookmarks cleared", Toast.LENGTH_SHORT).show();
                    } else {
                        int bmIdx = which - 1;
                        if (bmIdx >= 0 && bmIdx < bookmarks.size() && onSelect != null) {
                            onSelect.onBookmarkSelected(bookmarks.get(bmIdx));
                        }
                    }
                }
        );
    }

    public static String formatTimestamp(int millis) {
        int safe = Math.max(0, millis);
        int totalSec = safe / 1000;
        int hours = totalSec / 3600;
        int min = (totalSec % 3600) / 60;
        int sec = totalSec % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, min, sec);
        }
        return String.format(Locale.US, "%02d:%02d", min, sec);
    }
}
