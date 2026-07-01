package com.example.parrot;

import android.media.MediaPlayer;

public class MusicController {

    private static MediaPlayer player;
    private static String currentSongName;
    private static boolean isPlaying = false;

    public static void setPlayer(MediaPlayer mediaPlayer) {
        player = mediaPlayer;
    }

    public static MediaPlayer getPlayer() {
        return player;
    }

    public static void setSongName(String name) {
        currentSongName = name;
    }

    public static String getSongName() {
        return currentSongName;
    }

    public static boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public static void play() {
        try {
            if (player != null && !player.isPlaying()) {
                player.start();
            }
        } catch (Exception ignored) {}
    }

    public static void pause() {
        try {
            if (player != null && player.isPlaying()) {
                player.pause();
            }
        } catch (Exception ignored) {}
    }
}