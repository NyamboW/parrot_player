package com.attomaticsystems.parrotplayer;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Shared handler for extended song popup menu actions across all song lists:
 * - Go to Artist
 * - Go to Album
 * - Song Info & Details
 * - Share Song
 * - Set as Ringtone
 */
public final class SongMenuActionsHelper {

    private SongMenuActionsHelper() {}

    /**
     * Handles any of the extended song menu item IDs and returns true if handled.
     */
    public static boolean handleCommonMenuAction(final Context context, int itemId, final String songPath) {
        if (context == null || songPath == null || songPath.isEmpty()) return false;

        if (itemId == R.id.action_song_details) {
            TrackMetadataHelper.loadTrackInfoAsync(songPath, info ->
                    TrackMetadataHelper.showTrackDetailsDialog(context, info));
            return true;
        } else if (itemId == R.id.action_edit_tags) {
            TrackTagEditorHelper.showTagEditorDialog(context, songPath);
            return true;
        } else if (itemId == R.id.action_share_song) {
            shareSong(context, songPath);
            return true;
        } else if (itemId == R.id.action_go_to_artist) {
            goToArtist(context, songPath);
            return true;
        } else if (itemId == R.id.action_go_to_album) {
            goToAlbum(context, songPath);
            return true;
        } else if (itemId == R.id.action_set_ringtone) {
            setAsRingtone(context, songPath);
            return true;
        }
        return false;
    }

    public static void shareSong(Context context, String songPath) {
        Uri mediaUri = queryMediaStoreAudioUri(context, songPath);
        if (mediaUri == null) {
            Toast.makeText(context, "Unable to share this audio file", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("audio/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(shareIntent, "Share Song"));
        } catch (Exception e) {
            Toast.makeText(context, "No app found to share audio", Toast.LENGTH_SHORT).show();
        }
    }

    public static void goToArtist(final Context context, String songPath) {
        TrackMetadataHelper.loadTrackInfoAsync(songPath, info -> {
            if (info != null && info.hasKnownArtist()) {
                Intent intent = new Intent(context, GroupSongsActivity.class);
                intent.putExtra("type", "artist");
                intent.putExtra("name", info.artist);
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "Unknown artist for this track", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void goToAlbum(final Context context, String songPath) {
        TrackMetadataHelper.loadTrackInfoAsync(songPath, info -> {
            if (info != null && info.hasKnownAlbum()) {
                Intent intent = new Intent(context, GroupSongsActivity.class);
                intent.putExtra("type", "album");
                intent.putExtra("name", info.album);
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "Unknown album for this track", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void setAsRingtone(final Context context, final String songPath) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            ParrotModalHelper.showConfirmDialog(
                    context,
                    "Allow System Settings Access",
                    "To set a song as your phone's ringtone, please allow Parrot to modify system settings.",
                    "Open Settings",
                    "Cancel",
                    false,
                    () -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                            intent.setData(Uri.parse("package:" + context.getPackageName()));
                            context.startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(context, "Unable to open settings", Toast.LENGTH_SHORT).show();
                        }
                    },
                    null,
                    null
            );
            return;
        }

        Uri mediaUri = queryMediaStoreAudioUri(context, songPath);
        if (mediaUri == null) {
            Toast.makeText(context, "Could not locate track in MediaStore", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
                context.getContentResolver().update(mediaUri, values, null, null);
            }
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, mediaUri);
            Toast.makeText(context, "Set as default ringtone", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Failed to set ringtone: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static Uri queryMediaStoreAudioUri(Context context, String filePath) {
        if (context == null || filePath == null) return null;
        try {
            Cursor cursor = context.getContentResolver().query(
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
}
