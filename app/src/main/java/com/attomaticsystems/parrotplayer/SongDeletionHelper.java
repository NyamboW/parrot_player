package com.attomaticsystems.parrotplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles single and multi-track file deletion across Android 5.0 (API 21) through Android 15+ (API 35),
 * including Scoped Storage RecoverableSecurityException (Android 10) and MediaStore.createDeleteRequest (Android 11+).
 */
public class SongDeletionHelper {

    public interface ContextProvider {
        Context getContext();
    }

    private final ContextProvider contextProvider;
    private final ActivityResultLauncher<IntentSenderRequest> intentSenderLauncher;
    private final ArrayList<String> pendingDeletePaths = new ArrayList<>();
    private Runnable pendingOnComplete;

    private SongDeletionHelper(ContextProvider provider,
                               ActivityResultLauncher<IntentSenderRequest> launcher) {
        this.contextProvider = provider;
        this.intentSenderLauncher = launcher;
    }

    /**
     * Must be called during Fragment.onCreate() or Fragment.onCreateView() before fragment starts.
     */
    public static SongDeletionHelper forFragment(Fragment fragment) {
        final SongDeletionHelper[] holder = new SongDeletionHelper[1];
        ActivityResultLauncher<IntentSenderRequest> launcher = fragment.registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (holder[0] != null) {
                        holder[0].onIntentSenderResult(result.getResultCode());
                    }
                }
        );
        holder[0] = new SongDeletionHelper(fragment::getContext, launcher);
        return holder[0];
    }

    /**
     * Must be called during Activity.onCreate().
     */
    public static SongDeletionHelper forActivity(AppCompatActivity activity) {
        final SongDeletionHelper[] holder = new SongDeletionHelper[1];
        ActivityResultLauncher<IntentSenderRequest> launcher = activity.registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (holder[0] != null) {
                        holder[0].onIntentSenderResult(result.getResultCode());
                    }
                }
        );
        holder[0] = new SongDeletionHelper(() -> activity, launcher);
        return holder[0];
    }

    public void deleteSong(String path, Runnable onComplete) {
        if (path == null || path.isEmpty()) return;
        deleteSongs(Collections.singletonList(path), onComplete);
    }

    public void deleteSongs(List<String> paths, Runnable onComplete) {
        Context context = contextProvider.getContext();
        if (context == null || paths == null || paths.isEmpty()) return;

        final ArrayList<String> validPaths = new ArrayList<>();
        for (String p : paths) {
            if (p != null && !p.isEmpty()) {
                validPaths.add(p);
            }
        }
        if (validPaths.isEmpty()) return;

        PermissionHelper.ensureDeletePermissions(context, () -> {
            Context ctx = contextProvider.getContext();
            if (ctx == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30-35): MediaStore.createDeleteRequest shows the native OS confirmation prompt
                executeScopedStorageDeleteApi30(ctx, validPaths, onComplete);
            } else {
                // Android 5.0 - 10 (API 21-29): Show confirmation dialog first
                String title = validPaths.size() == 1 ? "Delete Song" : "Delete Songs";
                String message = validPaths.size() == 1
                        ? "Are you sure you want to permanently delete this song from your device?"
                        : "Are you sure you want to permanently delete " + validPaths.size() + " songs from your device?";

                new AlertDialog.Builder(ctx)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("Delete", (dialog, which) ->
                                executeLegacyOrApi29Delete(ctx, validPaths, onComplete))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void executeScopedStorageDeleteApi30(Context context,
                                                 ArrayList<String> paths,
                                                 Runnable onComplete) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;

        ContentResolver resolver = context.getContentResolver();
        ArrayList<Uri> urisToRequest = new ArrayList<>();
        ArrayList<String> directlyDeleted = new ArrayList<>();

        for (String path : paths) {
            Uri mediaUri = queryMediaStoreUri(resolver, path);
            if (mediaUri != null) {
                urisToRequest.add(mediaUri);
            } else {
                File f = new File(path);
                if (!f.exists() || f.delete()) {
                    directlyDeleted.add(path);
                }
            }
        }

        if (urisToRequest.isEmpty()) {
            if (!directlyDeleted.isEmpty()) {
                cleanupDeletedReferences(context, directlyDeleted);
                Toast.makeText(context,
                        directlyDeleted.size() == 1 ? "Song deleted" : directlyDeleted.size() + " songs deleted",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Could not locate song in MediaStore", Toast.LENGTH_SHORT).show();
            }
            if (onComplete != null) onComplete.run();
            return;
        }

        try {
            PendingIntent editPendingIntent = MediaStore.createDeleteRequest(resolver, urisToRequest);
            pendingDeletePaths.clear();
            pendingDeletePaths.addAll(paths);
            pendingOnComplete = onComplete;

            IntentSenderRequest request = new IntentSenderRequest.Builder(
                    editPendingIntent.getIntentSender()
            ).build();
            intentSenderLauncher.launch(request);
        } catch (Exception e) {
            Toast.makeText(context, "Unable to request delete permission: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void executeLegacyOrApi29Delete(Context context,
                                            ArrayList<String> paths,
                                            Runnable onComplete) {
        ContentResolver resolver = context.getContentResolver();
        ArrayList<String> deletedNow = new ArrayList<>();
        ArrayList<String> remainingPaths = new ArrayList<>();
        RecoverableSecurityException recoverableError = null;

        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            Uri mediaUri = queryMediaStoreUri(resolver, path);
            try {
                boolean deleted = false;
                if (mediaUri != null) {
                    int rows = resolver.delete(mediaUri, null, null);
                    deleted = (rows > 0);
                }
                File file = new File(path);
                if (file.exists()) {
                    if (file.delete()) {
                        deleted = true;
                    }
                }
                if (mediaUri == null) {
                    resolver.delete(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            MediaStore.Audio.Media.DATA + "=?",
                            new String[]{path}
                    );
                }
                if (deleted || !file.exists()) {
                    deletedNow.add(path);
                }
            } catch (SecurityException se) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && se instanceof RecoverableSecurityException) {
                    recoverableError = (RecoverableSecurityException) se;
                    for (int j = i; j < paths.size(); j++) {
                        remainingPaths.add(paths.get(j));
                    }
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (!deletedNow.isEmpty()) {
            cleanupDeletedReferences(context, deletedNow);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && recoverableError != null) {
            try {
                pendingDeletePaths.clear();
                pendingDeletePaths.addAll(remainingPaths);
                pendingOnComplete = onComplete;
                IntentSenderRequest request = new IntentSenderRequest.Builder(
                        recoverableError.getUserAction().getActionIntent().getIntentSender()
                ).build();
                intentSenderLauncher.launch(request);
                return;
            } catch (Exception ignored) {}
        }

        if (!deletedNow.isEmpty()) {
            Toast.makeText(context,
                    deletedNow.size() == 1 ? "Song deleted" : deletedNow.size() + " songs deleted",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Failed to delete song", Toast.LENGTH_SHORT).show();
        }
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void onIntentSenderResult(int resultCode) {
        Context context = contextProvider.getContext();
        ArrayList<String> paths = new ArrayList<>(pendingDeletePaths);
        Runnable callback = pendingOnComplete;
        pendingDeletePaths.clear();
        pendingOnComplete = null;

        if (context == null) return;

        if (resultCode == Activity.RESULT_OK) {
            // On Android 10 (API 29), after user approves RecoverableSecurityException for the first file,
            // finish deleting any remaining files. On Android 11+ (API 30+), MediaStore.createDeleteRequest
            // already deleted all approved URIs.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && !paths.isEmpty()) {
                executeLegacyOrApi29Delete(context, paths, callback);
                return;
            }

            cleanupDeletedReferences(context, paths);
            Toast.makeText(context,
                    paths.size() == 1 ? "Song deleted" : paths.size() + " songs deleted",
                    Toast.LENGTH_SHORT).show();
            if (callback != null) {
                callback.run();
            }
        }
    }

    private static Uri queryMediaStoreUri(ContentResolver resolver, String filePath) {
        if (filePath == null) return null;
        try {
            Cursor cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID},
                    MediaStore.Audio.Media.DATA + "=?",
                    new String[]{filePath},
                    null
            );
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        long id = cursor.getLong(0);
                        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void cleanupDeletedReferences(Context context, List<String> deletedPaths) {
        if (context == null || deletedPaths == null || deletedPaths.isEmpty()) return;

        FavouritesManager favManager = new FavouritesManager(context);
        PlaylistManager playlistManager = new PlaylistManager(context);
        ArrayList<String> playlists = playlistManager.getPlaylists();

        for (String path : deletedPaths) {
            if (favManager.isFavourite(path)) {
                favManager.removeFavourite(path);
            }
            for (String pl : playlists) {
                playlistManager.removeSongFromPlaylist(pl, path);
            }
        }

        MusicService service = MusicService.getActiveInstance();
        if (service != null) {
            service.onSongsDeletedFromStorage(deletedPaths);
        }

        Intent folderUpdate = new Intent(MusicFolderHelper.ACTION_FOLDERS_UPDATED);
        folderUpdate.setPackage(context.getPackageName());
        context.sendBroadcast(folderUpdate);
    }
}
