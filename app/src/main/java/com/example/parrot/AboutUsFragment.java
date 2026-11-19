package com.example.parrot;

import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class AboutUsFragment extends Fragment {

    public AboutUsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about_us, container, false);

        TextView appVersion = view.findViewById(R.id.appVersion);
        TextView txtDeviceSpec = view.findViewById(R.id.txtDeviceSpec);
        TextView txtAboutAccentTagline = view.findViewById(R.id.txtAboutAccentTagline);
        TextView btnAboutChangelog = view.findViewById(R.id.btnAboutChangelog);
        TextView btnAboutPrivacy = view.findViewById(R.id.btnAboutPrivacy);

        View bulletAboutPlayer = view.findViewById(R.id.bulletAboutPlayer);
        View bulletDeveloper = view.findViewById(R.id.bulletDeveloper);
        View bulletAudioEngine = view.findViewById(R.id.bulletAudioEngine);
        View bulletLibraries = view.findViewById(R.id.bulletLibraries);

        if (appVersion != null) {
            try {
                PackageInfo info = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0);
                if (info.versionName != null) {
                    appVersion.setText("v" + info.versionName);
                }
            } catch (Exception ignored) {}
        }

        if (txtDeviceSpec != null) {
            String abi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                    ? Build.SUPPORTED_ABIS[0]
                    : "armeabi";
            txtDeviceSpec.setText(abi + ", api " + Build.VERSION.SDK_INT);
        }

        int accent = AccentColorHelper.getAccentColor(requireContext());
        if (txtAboutAccentTagline != null) txtAboutAccentTagline.setTextColor(accent);
        if (btnAboutChangelog != null) btnAboutChangelog.setTextColor(accent);
        if (btnAboutPrivacy != null) btnAboutPrivacy.setTextColor(accent);

        if (bulletAboutPlayer != null) bulletAboutPlayer.setBackgroundColor(accent);
        if (bulletDeveloper != null) bulletDeveloper.setBackgroundColor(accent);
        if (bulletAudioEngine != null) bulletAudioEngine.setBackgroundColor(accent);
        if (bulletLibraries != null) bulletLibraries.setBackgroundColor(accent);

        if (btnAboutChangelog != null) {
            btnAboutChangelog.setOnClickListener(v -> showChangelogDialog());
        }
        if (btnAboutPrivacy != null) {
            btnAboutPrivacy.setOnClickListener(v -> showPrivacyDialog());
        }

        return view;
    }

    private void showChangelogDialog() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Parrot Change Log")
                .setMessage(
                        "• Custom 5-Band Equalizer, Bass Boost & Room Reverb\n"
                                + "• Synchronized LRC & Embedded Lyrics Viewer\n"
                                + "• 3-State Repeat (Off / All / One) & A–B Section Loop\n"
                                + "• Non-Repeating Fisher-Yates Shuffle & Track Counter\n"
                                + "• Smart Music Folders, M3U Import/Export & Queue Reorder\n"
                                + "• Swipe-Down Mini-Player & Auto-Hide Library Bars\n"
                                + "• Dynamic Accent Color Themes"
                )
                .setPositiveButton("Close", null)
                .show();
    }

    private void showPrivacyDialog() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Privacy & Offline Policy")
                .setMessage(
                        "Parrot Audio Player by attomaticsystems operates completely offline on your device.\n\n"
                                + "• No analytics, tracking, or advertisements.\n"
                                + "• Audio, storage, and notification permissions are used solely to scan, play, organize, and delete your local music files as requested."
                )
                .setPositiveButton("OK", null)
                .show();
    }
}
