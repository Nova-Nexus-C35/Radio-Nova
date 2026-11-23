package com.nova.radio;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Copia portable de favoritos y emisoras recientes; nunca incluye tokens ni datos de cuenta. */
public final class StationBackup {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_STATIONS = 500;
    private StationBackup() {}

    public static void write(Context context, OutputStream output) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "radio-nova-backup");
        root.put("version", 2);
        root.put("createdAt", System.currentTimeMillis());
        root.put("favorites", encode(context, StationStore.getFavorites(context)));
        root.put("recents", encode(context, StationStore.getRecent(context)));
        byte[] data = root.toString(2).getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) throw new IllegalArgumentException("Backup too large");
        output.write(data);
        output.flush();
    }

    public static int restore(Context context, InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0, count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_BYTES) throw new IllegalArgumentException("Backup too large");
            bytes.write(buffer, 0, count);
        }
        JSONObject root = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        if (!"radio-nova-backup".equals(root.optString("format"))
                || (root.optInt("version", -1) != 1 && root.optInt("version", -1) != 2)) {
            throw new IllegalArgumentException("Unsupported backup format");
        }
        JSONArray favorites = root.optJSONArray("favorites");
        JSONArray recents = root.optJSONArray("recents");
        int restored = restoreArray(context, favorites, true);
        restored += restoreArray(context, recents, false);
        return restored;
    }

    private static JSONArray encode(Context context, List<OnlineStation> stations) throws Exception {
        JSONArray array = new JSONArray();
        for (OnlineStation station : stations) {
            JSONObject item = new JSONObject();
            item.put("name", station.name);
            item.put("url", station.streamUrl);
            item.put("category", StationStore.getCategory(context, station.streamUrl));
            array.put(item);
        }
        return array;
    }

    private static int restoreArray(Context context, JSONArray array, boolean favorite) {
        if (array == null) return 0;
        int restored = 0;
        for (int i = 0; i < Math.min(array.length(), MAX_STATIONS); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name", "Emisora online").trim();
            String url = item.optString("url", "").trim();
            if (!StreamUrlValidator.isAllowedHttps(url)) continue;
            if (favorite) StationStore.addFavorite(context, name, url);
            else StationStore.addRecent(context, name, url);
            StationStore.setCategory(context, url, item.optString("category", ""));
            restored++;
        }
        return restored;
    }
}
