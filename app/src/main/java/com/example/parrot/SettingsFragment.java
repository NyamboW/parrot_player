package com.example.parrot;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bullhead.equalizer.DialogEqualizerFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsFragment extends Fragment {

    private MusicService musicService;
    private boolean isBound = false;

    private SwitchCompat switchDarkMode;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "parrot_settings";
    private static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_MUSIC_FOLDERS = "music_folders";

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MyBinder binder = (MusicService.MyBinder) service;
            musicService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() != null) {
            Intent bindIntent = new Intent(getActivity(), MusicService.class);
            getActivity().bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
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

        switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        View settingDarkMode = view.findViewById(R.id.setting_dark_mode);
        View settingMusicFolders = view.findViewById(R.id.setting_music_folders);
        View settingEqualizer = view.findViewById(R.id.setting_equalizer);

        // Dark Mode Logic
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, true);
        switchDarkMode.setChecked(isDarkMode);

        View.OnClickListener darkModeListener = v -> {
            boolean newState = !switchDarkMode.isChecked();
            if (v == switchDarkMode) {
                newState = switchDarkMode.isChecked();
            } else {
                switchDarkMode.setChecked(newState);
            }
            
            prefs.edit().putBoolean(KEY_DARK_MODE, newState).apply();
            
            if (newState) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        };

        settingDarkMode.setOnClickListener(darkModeListener);
        switchDarkMode.setOnClickListener(darkModeListener);

        // Equalizer Logic
        settingEqualizer.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                int sessionId = musicService.getAudioSessionId();
                if (sessionId != 0) {
                    DialogEqualizerFragment fragment = DialogEqualizerFragment.newBuilder()
                            .setAudioSessionId(sessionId)
                            .themeColor(android.graphics.Color.parseColor("#0B0B0F"))
                            .textColor(android.graphics.Color.WHITE)
                            .accentAlpha(android.graphics.Color.parseColor("#444CAF50"))
                            .darkColor(android.graphics.Color.parseColor("#0B0B0F"))
                            .setAccentColor(android.graphics.Color.parseColor("#4CAF50"))
                            .build();
                    fragment.show(getChildFragmentManager(), "eq");
                    
                    getChildFragmentManager().executePendingTransactions();
                    if (fragment.getDialog() != null) {
                        fragment.getDialog().setOnDismissListener(dialog -> {
                            saveAndReloadEqualizer();
                        });
                    }
                } else {
                    Toast.makeText(getContext(), "Play a song first to adjust EQ", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "Play a song first to adjust EQ", Toast.LENGTH_SHORT).show();
            }
        });
        // Music Folders Logic
        settingMusicFolders.setOnClickListener(v -> showFolderSelectionDialog());

        return view;
    }

    private void showFolderSelectionDialog() {
        MusicFolderHelper.showFolderSelectionDialog(requireContext(), false, null);
    }
    
    private void saveAndReloadEqualizer() {
        if (getContext() == null) return;
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("parrot_eq", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        editor.putBoolean("isEqualizerEnabled", com.bullhead.equalizer.Settings.isEqualizerEnabled);
        editor.putInt("presetPos", com.bullhead.equalizer.Settings.presetPos);
        editor.putInt("reverbPreset", com.bullhead.equalizer.Settings.reverbPreset);
        editor.putInt("bassStrength", com.bullhead.equalizer.Settings.bassStrength);
        
        if (com.bullhead.equalizer.Settings.seekbarpos != null) {
            for (int i = 0; i < com.bullhead.equalizer.Settings.seekbarpos.length; i++) {
                editor.putInt("band" + i, com.bullhead.equalizer.Settings.seekbarpos[i]);
            }
        }
        editor.apply();
        
        Intent intent = new Intent(getContext(), MusicService.class);
        intent.setAction(MusicService.ACTION_RELOAD_EQUALIZER);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}