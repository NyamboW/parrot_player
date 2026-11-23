package com.attomaticsystems.parrotplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronously extracts and caches rich track metadata (Artist, Album, Format, Bitrate,
 * Sample Rate, Channels, File Size) for the Player screen and Song Info inspector.
 */
public final class TrackMetadataHelper {

    public static final class TrackInfo {
        public final String filePath;
        public final String title;
        public final String artist;
        public final String album;
        public final String format;
        public final int bitrateKbps;
        public final int sampleRateHz;
        public final int channels;
        public final long durationMs;
        public final long fileSizeBytes;

        public TrackInfo(String filePath,
                         String title,
                         String artist,
                         String album,
                         String format,
                         int bitrateKbps,
                         int sampleRateHz,
                         int channels,
                         long durationMs,
                         long fileSizeBytes) {
            this.filePath = filePath;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.format = format;
            this.bitrateKbps = bitrateKbps;
            this.sampleRateHz = sampleRateHz;
            this.channels = channels;
            this.durationMs = durationMs;
            this.fileSizeBytes = fileSizeBytes;
        }

        public boolean hasKnownArtist() {
            return artist != null && !artist.isEmpty()
                    && !artist.equalsIgnoreCase("Unknown Artist")
                    && !artist.equalsIgnoreCase("<unknown>");
        }

        public boolean hasKnownAlbum() {
            return album != null && !album.isEmpty()
                    && !album.equalsIgnoreCase("Unknown Album")
                    && !album.equalsIgnoreCase("<unknown>");
        }

        public String getArtistAlbumSubtitle() {
            String cleanArtist = hasKnownArtist() ? artist : "Unknown Artist";
            if (hasKnownAlbum() && !album.equalsIgnoreCase(cleanArtist)) {
                return cleanArtist + "  •  " + album;
            }
            return cleanArtist;
        }

        public String getQualityBadgeText() {
            StringBuilder sb = new StringBuilder();
            sb.append(format != null && !format.isEmpty() ? format : "AUDIO");
            if (bitrateKbps > 0) {
                sb.append(" • ").append(bitrateKbps).append(" kbps");
            }
            if (sampleRateHz > 0) {
                float khz = sampleRateHz / 1000f;
                String khzStr = (sampleRateHz % 1000 == 0)
                        ? String.format(Locale.US, "%d kHz", sampleRateHz / 1000)
                        : String.format(Locale.US, "%.1f kHz", khz);
                sb.append(" • ").append(khzStr);
            }
            return sb.toString();
        }

        public String getFormattedFileSize() {
            if (fileSizeBytes <= 0) return "Unknown";
            double mb = fileSizeBytes / (1024.0 * 1024.0);
            if (mb >= 1.0) {
                return String.format(Locale.US, "%.2f MB", mb);
            }
            double kb = fileSizeBytes / 1024.0;
            return String.format(Locale.US, "%.0f KB", kb);
        }
    }

    public interface Callback {
        void onTrackInfoLoaded(TrackInfo info);
    }

    private static final LruCache<String, TrackInfo> cache = new LruCache<>(250);
    private static final ConcurrentHashMap<String, Long> durationCache = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final ExecutorService durationExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TrackMetadataHelper() {}

    public static void cacheDuration(String path, long durationMs) {
        if (path != null && !path.isEmpty() && durationMs > 0) {
            durationCache.put(path, durationMs);
        }
    }

