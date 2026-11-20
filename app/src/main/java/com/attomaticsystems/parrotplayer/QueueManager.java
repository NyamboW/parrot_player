package com.attomaticsystems.parrotplayer;

import java.util.ArrayList;
import java.util.Collections;

public class QueueManager {

    private final ArrayList<String> queue = new ArrayList<>();

    /**
     * Add a song path to the end of the queue.
     */
    public void addToQueue(String songPath) {
        queue.add(songPath);
    }

    /**
     * Add multiple songs to the end of the queue.
     */
    public void addAllToQueue(ArrayList<String> songPaths) {
        queue.addAll(songPaths);
    }

    /**
     * Insert a song at the front of the queue ("Play Next").
     */
    public void playNext(String songPath) {
        queue.add(0, songPath);
    }

    /**
     * Insert multiple songs at the front of the queue.
     */
    public void playNextMultiple(ArrayList<String> songPaths) {
        queue.addAll(0, songPaths);
    }

    /**
     * Remove a song from the queue by index.
     */
    public void removeFromQueue(int index) {
        if (index >= 0 && index < queue.size()) {
            queue.remove(index);
        }
    }

    /**
     * Returns and removes the first item in the queue, or null if empty.
     */
    public String pollNext() {
        if (!queue.isEmpty()) {
            return queue.remove(0);
        }
        return null;
    }

    /**
     * Returns the current queue (read-only copy).
     */
    public ArrayList<String> getQueue() {
        return new ArrayList<>(queue);
    }

    /**
     * Move an item within the queue (for drag reorder).
     */
    public void moveItem(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < queue.size() &&
            toIndex >= 0 && toIndex < queue.size()) {
            String item = queue.remove(fromIndex);
            queue.add(toIndex, item);
        }
    }

    /**
     * Clear the entire queue.
     */
    public void clearQueue() {
        queue.clear();
    }

    /**
     * Check if the queue is empty.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Get the queue size.
     */
    public int size() {
        return queue.size();
    }
}
