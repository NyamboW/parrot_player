package com.example.parrot;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FavouritesManager {

    private static final String PREFS_NAME = "parrot_favourites";
    private static final String KEY_FAVOURITES = "fav_songs";

    private final SharedPreferences prefs;

    public FavouritesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addFavourite(String songPath) {
        Set<String> favs = new HashSet<>(getFavouriteSet());
        favs.add(songPath);
        prefs.edit().putStringSet(KEY_FAVOURITES, favs).apply();
    }

    public void removeFavourite(String songPath) {
        Set<String> favs = new HashSet<>(getFavouriteSet());
        favs.remove(songPath);
        prefs.edit().putStringSet(KEY_FAVOURITES, favs).apply();
    }

    public boolean isFavourite(String songPath) {
        return getFavouriteSet().contains(songPath);
    }

    public void toggleFavourite(String songPath) {
        if (isFavourite(songPath)) {
            removeFavourite(songPath);
        } else {
            addFavourite(songPath);
        }
    }

    public ArrayList<String> getFavourites() {
        return new ArrayList<>(getFavouriteSet());
    }

    private Set<String> getFavouriteSet() {
        return prefs.getStringSet(KEY_FAVOURITES, new HashSet<>());
    }
}
