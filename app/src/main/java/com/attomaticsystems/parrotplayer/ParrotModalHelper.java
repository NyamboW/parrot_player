package com.attomaticsystems.parrotplayer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern dark-themed modal and dialog system replacing legacy Android platform AlertDialogs.
 */
public final class ParrotModalHelper {

    private static final int COLOR_SURFACE = Color.parseColor("#181C24");
    private static final int COLOR_SURFACE_STROKE = Color.parseColor("#2B3140");
    private static final int COLOR_INPUT_BG = Color.parseColor("#101319");
    private static final int COLOR_INPUT_STROKE = Color.parseColor("#2E3545");
    private static final int COLOR_SECONDARY_BTN_BG = Color.parseColor("#242A36");
    private static final int COLOR_TEXT_PRIMARY = Color.WHITE;
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#9AA3B5");
    private static final int COLOR_DANGER = Color.parseColor("#EF5350");

    public interface TextInputCallback {
        void onSubmitted(String text);
    }

    public interface ItemClickCallback {
        void onItemSelected(int index);
    }

    public interface MultiChoiceCallback {
        void onConfirmed(boolean[] checkedItems);
    }

    private ParrotModalHelper() {}

    private static boolean isContextValid(Context context) {
        if (context == null) return false;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        return true;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable createRoundedRect(int fillColor, float radiusPx, int strokeColor, int strokeWidthPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) {
            gd.setStroke(strokeWidthPx, strokeColor);
        }
        return gd;
    }

