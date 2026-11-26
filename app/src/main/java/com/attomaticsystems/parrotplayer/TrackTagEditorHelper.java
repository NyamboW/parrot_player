package com.attomaticsystems.parrotplayer;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * In-App Tag & Lyrics Editor: allows editing Title, Artist, Album, and Lyrics (plain or LRC)
 * for any audio track, persisting overrides and updating MediaStore/companion LRC files.
 */
public final class TrackTagEditorHelper {

    private static final String PREFS_NAME = "parrot_tag_overrides";
    private static volatile Context appContext;

    public static final class TagOverride {
        public final String title;
        public final String artist;
        public final String album;
        public final String lyrics;

        public TagOverride(String title, String artist, String album, String lyrics) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.lyrics = lyrics;
        }
    }

    private TrackTagEditorHelper() {}

    public static void init(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }
    }

    @Nullable
    public static TagOverride getOverride(@Nullable Context context, @Nullable String songPath) {
        if (songPath == null || songPath.isEmpty()) return null;
        Context ctx = context != null ? context.getApplicationContext() : appContext;
        if (ctx == null) return null;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString("tag_" + songPath, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(raw);
            return new TagOverride(
                    obj.optString("title", ""),
                    obj.optString("artist", ""),
                    obj.optString("album", ""),
                    obj.optString("lyrics", "")
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void saveOverride(Context context, String songPath,
                                    String title, String artist, String album, String lyrics) {
        if (context == null || songPath == null || songPath.isEmpty()) return;
        init(context);
        try {
            JSONObject obj = new JSONObject();
            obj.put("title", title != null ? title.trim() : "");
            obj.put("artist", artist != null ? artist.trim() : "");
            obj.put("album", album != null ? album.trim() : "");
            obj.put("lyrics", lyrics != null ? lyrics : "");
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("tag_" + songPath, obj.toString())
                    .apply();
        } catch (Exception ignored) {}

        // Best-effort update in MediaStore for pre-scoped or owned files
        try {
            ContentValues values = new ContentValues();
            if (title != null && !title.trim().isEmpty()) {
                values.put(MediaStore.Audio.Media.TITLE, title.trim());
            }
            if (artist != null && !artist.trim().isEmpty()) {
                values.put(MediaStore.Audio.Media.ARTIST, artist.trim());
            }
            if (album != null && !album.trim().isEmpty()) {
                values.put(MediaStore.Audio.Media.ALBUM, album.trim());
            }
            if (values.size() > 0) {
                context.getContentResolver().update(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        values,
                        MediaStore.Audio.Media.DATA + "=?",
                        new String[]{songPath}
                );
            }
        } catch (Exception ignored) {}

        // Best-effort write companion .lrc file if lyrics were provided and directory is writable
        if (lyrics != null && !lyrics.trim().isEmpty()) {
            try {
                File audioFile = new File(songPath);
                File parent = audioFile.getParentFile();
                if (parent != null && parent.canWrite()) {
                    String name = audioFile.getName();
                    int dot = name.lastIndexOf('.');
                    String baseName = (dot > 0) ? name.substring(0, dot) : name;
                    File lrcFile = new File(parent, baseName + ".lrc");
                    try (FileOutputStream fos = new FileOutputStream(lrcFile, false)) {
                        fos.write(lyrics.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception ignored) {}
        }

        TrackMetadataHelper.invalidateCache(songPath);
        LyricsExtractor.invalidateCache(songPath);

        Intent folderRefresh = new Intent(MusicFolderHelper.ACTION_FOLDERS_UPDATED);
        folderRefresh.setPackage(context.getPackageName());
        context.sendBroadcast(folderRefresh);

        Intent serviceUpdate = new Intent(context, MusicService.class);
        serviceUpdate.setAction(MusicService.ACTION_REQUEST_UPDATE);
        try {
            context.startService(serviceUpdate);
        } catch (Exception ignored) {}
    }

    public static void showTagEditorDialog(final Context context, final String songPath) {
        if (context == null || songPath == null || songPath.isEmpty()) return;
        if (context instanceof Activity) {
            Activity act = (Activity) context;
            if (act.isFinishing() || act.isDestroyed()) return;
        }
        init(context);

        TrackMetadataHelper.loadTrackInfoAsync(songPath, info -> {
            LyricsExtractor.loadLyricsAsync(songPath, lyricsData -> {
                if (context instanceof Activity) {
                    Activity act = (Activity) context;
                    if (act.isFinishing() || act.isDestroyed()) return;
                }
                buildAndShowDialog(context, songPath, info, lyricsData);
            });
        });
    }

    private static void buildAndShowDialog(final Context context,
                                           final String songPath,
                                           @Nullable TrackMetadataHelper.TrackInfo info,
                                           @Nullable LyricsExtractor.LyricsData lyricsData) {
        final float density = context.getResources().getDisplayMetrics().density;
        final int accent = AccentColorHelper.getAccentColor(context);

        TagOverride existingOverride = getOverride(context, songPath);
        String initialTitle = (existingOverride != null && !existingOverride.title.isEmpty())
                ? existingOverride.title
                : (info != null ? info.title : "");
        String initialArtist = (existingOverride != null && !existingOverride.artist.isEmpty())
                ? existingOverride.artist
                : (info != null ? info.artist : "");
        String initialAlbum = (existingOverride != null && !existingOverride.album.isEmpty())
                ? existingOverride.album
                : (info != null ? info.album : "");

        String initialLyrics = "";
        if (existingOverride != null && !existingOverride.lyrics.isEmpty()) {
            initialLyrics = existingOverride.lyrics;
        } else if (lyricsData != null && lyricsData.hasLyrics()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lyricsData.lines.size(); i++) {
                LyricsExtractor.LyricLine line = lyricsData.lines.get(i);
                if (i > 0) sb.append("\n");
                if (lyricsData.isSynced && line.timeMs >= 0) {
                    long totalSec = line.timeMs / 1000;
                    long min = totalSec / 60;
                    long sec = totalSec % 60;
                    long cs = (line.timeMs % 1000) / 10;
                    sb.append(String.format(java.util.Locale.US, "[%02d:%02d.%02d] ", min, sec, cs));
                }
                sb.append(line.text);
            }
            initialLyrics = sb.toString();
        }

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * density);
        card.setPadding(pad, pad, pad, Math.round(16 * density));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#181C24"));
        cardBg.setCornerRadius(24 * density);
        cardBg.setStroke(Math.round(1 * density), Color.parseColor("#2B3140"));
        card.setBackground(cardBg);

        // Header
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(context);
        int iconSize = Math.round(22 * density);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMarginEnd(Math.round(10 * density));
        icon.setLayoutParams(iconLp);
        icon.setImageResource(R.drawable.ic_music);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(accent));
        header.addView(icon);

        TextView txtHeader = new TextView(context);
        txtHeader.setText("Edit Track Tags & Lyrics");
        txtHeader.setTextColor(Color.WHITE);
        txtHeader.setTextSize(18f);
        txtHeader.setTypeface(null, Typeface.BOLD);
        header.addView(txtHeader);
        card.addView(header);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.topMargin = Math.round(14 * density);
        scrollView.setLayoutParams(scrollLp);

        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(form);

        final EditText inputTitle = addLabeledInput(context, form, "TITLE", initialTitle, "Track title", false, accent, density);
        final EditText inputArtist = addLabeledInput(context, form, "ARTIST", initialArtist, "Artist name", false, accent, density);
        final EditText inputAlbum = addLabeledInput(context, form, "ALBUM", initialAlbum, "Album name", false, accent, density);
        final EditText inputLyrics = addLabeledInput(context, form, "LYRICS (PLAIN OR [mm:ss.xx] LRC)",
                initialLyrics, "Paste plain lyrics or timestamped [00:12.50] LRC lines...", true, accent, density);

        card.addView(scrollView);

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(card);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int width = Math.min((int) (dm.widthPixels * 0.92f), Math.round(430 * density));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.68f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Math.round(16 * density);
        btnRow.setLayoutParams(rowLp);

        TextView btnCancel = createActionButton(context, "Cancel", Color.parseColor("#242A36"), Color.parseColor("#9AA3B5"), false, density);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(btnCancel);

        TextView btnSave = createActionButton(context, "Save Tags", accent, Color.WHITE, true, density);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.setMarginStart(Math.round(10 * density));
        btnSave.setLayoutParams(saveLp);
        btnSave.setOnClickListener(v -> {
            String newTitle = inputTitle.getText() != null ? inputTitle.getText().toString().trim() : "";
            String newArtist = inputArtist.getText() != null ? inputArtist.getText().toString().trim() : "";
            String newAlbum = inputAlbum.getText() != null ? inputAlbum.getText().toString().trim() : "";
            String newLyrics = inputLyrics.getText() != null ? inputLyrics.getText().toString() : "";

            if (newTitle.isEmpty()) {
                Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            saveOverride(context, songPath, newTitle, newArtist, newAlbum, newLyrics);
            Toast.makeText(context, "Track tags & lyrics saved", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(btnSave);
        card.addView(btnRow);

        dialog.show();
    }

    private static EditText addLabeledInput(Context context,
                                            LinearLayout parent,
                                            String labelText,
                                            String initialValue,
                                            String hint,
                                            boolean multiLine,
                                            int accent,
                                            float density) {
        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextColor(accent);
        label.setTextSize(10.5f);
        label.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = Math.round(10 * density);
        labelLp.bottomMargin = Math.round(4 * density);
        label.setLayoutParams(labelLp);
        parent.addView(label);

        EditText input = new EditText(context);
        input.setText(initialValue);
        input.setHint(hint);
        input.setHintTextColor(Color.parseColor("#6E778A"));
        input.setTextColor(Color.WHITE);
        input.setTextSize(14.5f);
        int padH = Math.round(14 * density);
        int padV = Math.round(11 * density);
        input.setPadding(padH, padV, padH, padV);

        if (multiLine) {
            input.setSingleLine(false);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            input.setMinLines(3);
            input.setMaxLines(6);
            input.setGravity(Gravity.TOP | Gravity.START);
        } else {
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#101319"));
        bg.setCornerRadius(12 * density);
        bg.setStroke(Math.round(1 * density), Color.parseColor("#2E3545"));
        input.setBackground(bg);

        parent.addView(input);
        return input;
    }

    private static TextView createActionButton(Context context, String label, int bgColor, int textColor, boolean bold, float density) {
        TextView btn = new TextView(context);
        btn.setText(label);
        btn.setTextColor(textColor);
        btn.setTextSize(14f);
        btn.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(Math.round(18 * density), Math.round(10 * density), Math.round(18 * density), Math.round(10 * density));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(12 * density);
        btn.setBackground(bg);
        return btn;
    }
}
