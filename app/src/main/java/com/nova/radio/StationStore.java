package com.nova.radio;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Favoritos e últimas emisoras guardados localmente, sin cuenta ni conexión. */
public final class StationStore {
    private static final String PREFS = "radio_nova_stations";
    private static final String FAVORITES = "favorites";
    private static final String RECENTS = "recents";
    private static final String CACHE_PREFIX = "search_cache:";
    private static final String CATEGORY_PREFIX = "station_category:";
    private static final String METADATA_PREFIX = "station_metadata:";
    private static final String CACHE_INDEX = "search_cache_index";
    private static final int MAX_SEARCH_CACHE = 12;
    private static final int MAX_RECENTS = 20;
    private static final String SEP = String.valueOf((char) 31);

    private StationStore() {}

    public static boolean isFavorite(Context context, String url) {
        return findByUrl(prefs(context).getStringSet(FAVORITES, Collections.emptySet()), url) != null;
    }

    public static void toggleFavorite(Context context, String name, String url) {
        SharedPreferences prefs = prefs(context);
        Set<String> entries = new HashSet<>(prefs.getStringSet(FAVORITES, Collections.emptySet()));
        String existing = findByUrl(entries, url);
        if (existing != null) entries.remove(existing);
        else entries.add(encode(System.currentTimeMillis(), name, url));
        prefs.edit().putStringSet(FAVORITES, entries).apply();
    }

    /** Añade un favorito sin alternar ni eliminar uno que ya exista. */
    public static void addFavorite(Context context, String name, String url) {
        if (!StreamUrlValidator.isAllowedHttps(url)) return;
        SharedPreferences prefs = prefs(context);
        Set<String> entries = new HashSet<>(prefs.getStringSet(FAVORITES, Collections.emptySet()));
        if (findByUrl(entries, url) == null) {
            entries.add(encode(System.currentTimeMillis(), name, url));
            prefs.edit().putStringSet(FAVORITES, entries).apply();
        }
    }

    /** Exporta los favoritos en formato M3U estándar. */
    public static String exportFavoritesM3u(Context context) {
        StringBuilder output = new StringBuilder("#EXTM3U\n");
        for (OnlineStation station : getFavorites(context)) {
            String name = station.name.replace("\r", " ").replace("\n", " ").replace(",", " - ");
            String category = getCategory(context, station.streamUrl);
            if (!category.isEmpty()) output.append("#EXTGRP:").append(category.replace("\r", " ").replace("\n", " ")).append('\n');
            output.append("#EXTINF:-1,").append(name).append('\n')
                    .append(station.streamUrl).append('\n');
        }
        return output.toString();
    }