    private static android.graphics.drawable.Drawable createRippleButtonBg(Context context, int fillColor, float radiusPx, int strokeColor, int strokeWidthPx) {
        GradientDrawable content = createRoundedRect(fillColor, radiusPx, strokeColor, strokeWidthPx);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable mask = createRoundedRect(Color.WHITE, radiusPx, Color.TRANSPARENT, 0);
            return new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), content, mask);
        }
        return content;
    }

    private static Dialog createBaseDialog(Context context, LinearLayout rootCard, boolean cancelable) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(rootCard);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int width = Math.min((int) (dm.widthPixels * 0.90f), dp(context, 420));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.68f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return dialog;
    }

    private static LinearLayout createDialogCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(context, 22);
        int padTop = dp(context, 22);
        int padBottom = dp(context, 18);
        card.setPadding(padH, padTop, padH, padBottom);
        card.setBackground(createRoundedRect(COLOR_SURFACE, dp(context, 24), COLOR_SURFACE_STROKE, dp(context, 1)));
        return card;
    }

    private static void addHeader(Context context, LinearLayout card, String title, @Nullable String subtitle, int iconResId) {
        int accent = AccentColorHelper.getAccentColor(context);

        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        if (iconResId != 0) {
            LinearLayout badge = new LinearLayout(context);
            badge.setGravity(Gravity.CENTER);
            int badgeSize = dp(context, 38);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
            badgeLp.setMarginEnd(dp(context, 12));
            badge.setLayoutParams(badgeLp);
            badge.setBackground(createRoundedRect(AccentColorHelper.withAlpha(accent, 0x24), dp(context, 12),
                    AccentColorHelper.withAlpha(accent, 0x55), dp(context, 1)));

            ImageView icon = new ImageView(context);
            int iconSize = dp(context, 20);
            icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            icon.setImageResource(iconResId);
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(accent));
            badge.addView(icon);
            headerRow.addView(badge);
        }

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView txtTitle = new TextView(context);
        txtTitle.setText(title);
        txtTitle.setTextColor(COLOR_TEXT_PRIMARY);
        txtTitle.setTextSize(18f);
        txtTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(txtTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView txtSub = new TextView(context);
            txtSub.setText(subtitle);
            txtSub.setTextColor(COLOR_TEXT_SECONDARY);
            txtSub.setTextSize(13f);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(context, 2);
            txtSub.setLayoutParams(subLp);
            titleCol.addView(txtSub);
        }

        headerRow.addView(titleCol);
        card.addView(headerRow);
    }

    private static TextView createPillButton(Context context, String label, int bgColor, int textColor, boolean bold) {
        TextView btn = new TextView(context);
        btn.setText(label);
        btn.setTextColor(textColor);
        btn.setTextSize(14f);
        btn.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(context, 18), dp(context, 11), dp(context, 18), dp(context, 11));
        btn.setBackground(createRippleButtonBg(context, bgColor, dp(context, 12), Color.TRANSPARENT, 0));
        return btn;
    }

    /**
     * Modern text input popup (Create Playlist, Rename Playlist, Save Queue as Playlist).
     */
    public static void showTextInputDialog(final Context context,
                                           String title,
                                           @Nullable String subtitle,
                                           String hint,
                                           @Nullable String initialText,
                                           String positiveLabel,
                                           @Nullable String neutralLabel,
                                           @Nullable Runnable onNeutralClick,
                                           final TextInputCallback onConfirm) {
        if (!isContextValid(context)) return;
        final int accent = AccentColorHelper.getAccentColor(context);
        LinearLayout card = createDialogCard(context);

        addHeader(context, card, title, subtitle, R.drawable.ic_playlist);

        final EditText input = new EditText(context);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = dp(context, 18);
        input.setLayoutParams(inputLp);
        input.setHint(hint);
        input.setHintTextColor(Color.parseColor("#6E778A"));
        input.setTextColor(COLOR_TEXT_PRIMARY);
        input.setTextSize(15.5f);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        int padH = dp(context, 16);
        int padV = dp(context, 13);
        input.setPadding(padH, padV, padH, padV);
        input.setBackground(createRoundedRect(COLOR_INPUT_BG, dp(context, 14), accent, dp(context, 1)));
        if (initialText != null && !initialText.isEmpty()) {
            input.setText(initialText);
            input.setSelection(initialText.length());
        }
        card.addView(input);

        final Dialog dialog = createBaseDialog(context, card, true);

        if (neutralLabel != null && onNeutralClick != null) {
            TextView btnNeutral = new TextView(context);
            LinearLayout.LayoutParams neutralLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            neutralLp.topMargin = dp(context, 12);
            btnNeutral.setLayoutParams(neutralLp);
            btnNeutral.setText(neutralLabel);
            btnNeutral.setTextColor(accent);
            btnNeutral.setTextSize(13.5f);
            btnNeutral.setTypeface(null, Typeface.BOLD);
            btnNeutral.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
            btnNeutral.setBackground(createRippleButtonBg(
                    context,
                    AccentColorHelper.withAlpha(accent, 0x1A),
                    dp(context, 10),
                    AccentColorHelper.withAlpha(accent, 0x45),
                    dp(context, 1)));
            btnNeutral.setOnClickListener(v -> {
                dialog.dismiss();
                onNeutralClick.run();
            });
            card.addView(btnNeutral);
        }

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(context, 20);
        btnRow.setLayoutParams(rowLp);

        TextView btnCancel = createPillButton(context, "Cancel", COLOR_SECONDARY_BTN_BG, COLOR_TEXT_SECONDARY, false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(btnCancel);

        TextView btnConfirm = createPillButton(context, positiveLabel, accent, Color.WHITE, true);
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        confirmLp.setMarginStart(dp(context, 10));
        btnConfirm.setLayoutParams(confirmLp);

        final Runnable submitAction = () -> {
            String value = input.getText() != null ? input.getText().toString().trim() : "";
            if (value.isEmpty()) {
                Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            if (onConfirm != null) {
                onConfirm.onSubmitted(value);
            }
        };

        btnConfirm.setOnClickListener(v -> submitAction.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitAction.run();
                return true;
            }
            return false;
        });
        btnRow.addView(btnConfirm);
        card.addView(btnRow);

        dialog.setOnShowListener(d -> input.postDelayed(() -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100));

        dialog.show();
    }

    /**
     * Unified modern "Add to Playlist" dialog with a dedicated "+ Create New Playlist" card and playlist track counts.
     */
    public static void showAddToPlaylistDialog(final Context context,
                                               final ArrayList<String> songPaths,
                                               @Nullable final Runnable onPlaylistUpdated) {
        if (!isContextValid(context) || songPaths == null || songPaths.isEmpty()) return;
        final PlaylistManager playlistManager = new PlaylistManager(context);
        final ArrayList<String> playlists = playlistManager.getPlaylists();
        final int accent = AccentColorHelper.getAccentColor(context);

        LinearLayout card = createDialogCard(context);
        String sub = songPaths.size() == 1 ? "1 track selected" : (songPaths.size() + " tracks selected");
        addHeader(context, card, "Add to Playlist", sub, R.drawable.ic_playlist);

        final Dialog dialog = createBaseDialog(context, card, true);

        // "+ Create New Playlist" prominent card button
        LinearLayout createBtn = new LinearLayout(context);
        createBtn.setOrientation(LinearLayout.HORIZONTAL);
        createBtn.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        createLp.topMargin = dp(context, 16);
        createBtn.setLayoutParams(createLp);
        int cPadH = dp(context, 14);
        int cPadV = dp(context, 12);
        createBtn.setPadding(cPadH, cPadV, cPadH, cPadV);
        createBtn.setBackground(createRippleButtonBg(
                context,
                AccentColorHelper.withAlpha(accent, 0x1E),
                dp(context, 14),
                AccentColorHelper.withAlpha(accent, 0x55),
                dp(context, 1)));

        ImageView addIcon = new ImageView(context);
        int addIconSize = dp(context, 22);
        LinearLayout.LayoutParams addIconLp = new LinearLayout.LayoutParams(addIconSize, addIconSize);
        addIconLp.setMarginEnd(dp(context, 12));
        addIcon.setLayoutParams(addIconLp);
        addIcon.setImageResource(R.drawable.ic_nav_playlist_add);
        ImageViewCompat.setImageTintList(addIcon, ColorStateList.valueOf(accent));
        createBtn.addView(addIcon);

        TextView txtCreate = new TextView(context);
        txtCreate.setText("Create New Playlist");
        txtCreate.setTextColor(COLOR_TEXT_PRIMARY);
        txtCreate.setTextSize(15f);
        txtCreate.setTypeface(null, Typeface.BOLD);
        createBtn.addView(txtCreate);

        createBtn.setOnClickListener(v -> {
            dialog.dismiss();
            showTextInputDialog(
                    context,
                    "Create New Playlist",
                    "Create and add " + sub,
                    "Playlist name",
                    null,
                    "Create & Add",
                    null,
                    null,
                    name -> {
                        playlistManager.createPlaylist(name);
                        playlistManager.addSongsToPlaylist(name, songPaths);
                        Toast.makeText(context, songPaths.size() + " added to '" + name + "'", Toast.LENGTH_SHORT).show();
                        if (onPlaylistUpdated != null) {
                            onPlaylistUpdated.run();
                        }
                    }
            );
        });
        card.addView(createBtn);

        if (playlists.isEmpty()) {
            TextView emptyView = new TextView(context);
            LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            emptyLp.topMargin = dp(context, 16);
            emptyView.setLayoutParams(emptyLp);
            emptyView.setText("No saved playlists yet. Tap above to create your first playlist.");
            emptyView.setTextColor(COLOR_TEXT_SECONDARY);
            emptyView.setTextSize(13.5f);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(dp(context, 8), dp(context, 12), dp(context, 8), dp(context, 8));
            card.addView(emptyView);
        } else {
            ListView listView = new ListView(context);
            listView.setDivider(null);
            listView.setDividerHeight(dp(context, 6));
            listView.setSelector(new ColorDrawable(Color.TRANSPARENT));

            int maxVisibleRows = Math.min(playlists.size(), 5);
            int listHeight = playlists.size() > 5 ? dp(context, maxVisibleRows * 56) : ViewGroup.LayoutParams.WRAP_CONTENT;
            LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, listHeight);
            listLp.topMargin = dp(context, 12);
            listView.setLayoutParams(listLp);

            listView.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return playlists.size();
                }

                @Override
                public Object getItem(int position) {
                    return playlists.get(position);
                }

                @Override
                public long getItemId(int position) {
                    return position;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    String plName = playlists.get(position);
                    int trackCount = playlistManager.getSongsInPlaylist(plName).size();

                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
                    row.setBackground(createRippleButtonBg(context, COLOR_INPUT_BG, dp(context, 12), COLOR_INPUT_STROKE, dp(context, 1)));

                    ImageView icon = new ImageView(context);
                    int iconSize = dp(context, 20);
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                    iconLp.setMarginEnd(dp(context, 12));
                    icon.setLayoutParams(iconLp);
                    icon.setImageResource(R.drawable.ic_nav_playlist_item);
                    ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(accent));
                    row.addView(icon);

                    TextView txtName = new TextView(context);
                    txtName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    txtName.setText(plName);
                    txtName.setTextColor(COLOR_TEXT_PRIMARY);
                    txtName.setTextSize(15f);
                    txtName.setTypeface(null, Typeface.BOLD);
                    txtName.setSingleLine(true);
                    txtName.setEllipsize(TextUtils.TruncateAt.END);
                    row.addView(txtName);

                    TextView txtCount = new TextView(context);
                    txtCount.setText(trackCount + (trackCount == 1 ? " song" : " songs"));
                    txtCount.setTextColor(COLOR_TEXT_SECONDARY);
                    txtCount.setTextSize(12.5f);
                    row.addView(txtCount);

                    return row;
                }
            });

            listView.setOnItemClickListener((parent, view, position, id) -> {
                String selectedPlaylist = playlists.get(position);
                playlistManager.addSongsToPlaylist(selectedPlaylist, songPaths);
                Toast.makeText(context, songPaths.size() + " added to '" + selectedPlaylist + "'", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                if (onPlaylistUpdated != null) {
                    onPlaylistUpdated.run();
                }
            });
            card.addView(listView);
        }

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(context, 16);
        btnRow.setLayoutParams(rowLp);

        TextView btnCancel = createPillButton(context, "Cancel", COLOR_SECONDARY_BTN_BG, COLOR_TEXT_SECONDARY, false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(btnCancel);
        card.addView(btnRow);

        dialog.show();
    }

    /**
     * Modern confirmation or information dialog.
     */
    public static void showConfirmDialog(Context context,
                                         String title,
                                         String message,
                                         String positiveLabel,
                                         @Nullable String negativeLabel,
                                         boolean isDestructive,
                                         @Nullable Runnable onPositive,
                                         @Nullable Runnable onNegative,
                                         @Nullable DialogInterface.OnCancelListener onCancel) {
        if (!isContextValid(context)) return;
        int accent = AccentColorHelper.getAccentColor(context);
        LinearLayout card = createDialogCard(context);

        addHeader(context, card, title, null, 0);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.topMargin = dp(context, 14);
        scrollView.setLayoutParams(scrollLp);

        TextView txtMsg = new TextView(context);
        txtMsg.setText(message);
        txtMsg.setTextColor(Color.parseColor("#D5DCE8"));
        txtMsg.setTextSize(14.5f);
        txtMsg.setLineSpacing(dp(context, 3), 1.0f);
        scrollView.addView(txtMsg);
        card.addView(scrollView);

        final Dialog dialog = createBaseDialog(context, card, true);
        if (onCancel != null) {
            dialog.setOnCancelListener(onCancel);
        }

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(context, 20);
        btnRow.setLayoutParams(rowLp);

        if (negativeLabel != null && !negativeLabel.isEmpty()) {
            TextView btnNeg = createPillButton(context, negativeLabel, COLOR_SECONDARY_BTN_BG, COLOR_TEXT_SECONDARY, false);
            btnNeg.setOnClickListener(v -> {
                dialog.dismiss();
                if (onNegative != null) onNegative.run();
            });
            btnRow.addView(btnNeg);
        }

        int primaryBg = isDestructive ? COLOR_DANGER : accent;
        TextView btnPos = createPillButton(context, positiveLabel, primaryBg, Color.WHITE, true);
        LinearLayout.LayoutParams posLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (negativeLabel != null && !negativeLabel.isEmpty()) {
            posLp.setMarginStart(dp(context, 10));
        }
        btnPos.setLayoutParams(posLp);
        btnPos.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPositive != null) onPositive.run();
        });
        btnRow.addView(btnPos);

        card.addView(btnRow);
        dialog.show();
    }

    /**
     * Modern single-choice or action-sheet list dialog (Sort options, Playlist long-press actions, Track quick actions).
     */
    public static void showSingleChoiceDialog(final Context context,
                                              String title,
                                              final String[] items,
                                              final int selectedIndex,
                                              final ItemClickCallback callback) {
        if (!isContextValid(context) || items == null || items.length == 0) return;
        final int accent = AccentColorHelper.getAccentColor(context);
        LinearLayout card = createDialogCard(context);

        addHeader(context, card, title, null, 0);

        final Dialog dialog = createBaseDialog(context, card, true);

        ListView listView = new ListView(context);
        listView.setDivider(null);
        listView.setDividerHeight(dp(context, 6));
        listView.setSelector(new ColorDrawable(Color.TRANSPARENT));

        int maxHeight = items.length > 6 ? dp(context, 320) : ViewGroup.LayoutParams.WRAP_CONTENT;
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        listLp.topMargin = dp(context, 16);
        listView.setLayoutParams(listLp);

        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return items.length;
            }

            @Override
            public Object getItem(int position) {
                return items[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                boolean isSelected = (position == selectedIndex);
                String text = items[position];
                boolean isDestructiveItem = text != null && text.toLowerCase().contains("delete");

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(context, 14), dp(context, 13), dp(context, 14), dp(context, 13));

                int rowBg = isSelected ? AccentColorHelper.withAlpha(accent, 0x22) : COLOR_INPUT_BG;
                int rowStroke = isSelected ? AccentColorHelper.withAlpha(accent, 0x66) : COLOR_INPUT_STROKE;
                row.setBackground(createRippleButtonBg(context, rowBg, dp(context, 12), rowStroke, dp(context, 1)));

                TextView label = new TextView(context);
                label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                label.setText(text);
                label.setTextSize(15f);
                if (isDestructiveItem) {
                    label.setTextColor(COLOR_DANGER);
                    label.setTypeface(null, Typeface.BOLD);
                } else {
                    label.setTextColor(isSelected ? Color.WHITE : Color.parseColor("#E0E5F0"));
                    label.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
                }
                row.addView(label);

                if (selectedIndex >= 0) {
                    ImageView check = new ImageView(context);
                    int size = dp(context, 20);
                    check.setLayoutParams(new LinearLayout.LayoutParams(size, size));
                    check.setImageResource(isSelected ? R.drawable.ic_check_circle_filled : R.drawable.ic_check_circle_outline);
                    ImageViewCompat.setImageTintList(check,
                            ColorStateList.valueOf(isSelected ? accent : Color.parseColor("#5C6475")));
                    row.addView(check);
                }

                return row;
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            if (callback != null) {
                callback.onItemSelected(position);
            }
        });
        card.addView(listView);

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(context, 14);
        btnRow.setLayoutParams(rowLp);

        TextView btnCancel = createPillButton(context, "Cancel", COLOR_SECONDARY_BTN_BG, COLOR_TEXT_SECONDARY, false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(btnCancel);
        card.addView(btnRow);

        dialog.show();
    }

    /**
     * Modern custom adapter list dialog (e.g., Accent Color picker).
     */
    public static void showCustomAdapterDialog(Context context,
                                               String title,
                                               ListAdapter adapter,
                                               ItemClickCallback callback) {
        if (!isContextValid(context) || adapter == null) return;
        LinearLayout card = createDialogCard(context);
        addHeader(context, card, title, null, R.drawable.ic_palette);

        final Dialog dialog = createBaseDialog(context, card, true);

        ListView listView = new ListView(context);
        listView.setDivider(null);
        listView.setDividerHeight(dp(context, 4));
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listLp.topMargin = dp(context, 14);
        listView.setLayoutParams(listLp);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            if (callback != null) {
                callback.onItemSelected(position);
            }
        });
        card.addView(listView);

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(context, 14);
        btnRow.setLayoutParams(rowLp);

        TextView btnCancel = createPillButton(context, "Cancel", COLOR_SECONDARY_BTN_BG, COLOR_TEXT_SECONDARY, false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(btnCancel);
        card.addView(btnRow);

        dialog.show();
    }

    /**
     * Modern multi-choice dialog (Music Folders selection, Import from Folder, Select Songs to Add).
     */
    public static void showMultiChoiceDialog(final Context context,
                                             String title,
                                             final String[] labels,
                                             final boolean[] checkedItems,
                                             String positiveLabel,
                                             @Nullable String neutralLabel,
                                             @Nullable Runnable onNeutral,
                                             boolean cancelable,
                                             final MultiChoiceCallback onConfirm) {
        if (!isContextValid(context) || labels == null || checkedItems == null) return;
        final int accent = AccentColorHelper.getAccentColor(context);
        LinearLayout card = createDialogCard(context);

        addHeader(context, card, title, null, R.drawable.ic_folder_music);

        final Dialog dialog = createBaseDialog(context, card, cancelable);

        ListView listView = new ListView(context);
        listView.setDivider(null);
        listView.setDividerHeight(dp(context, 6));
        listView.setSelector(new ColorDrawable(Color.TRANSPARENT));

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int maxListHeight = Math.min((int) (dm.heightPixels * 0.50f), dp(context, 340));
        int listHeight = labels.length > 5 ? maxListHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeight);
        listLp.topMargin = dp(context, 16);
        listView.setLayoutParams(listLp);

        final BaseAdapter multiAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return labels.length;
            }

            @Override
            public Object getItem(int position) {
                return labels[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                boolean isChecked = checkedItems[position];
                String raw = labels[position] != null ? labels[position] : "";
                String primaryText = raw;
                String secondaryText = null;
                int nl = raw.indexOf('\n');
                if (nl >= 0) {
                    primaryText = raw.substring(0, nl);
                    secondaryText = raw.substring(nl + 1);
                }

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11));

                int bg = isChecked ? AccentColorHelper.withAlpha(accent, 0x20) : COLOR_INPUT_BG;
                int stroke = isChecked ? AccentColorHelper.withAlpha(accent, 0x60) : COLOR_INPUT_STROKE;
                row.setBackground(createRippleButtonBg(context, bg, dp(context, 12), stroke, dp(context, 1)));

                LinearLayout textCol = new LinearLayout(context);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView txtPrimary = new TextView(context);
                txtPrimary.setText(primaryText);
                txtPrimary.setTextColor(COLOR_TEXT_PRIMARY);
                txtPrimary.setTextSize(14.5f);
                txtPrimary.setTypeface(null, isChecked ? Typeface.BOLD : Typeface.NORMAL);
                txtPrimary.setSingleLine(true);
                txtPrimary.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(txtPrimary);

                if (secondaryText != null && !secondaryText.isEmpty()) {
                    TextView txtSecondary = new TextView(context);
                    txtSecondary.setText(secondaryText);
                    txtSecondary.setTextColor(COLOR_TEXT_SECONDARY);
                    txtSecondary.setTextSize(12f);
                    txtSecondary.setSingleLine(true);
                    txtSecondary.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    textCol.addView(txtSecondary);
                }

                row.addView(textCol);

                ImageView check = new ImageView(context);
                int iconSize = dp(context, 22);
                LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                checkLp.setMarginStart(dp(context, 10));
                check.setLayoutParams(checkLp);
                check.setImageResource(isChecked ? R.drawable.ic_check_circle_filled : R.drawable.ic_check_circle_outline);
                ImageViewCompat.setImageTintList(check,
                        ColorStateList.valueOf(isChecked ? accent : Color.parseColor("#5C6475")));
                row.addView(check);

                return row;
            }
        };

        listView.setAdapter(multiAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            checkedItems[position] = !checkedItems[position];
            multiAdapter.notifyDataSetChanged();
        });
        card.addView(listView);

        if (neutralLabel != null && onNeutral != null) {
            TextView btnNeutral = new TextView(context);
            LinearLayout.LayoutParams neutralLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            neutralLp.topMargin = dp(context, 12);
            btnNeutral.setLayoutParams(neutralLp);
            btnNeutral.setText(neutralLabel);
            btnNeutral.setTextColor(accent);
            btnNeutral.setTextSize(13.5f);
            btnNeutral.setTypeface(null, Typeface.BOLD);
            btnNeutral.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
            btnNeutral.setBackground(createRippleButtonBg(
                    context,
                    AccentColorHelper.withAlpha(accent, 0x1A),
                    dp(context, 10),
                    AccentColorHelper.withAlpha(accent, 0x45),
                    dp(context, 1)));
            btnNeutral.setOnClickListener(v -> {
                dialog.dismiss();
                onNeutral.run();
            });
            card.addView(btnNeutral);
        }

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(context, 16);
        btnRow.setLayoutParams(rowLp);

        if (cancelable) {
            TextView btnCancel = createPillButton(context, "Cancel", COLOR_SECONDARY_BTN_BG, COLOR_TEXT_SECONDARY, false);
            btnCancel.setOnClickListener(v -> dialog.dismiss());
            btnRow.addView(btnCancel);
        }

        TextView btnConfirm = createPillButton(context, positiveLabel, accent, Color.WHITE, true);
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (cancelable) {
            confirmLp.setMarginStart(dp(context, 10));
        }
        btnConfirm.setLayoutParams(confirmLp);
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) {
                onConfirm.onConfirmed(checkedItems);
            }
        });
        btnRow.addView(btnConfirm);

        card.addView(btnRow);
        dialog.show();
    }
}
