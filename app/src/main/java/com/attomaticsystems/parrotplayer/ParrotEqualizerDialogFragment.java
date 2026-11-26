package com.attomaticsystems.parrotplayer;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Custom in-house Parrot Sound Effects, Preamp, Loudness, Stereo Balance & 5-Band Equalizer BottomSheet UI.
 */
public class ParrotEqualizerDialogFragment extends BottomSheetDialogFragment {

    public static final String PREFS_EQ = "parrot_eq";
    private static final int BAND_COUNT = 5;
    private static final int DEFAULT_MIN_MB = -1500;
    private static final int DEFAULT_MAX_MB = 1500;
    private static final int PREAMP_MIN_MB = -1200;
    private static final int PREAMP_MAX_MB = 1200;

    private static final String[] DEFAULT_FREQ_LABELS = {
            "60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz"
    };

    private static final String[] PRESET_NAMES = {
            "Custom", "Flat", "Bass Boost", "Rock", "Pop",
            "Hip-Hop", "Electronic", "Vocal", "Jazz", "Classical"
    };

    private static final int[][] PRESET_GAINS_MB = {
            {0, 0, 0, 0, 0},             // Custom (placeholder)
            {0, 0, 0, 0, 0},             // Flat
            {650, 450, 0, 100, 200},     // Bass Boost
            {500, 300, -150, 300, 500},  // Rock
            {-150, 250, 450, 200, -150}, // Pop
            {600, 400, 0, 150, 350},     // Hip-Hop
            {450, 200, -100, 250, 500},  // Electronic
            {-250, 150, 500, 350, -150}, // Vocal
            {350, 200, -150, 200, 400},  // Jazz
            {400, 250, -100, 250, 450}   // Classical
    };

    private static final String[] REVERB_NAMES = {
            "None", "Small Room", "Medium Room", "Large Room",
            "Medium Hall", "Large Hall", "Plate"
    };

    private SharedPreferences eqPrefs;
    private SwitchCompat switchEqEnable;
    private TextView btnEqOutputProfile;
    private TextView btnEqLimiterToggle;
    private TextView txtEqPresetsHeader;
    private TextView txtPreampValue;
    private SeekBar seekPreamp;
    private TextView txtLoudnessValue;
    private SeekBar seekLoudness;
    private TextView txtBassValue;
    private SeekBar seekBassBoost;
    private TextView txtBalanceValue;
    private SeekBar seekStereoBalance;
    private LinearLayout eqDspTogglesRow;
    private LinearLayout eqPreampCard;
    private LinearLayout eqPresetsContainer;
    private LinearLayout eqBandsContainer;
    private LinearLayout reverbChipsContainer;

    private final ArrayList<TextView> presetChipViews = new ArrayList<>();
    private final ArrayList<TextView> reverbChipViews = new ArrayList<>();
    private final SeekBar[] bandSeekBars = new SeekBar[BAND_COUNT];
    private final TextView[] bandValueViews = new TextView[BAND_COUNT];

