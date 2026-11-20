package com.attomaticsystems.parrotplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util. LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Central helper for Poweramp/AIMP-style music folder selection and filtering.
 * Excludes voice notes, call recordings, and unselected directories across all tabs.
 */
public final class MusicFolderHelper {

    public static final String PREFS_NAME = "parrot_settings";
    public static final String KEY_MUSIC_FOLDERS = "music_folders";
    public static final String KEY_FOLDERS_CONFIGURED = "folders_configured";
    public static final String ACTION_FOLDERS_UPDATED = "com.attomaticsystems.parrotplayer.FOLDERS_UPDATED";

    private static boolean startupPromptShownInSession = false;

    private MusicFolderHelper() {}

    public static boolean isConfigured(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_FOLDERS_CONFIGURED, false)) {
            return true;
        }
        Set<String> existing = prefs.getStringSet(KEY_MUSIC_FOLDERS, null);
        return existing != null && !existing.isEmpty();
    }

    public static Set<String> getSelectedFolders(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_MUSIC_FOLDERS, null);
        if (saved != null) {
            return new HashSet<>(saved);
        }
        return null;
    }

    /**
     * Checks whether a song file path belongs to the user's selected music folders.
     * If folders have not been configured yet, falls back to excluding known voice-note
     * and recording directories until the user completes the startup folder dialog.
     */
    public static boolean isPathAllowed(Context context, String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        File file = new File(filePath);
        String parent = file.getParent();
        return isFolderAllowed(context, parent);
    }

    public static boolean isFolderAllowed(Context context, String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) return false;
        Set<String> selectedFolders = getSelectedFolders(context);
        if (selectedFolders != null) {
            return selectedFolders.contains(folderPath);
        }
        return !isLikelyNonMusicFolder(folderPath);
    }

    /**
     * Heuristic to identify WhatsApp/Telegram voice notes, call recordings, ringtones, etc.
     */
    public static boolean isLikelyNonMusicFolder(String folderPath) {
        if (folderPath == null) return true;
        String lower = folderPath.toLowerCase(Locale.ROOT).replace('\\', '/');
        return lower.contains("whatsapp voice notes")
                || lower.contains("whatsapp audio")
                || lower.contains("/voice notes")
                || lower.contains("/voicenotes")
                || lower.contains("/recordings")
                || lower.contains("/callrecordings")
                || lower.contains("/call_rec")
                || lower.contains("/sound_recorder")
                || lower.contains("/voice recorder")
                || lower.contains("/telegram/telegram audio")
                || lower.contains("/telegram audio")
                || lower.contains("/ringtones")
                || lower.contains("/notifications")
                || lower.contains("/alarms");
    }

    public static boolean isLikelyDefaultMusicFolder(String folderPath) {
        if (folderPath == null || isLikelyNonMusicFolder(folderPath)) return false;
        String lower = folderPath.toLowerCase(Locale.ROOT).replace('\\', '/');
        return lower.endsWith("/music")
                || lower.contains("/music/")
                || lower.endsWith("/download")
                || lower.contains("/download/")
                || lower.endsWith("/downloads")
                || lower.contains("/downloads/")
                || lower.contains("/songs")
                || lower.contains("/audio");
    }

    /**
     * Prompts the user on startup if music folders haven't been configured yet.
     */
    public static void promptIfNotConfigured(Context context, Runnable onFoldersSaved) {
        if (isConfigured(context) || startupPromptShownInSession) {
            return;
        }
        startupPromptShownInSession = true;
        showFolderSelectionDialog(context, true, onFoldersSaved);
    }

    public static void showFolderSelectionDialog(Context context, boolean isFirstRun, Runnable onFoldersSaved) {
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        Map<String, Integer> folderCounts = new LinkedHashMap<>();

        try {
            Cursor cursor = context.getContentResolver().query(
                    uri,
                    new String[]{MediaStore.Audio.Media.DATA},
                    selection,
                    null,
                    MediaStore.Audio.Media.DATA + " ASC"
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(0);
                    if (path != null) {
                        String parent = new File(path).getParent();
                        if (parent != null) {
                            Integer current = folderCounts.get(parent);
                            folderCounts.put(parent, current == null ? 1 : current + 1);
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception ignored) {
        }

        List<String> folderList = new ArrayList<>(folderCounts.keySet());
        Collections.sort(folderList, (a, b) -> {
            boolean aMusic = isLikelyDefaultMusicFolder(a);
            boolean bMusic = isLikelyDefaultMusicFolder(b);
            if (aMusic != bMusic) return aMusic ? -1 : 1;
            boolean aJunk = isLikelyNonMusicFolder(a);
            boolean bJunk = isLikelyNonMusicFolder(b);
            if (aJunk != bJunk) return aJunk ? 1 : -1;
            return a.compareToIgnoreCase(b);
        });

        if (folderList.isEmpty()) {
            if (!isFirstRun) {
                Toast.makeText(context, "No audio folders found on device", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String[] folderPaths = folderList.toArray(new String[0]);
        String[] displayLabels = new String[folderPaths.length];
        boolean[] checkedItems = new boolean[folderPaths.length];

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> savedFolders = prefs.getStringSet(KEY_MUSIC_FOLDERS, null);

        boolean anyPreChecked = false;
        for (int i = 0; i < folderPaths.length; i++) {
            String path = folderPaths[i];
            String folderName = new File(path).getName();
            int count = folderCounts.containsKey(path) ? folderCounts.get(path) : 0;
            displayLabels[i] = folderName + " (" + count + ")\n" + shortenPath(path);

            if (savedFolders != null) {
                checkedItems[i] = savedFolders.contains(path);
            } else {
                checkedItems[i] = isLikelyDefaultMusicFolder(path) || !isLikelyNonMusicFolder(path);
            }
            if (checkedItems[i]) {
                anyPreChecked = true;
            }
        }

        if (savedFolders == null && !anyPreChecked && checkedItems.length > 0) {
            checkedItems[0] = true;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(isFirstRun ? "Choose Music Folders" : "Select Music Folders");
        builder.setMultiChoiceItems(displayLabels, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
        });

        builder.setPositiveButton("Save", (dialog, which) -> {
            Set<String> selectedFolders = new HashSet<>();
            for (int i = 0; i < folderPaths.length; i++) {
                if (checkedItems[i]) {
                    selectedFolders.add(folderPaths[i]);
                }
            }
            prefs.edit()
                    .putStringSet(KEY_MUSIC_FOLDERS, selectedFolders)
                    .putBoolean(KEY_FOLDERS_CONFIGURED, true)
                    .apply();

            Intent broadcast = new Intent(ACTION_FOLDERS_UPDATED);
            broadcast.setPackage(context.getPackageName());
            context.sendBroadcast(broadcast);

            Toast.makeText(context,
                    "Scanning " + selectedFolders.size() + (selectedFolders.size() == 1 ? " music folder" : " music folders"),
                    Toast.LENGTH_SHORT).show();

            if (onFoldersSaved != null) {
                onFoldersSaved.run();
            }
        });

        if (!isFirstRun) {
            builder.setNegativeButton("Cancel", null);
        } else {
            builder.setCancelable(false);
        }

        builder.show();
    }

    private static String shortenPath(String fullPath) {
        if (fullPath == null) return "";
        return fullPath.replaceFirst("^/storage/emulated/0", "/Internal");
    }
}