    public static void addRecent(Context context, String name, String url) {
        SharedPreferences prefs = prefs(context);
        Set<String> entries = new HashSet<>(prefs.getStringSet(RECENTS, Collections.emptySet()));
        String existing = findByUrl(entries, url);
        if (existing != null) entries.remove(existing);
        entries.add(encode(System.currentTimeMillis(), name, url));

        List<String> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator.comparingLong(StationStore::timestamp).reversed());
        Set<String> trimmed = new HashSet<>();
        for (int i = 0; i < Math.min(MAX_RECENTS, ordered.size()); i++) trimmed.add(ordered.get(i));
        prefs.edit().putStringSet(RECENTS, trimmed).apply();
    }

    public static List<OnlineStation> getFavorites(Context context) {
        List<OnlineStation> stations = read(context, FAVORITES);
        stations.sort(Comparator.comparing(s -> s.name.toLowerCase()));
        return stations;
    }

    public static List<OnlineStation> getRecent(Context context) {
        List<OnlineStation> stations = read(context, RECENTS);
        stations.sort((a, b) -> Long.compare(
                timestampForUrl(context, b.streamUrl), timestampForUrl(context, a.streamUrl)));
        return stations;
    }

    private static long timestampForUrl(Context context, String url) {
        String entry = findByUrl(prefs(context).getStringSet(RECENTS, Collections.emptySet()), url);
        return entry == null ? 0L : timestamp(entry);
    }

    private static List<OnlineStation> read(Context context, String key) {
        List<OnlineStation> result = new ArrayList<>();
        for (String entry : prefs(context).getStringSet(key, Collections.emptySet())) {
            String[] parts = entry.split(java.util.regex.Pattern.quote(SEP), 3);
            if (parts.length != 3 || parts[2].trim().isEmpty()) continue;
            result.add(new OnlineStation("", parts[1], "", parts[2], "", 0));
        }
        return result;
    }

    private static String encode(long time, String name, String url) {
        String safeName = name == null ? "Emisora online" : name.replace(SEP, " ").trim();
        return time + SEP + safeName + SEP + url;
    }

    private static String findByUrl(Set<String> entries, String url) {
        for (String entry : entries) {
            String[] parts = entry.split(java.util.regex.Pattern.quote(SEP), 3);
            if (parts.length == 3 && parts[2].equals(url)) return entry;
        }
        return null;
    }

    private static long timestamp(String entry) {
        try { return Long.parseLong(entry.substring(0, entry.indexOf(SEP))); }
        catch (Exception ignored) { return 0L; }
    }


    /** Guarda cada consulta por separado para que las búsquedas recientes sigan disponibles offline. */
    public static void saveSearchCache(Context context, String query, List<OnlineStation> stations) {
        try {
            JSONArray array = new JSONArray();
            for (OnlineStation station : stations) {
                JSONObject item = new JSONObject();
                item.put("id", station.id);
                item.put("name", station.name);
                item.put("country", station.country);
                item.put("url", station.streamUrl);
                item.put("codec", station.codec);
                item.put("bitrate", station.bitrate);
                item.put("tags", station.tags);
                item.put("language", station.language);
                item.put("favicon", station.favicon);
                item.put("homepage", station.homepage);
                array.put(item);
            }
            JSONObject cached = new JSONObject();
            cached.put("savedAt", System.currentTimeMillis());
            cached.put("stations", array);
            String key = CACHE_PREFIX + cacheId(query);
            SharedPreferences store = prefs(context);
            Set<String> keys = new HashSet<>(store.getStringSet(CACHE_INDEX, Collections.emptySet()));
            keys.add(key);
            store.edit().putString(key, cached.toString()).putStringSet(CACHE_INDEX, new HashSet<>(keys)).apply();
            trimSearchCache(store, keys);
        } catch (Exception ignored) {
            // Un error de caché no debe interrumpir la búsqueda online.
        }
    }

    /** Devuelve la consulta exacta guardada localmente, o una lista vacía si no existe. */
    public static List<OnlineStation> getCachedSearch(Context context, String query) {
        String key = CACHE_PREFIX + cacheId(query);
        String raw = prefs(context).getString(key, null);
        if (raw == null) return new ArrayList<>();
        List<OnlineStation> result = new ArrayList<>();
        try {
            JSONArray array = new JSONObject(raw).optJSONArray("stations");
            if (array == null) return result;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "");
                if (!StreamUrlValidator.isAllowedHttps(url)) continue;
                result.add(new OnlineStation(item.optString("id", ""),
                        item.optString("name", "Emisora online"), item.optString("country", ""), url,
                        item.optString("codec", ""), item.optInt("bitrate", 0),
                        item.optString("tags", ""), item.optString("language", ""),
                        item.optString("favicon", ""), item.optString("homepage", "")));
            }
        } catch (Exception ignored) { return new ArrayList<>(); }
        return result;
    }

    private static String cacheId(String query) {
        String value = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static void trimSearchCache(SharedPreferences store, Set<String> keys) {
        if (keys.size() <= MAX_SEARCH_CACHE) return;
        List<String> ordered = new ArrayList<>(keys);
        ordered.sort((a, b) -> Long.compare(cacheTimestamp(store.getString(b, "")),
                cacheTimestamp(store.getString(a, ""))));
        SharedPreferences.Editor editor = store.edit();
        while (ordered.size() > MAX_SEARCH_CACHE) {
            String oldest = ordered.remove(0);
            keys.remove(oldest);
            editor.remove(oldest);
        }
        editor.putStringSet(CACHE_INDEX, new HashSet<>(keys)).apply();
    }

    private static long cacheTimestamp(String raw) {
        try { return new JSONObject(raw).optLong("savedAt", 0L); }
        catch (Exception ignored) { return 0L; }
    }

    /** Guarda una colección/etiqueta local para una emisora, sin sincronizarla con terceros. */
    public static void setCategory(Context context, String url, String category) {
        if (url == null || url.isEmpty()) return;
        String key = CATEGORY_PREFIX + cacheId(url);
        String value = category == null ? "" : category.trim();
        if (value.length() > 40) value = value.substring(0, 40);
        SharedPreferences.Editor editor = prefs(context).edit();
        if (value.isEmpty()) editor.remove(key);
        else editor.putString(key, value);
        editor.apply();
    }

    public static String getCategory(Context context, String url) {
        if (url == null || url.isEmpty()) return "";
        return prefs(context).getString(CATEGORY_PREFIX + cacheId(url), "");
    }

    /** Conserva códec, bitrate y país del directorio para mostrarlos en los controles del coche. */
    public static void saveStationMetadata(Context context, OnlineStation station) {
        if (station == null || station.streamUrl.isEmpty()) return;
        try {
            JSONObject data = new JSONObject();
            data.put("codec", station.codec);
            data.put("bitrate", station.bitrate);
            data.put("country", station.country);
            prefs(context).edit().putString(METADATA_PREFIX + cacheId(station.streamUrl), data.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Enriquece favoritos/recientes locales sin exigir una consulta de red. */
    public static OnlineStation enrichStation(Context context, OnlineStation station) {
        if (station == null) return null;
        String raw = prefs(context).getString(METADATA_PREFIX + cacheId(station.streamUrl), null);
        if (raw == null) return station;
        try {
            JSONObject data = new JSONObject(raw);
            return new OnlineStation(station.id, station.name, data.optString("country", station.country),
                    station.streamUrl, data.optString("codec", station.codec),
                    data.optInt("bitrate", station.bitrate), station.tags, station.language,
                    station.favicon, station.homepage, station.category);
        } catch (Exception ignored) { return station; }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