    private int minLevelMb = DEFAULT_MIN_MB;
    private int maxLevelMb = DEFAULT_MAX_MB;
    private int selectedPresetIndex = 1; // Default Flat
    private int selectedReverbPreset = 0;
    private boolean isUpdatingSliders = false;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_parrot_equalizer, container, false);

        eqPrefs = requireContext().getSharedPreferences(PREFS_EQ, Context.MODE_PRIVATE);
        int accent = AccentColorHelper.getAccentColor(requireContext());

        switchEqEnable = view.findViewById(R.id.switchEqEnable);
        TextView btnEqReset = view.findViewById(R.id.btnEqReset);
        btnEqOutputProfile = view.findViewById(R.id.btnEqOutputProfile);
        btnEqLimiterToggle = view.findViewById(R.id.btnEqLimiterToggle);
        eqDspTogglesRow = view.findViewById(R.id.eqDspTogglesRow);
        eqPreampCard = view.findViewById(R.id.eqPreampCard);
        txtEqPresetsHeader = view.findViewById(R.id.txtEqPresetsHeader);
        txtPreampValue = view.findViewById(R.id.txtPreampValue);
        seekPreamp = view.findViewById(R.id.seekPreamp);
        txtLoudnessValue = view.findViewById(R.id.txtLoudnessValue);
        seekLoudness = view.findViewById(R.id.seekLoudness);
        txtBassValue = view.findViewById(R.id.txtBassValue);
        seekBassBoost = view.findViewById(R.id.seekBassBoost);
        txtBalanceValue = view.findViewById(R.id.txtBalanceValue);
        seekStereoBalance = view.findViewById(R.id.seekStereoBalance);
        eqPresetsContainer = view.findViewById(R.id.eqPresetsContainer);
        eqBandsContainer = view.findViewById(R.id.eqBandsContainer);
        reverbChipsContainer = view.findViewById(R.id.reverbChipsContainer);

        readHardwareRangeIfAvailable();
        styleAccentControls(accent);

        boolean enabled = eqPrefs.getBoolean("isEqualizerEnabled", true);
        switchEqEnable.setChecked(enabled);
        switchEqEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            eqPrefs.edit().putBoolean("isEqualizerEnabled", isChecked).apply();
            updateControlsEnabledState(isChecked);
            notifyServiceReload();
        });

        btnEqReset.setOnClickListener(v -> resetAllToDefault(accent));

        setupOutputProfileAndLimiterButtons(accent);
        buildPresetChips(accent);
        buildBandRows(accent);
        setupPreampAndLoudnessSliders(accent);
        setupBassAndBalanceSliders(accent);
        buildReverbChips(accent);
        loadCurrentProfileIntoUi(accent);
        updateControlsEnabledState(enabled);

        return view;
    }

    private String getProfilePrefix() {
        return MusicService.resolveEqProfilePrefix(getContext(), eqPrefs);
    }

    private void setupOutputProfileAndLimiterButtons(final int accent) {
        if (btnEqOutputProfile != null) {
            btnEqOutputProfile.setOnClickListener(v -> {
                boolean currentAuto = eqPrefs.getBoolean("autoOutputProfile", false);
                boolean nextAuto = !currentAuto;
                eqPrefs.edit().putBoolean("autoOutputProfile", nextAuto).apply();
                loadCurrentProfileIntoUi(accent);
                notifyServiceReload();
                String activeRoute = "headset_".equals(getProfilePrefix()) ? "Headset / BT" : "Speaker";
                Toast.makeText(getContext(),
                        nextAuto ? ("Auto Output EQ enabled (" + activeRoute + ")") : "Switched to Global EQ profile",
                        Toast.LENGTH_SHORT).show();
            });
        }
        if (btnEqLimiterToggle != null) {
            btnEqLimiterToggle.setOnClickListener(v -> {
                boolean currentLimiter = eqPrefs.getBoolean("limiterEnabled", true);
                boolean nextLimiter = !currentLimiter;
                eqPrefs.edit().putBoolean("limiterEnabled", nextLimiter).apply();
                updateDspToggleChipStyles(accent);
                notifyServiceReload();
            });
        }
        updateDspToggleChipStyles(accent);
    }

    private void updateDspToggleChipStyles(int accent) {
        if (!isAdded()) return;
        float density = getResources().getDisplayMetrics().density;
        boolean autoProfile = eqPrefs.getBoolean("autoOutputProfile", false);
        boolean limiterOn = eqPrefs.getBoolean("limiterEnabled", true);

        if (btnEqOutputProfile != null) {
            String pfx = getProfilePrefix();
            String label = autoProfile
                    ? ("Auto: " + ("headset_".equals(pfx) ? "Headset / BT" : "Speaker"))
                    : "Profile: Global";
            btnEqOutputProfile.setText(label);
            btnEqOutputProfile.setTextColor(autoProfile ? accent : Color.parseColor("#D5DCE6"));
            btnEqOutputProfile.setBackground(AccentColorHelper.createRoundedRect(
                    autoProfile ? AccentColorHelper.withAlpha(accent, 0x24) : Color.parseColor("#161A22"),
                    autoProfile ? AccentColorHelper.withAlpha(accent, 0x66) : Color.parseColor("#242A36"),
                    (int) (1 * density),
                    12f * density
            ));
        }

        if (btnEqLimiterToggle != null) {
            btnEqLimiterToggle.setText(limiterOn ? "Anti-Clip Limiter: ON" : "Anti-Clip Limiter: OFF");
            btnEqLimiterToggle.setTextColor(limiterOn ? accent : Color.parseColor("#9AA3B2"));
            btnEqLimiterToggle.setBackground(AccentColorHelper.createRoundedRect(
                    limiterOn ? AccentColorHelper.withAlpha(accent, 0x24) : Color.parseColor("#161A22"),
                    limiterOn ? AccentColorHelper.withAlpha(accent, 0x66) : Color.parseColor("#242A36"),
                    (int) (1 * density),
                    12f * density
            ));
        }
    }

    private void loadCurrentProfileIntoUi(int accent) {
        isUpdatingSliders = true;
        String pfx = getProfilePrefix();

        selectedPresetIndex = eqPrefs.getInt(pfx + "presetPos", eqPrefs.getInt("presetPos", 1));
        if (selectedPresetIndex < 0 || selectedPresetIndex >= PRESET_NAMES.length) {
            selectedPresetIndex = 1;
        }
        int savedPreampMb = Math.max(PREAMP_MIN_MB, Math.min(PREAMP_MAX_MB,
                eqPrefs.getInt(pfx + "preampMb", eqPrefs.getInt("preampMb", 0))));
        int savedLoudnessMb = Math.max(0, Math.min(1000,
                eqPrefs.getInt(pfx + "loudnessMb", eqPrefs.getInt("loudnessMb", 0))));
        int savedBass = Math.max(0, Math.min(1000,
                eqPrefs.getInt(pfx + "bassStrength", eqPrefs.getInt("bassStrength", 0))));
        int savedBalance = Math.max(-100, Math.min(100,
                eqPrefs.getInt(pfx + "stereoBalance", eqPrefs.getInt("stereoBalance", 0))));
        selectedReverbPreset = Math.max(0, Math.min(REVERB_NAMES.length - 1,
                eqPrefs.getInt(pfx + "reverbPreset", eqPrefs.getInt("reverbPreset", 0))));

        if (seekPreamp != null) {
            seekPreamp.setProgress(savedPreampMb - PREAMP_MIN_MB);
        }
        if (txtPreampValue != null) {
            updateBandDbLabel(txtPreampValue, savedPreampMb, accent);
        }

        if (seekLoudness != null) {
            seekLoudness.setProgress(savedLoudnessMb);
        }
        if (txtLoudnessValue != null) {
            txtLoudnessValue.setText((savedLoudnessMb / 10) + "%");
        }

        if (seekBassBoost != null) {
            seekBassBoost.setProgress(savedBass);
        }
        if (txtBassValue != null) {
            txtBassValue.setText((savedBass / 10) + "%");
        }

        if (seekStereoBalance != null) {
            seekStereoBalance.setProgress(savedBalance + 100);
        }
        if (txtBalanceValue != null) {
            updateBalanceLabel(savedBalance, accent);
        }

        int range = maxLevelMb - minLevelMb;
        for (int i = 0; i < BAND_COUNT; i++) {
            int savedMb = getSavedBandLevelMb(i);
            if (bandSeekBars[i] != null) {
                bandSeekBars[i].setProgress(Math.max(0, Math.min(range, savedMb - minLevelMb)));
            }
            if (bandValueViews[i] != null) {
                updateBandDbLabel(bandValueViews[i], savedMb, accent);
            }
        }

        updateDspToggleChipStyles(accent);
        updatePresetChipStyles(accent);
        updateReverbChipStyles(accent);
        isUpdatingSliders = false;
    }

    private void readHardwareRangeIfAvailable() {
        MusicService service = MusicService.getActiveInstance();
        if (service != null) {
            Equalizer eq = service.getEqualizerInstance();
            if (eq != null) {
                try {
                    short[] range = eq.getBandLevelRange();
                    if (range != null && range.length >= 2 && range[1] > range[0]) {
                        minLevelMb = range[0];
                        maxLevelMb = range[1];
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void styleAccentControls(int accent) {
        if (txtEqPresetsHeader != null) {
            txtEqPresetsHeader.setTextColor(accent);
        }
        if (txtLoudnessValue != null) {
            txtLoudnessValue.setTextColor(accent);
        }
        if (txtBassValue != null) {
            txtBassValue.setTextColor(accent);
        }
        SeekBar[] globalSliders = new SeekBar[]{seekPreamp, seekLoudness, seekBassBoost, seekStereoBalance};
        for (SeekBar sb : globalSliders) {
            if (sb != null) {
                sb.setProgressTintList(ColorStateList.valueOf(accent));
                sb.setThumbTintList(ColorStateList.valueOf(accent));
            }
        }
        if (switchEqEnable != null) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{-android.R.attr.state_checked}
            };
            int[] thumbColors = new int[]{accent, Color.parseColor("#8A93A2")};
            int[] trackColors = new int[]{AccentColorHelper.withAlpha(accent, 0x66), Color.parseColor("#2A303C")};
            switchEqEnable.setThumbTintList(new ColorStateList(states, thumbColors));
            switchEqEnable.setTrackTintList(new ColorStateList(states, trackColors));
        }
    }

    private void setupPreampAndLoudnessSliders(final int accent) {
        if (seekPreamp != null) {
            seekPreamp.setMax(PREAMP_MAX_MB - PREAMP_MIN_MB);
            seekPreamp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int preampMb = PREAMP_MIN_MB + progress;
                    if (fromUser && Math.abs(preampMb) < 40) {
                        preampMb = 0;
                        seekBar.setProgress(-PREAMP_MIN_MB);
                    }
                    if (txtPreampValue != null) {
                        updateBandDbLabel(txtPreampValue, preampMb, accent);
                    }
                    if (fromUser && !isUpdatingSliders) {
                        String pfx = getProfilePrefix();
                        eqPrefs.edit()
                                .putInt(pfx + "preampMb", preampMb)
                                .putInt("preampMb", preampMb)
                                .apply();
                        notifyServiceReload();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (seekLoudness != null) {
            seekLoudness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (txtLoudnessValue != null) {
                        txtLoudnessValue.setText((progress / 10) + "%");
                    }
                    if (fromUser && !isUpdatingSliders) {
                        String pfx = getProfilePrefix();
                        eqPrefs.edit()
                                .putInt(pfx + "loudnessMb", progress)
                                .putInt("loudnessMb", progress)
                                .apply();
                        notifyServiceReload();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void setupBassAndBalanceSliders(final int accent) {
        if (seekBassBoost != null) {
            seekBassBoost.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    txtBassValue.setText((progress / 10) + "%");
                    if (fromUser && !isUpdatingSliders) {
                        String pfx = getProfilePrefix();
                        eqPrefs.edit()
                                .putInt(pfx + "bassStrength", progress)
                                .putInt("bassStrength", progress)
                                .apply();
                        notifyServiceReload();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (seekStereoBalance != null) {
            seekStereoBalance.setMax(200);
            seekStereoBalance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int balance = progress - 100;
                    if (fromUser && Math.abs(balance) <= 4) {
                        balance = 0;
                        seekBar.setProgress(100);
                    }
                    updateBalanceLabel(balance, accent);
                    if (fromUser && !isUpdatingSliders) {
                        String pfx = getProfilePrefix();
                        eqPrefs.edit()
                                .putInt(pfx + "stereoBalance", balance)
                                .putInt("stereoBalance", balance)
                                .apply();
                        notifyServiceReload();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void updateBalanceLabel(int balance, int accent) {
        if (txtBalanceValue == null) return;
        if (balance == 0) {
            txtBalanceValue.setText("Center");
            txtBalanceValue.setTextColor(Color.parseColor("#8A93A2"));
        } else if (balance < 0) {
            txtBalanceValue.setText("L " + Math.abs(balance) + "%");
            txtBalanceValue.setTextColor(accent);
        } else {
            txtBalanceValue.setText("R " + balance + "%");
            txtBalanceValue.setTextColor(accent);
        }
    }

    private void buildPresetChips(int accent) {
        eqPresetsContainer.removeAllViews();
        presetChipViews.clear();
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < PRESET_NAMES.length; i++) {
            final int presetIdx = i;
            TextView chip = new TextView(requireContext());
            chip.setText(PRESET_NAMES[i]);
            chip.setTextSize(12.5f);
            chip.setGravity(Gravity.CENTER);
            int hPad = (int) (14 * density);
            int vPad = (int) (7 * density);
            chip.setPadding(hPad, vPad, hPad, vPad);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd((int) (8 * density));
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                selectedPresetIndex = presetIdx;
                String pfx = getProfilePrefix();
                eqPrefs.edit()
                        .putInt(pfx + "presetPos", selectedPresetIndex)
                        .putInt("presetPos", selectedPresetIndex)
                        .apply();
                updatePresetChipStyles(accent);
                if (presetIdx > 0) {
                    applyPresetBands(PRESET_GAINS_MB[presetIdx], accent);
                }
            });

            presetChipViews.add(chip);
            eqPresetsContainer.addView(chip);
        }
        updatePresetChipStyles(accent);
    }

    private void updatePresetChipStyles(int accent) {
        if (!isAdded()) return;
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < presetChipViews.size(); i++) {
            TextView chip = presetChipViews.get(i);
            boolean selected = (i == selectedPresetIndex);
            chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setTextColor(selected ? Color.WHITE : Color.parseColor("#9AA3B2"));
            chip.setBackground(AccentColorHelper.createRoundedRect(
                    selected ? AccentColorHelper.withAlpha(accent, 0x38) : Color.parseColor("#161A22"),
                    selected ? accent : Color.parseColor("#242A36"),
                    (int) (1 * density),
                    16f * density
            ));
        }
    }

    private void buildBandRows(int accent) {
        eqBandsContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int range = maxLevelMb - minLevelMb;

        MusicService service = MusicService.getActiveInstance();
        Equalizer activeEq = service != null ? service.getEqualizerInstance() : null;

        for (int i = 0; i < BAND_COUNT; i++) {
            final int bandIdx = i;

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                rowLp.topMargin = (int) (10 * density);
            }
            row.setLayoutParams(rowLp);

            TextView txtFreq = new TextView(requireContext());
            txtFreq.setText(resolveFreqLabel(activeEq, i));
            txtFreq.setTextColor(Color.parseColor("#C8D0DC"));
            txtFreq.setTextSize(12.5f);
            txtFreq.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams freqLp = new LinearLayout.LayoutParams(
                    (int) (58 * density),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            txtFreq.setLayoutParams(freqLp);

            SeekBar seekBar = new SeekBar(requireContext());
            seekBar.setMax(range);
            seekBar.setProgressTintList(ColorStateList.valueOf(accent));
            seekBar.setThumbTintList(ColorStateList.valueOf(accent));
            LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            );
            seekBar.setLayoutParams(seekLp);

            TextView txtDb = new TextView(requireContext());
            txtDb.setGravity(Gravity.END);
            txtDb.setTextSize(12f);
            txtDb.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams dbLp = new LinearLayout.LayoutParams(
                    (int) (58 * density),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            txtDb.setLayoutParams(dbLp);

            int savedMb = getSavedBandLevelMb(i);
            int progress = Math.max(0, Math.min(range, savedMb - minLevelMb));
            seekBar.setProgress(progress);
            updateBandDbLabel(txtDb, savedMb, accent);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                    int levelMb = minLevelMb + prog;
                    if (fromUser && Math.abs(levelMb) < 35) {
                        levelMb = 0;
                        sb.setProgress(-minLevelMb);
                    }
                    updateBandDbLabel(txtDb, levelMb, accent);
                    if (fromUser && !isUpdatingSliders) {
                        selectedPresetIndex = 0; // Custom
                        updatePresetChipStyles(accent);
                        String pfx = getProfilePrefix();
                        eqPrefs.edit()
                                .putInt(pfx + "presetPos", 0)
                                .putInt("presetPos", 0)
                                .putInt(pfx + "band_mb_" + bandIdx, levelMb)
                                .putInt("band_mb_" + bandIdx, levelMb)
                                .putInt("band" + bandIdx, levelMb)
                                .apply();
                        notifyServiceReload();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {}

                @Override
                public void onStopTrackingTouch(SeekBar sb) {}
            });

            bandSeekBars[i] = seekBar;
            bandValueViews[i] = txtDb;

            row.addView(txtFreq);
            row.addView(seekBar);
            row.addView(txtDb);
            eqBandsContainer.addView(row);
        }
    }

    private int getSavedBandLevelMb(int bandIndex) {
        String pfx = getProfilePrefix();
        if (eqPrefs.contains(pfx + "band_mb_" + bandIndex)) {
            int val = eqPrefs.getInt(pfx + "band_mb_" + bandIndex, 0);
            return Math.max(minLevelMb, Math.min(maxLevelMb, val));
        }
        if (eqPrefs.contains("band_mb_" + bandIndex)) {
            int val = eqPrefs.getInt("band_mb_" + bandIndex, 0);
            return Math.max(minLevelMb, Math.min(maxLevelMb, val));
        }
        if (selectedPresetIndex > 0 && selectedPresetIndex < PRESET_GAINS_MB.length) {
            return PRESET_GAINS_MB[selectedPresetIndex][bandIndex];
        }
        return 0;
    }

    private String resolveFreqLabel(Equalizer eq, int bandIndex) {
        if (eq != null) {
            try {
                if (bandIndex < eq.getNumberOfBands()) {
                    int freqMilliHz = eq.getCenterFreq((short) bandIndex);
                    int hz = freqMilliHz / 1000;
                    if (hz >= 1000) {
                        float khz = hz / 1000f;
                        return (khz == (int) khz)
                                ? String.format(Locale.US, "%d kHz", (int) khz)
                                : String.format(Locale.US, "%.1f kHz", khz);
                    } else if (hz > 0) {
                        return hz + " Hz";
                    }
                }
            } catch (Exception ignored) {}
        }
        return DEFAULT_FREQ_LABELS[bandIndex];
    }

    private void updateBandDbLabel(TextView label, int levelMb, int accent) {
        float db = levelMb / 100f;
        String text = (db > 0.05f)
                ? String.format(Locale.US, "+%.1f dB", db)
                : (db < -0.05f)
                ? String.format(Locale.US, "%.1f dB", db)
                : "0.0 dB";
        label.setText(text);
        label.setTextColor(Math.abs(levelMb) > 10 ? accent : Color.parseColor("#8A93A2"));
    }

    private void applyPresetBands(int[] gainsMb, int accent) {
        isUpdatingSliders = true;
        int range = maxLevelMb - minLevelMb;
        String pfx = getProfilePrefix();
        SharedPreferences.Editor editor = eqPrefs.edit();
        for (int i = 0; i < BAND_COUNT; i++) {
            int clamped = Math.max(minLevelMb, Math.min(maxLevelMb, gainsMb[i]));
            if (bandSeekBars[i] != null) {
                bandSeekBars[i].setProgress(Math.max(0, Math.min(range, clamped - minLevelMb)));
            }
            if (bandValueViews[i] != null) {
                updateBandDbLabel(bandValueViews[i], clamped, accent);
            }
            editor.putInt(pfx + "band_mb_" + i, clamped);
            editor.putInt("band_mb_" + i, clamped);
            editor.putInt("band" + i, clamped);
        }
        editor.apply();
        isUpdatingSliders = false;
        notifyServiceReload();
    }

    private void buildReverbChips(int accent) {
        reverbChipsContainer.removeAllViews();
        reverbChipViews.clear();
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < REVERB_NAMES.length; i++) {
            final int reverbIdx = i;
            TextView chip = new TextView(requireContext());
            chip.setText(REVERB_NAMES[i]);
            chip.setTextSize(12f);
            chip.setGravity(Gravity.CENTER);
            int hPad = (int) (13 * density);
            int vPad = (int) (6 * density);
            chip.setPadding(hPad, vPad, hPad, vPad);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd((int) (8 * density));
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                selectedReverbPreset = reverbIdx;
                String pfx = getProfilePrefix();
                eqPrefs.edit()
                        .putInt(pfx + "reverbPreset", selectedReverbPreset)
                        .putInt("reverbPreset", selectedReverbPreset)
                        .apply();
                updateReverbChipStyles(accent);
                notifyServiceReload();
            });

            reverbChipViews.add(chip);
            reverbChipsContainer.addView(chip);
        }
        updateReverbChipStyles(accent);
    }

    private void updateReverbChipStyles(int accent) {
        if (!isAdded()) return;
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < reverbChipViews.size(); i++) {
            TextView chip = reverbChipViews.get(i);
            boolean selected = (i == selectedReverbPreset);
            chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setTextColor(selected ? Color.WHITE : Color.parseColor("#9AA3B2"));
            chip.setBackground(AccentColorHelper.createRoundedRect(
                    selected ? AccentColorHelper.withAlpha(accent, 0x38) : Color.parseColor("#161A22"),
                    selected ? accent : Color.parseColor("#242A36"),
                    (int) (1 * density),
                    14f * density
            ));
        }
    }

    private void resetAllToDefault(int accent) {
        selectedPresetIndex = 1; // Flat
        selectedReverbPreset = 0; // None
        String pfx = getProfilePrefix();
        eqPrefs.edit()
                .putBoolean("isEqualizerEnabled", true)
                .putInt(pfx + "presetPos", 1)
                .putInt("presetPos", 1)
                .putInt(pfx + "preampMb", 0)
                .putInt("preampMb", 0)
                .putInt(pfx + "loudnessMb", 0)
                .putInt("loudnessMb", 0)
                .putInt(pfx + "bassStrength", 0)
                .putInt("bassStrength", 0)
                .putInt(pfx + "stereoBalance", 0)
                .putInt("stereoBalance", 0)
                .putInt(pfx + "reverbPreset", 0)
                .putInt("reverbPreset", 0)
                .apply();
        switchEqEnable.setChecked(true);
        applyPresetBands(PRESET_GAINS_MB[1], accent);
        loadCurrentProfileIntoUi(accent);
    }

    private void updateControlsEnabledState(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.45f;
        if (eqDspTogglesRow != null) eqDspTogglesRow.setAlpha(alpha);
        if (eqPreampCard != null) eqPreampCard.setAlpha(alpha);
        if (eqPresetsContainer != null) eqPresetsContainer.setAlpha(alpha);
        if (eqBandsContainer != null) eqBandsContainer.setAlpha(alpha);
        if (seekPreamp != null) seekPreamp.setEnabled(enabled);
        if (seekLoudness != null) seekLoudness.setEnabled(enabled);
        if (seekBassBoost != null) seekBassBoost.setEnabled(enabled);
        if (seekStereoBalance != null) seekStereoBalance.setEnabled(enabled);
        for (SeekBar sb : bandSeekBars) {
            if (sb != null) sb.setEnabled(enabled);
        }
    }

    private void notifyServiceReload() {
        MusicService service = MusicService.getActiveInstance();
        if (service != null) {
            service.reloadEqualizerNow();
            return;
        }
        if (getContext() != null) {
            Intent intent = new Intent(getContext(), MusicService.class);
            intent.setAction(MusicService.ACTION_RELOAD_EQUALIZER);
            try {
                getContext().startService(intent);
            } catch (Exception ignored) {}
        }
    }
}
