package com.example.parrot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class HomeFragment extends Fragment {

    private final String[] tabTitles = new String[]{"Artists", "Tracks", "Albums", "Playlists", "Favorites", "Folders"};
    private TabLayout tabLayout;

    private final BroadcastReceiver accentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            applyAccentColor();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tabLayout = view.findViewById(R.id.tabLayout);
        ViewPager2 viewPager = view.findViewById(R.id.viewPager);

        HomePagerAdapter adapter = new HomePagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(tabTitles[position])).attach();

        // Set default to Tracks
        viewPager.setCurrentItem(1, false);
        applyAccentColor();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(accentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(accentReceiver, filter);
        }
        applyAccentColor();
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            requireContext().unregisterReceiver(accentReceiver);
        } catch (Exception ignored) {}
    }

    private void applyAccentColor() {
        if (tabLayout != null && isAdded()) {
            tabLayout.setSelectedTabIndicatorColor(AccentColorHelper.getAccentColor(requireContext()));
        }
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
