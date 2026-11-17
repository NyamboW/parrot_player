package com.example.parrot;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.Locale;
import java.util.Set;

public class SettingsFragment extends Fragment {

    public static final String PREFS_NAME = "parrot_settings";
    public static final String KEY_MUSIC_FOLDERS = "music_folders";
    public static final String KEY_PLAYBACK_SPEED = "playback_speed";
    public static final String KEY_CROSSFADE_SECONDS = "crossfade_seconds";
    public static final String KEY_SKIP_SILENCE = "skip_silence";
    public static final String KEY_PAUSE_ON_UNPLUG = "pause_on_unplug";
    public static final String KEY_RESUME_ON_HEADSET = "resume_on_headset";
    public static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";

    private MusicService musicService;
    private boolean isBound = false;
    private SharedPreferences prefs;

    private TextView txtSleepTimerValue, txtSleepTimerStatus;
    private SeekBar seekBarSleepTimer;
    private TextView txtPlaybackSpeedValue, btnResetSpeed;
    private SeekBar seekBarPlaybackSpeed;
    private TextView txtCrossfadeValue;
    private SeekBar seekBarCrossfade;
    private SwitchCompat switchSkipSilence, switchPauseOnUnplug, switchResumeOnHeadset, switchKeepScreenOn;
    private TextView txtMusicFoldersSummary, txtAccentColorName;
    private View viewAccentSwatch;
    private TextView headerPlaybackTiming, headerAudioHeadset, headerLibraryDisplay;
    private ImageView iconSleepTimer, iconPlaybackSpeed, iconCrossfade, iconSkipSilence;
    private ImageView iconEqualizer, iconPauseUnplug, iconResumeHeadset;
    private ImageView iconAccentColor, iconMusicFolders, iconKeepScreenOn;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable sleepTimerUiUpdater = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;
            updateSleepTimerStatusUi();
            uiHandler.postDelayed(this, 1000L);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MyBinder binder = (MusicService.MyBinder) service;
            musicService = binder.getService();
            isBound = true;
            syncWithService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    public SettingsFragment() {}

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() != null) {
            Intent bindIntent = new Intent(getActivity(), MusicService.class);
            getActivity().bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
        uiHandler.post(sleepTimerUiUpdater);
    }

    @Override
    public void onStop() {
        super.onStop();
        uiHandler.removeCallbacks(sleepTimerUiUpdater);
        if (isBound && getActivity() != null) {
            getActivity().unbindService(serviceConnection);
            isBound = false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        headerPlaybackTiming = view.findViewById(R.id.headerPlaybackTiming);
        headerAudioHeadset = view.findViewById(R.id.headerAudioHeadset);
        headerLibraryDisplay = view.findViewById(R.id.headerLibraryDisplay);

        iconSleepTimer = view.findViewById(R.id.iconSleepTimer);
        iconPlaybackSpeed = view.findViewById(R.id.iconPlaybackSpeed);
        iconCrossfade = view.findViewById(R.id.iconCrossfade);
        iconSkipSilence = view.findViewById(R.id.iconSkipSilence);
        iconEqualizer = view.findViewById(R.id.iconEqualizer);
        iconPauseUnplug = view.findViewById(R.id.iconPauseUnplug);
        iconResumeHeadset = view.findViewById(R.id.iconResumeHeadset);
        iconAccentColor = view.findViewById(R.id.iconAccentColor);
        iconMusicFolders = view.findViewById(R.id.iconMusicFolders);
        iconKeepScreenOn = view.findViewById(R.id.iconKeepScreenOn);

        txtSleepTimerValue = view.findViewById(R.id.txtSleepTimerValue);
        txtSleepTimerStatus = view.findViewById(R.id.txtSleepTimerStatus);
        seekBarSleepTimer = view.findViewById(R.id.seekBarSleepTimer);

        txtPlaybackSpeedValue = view.findViewById(R.id.txtPlaybackSpeedValue);
        btnResetSpeed = view.findViewById(R.id.btnResetSpeed);
        seekBarPlaybackSpeed = view.findViewById(R.id.seekBarPlaybackSpeed);

        txtCrossfadeValue = view.findViewById(R.id.txtCrossfadeValue);
        seekBarCrossfade = view.findViewById(R.id.seekBarCrossfade);

        switchSkipSilence = view.findViewById(R.id.switchSkipSilence);
        switchPauseOnUnplug = view.findViewById(R.id.switchPauseOnUnplug);
        switchResumeOnHeadset = view.findViewById(R.id.switchResumeOnHeadset);
        switchKeepScreenOn = view.findViewById(R.id.switchKeepScreenOn);
        txtMusicFoldersSummary = view.findViewById(R.id.txtMusicFoldersSummary);
        txtAccentColorName = view.findViewById(R.id.txtAccentColorName);
        viewAccentSwatch = view.findViewById(R.id.viewAccentSwatch);

        View settingSkipSilence = view.findViewById(R.id.setting_skip_silence);
        View settingEqualizer = view.findViewById(R.id.setting_equalizer);
        View settingPauseOnUnplug = view.findViewById(R.id.setting_pause_on_unplug);
        View settingResumeOnHeadset = view.findViewById(R.id.setting_resume_on_headset);
        View settingAccentColor = view.findViewById(R.id.setting_accent_color);
        View settingMusicFolders = view.findViewById(R.id.setting_music_folders);
        View settingKeepScreenOn = view.findViewById(R.id.setting_keep_screen_on);

        setupSleepTimerSlider();
        setupPlaybackSpeedSlider();
        setupCrossfadeSlider();
        setupToggleSwitches(
                settingSkipSilence,
                settingPauseOnUnplug,
                settingResumeOnHeadset,
                settingKeepScreenOn
        );

        settingAccentColor.setOnClickListener(v ->
                AccentColorHelper.showColorPickerDialog(requireContext(), this::applyAccentColor)
        );

        settingEqualizer.setOnClickListener(v -> openEqualizerDialog());

        settingMusicFolders.setOnClickListener(v ->
                MusicFolderHelper.showFolderSelectionDialog(requireContext(), false, this::updateMusicFoldersSummary)
        );
        updateMusicFoldersSummary();
        applyAccentColor();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        applyAccentColor();
    }

    private void applyAccentColor() {
        if (!isAdded()) return;
        int accent = AccentColorHelper.getAccentColor(requireContext());
        ColorStateList accentList = ColorStateList.valueOf(accent);

        if (headerPlaybackTiming != null) headerPlaybackTiming.setTextColor(accent);
        if (headerAudioHeadset != null) headerAudioHeadset.setTextColor(accent);
        if (headerLibraryDisplay != null) headerLibraryDisplay.setTextColor(accent);

        ImageView[] icons = new ImageView[]{
                iconSleepTimer, iconPlaybackSpeed, iconCrossfade, iconSkipSilence,
                iconEqualizer, iconPauseUnplug, iconResumeHeadset,
                iconAccentColor, iconMusicFolders, iconKeepScreenOn
        };
        for (ImageView icon : icons) {
            if (icon != null) {
                ImageViewCompat.setImageTintList(icon, accentList);
            }
        }

        if (txtSleepTimerValue != null) txtSleepTimerValue.setTextColor(accent);
        if (txtPlaybackSpeedValue != null) txtPlaybackSpeedValue.setTextColor(accent);
        if (txtCrossfadeValue != null) txtCrossfadeValue.setTextColor(accent);

        AccentColorHelper.tintSeekBar(seekBarSleepTimer, accent);
        AccentColorHelper.tintSeekBar(seekBarPlaybackSpeed, accent);
        AccentColorHelper.tintSeekBar(seekBarCrossfade, accent);

        tintSwitch(switchSkipSilence, accent);
        tintSwitch(switchPauseOnUnplug, accent);
        tintSwitch(switchResumeOnHeadset, accent);
        tintSwitch(switchKeepScreenOn, accent);

        if (txtAccentColorName != null) {
            txtAccentColorName.setText(AccentColorHelper.getAccentName(requireContext()));
        }
        if (viewAccentSwatch != null) {
            float density = getResources().getDisplayMetrics().density;
            viewAccentSwatch.setBackground(AccentColorHelper.createCircle(
                    accent, Color.parseColor("#33FFFFFF"), (int) (1.5f * density)));
        }
    }

    private void tintSwitch(SwitchCompat sw, int accent) {
        if (sw == null) return;
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        ColorStateList thumbColors = new ColorStateList(states, new int[]{
                accent, Color.parseColor("#B0B6C2")
        });
        ColorStateList trackColors = new ColorStateList(states, new int[]{
                AccentColorHelper.withAlpha(accent, 0x55), Color.parseColor("#2A303D")
        });
        sw.setThumbTintList(thumbColors);
        sw.setTrackTintList(trackColors);
    }

    private void setupSleepTimerSlider() {
        seekBarSleepTimer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int minutes = progress * 5;
                txtSleepTimerValue.setText(minutes == 0 ? "Off" : minutes + " min");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int minutes = seekBar.getProgress() * 5;
                if (isBound && musicService != null) {
                    musicService.setSleepTimerMinutes(minutes);
                }
                updateSleepTimerStatusUi();
                if (minutes > 0) {
                    Toast.makeText(getContext(), "Sleep timer set for " + minutes + " minutes", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Sleep timer turned off", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void syncWithService() {
        if (!isBound || musicService == null || !isAdded()) return;
        long remainingMs = musicService.getSleepTimerRemainingMs();
        if (remainingMs > 0) {
            int approxSteps = (int) Math.ceil(remainingMs / (60_000.0 * 5.0));
            seekBarSleepTimer.setProgress(Math.min(24, Math.max(1, approxSteps)));
        } else {
            seekBarSleepTimer.setProgress(0);
        }
        updateSleepTimerStatusUi();
    }

    private void updateSleepTimerStatusUi() {
        if (txtSleepTimerStatus == null || txtSleepTimerValue == null) return;
        if (isBound && musicService != null) {
            long remainingMs = musicService.getSleepTimerRemainingMs();
            if (remainingMs > 0) {
                long totalSec = remainingMs / 1000L;
                long min = totalSec / 60L;
                long sec = totalSec % 60L;
                txtSleepTimerValue.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
                txtSleepTimerStatus.setText("Playback will fade out and pause automatically");
                return;
            }
        }
        if (seekBarSleepTimer != null && !seekBarSleepTimer.isPressed()) {
            txtSleepTimerValue.setText("Off");
        }
        txtSleepTimerStatus.setText("Auto-pause playback after timer ends");
    }

    private void setupPlaybackSpeedSlider() {
        float savedSpeed = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f);
        int initialProgress = Math.round((savedSpeed - 0.50f) / 0.05f);
        initialProgress = Math.max(0, Math.min(30, initialProgress));
        seekBarPlaybackSpeed.setProgress(initialProgress);
        txtPlaybackSpeedValue.setText(String.format(Locale.US, "%.2fx", 0.50f + initialProgress * 0.05f));

        seekBarPlaybackSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float speed = 0.50f + progress * 0.05f;
                txtPlaybackSpeedValue.setText(String.format(Locale.US, "%.2fx", speed));
                if (fromUser) {
                    prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply();
                    if (isBound && musicService != null) {
                        musicService.setPlaybackSpeed(speed);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnResetSpeed.setOnClickListener(v -> {
            seekBarPlaybackSpeed.setProgress(10);
            prefs.edit().putFloat(KEY_PLAYBACK_SPEED, 1.0f).apply();
            txtPlaybackSpeedValue.setText("1.00x");
            if (isBound && musicService != null) {
                musicService.setPlaybackSpeed(1.0f);
            }
        });
    }

    private void setupCrossfadeSlider() {
        int savedCrossfade = prefs.getInt(KEY_CROSSFADE_SECONDS, 0);
        savedCrossfade = Math.max(0, Math.min(12, savedCrossfade));
        seekBarCrossfade.setProgress(savedCrossfade);
        txtCrossfadeValue.setText(savedCrossfade == 0 ? "Off" : savedCrossfade + "s");

        seekBarCrossfade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txtCrossfadeValue.setText(progress == 0 ? "Off" : progress + "s");
                if (fromUser) {
                    prefs.edit().putInt(KEY_CROSSFADE_SECONDS, progress).apply();
                    if (isBound && musicService != null) {
                        musicService.setCrossfadeSeconds(progress);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupToggleSwitches(View rowSkipSilence,
                                     View rowPauseOnUnplug,
                                     View rowResumeOnHeadset,
                                     View rowKeepScreenOn) {
        boolean skipSilence = prefs.getBoolean(KEY_SKIP_SILENCE, false);
        boolean pauseOnUnplug = prefs.getBoolean(KEY_PAUSE_ON_UNPLUG, true);
        boolean resumeOnHeadset = prefs.getBoolean(KEY_RESUME_ON_HEADSET, false);
        boolean keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false);

        switchSkipSilence.setChecked(skipSilence);
        switchPauseOnUnplug.setChecked(pauseOnUnplug);
        switchResumeOnHeadset.setChecked(resumeOnHeadset);
        switchKeepScreenOn.setChecked(keepScreenOn);

        View.OnClickListener skipSilenceListener = v -> {
            boolean next = (v == switchSkipSilence) ? switchSkipSilence.isChecked() : !switchSkipSilence.isChecked();
            switchSkipSilence.setChecked(next);
            prefs.edit().putBoolean(KEY_SKIP_SILENCE, next).apply();
            if (isBound && musicService != null) {
                musicService.setSkipSilence(next);
            }
        };
        rowSkipSilence.setOnClickListener(skipSilenceListener);
        switchSkipSilence.setOnClickListener(skipSilenceListener);

        View.OnClickListener pauseUnplugListener = v -> {
            boolean next = (v == switchPauseOnUnplug) ? switchPauseOnUnplug.isChecked() : !switchPauseOnUnplug.isChecked();
            switchPauseOnUnplug.setChecked(next);
            prefs.edit().putBoolean(KEY_PAUSE_ON_UNPLUG, next).apply();
        };
        rowPauseOnUnplug.setOnClickListener(pauseUnplugListener);
        switchPauseOnUnplug.setOnClickListener(pauseUnplugListener);

        View.OnClickListener resumeHeadsetListener = v -> {
            boolean next = (v == switchResumeOnHeadset) ? switchResumeOnHeadset.isChecked() : !switchResumeOnHeadset.isChecked();
            switchResumeOnHeadset.setChecked(next);
            prefs.edit().putBoolean(KEY_RESUME_ON_HEADSET, next).apply();
        };
        rowResumeOnHeadset.setOnClickListener(resumeHeadsetListener);
        switchResumeOnHeadset.setOnClickListener(resumeHeadsetListener);

        View.OnClickListener keepScreenOnListener = v -> {
            boolean next = (v == switchKeepScreenOn) ? switchKeepScreenOn.isChecked() : !switchKeepScreenOn.isChecked();
            switchKeepScreenOn.setChecked(next);
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, next).apply();
        };
        rowKeepScreenOn.setOnClickListener(keepScreenOnListener);
        switchKeepScreenOn.setOnClickListener(keepScreenOnListener);
    }

    private void updateMusicFoldersSummary() {
        if (txtMusicFoldersSummary == null || !isAdded()) return;
        Set<String> folders = MusicFolderHelper.getSelectedFolders(requireContext());
        if (folders != null && !folders.isEmpty()) {
            int count = folders.size();
            txtMusicFoldersSummary.setText(count + (count == 1 ? " folder selected for scanning" : " folders selected for scanning"));
        } else {
            txtMusicFoldersSummary.setText("Select which folders to scan for music");
        }
    }

    private void openEqualizerDialog() {
        new ParrotEqualizerDialogFragment().show(getChildFragmentManager(), "eq");
    }
}
