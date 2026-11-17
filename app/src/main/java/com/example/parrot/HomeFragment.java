package com.example.parrot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.Collections;

public class HomeFragment extends Fragment {

    private final String[] tabTitles = new String[]{"Artists", "Tracks", "Albums", "Playlists", "Favorites", "Folders"};
    private TabLayout tabLayout;
    private TextView txtHomeTrackCount, txtHomeShuffleLabel;
    private ImageView imgHomeShuffleIcon;
    private LinearLayout btnHomePlayAll, btnHomeShuffleAll;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            if (AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED.equals(intent.getAction())) {
                applyAccentColor();
            } else if (MusicFolderHelper.ACTION_FOLDERS_UPDATED.equals(intent.getAction())
                    || PermissionHelper.ACTION_PERMISSIONS_GRANTED.equals(intent.getAction())) {
                refreshLibraryCount();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tabLayout = view.findViewById(R.id.tabLayout);
        ViewPager2 viewPager = view.findViewById(R.id.viewPager);
        txtHomeTrackCount = view.findViewById(R.id.txtHomeTrackCount);
        btnHomePlayAll = view.findViewById(R.id.btnHomePlayAll);
        btnHomeShuffleAll = view.findViewById(R.id.btnHomeShuffleAll);
        imgHomeShuffleIcon = view.findViewById(R.id.imgHomeShuffleIcon);
        txtHomeShuffleLabel = view.findViewById(R.id.txtHomeShuffleLabel);

        HomePagerAdapter adapter = new HomePagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2);
        viewPager.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            if (absPos >= 1f) {
                page.setAlpha(0f);
            } else {
                page.setAlpha(1f - absPos * 0.35f);
                float scale = 1f - absPos * 0.04f;
                page.setScaleX(scale);
                page.setScaleY(scale);
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(tabTitles[position])).attach();
        tabLayout.setTabRippleColor(null);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (getActivity() instanceof AutoHideScrollHelper.ScrollBarController) {
                    ((AutoHideScrollHelper.ScrollBarController) getActivity()).setBarsVisible(true);
                }
            }
        });

        // Default to Tracks
        viewPager.setCurrentItem(1, false);

        btnHomePlayAll.setOnClickListener(v -> playLibrary(false));
        btnHomeShuffleAll.setOnClickListener(v -> playLibrary(true));

        applyAccentColor();
        refreshLibraryCount();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED);
        filter.addAction(MusicFolderHelper.ACTION_FOLDERS_UPDATED);
        filter.addAction(PermissionHelper.ACTION_PERMISSIONS_GRANTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(updateReceiver, filter);
        }
        applyAccentColor();
        refreshLibraryCount();
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            requireContext().unregisterReceiver(updateReceiver);
        } catch (Exception ignored) {}
    }

    private void applyAccentColor() {
        if (!isAdded()) return;
        int accent = AccentColorHelper.getAccentColor(requireContext());
        if (tabLayout != null) {
            tabLayout.setSelectedTabIndicatorColor(AccentColorHelper.withAlpha(accent, 0x3A));
        }
        if (btnHomeShuffleAll != null) {
            float density = getResources().getDisplayMetrics().density;
            btnHomeShuffleAll.setBackground(AccentColorHelper.createRoundedRect(
                    AccentColorHelper.withAlpha(accent, 0x1E),
                    AccentColorHelper.withAlpha(accent, 0x55),
                    (int) (1 * density),
                    14f * density));
        }
        if (imgHomeShuffleIcon != null) {
            ImageViewCompat.setImageTintList(imgHomeShuffleIcon, ColorStateList.valueOf(accent));
        }
        if (txtHomeShuffleLabel != null) {
            txtHomeShuffleLabel.setTextColor(accent);
        }
    }

    private ArrayList<String> queryAllowedSongs() {
        ArrayList<String> songs = new ArrayList<>();
        if (!isAdded() || !PermissionHelper.hasAudioPermission(requireContext())) return songs;
        try {
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
            Cursor cursor = requireContext().getContentResolver().query(
                    uri,
                    new String[]{MediaStore.Audio.Media.DATA},
                    selection,
                    null,
                    MediaStore.Audio.Media.DATE_ADDED + " DESC"
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(0);
                    if (MusicFolderHelper.isPathAllowed(requireContext(), path)) {
                        songs.add(path);
                    }
                }
                cursor.close();
            }
        } catch (Exception ignored) {}
        return songs;
    }

    private void refreshLibraryCount() {
        if (txtHomeTrackCount == null || !isAdded()) return;
        ArrayList<String> songs = queryAllowedSongs();
        int count = songs.size();
        txtHomeTrackCount.setText(count + (count == 1 ? " track ready" : " tracks ready"));
    }

    private void playLibrary(boolean shuffle) {
        ArrayList<String> songs = queryAllowedSongs();
        if (songs.isEmpty()) {
            Toast.makeText(getContext(), "No tracks in selected music folders", Toast.LENGTH_SHORT).show();
            return;
        }
        if (shuffle) {
            Collections.shuffle(songs);
        }
        AlbumArtLoader.getInstance().preloadFullArt(songs.get(0));
        Intent intent = new Intent(getContext(), PlayerActivity.class);
        intent.putStringArrayListExtra("songs", songs);
        intent.putExtra("pos", 0);
        startActivity(intent);
    }

    private class HomePagerAdapter extends FragmentStateAdapter {

        public HomePagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new ArtistsFragment();
                case 1:
                    return new Music_Fragment();
                case 2:
                    return new AlbumsFragment();
                case 3:
                    return new PlaylistsFragment();
                case 4:
                    return new FavFragment();
                case 5:
                    return new FoldersFragment();
                default:
                    return new Music_Fragment();
            }
        }

        @Override
        public int getItemCount() {
            return tabTitles.length;
        }
    }
}
