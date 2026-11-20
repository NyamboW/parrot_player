package com.attomaticsystems.parrotplayer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

/**
 * Shared toolbar SearchView setup for fragments and activities to keep dark-theme styling
 * and keyboard focus handling consistent without duplicating boilerplate.
 */
public final class ToolbarSearchHelper {

    public interface QueryListener {
        void onQueryChanged(@NonNull String query);
    }

    private ToolbarSearchHelper() {}

    /**
     * Attaches an options menu strictly while the Fragment is RESUMED so ViewPager2 offscreen tabs
     * never stack duplicate Search/Sort icons onto the shared toolbar.
     */
    public static void attachFragmentSearchMenu(@NonNull Fragment fragment,
                                                @MenuRes int menuResId,
                                                @NonNull String hint,
                                                @NonNull QueryListener listener,
                                                @Nullable Runnable onSortSelected) {
        fragment.requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menu.clear();
                listener.onQueryChanged("");
                setupSearchMenu(fragment.requireContext(), menu, menuInflater, menuResId, hint, listener);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.item_sort && onSortSelected != null) {
                    onSortSelected.run();
                    return true;
                }
                return false;
            }
        }, fragment.getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    public static void setupSearchMenu(@NonNull Context context,
                                       @NonNull Menu menu,
                                       @NonNull MenuInflater inflater,
                                       @MenuRes int menuResId,
                                       @NonNull String hint,
                                       @NonNull QueryListener listener) {
        inflater.inflate(menuResId, menu);
        MenuItem searchItem = menu.findItem(R.id.item_search);
        if (searchItem == null) return;

        View actionView = searchItem.getActionView();
        if (!(actionView instanceof SearchView)) return;
        SearchView searchView = (SearchView) actionView;

        searchView.setIconifiedByDefault(true);
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchView.setQueryHint(hint);

        EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchEditText != null) {
            searchEditText.setTextColor(Color.WHITE);
            searchEditText.setHintTextColor(Color.parseColor("#8A93A2"));
        }
        View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
        if (searchPlate != null) {
            searchPlate.setBackgroundColor(Color.TRANSPARENT);
        }

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                searchView.postDelayed(() -> {
                    searchView.requestFocus();
                    View target = searchEditText != null ? searchEditText : searchView;
                    target.requestFocus();
                    InputMethodManager imm = (InputMethodManager) context
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 120);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                searchView.setQuery("", false);
                listener.onQueryChanged("");
                searchView.clearFocus();
                hideKeyboard(context, searchView);
                return true;
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                hideKeyboard(context, searchView);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                listener.onQueryChanged(newText != null ? newText : "");
                return true;
            }
        });
    }

    public static void hideKeyboard(@NonNull Context context, @Nullable View anchor) {
        InputMethodManager imm = (InputMethodManager) context
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        View focused = (context instanceof Activity) ? ((Activity) context).getCurrentFocus() : null;
        View target = focused != null ? focused : anchor;
        if (target != null) {
            imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
            target.clearFocus();
        }
    }
}
