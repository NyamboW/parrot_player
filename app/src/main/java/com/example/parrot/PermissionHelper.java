package com.example.parrot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Central permission manager supporting Android 5.0 (API 21) through Android 15+ (API 35).
 */
public final class PermissionHelper {

    public static final String ACTION_PERMISSIONS_GRANTED = "com.example.parrot.PERMISSIONS_GRANTED";
    private static boolean settingsDialogShowing = false;

    private PermissionHelper() {}

    /**
     * Returns true if the app currently has permission to read audio files on the device.
     */
    public static boolean hasAudioPermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Android 5.0 - 5.1 (API 21-22): permissions are granted at install time
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13, 14, 15+ (API 33+)
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        // Android 6.0 - 12L (API 23-32)
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Ensures permissions required to delete audio files are granted across API 21-35.
     */
    public static void ensureDeletePermissions(Context context, Runnable onGranted) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            boolean hasRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            boolean hasWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!hasRead || !hasWrite) {
                Dexter.withContext(context)
                        .withPermissions(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                        .withListener(new MultiplePermissionsListener() {
                            @Override
                            public void onPermissionsChecked(MultiplePermissionsReport report) {
                                if (report != null && report.areAllPermissionsGranted()) {
                                    if (onGranted != null) onGranted.run();
                                } else if (report != null && report.isAnyPermissionPermanentlyDenied()) {
                                    showPermissionSettingsDialog(context);
                                }
                            }

                            @Override
                            public void onPermissionRationaleShouldBeShown(
                                    List<PermissionRequest> list, PermissionToken token) {
                                token.continuePermissionRequest();
                            }
                        }).check();
                return;
            }
        }
        ensurePermissions(context, onGranted);
    }

    /**
     * Requests the appropriate audio/storage and notification permissions for the current Android version.
     */
    public static void ensurePermissions(Context context, Runnable onAudioGranted) {
        if (context == null) return;

        if (hasAudioPermission(context)) {
            // Also request POST_NOTIFICATIONS silently on Android 13+ if not yet granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Dexter.withContext(context)
                        .withPermissions(Manifest.permission.POST_NOTIFICATIONS)
                        .withListener(new MultiplePermissionsListener() {
                            @Override
                            public void onPermissionsChecked(MultiplePermissionsReport report) {}

                            @Override
                            public void onPermissionRationaleShouldBeShown(
                                    List<PermissionRequest> list, PermissionToken token) {
                                token.continuePermissionRequest();
                            }
                        }).check();
            }
            if (onAudioGranted != null) {
                onAudioGranted.run();
            }
            return;
        }

        List<String> permissionsToRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 - 12L (API 30-32)
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            // Android 6.0 - 10 (API 23-29)
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        Dexter.withContext(context)
                .withPermissions(permissionsToRequest)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (hasAudioPermission(context)) {
                            Intent broadcast = new Intent(ACTION_PERMISSIONS_GRANTED);
                            broadcast.setPackage(context.getPackageName());
                            context.sendBroadcast(broadcast);

                            if (onAudioGranted != null) {
                                onAudioGranted.run();
                            }
                        } else if (report != null && report.isAnyPermissionPermanentlyDenied()) {
                            showPermissionSettingsDialog(context);
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(
                            List<PermissionRequest> list, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    private static void showPermissionSettingsDialog(Context context) {
        if (settingsDialogShowing) return;
        if (context instanceof Activity) {
            Activity act = (Activity) context;
            if (act.isFinishing() || act.isDestroyed()) return;
        }
        settingsDialogShowing = true;
        new AlertDialog.Builder(context)
                .setTitle("Audio Permission Required")
                .setMessage("Parrot needs permission to access your music files. Please grant Music & Audio access in App Settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    settingsDialogShowing = false;
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", (dialog, which) -> settingsDialogShowing = false)
                .setOnCancelListener(d -> settingsDialogShowing = false)
                .show();
    }
}
