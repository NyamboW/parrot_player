package com.attomaticsystems.parrotplayer;

import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts and parses synchronized (.lrc / SYLT / timestamped USLT) and unsynchronized
 * lyrics from companion .lrc/.txt files or embedded audio tags (ID3v2 MP3, FLAC/OGG Vorbis, M4A ©lyr).
 */
public final class LyricsExtractor {

    public static final class LyricLine implements Comparable<LyricLine> {
        public final long timeMs; // -1 for unsynced plain lines
        public final String text;

        public LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }

        @Override
        public int compareTo(LyricLine other) {
            return Long.compare(this.timeMs, other.timeMs);
        }
    }

    public static final class LyricsData {
        public static final LyricsData EMPTY = new LyricsData(Collections.emptyList(), false);

        public final List<LyricLine> lines;
        public final boolean isSynced;

        public LyricsData(List<LyricLine> lines, boolean isSynced) {
            this.lines = lines;
            this.isSynced = isSynced;
        }

        public boolean hasLyrics() {
            return lines != null && !lines.isEmpty();
        }

        /**
         * Returns the index of the currently active lyric line for the given playback position in ms.
         * For unsynced lyrics, estimates line index proportionally across the track duration.
         */
        public int findActiveLineIndex(long positionMs, long durationMs) {
            if (!hasLyrics()) return -1;
            if (!isSynced) {
                if (durationMs <= 0) return 0;
                float fraction = Math.max(0f, Math.min(0.999f, positionMs / (float) durationMs));
                return Math.min(lines.size() - 1, (int) (fraction * lines.size()));
            }

            int low = 0;
            int high = lines.size() - 1;
            int result = -1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                long midTime = lines.get(mid).timeMs;
                if (midTime <= positionMs + 120) {
                    result = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return result >= 0 ? result : 0;
        }
    }

    public interface Callback {
        void onLyricsLoaded(LyricsData data);
    }

    private static final Pattern LRC_TIME_PATTERN =
            Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern LRC_OFFSET_PATTERN =
            Pattern.compile("(?i)\\[offset:\\s*([+-]?\\d+)\\s*\\]");
    private static final Pattern LRC_META_PATTERN =
            Pattern.compile("(?i)^\\[(ti|ar|al|au|by|length|re|ve|tool):.*\\]$");

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final LruCache<String, LyricsData> cache = new LruCache<>(120);

    private LyricsExtractor() {}

    public static void loadLyricsAsync(final String songPath, final Callback callback) {
        if (callback == null) return;
        if (songPath == null || songPath.isEmpty()) {
            callback.onLyricsLoaded(LyricsData.EMPTY);
            return;
        }

        LyricsData cached = cache.get(songPath);
        if (cached != null) {
            callback.onLyricsLoaded(cached);
            return;
        }

        executor.execute(() -> {
            LyricsData extracted = extractLyrics(songPath);
            if (extracted == null) {
                extracted = LyricsData.EMPTY;
            }
            cache.put(songPath, extracted);
            final LyricsData finalResult = extracted;
            mainHandler.post(() -> callback.onLyricsLoaded(finalResult));
        });
    }

    public static void invalidateCache(String songPath) {
        if (songPath != null && !songPath.isEmpty()) {
            cache.remove(songPath);
        }
    }

    public static LyricsData extractLyrics(String songPath) {
        try {
            TrackTagEditorHelper.TagOverride override = TrackTagEditorHelper.getOverride(null, songPath);
            if (override != null && override.lyrics != null && !override.lyrics.trim().isEmpty()) {
                LyricsData customParsed = parseRawLyricsText(override.lyrics);
                if (customParsed.hasLyrics()) {
                    return customParsed;
                }
            }

            File audioFile = new File(songPath);
            if (!audioFile.exists()) return LyricsData.EMPTY;

            // 1. Check for external .lrc or .txt file next to the song or in Lyrics subfolder
            File externalLrc = findCompanionLyricsFile(audioFile);
            if (externalLrc != null) {
                String rawLrc = readFileText(externalLrc);
                LyricsData parsed = parseRawLyricsText(rawLrc);
                if (parsed.hasLyrics()) {
                    return parsed;
                }
            }

            // 2. Check embedded tags inside the audio file
            String lowerName = audioFile.getName().toLowerCase(Locale.ROOT);
            String embeddedText = null;
            if (lowerName.endsWith(".mp3")) {
                LyricsData sylt = extractMp3Sylt(audioFile);
                if (sylt != null && sylt.hasLyrics()) {
                    return sylt;
                }
                embeddedText = extractMp3Id3Lyrics(audioFile);
            } else if (lowerName.endsWith(".flac") || lowerName.endsWith(".ogg")) {
                embeddedText = extractVorbisCommentLyrics(audioFile);
            } else if (lowerName.endsWith(".m4a") || lowerName.endsWith(".mp4") || lowerName.endsWith(".aac")) {
                embeddedText = extractM4aLyrics(audioFile);
            }

            if (embeddedText == null) {
                // Fallback: try ID3v2 on any extension
                embeddedText = extractMp3Id3Lyrics(audioFile);
            }

            if (embeddedText != null && !embeddedText.trim().isEmpty()) {
                return parseRawLyricsText(embeddedText);
            }
        } catch (Exception ignored) {
        }
        return LyricsData.EMPTY;
    }

    private static File findCompanionLyricsFile(File audioFile) {
        File parent = audioFile.getParentFile();
        if (parent == null || !parent.exists()) return null;

        String name = audioFile.getName();
        int dot = name.lastIndexOf('.');
        String baseName = (dot > 0) ? name.substring(0, dot) : name;

        String[] candidates = new String[]{
                baseName + ".lrc",
                baseName + ".LRC",
                baseName + ".txt",
                "Lyrics/" + baseName + ".lrc",
                "lyrics/" + baseName + ".lrc"
        };
        for (String rel : candidates) {
            File f = new File(parent, rel);
            if (f.exists() && f.isFile() && f.length() > 10 && f.length() < 512 * 1024) {
                return f;
            }
        }

        // Case-insensitive directory scan for .lrc file matching baseName
        File[] siblings = parent.listFiles();
        if (siblings != null) {
            String targetLrc = (baseName + ".lrc").toLowerCase(Locale.ROOT);
            for (File f : siblings) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).equals(targetLrc)) {
                    return f;
                }
            }
        }
        return null;
    }

    private static String readFileText(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = fis.read(buf)) != -1) {
                bos.write(buf, 0, r);
            }
            byte[] data = bos.toByteArray();
            return decodeBytesAutoCharset(data, 0, data.length);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses raw text into either timestamped LRC LyricLines or plain unsynced LyricLines.
     */
    public static LyricsData parseRawLyricsText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return LyricsData.EMPTY;
        }

        String normalized = rawText.replace("\r\n", "\n").replace('\r', '\n');
        String[] rawLines = normalized.split("\n");

        long offsetMs = 0;
        Matcher offsetMatcher = LRC_OFFSET_PATTERN.matcher(normalized);
        if (offsetMatcher.find()) {
            try {
                offsetMs = Long.parseLong(offsetMatcher.group(1));
            } catch (Exception ignored) {}
        }

        List<LyricLine> syncedLines = new ArrayList<>();
        List<LyricLine> plainLines = new ArrayList<>();

        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (LRC_META_PATTERN.matcher(trimmed).matches() || LRC_OFFSET_PATTERN.matcher(trimmed).matches()) {
                continue;
            }

            Matcher matcher = LRC_TIME_PATTERN.matcher(trimmed);
            List<Long> timestamps = new ArrayList<>();
            int lastMatchEnd = 0;

            while (matcher.find()) {
                try {
                    long minutes = Long.parseLong(matcher.group(1));
                    long seconds = Long.parseLong(matcher.group(2));
                    String fracStr = matcher.group(3);
                    long millis = 0;
                    if (fracStr != null && !fracStr.isEmpty()) {
                        if (fracStr.length() == 1) {
                            millis = Long.parseLong(fracStr) * 100;
                        } else if (fracStr.length() == 2) {
                            millis = Long.parseLong(fracStr) * 10;
                        } else {
                            millis = Long.parseLong(fracStr.substring(0, 3));
                        }
                    }
                    long totalMs = Math.max(0, (minutes * 60_000L) + (seconds * 1000L) + millis - offsetMs);
                    timestamps.add(totalMs);
                    lastMatchEnd = matcher.end();
                } catch (Exception ignored) {}
            }

            if (!timestamps.isEmpty()) {
                String lyricContent = trimmed.substring(lastMatchEnd).trim();
                // Clean any inline word-level LRC tags like <00:12.34>
                lyricContent = lyricContent.replaceAll("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>", "").trim();
                if (!lyricContent.isEmpty()) {
                    for (Long ts : timestamps) {
                        syncedLines.add(new LyricLine(ts, lyricContent));
                    }
                }
            } else {
                // Skip pure bracket metadata lines
                if (!(trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() < 32)) {
                    plainLines.add(new LyricLine(-1, trimmed));
                }
            }
        }

        if (!syncedLines.isEmpty()) {
            Collections.sort(syncedLines);
            return new LyricsData(syncedLines, true);
        }
        if (!plainLines.isEmpty()) {
            return new LyricsData(plainLines, false);
        }
        return LyricsData.EMPTY;
    }

    /**
     * Reads ID3v2.2 (ULT), ID3v2.3 / ID3v2.4 (USLT, TXXX:LYRICS) text lyrics from an MP3 file.
     */
    private static String extractMp3Id3Lyrics(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 16) return null;
            byte[] header = new byte[10];
            raf.readFully(header);
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
                return null;
            }

            int version = header[3] & 0xFF;
            int flags = header[5] & 0xFF;
            int tagSize = readSynchsafeInt(header, 6);
            long tagEnd = Math.min(raf.length(), 10L + tagSize);

            // Skip extended header if present
            if ((flags & 0x40) != 0 && raf.getFilePointer() + 4 < tagEnd) {
                byte[] extSizeBytes = new byte[4];
                raf.readFully(extSizeBytes);
                int extSize = (version == 4) ? readSynchsafeInt(extSizeBytes, 0) : readBigEndianInt(extSizeBytes, 0);
                if (extSize > 0 && extSize < tagSize) {
                    raf.skipBytes(extSize - (version == 3 ? 0 : 4));
                }
            }

            while (raf.getFilePointer() + (version == 2 ? 6 : 10) <= tagEnd) {
                String frameId;
                int frameSize;
                int frameFlags = 0;

                if (version == 2) {
                    byte[] fh = new byte[6];
                    raf.readFully(fh);
                    if (fh[0] == 0) break;
                    frameId = new String(fh, 0, 3, StandardCharsets.ISO_8859_1);
                    frameSize = ((fh[3] & 0xFF) << 16) | ((fh[4] & 0xFF) << 8) | (fh[5] & 0xFF);
                } else {
                    byte[] fh = new byte[10];
                    raf.readFully(fh);
                    if (fh[0] == 0) break;
                    frameId = new String(fh, 0, 4, StandardCharsets.ISO_8859_1);
                    frameSize = (version == 4) ? readSynchsafeInt(fh, 4) : readBigEndianInt(fh, 4);
                    frameFlags = ((fh[8] & 0xFF) << 8) | (fh[9] & 0xFF);
                }

                if (frameSize <= 0 || raf.getFilePointer() + frameSize > tagEnd) {
                    break;
                }

                if ("USLT".equals(frameId) || "ULT".equals(frameId)) {
                    if (frameSize > 512 * 1024) {
                        raf.skipBytes(frameSize);
                        continue;
                    }
                    byte[] payload = new byte[frameSize];
                    raf.readFully(payload);
                    String uslt = decodeUsltPayload(payload, frameFlags);
                    if (uslt != null && !uslt.trim().isEmpty()) {
                        return uslt;
                    }
                } else if ("TXXX".equals(frameId) || "TXX".equals(frameId)) {
                    if (frameSize > 512 * 1024) {
                        raf.skipBytes(frameSize);
                        continue;
                    }
                    byte[] payload = new byte[frameSize];
                    raf.readFully(payload);
                    String txxx = decodeTxxxLyricsPayload(payload);
                    if (txxx != null && !txxx.trim().isEmpty()) {
                        return txxx;
                    }
                } else {
                    raf.skipBytes(frameSize);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Reads ID3v2 SYLT (Synchronised Lyrics/Text) binary frames if present.
     */
    private static LyricsData extractMp3Sylt(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 16) return null;
            byte[] header = new byte[10];
            raf.readFully(header);
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return null;

            int version = header[3] & 0xFF;
            if (version < 3) return null;
            int tagSize = readSynchsafeInt(header, 6);
            long tagEnd = Math.min(raf.length(), 10L + tagSize);

            while (raf.getFilePointer() + 10 <= tagEnd) {
                byte[] fh = new byte[10];
                raf.readFully(fh);
                if (fh[0] == 0) break;
                String frameId = new String(fh, 0, 4, StandardCharsets.ISO_8859_1);
                int frameSize = (version == 4) ? readSynchsafeInt(fh, 4) : readBigEndianInt(fh, 4);
                if (frameSize <= 0 || raf.getFilePointer() + frameSize > tagEnd) break;

                if ("SYLT".equals(frameId) && frameSize > 10 && frameSize < 512 * 1024) {
                    byte[] payload = new byte[frameSize];
                    raf.readFully(payload);
                    LyricsData sylt = decodeSyltPayload(payload);
                    if (sylt != null && sylt.hasLyrics()) {
                        return sylt;
                    }
                } else {
                    raf.skipBytes(frameSize);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static LyricsData decodeSyltPayload(byte[] payload) {
        try {
            int encoding = payload[0] & 0xFF;
            // payload[1..3] = language, payload[4] = time stamp format (2 = ms), payload[5] = content type
            int timeFormat = payload[4] & 0xFF;
            if (timeFormat != 2) return null; // Only millisecond format

            int pos = skipNullTerminatedString(payload, 6, encoding);
            List<LyricLine> lines = new ArrayList<>();
            while (pos < payload.length) {
                int textStart = pos;
                int textEnd = findNullTerminator(payload, textStart, encoding);
                if (textEnd < 0) break;
                String text = decodeId3String(payload, textStart, textEnd - textStart, encoding).trim();
                pos = textEnd + ((encoding == 1 || encoding == 2) ? 2 : 1);
                if (pos + 4 > payload.length) break;
                long timeMs = readBigEndianInt(payload, pos) & 0xFFFFFFFFL;
                pos += 4;
                if (!text.isEmpty()) {
                    lines.add(new LyricLine(timeMs, text));
                }
            }
            if (!lines.isEmpty()) {
                Collections.sort(lines);
                return new LyricsData(lines, true);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String decodeUsltPayload(byte[] payload, int frameFlags) {
        if (payload == null || payload.length <= 4) return null;
        int offset = 0;
        // If ID3v2.4 data length indicator flag is set, skip 4 bytes
        if ((frameFlags & 0x01) != 0 && payload.length > 8) {
            offset = 4;
        }
        int encoding = payload[offset] & 0xFF;
        // Skip encoding (1 byte) + language (3 bytes) = 4 bytes
        int pos = offset + 4;
        if (pos >= payload.length) return null;

        // Skip content descriptor (null-terminated according to encoding)
        int lyricsStart = skipNullTerminatedString(payload, pos, encoding);
        if (lyricsStart >= payload.length) return null;

        return decodeId3String(payload, lyricsStart, payload.length - lyricsStart, encoding);
    }

    private static String decodeTxxxLyricsPayload(byte[] payload) {
        if (payload == null || payload.length <= 2) return null;
        int encoding = payload[0] & 0xFF;
        int descEnd = findNullTerminator(payload, 1, encoding);
        if (descEnd < 0) return null;
        String desc = decodeId3String(payload, 1, descEnd - 1, encoding).trim().toUpperCase(Locale.ROOT);
        int valStart = descEnd + ((encoding == 1 || encoding == 2) ? 2 : 1);
        if (valStart >= payload.length) return null;

        if (desc.contains("LYRIC")) {
            return decodeId3String(payload, valStart, payload.length - valStart, encoding);
        }
        return null;
    }

    private static int skipNullTerminatedString(byte[] data, int start, int encoding) {
        int term = findNullTerminator(data, start, encoding);
        if (term < 0) return data.length;
        return term + ((encoding == 1 || encoding == 2) ? 2 : 1);
    }

    private static int findNullTerminator(byte[] data, int start, int encoding) {
        if (encoding == 1 || encoding == 2) {
            // UTF-16: 2-byte aligned 0x00 0x00
            for (int i = start; i + 1 < data.length; i += 2) {
                if (data[i] == 0 && data[i + 1] == 0) {
                    return i;
                }
            }
        } else {
            for (int i = start; i < data.length; i++) {
                if (data[i] == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String decodeId3String(byte[] data, int offset, int length, int encoding) {
        if (length <= 0 || offset < 0 || offset + length > data.length) return "";
        Charset charset;
        switch (encoding) {
            case 1:
                charset = StandardCharsets.UTF_16;
                break;
            case 2:
                charset = StandardCharsets.UTF_16BE;
                break;
            case 3:
                charset = StandardCharsets.UTF_8;
                break;
            default:
                charset = StandardCharsets.ISO_8859_1;
                break;
        }
        String str = new String(data, offset, length, charset);
        return str.replace("\u0000", "").trim();
    }

    private static String decodeBytesAutoCharset(byte[] data, int offset, int length) {
        if (length >= 2) {
            int b0 = data[offset] & 0xFF;
            int b1 = data[offset + 1] & 0xFF;
            if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) {
                return new String(data, offset, length, StandardCharsets.UTF_16);
            }
        }
        if (length >= 3 && (data[offset] & 0xFF) == 0xEF
                && (data[offset + 1] & 0xFF) == 0xBB
                && (data[offset + 2] & 0xFF) == 0xBF) {
            return new String(data, offset + 3, length - 3, StandardCharsets.UTF_8);
        }
        return new String(data, offset, length, StandardCharsets.UTF_8);
    }

    /**
     * Scans the first 256 KB of a FLAC or OGG file for Vorbis comment "LYRICS=" or "UNSYNCEDLYRICS=".
     */
    private static String extractVorbisCommentLyrics(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            int maxScan = (int) Math.min(file.length(), 256 * 1024);
            byte[] buf = new byte[maxScan];
            int total = 0;
            while (total < maxScan) {
                int r = fis.read(buf, total, maxScan - total);
                if (r <= 0) break;
                total += r;
            }
            String[] keys = new String[]{"LYRICS=", "lyrics=", "UNSYNCEDLYRICS=", "unsyncedlyrics="};
            for (String key : keys) {
                byte[] keyBytes = key.getBytes(StandardCharsets.US_ASCII);
                int idx = indexOfBytes(buf, total, keyBytes);
                if (idx >= 4) {
                    int len = (buf[idx - 4] & 0xFF)
                            | ((buf[idx - 3] & 0xFF) << 8)
                            | ((buf[idx - 2] & 0xFF) << 16)
                            | ((buf[idx - 1] & 0xFF) << 24);
                    int valStart = idx + keyBytes.length;
                    int valLen = len - keyBytes.length;
                    if (valLen > 4 && valLen < 128 * 1024 && valStart + valLen <= total) {
                        return new String(buf, valStart, valLen, StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Scans the first 512 KB of an M4A/MP4 file for the ©lyr metadata atom.
     */
    private static String extractM4aLyrics(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            int maxScan = (int) Math.min(file.length(), 512 * 1024);
            byte[] buf = new byte[maxScan];
            int total = 0;
            while (total < maxScan) {
                int r = fis.read(buf, total, maxScan - total);
                if (r <= 0) break;
                total += r;
            }
            byte[] lyrAtom = new byte[]{(byte) 0xA9, 'l', 'y', 'r'};
            int idx = indexOfBytes(buf, total, lyrAtom);
            if (idx >= 4 && idx + 20 < total) {
                // Inside ©lyr atom lies a 'data' sub-atom: [4-byte size]['d','a','t','a'][4-byte type][4-byte locale][UTF-8 text]
                byte[] dataTag = new byte[]{'d', 'a', 't', 'a'};
                int dataIdx = indexOfBytesFrom(buf, total, dataTag, idx, Math.min(total, idx + 32));
                if (dataIdx >= 4) {
                    int atomSize = readBigEndianInt(buf, dataIdx - 4);
                    int textStart = dataIdx + 12;
                    int textLen = atomSize - 16;
                    if (textLen > 4 && textLen < 128 * 1024 && textStart + textLen <= total) {
                        return new String(buf, textStart, textLen, StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static int indexOfBytes(byte[] haystack, int haystackLen, byte[] needle) {
        return indexOfBytesFrom(haystack, haystackLen, needle, 0, haystackLen);
    }

    private static int indexOfBytesFrom(byte[] haystack, int haystackLen, byte[] needle, int from, int to) {
        int limit = Math.min(haystackLen, to) - needle.length;
        for (int i = Math.max(0, from); i <= limit; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private static int readSynchsafeInt(byte[] b, int offset) {
        return ((b[offset] & 0x7F) << 21)
                | ((b[offset + 1] & 0x7F) << 14)
                | ((b[offset + 2] & 0x7F) << 7)
                | (b[offset + 3] & 0x7F);
    }

    private static int readBigEndianInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24)
                | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8)
                | (b[offset + 3] & 0xFF);
    }
}