    public static String formatDuration(long durationMs) {
        long totalSec = Math.max(0L, durationMs) / 1000L;
        long hours = totalSec / 3600L;
        long minutes = (totalSec % 3600L) / 60L;
        long seconds = totalSec % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    public static void bindDuration(final TextView txtDuration, final String path) {
        if (txtDuration == null) return;
        if (path == null || path.isEmpty()) {
            txtDuration.setTag(null);
            txtDuration.setVisibility(View.GONE);
            return;
        }
        txtDuration.setTag(path);

        Long cachedDur = durationCache.get(path);
        if (cachedDur == null) {
            TrackInfo info = cache.get(path);
            if (info != null && info.durationMs > 0) {
                cachedDur = info.durationMs;
                durationCache.put(path, cachedDur);
            }
        }
        if (cachedDur != null && cachedDur > 0) {
            txtDuration.setText(formatDuration(cachedDur));
            txtDuration.setVisibility(View.VISIBLE);
            return;
        }

        txtDuration.setVisibility(View.GONE);
        durationExecutor.execute(() -> {
            long durMs = 0L;
            Long existing = durationCache.get(path);
            if (existing != null && existing > 0) {
                durMs = existing;
            } else {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                try {
                    mmr.setDataSource(path);
                    String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (dur != null) {
                        durMs = Long.parseLong(dur.trim());
                    }
                } catch (Exception ignored) {
                } finally {
                    try {
                        mmr.release();
                    } catch (Exception ignored) {}
                }
                if (durMs > 0) {
                    durationCache.put(path, durMs);
                }
            }
            final long finalDurMs = durMs;
            if (finalDurMs > 0) {
                mainHandler.post(() -> {
                    if (path.equals(txtDuration.getTag())) {
                        txtDuration.setText(formatDuration(finalDurMs));
                        txtDuration.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    public static TrackInfo getCached(String path) {
        return path != null ? cache.get(path) : null;
    }

    public static void loadTrackInfoAsync(final String path, final Callback callback) {
        if (callback == null || path == null || path.isEmpty()) return;

        TrackInfo cached = cache.get(path);
        if (cached != null) {
            callback.onTrackInfoLoaded(cached);
            return;
        }

        executor.execute(() -> {
            final TrackInfo info = extractSync(path);
            cache.put(path, info);
            mainHandler.post(() -> callback.onTrackInfoLoaded(info));
        });
    }

    public static TrackInfo extractSync(String path) {
        File file = new File(path);
        long fileSize = file.exists() ? file.length() : 0L;
        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        String ext = (dot >= 0 && dot + 1 < fileName.length())
                ? fileName.substring(dot + 1).toUpperCase(Locale.ROOT)
                : "AUDIO";
        String fallbackTitle = (dot > 0) ? fileName.substring(0, dot) : fileName;

        String title = fallbackTitle;
        String artist = "Unknown Artist";
        String album = "Unknown Album";
        int bitrateKbps = 0;
        int sampleRateHz = 0;
        int channels = 2;
        long durationMs = 0L;

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(path);
            String t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (t != null && !t.trim().isEmpty()) {
                title = t.trim();
            }
            String ar = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (ar != null && !ar.trim().isEmpty() && !ar.equalsIgnoreCase("<unknown>")) {
                artist = ar.trim();
            }
            String al = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (al != null && !al.trim().isEmpty() && !al.equalsIgnoreCase("<unknown>")) {
                album = al.trim();
            }
            String br = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (br != null) {
                int bps = Integer.parseInt(br.trim());
                if (bps > 1000) {
                    bitrateKbps = Math.round(bps / 1000f);
                }
            }
            String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (dur != null) {
                durationMs = Long.parseLong(dur.trim());
            }
        } catch (Exception ignored) {
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {}
        }

        // Extract sample rate & channel count via MediaExtractor (works on API 21+)
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(path);
            if (mediaExtractor.getTrackCount() > 0) {
                MediaFormat format = mediaExtractor.getTrackFormat(0);
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                }
                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                }
                if (bitrateKbps <= 0 && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    bitrateKbps = Math.round(format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000f);
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                mediaExtractor.release();
            } catch (Exception ignored) {}
        }

        if (bitrateKbps <= 0 && durationMs > 0 && fileSize > 0) {
            bitrateKbps = (int) ((fileSize * 8L) / durationMs);
        }

        return new TrackInfo(
                path, title, artist, album, ext,
                bitrateKbps, sampleRateHz, channels, durationMs, fileSize
        );
    }

    public static void showTrackDetailsDialog(Context context, TrackInfo info) {
        if (context == null || info == null) return;
        String channelLabel = (info.channels == 1) ? "Mono (1 ch)" : "Stereo (" + info.channels + " ch)";
        String durationStr = formatDuration(info.durationMs);

        String details = "Title: " + info.title
                + "\nArtist: " + info.artist
                + "\nAlbum: " + info.album
                + "\n\nFormat: " + info.format
                + (info.bitrateKbps > 0 ? "\nBitrate: " + info.bitrateKbps + " kbps" : "")
                + (info.sampleRateHz > 0 ? "\nSample Rate: " + info.sampleRateHz + " Hz" : "")
                + "\nChannels: " + channelLabel
                + "\nDuration: " + durationStr
                + "\nFile Size: " + info.getFormattedFileSize()
                + "\n\nPath:\n" + info.filePath;

        ParrotModalHelper.showConfirmDialog(
                context,
                "Track Details",
                details,
                "Close",
                null,
                false,
                null,
                null,
                null
        );
    }
}
