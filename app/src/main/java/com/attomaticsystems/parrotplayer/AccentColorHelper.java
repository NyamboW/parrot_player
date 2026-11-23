package com.attomaticsystems.parrotplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Manages the user-selectable accent color across the app.
 */
public final class AccentColorHelper {

    public static final String ACTION_ACCENT_COLOR_CHANGED = "com.attomaticsystems.parrotplayer.ACCENT_COLOR_CHANGED";
    public static final String KEY_ACCENT_COLOR = "accent_color";
    public static final int DEFAULT_ACCENT_COLOR = 0xFF4CAF50;

    public static final int[] PRESET_COLORS = new int[]{
            0xFF4CAF50, // Emerald Green
            0xFFEF5350, // Crimson Red
            0xFF42A5F5, // Ocean Blue
            0xFFAB47BC, // Royal Purple
            0xFFFF9800, // Sunset Orange
            0xFF26A69A, // Teal Cyan
            0xFFFFC107, // Gold Amber
            0xFFEC407A  // Rose Pink
    };

    public static final String[] PRESET_NAMES = new String[]{
            "Emerald Green",
            "Crimson Red",
            "Ocean Blue",
            "Royal Purple",
            "Sunset Orange",
            "Teal Cyan",
            "Gold Amber",
            "Rose Pink"
    };

    private AccentColorHelper() {}

    public static int getAccentColor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR);
    }

    public static String getAccentName(Context context) {
        int color = getAccentColor(context);
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (PRESET_COLORS[i] == color) {
                return PRESET_NAMES[i];
            }
        }
        return String.format("#%06X", (0xFFFFFF & color));
    }

    public static void setAccentColor(Context context, int color) {
        context.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_ACCENT_COLOR, color)
                .apply();

        Intent intent = new Intent(ACTION_ACCENT_COLOR_CHANGED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static void tintSeekBar(SeekBar seekBar, int accent) {
        if (seekBar == null) return;
        ColorStateList accentList = ColorStateList.valueOf(accent);
        seekBar.setProgressTintList(accentList);
        seekBar.setThumbTintList(accentList);
    }

    public static GradientDrawable createRoundedRect(int fillColor, int strokeColor, int strokeWidthPx, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) {
            drawable.setStroke(strokeWidthPx, strokeColor);
        }
        return drawable;
    }

    public static GradientDrawable createRightRoundedPill(int fillColor, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        drawable.setCornerRadii(new float[]{0f, 0f, radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f});
        return drawable;
    }

    public static GradientDrawable createCircle(int fillColor, int strokeColor, int strokeWidthPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        if (strokeWidthPx > 0) {
            drawable.setStroke(strokeWidthPx, strokeColor);
        }
        return drawable;
    }

    public static void showColorPickerDialog(Context context, Runnable onColorSelected) {
        final int current = getAccentColor(context);
        final float density = context.getResources().getDisplayMetrics().density;

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return PRESET_COLORS.length;
            }

            @Override
            public Object getItem(int position) {
                return PRESET_COLORS[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int padH = (int) (20 * density);
                int padV = (int) (12 * density);
                row.setPadding(padH, padV, padH, padV);

                int swatchColor = PRESET_COLORS[position];
                boolean isSelected = (swatchColor == current);

                View swatch = new View(context);
                int swatchSize = (int) (28 * density);
                LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(swatchSize, swatchSize);
                swatch.setLayoutParams(swatchParams);
                swatch.setBackground(createCircle(
                        swatchColor,
                        isSelected ? Color.WHITE : Color.TRANSPARENT,
                        isSelected ? (int) (2 * density) : 0
                ));
                row.addView(swatch);

                TextView label = new TextView(context);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                textParams.setMarginStart((int) (16 * density));
                label.setLayoutParams(textParams);
                label.setText(PRESET_NAMES[position]);
                label.setTextSize(15f);
                label.setTextColor(isSelected ? Color.WHITE : Color.parseColor("#D5DAE3"));
                label.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
                row.addView(label);

                if (isSelected) {
                    ImageView check = new ImageView(context);
                    int iconSize = (int) (20 * density);
                    check.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
                    check.setImageResource(R.drawable.ic_check_circle_filled);
                    check.setImageTintList(ColorStateList.valueOf(swatchColor));
                    row.addView(check);
                }

                return row;
            }
        };

        ParrotModalHelper.showCustomAdapterDialog(
                context,
                "Choose Accent Color",
                adapter,
                which -> {
                    setAccentColor(context, PRESET_COLORS[which]);
                    if (onColorSelected != null) {
                        onColorSelected.run();
                    }
                }
        );
    }
}
