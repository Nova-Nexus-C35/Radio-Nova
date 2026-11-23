package com.nova.radio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parser M3U/M3U8 sencillo y limitado; solo acepta destinos HTTPS. */
public final class M3uPlaylistParser {
    private static final int MAX_LINES = 5000;
    private static final int MAX_STATIONS = 500;
    private static final int MAX_LINE_LENGTH = 4096;
    private static final long MAX_FILE_BYTES = 1_048_576L;

    private M3uPlaylistParser() {}

    public static List<OnlineStation> parse(InputStream input) throws IOException {
        Map<String, OnlineStation> unique = new LinkedHashMap<>();
        String pendingName = null;
        String pendingCategory = null;
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new LimitedInputStream(input, MAX_FILE_BYTES), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (++lines > MAX_LINES) throw new IOException("La lista M3U supera el límite de líneas.");
                if (line.length() > MAX_LINE_LENGTH) continue;
                line = line.trim();
                if (lines == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
                if (line.isEmpty()) continue;

                if (line.regionMatches(true, 0, "#EXTGRP:", 0, 8)) {
                    pendingCategory = line.substring(8).trim();
                    if (pendingCategory.length() > 40) pendingCategory = pendingCategory.substring(0, 40);
                    continue;
                }
                if (line.regionMatches(true, 0, "#EXTINF:", 0, 8)) {
                    int comma = line.indexOf(',');
                    if (comma >= 0 && comma + 1 < line.length()) {
                        pendingName = line.substring(comma + 1).trim();
                    }
                    continue;
                }
                if (line.startsWith("#")) continue;
                if (!StreamUrlValidator.isAllowedHttps(line)) {
                    pendingName = null;
                    pendingCategory = null;
                    continue;
                }
                String name = pendingName;
                if (name == null || name.isEmpty()) name = "Emisora importada " + (unique.size() + 1);
                unique.putIfAbsent(line, new OnlineStation("", name, "", line, "", 0, "", "", "", "", pendingCategory));
                pendingName = null;
                pendingCategory = null;
                if (unique.size() >= MAX_STATIONS) break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** Impide que un archivo M3U enorme agote memoria al leerse por líneas. */
    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream input, long maxBytes) {
            super(input);
            remaining = maxBytes;
        }

        @Override public int read() throws IOException {
            if (remaining == 0) {
                if (super.read() == -1) return -1;
                throw new IOException("La lista M3U supera 1 MiB.");
            }
            int value = super.read();
            if (value != -1) remaining--;
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining == 0) return read() == -1 ? -1 : 0;
            int count = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (count > 0) remaining -= count;
            return count;
        }
    }
}
