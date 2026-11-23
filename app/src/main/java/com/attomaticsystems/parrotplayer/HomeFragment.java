package com.attomaticsystems.parrotplayer;

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

    public static final int TAB_ARTISTS = 0;
    public static final int TAB_TRACKS = 1;
    public static final int TAB_ALBUMS = 2;
    public static final int TAB_PLAYLISTS = 3;
    public static final int TAB_FAVORITES = 4;
    public static final int TAB_MOST_PLAYED = 5;
    public static final int TAB_RECENTLY_PLAYED = 6;
    public static final int TAB_RECENTLY_ADDED = 7;
    public static final int TAB_FOLDERS = 8;

    private final String[] tabTitles = new String[]{
            "Artists",
            "Tracks",
            "Albums",
            "Playlists",
            "Favorites",
            "Most Played",
            "Recently Played",
            "Recently Added",
            "Folders"
    };
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private int pendingTabIndex = -1;

    private final BroadcastReceiver accentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && AccentColorHelper.ACTION_ACCENT_COLOR_CHANGED.equals(intent.getAction())) {
                applyAccentColor();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tabLayout = view.findViewById(R.id.tabLayout);
        viewPager = view.findViewById(R.id.viewPager);

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

        // Default to Tracks (or pending shortcut tab if requested before view creation)
        int initialTab = (pendingTabIndex >= 0 && pendingTabIndex < tabTitles.length) ? pendingTabIndex : 1;
        pendingTabIndex = -1;
        viewPager.setCurrentItem(initialTab, false);
        applyAccentColor();

        return view;
    }

    /**
     * Switches the active Home tab (e.g. from launcher shortcuts).
     */
    public void selectTab(int index) {
        if (index < 0 || index >= tabTitles.length) return;
        if (viewPager != null) {
            viewPager.setCurrentItem(index, false);
        } else {
            pendingTabIndex = index;
        }
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
        if (!isAdded() || tabLayout == null) return;
        int accent = AccentColorHelper.getAccentColor(requireContext());
        tabLayout.setSelectedTabIndicatorColor(AccentColorHelper.withAlpha(accent, 0x3A));
    }

    private class HomePagerAdapter extends FragmentStateAdapter {

        public HomePagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case TAB_ARTISTS:
                    return new ArtistsFragment();
                case TAB_TRACKS:
                    return new Music_Fragment();
                case TAB_ALBUMS:
                    return new AlbumsFragment();
                case TAB_PLAYLISTS:
                    return new PlaylistsFragment();
                case TAB_FAVORITES:
                    return new FavFragment();
                case TAB_MOST_PLAYED:
                    return SmartCategoryFragment.newInstance(ListeningStatsManager.SMART_MOST_PLAYED);
                case TAB_RECENTLY_PLAYED:
                    return SmartCategoryFragment.newInstance(ListeningStatsManager.SMART_RECENTLY_PLAYED);
                case TAB_RECENTLY_ADDED:
                    return SmartCategoryFragment.newInstance(ListeningStatsManager.SMART_RECENTLY_ADDED);
                case TAB_FOLDERS:
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
