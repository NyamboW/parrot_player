package com.example.parrot;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared album art loader with in-memory LRU caching, negative caching,
 * in-flight request deduplication, and downsampled bitmap decoding.
 */
public final class AlbumArtLoader {

    public static final int SIZE_THUMB = 128;
    public static final int SIZE_FULL = 512;

    public interface ArtCallback {
        void onArtLoaded(Bitmap bitmap);
    }

    private static volatile AlbumArtLoader instance;

    private final LruCache<String, Bitmap> memoryCache;
    private final LruCache<String, Boolean> noArtCache;
    private final Map<String, List<ArtCallback>> inFlight = new HashMap<>();
    private final ExecutorService priorityExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AlbumArtLoader() {
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = Math.max(4 * 1024, maxMemory / 6);

        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        noArtCache = new LruCache<>(1000);
    }

    public static AlbumArtLoader getInstance() {
        if (instance == null) {
            synchronized (AlbumArtLoader.class) {
                if (instance == null) {
                    instance = new AlbumArtLoader();
                }
            }
        }
        return instance;
    }

    private static String cacheKey(String path, int targetSize) {
        return targetSize + ":" + path;
    }

    public Bitmap getCachedFull(String path) {
        if (path == null)
            return null;
        return memoryCache.get(cacheKey(path, SIZE_FULL));
    }

    public Bitmap getCachedThumb(String path) {
        if (path == null)
            return null;
        return memoryCache.get(cacheKey(path, SIZE_THUMB));
    }

    public Bitmap getBestCachedArt(String path) {
        Bitmap full = getCachedFull(path);
        if (full != null)
            return full;
        return getCachedThumb(path);
    }

    public boolean hasNoArt(String path) {
        return path != null && Boolean.TRUE.equals(noArtCache.get(path));
    }

    public void loadFullArt(String path, ArtCallback callback) {
        loadArt(path, SIZE_FULL, priorityExecutor, callback);
    }

    public void loadThumbArt(String path, ArtCallback callback) {
        loadArt(path, SIZE_THUMB, thumbnailExecutor, callback);
    }

    public void preloadFullArt(String path) {
        if (path == null || path.isEmpty())
            return;
        if (getCachedFull(path) != null || hasNoArt(path))
            return;
        loadArt(path, SIZE_FULL, priorityExecutor, null);
    }

    private void loadArt(String path, int targetSize, ExecutorService executor, ArtCallback callback) {
        if (path == null || path.isEmpty()) {
            deliverCallback(callback, null);
            return;
        }

        String key = cacheKey(path, targetSize);
        Bitmap cached = memoryCache.get(key);
        if (cached != null) {
            deliverCallback(callback, cached);
            return;
        }

        if (Boolean.TRUE.equals(noArtCache.get(path))) {
            deliverCallback(callback, null);
            return;
        }

        synchronized (inFlight) {
            List<ArtCallback> existing = inFlight.get(key);
            if (existing != null) {
                if (callback != null) {
                    existing.add(callback);
                }
                return;
            }
            List<ArtCallback> list = new ArrayList<>();
            if (callback != null) {
                list.add(callback);
            }
            inFlight.put(key, list);
        }

        executor.execute(() -> {
            Bitmap result = decodeEmbeddedArt(path, targetSize);

            List<ArtCallback> callbacks;
            synchronized (inFlight) {
                callbacks = inFlight.remove(key);
            }

            if (callbacks != null && !callbacks.isEmpty()) {
                mainHandler.post(() -> {
                    for (ArtCallback cb : callbacks) {
                        cb.onArtLoaded(result);
                    }
                });
            }
        });
    }

    private Bitmap decodeEmbeddedArt(String path, int targetSize) {
        String key = cacheKey(path, targetSize);
        Bitmap cached = memoryCache.get(key);
        if (cached != null) {
            return cached;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            byte[] art = retriever.getEmbeddedPicture();
            if (art == null || art.length == 0) {
                noArtCache.put(path, Boolean.TRUE);
                return null;
            }

            Bitmap bitmap = decodeSampledBitmap(art, targetSize);
            if (bitmap != null) {
                memoryCache.put(key, bitmap);
            }
            return bitmap;
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static Bitmap decodeSampledBitmap(byte[] data, int targetSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, targetSize);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static int calculateInSampleSize(int width, int height, int reqSize) {
        int inSampleSize = 1;
        if (height > reqSize || width > reqSize) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqSize && (halfWidth / inSampleSize) >= reqSize) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void deliverCallback(ArtCallback callback, Bitmap bitmap) {
        if (callback == null)
            return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onArtLoaded(bitmap);
        } else {
            mainHandler.post(() -> callback.onArtLoaded(bitmap));
        }
    }
}
