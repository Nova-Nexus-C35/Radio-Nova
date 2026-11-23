package com.nova.radio;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente de Radio Browser: descubre espejos, conserva nodos de respaldo,
 * filtra emisoras caídas y usa la última búsqueda guardada si no hay red.
 */
public final class RadioBrowserClient {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final String[] FALLBACK_SERVERS = {
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info"
    };
    private static final long SERVER_CACHE_MS = 6L * 60L * 60L * 1000L;
    private static volatile List<String> cachedServers = new ArrayList<>();
    private static volatile long serversLoadedAt;

    public interface Callback {
        void onResult(List<OnlineStation> stations);
        void onError(String message);
        default void onCachedResult(List<OnlineStation> stations) {
            onResult(stations);
        }
    }

    private RadioBrowserClient() {}

    /** Compatibilidad con versiones anteriores: búsqueda limitada a Cuba. */
    public static void searchCuba(Context context, String query, Callback callback) {
        searchStations(context, query, "Cuba", "name", callback);
    }

    public static void searchCuba(Context context, String query, String filter, Callback callback) {
        searchStations(context, query, "Cuba", filter, callback);
    }

    /**
     * Busca en el directorio mundial. Un país vacío significa todos los países.
     * Radio Browser admite búsqueda por país, idioma, etiqueta, códec y nombre.
     */
    public static void searchStations(Context context, String query, String country,
            String filter, Callback callback) {
        final Context appContext = context.getApplicationContext();
        final String rawQuery = query == null ? "" : query.trim();
        final String safeCountry = normalizeCountryName(country);
        final String safeQuery = "country".equals(filter)
                ? normalizeCountryName(rawQuery) : rawQuery;
        final String safeFilter = ("tag".equals(filter) || "language".equals(filter)
                || "codec".equals(filter) || "country".equals(filter)) ? filter : "name";
        final String cacheKey = safeCountry.toLowerCase(java.util.Locale.ROOT) + ":"
                + safeFilter + ":" + safeQuery;
        EXECUTOR.execute(() -> {
            Exception last = null;
            for (String server : getServers()) {
                HttpURLConnection connection = null;
                try {
                    Uri.Builder uri = Uri.parse(server + "/json/stations/search").buildUpon()
                            .appendQueryParameter("hidebroken", "true")
                            .appendQueryParameter("order", "clickcount")
                            .appendQueryParameter("reverse", "true")
                            .appendQueryParameter("limit", "50");
                    if (!safeCountry.isEmpty() && !"country".equals(safeFilter)) {
                        uri.appendQueryParameter("country", safeCountry);
                    }
                    if (!safeQuery.isEmpty()) {
                        uri.appendQueryParameter(safeFilter, safeQuery);
                    }

                    connection = open(uri.build().toString());
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                    if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                        throw new IllegalStateException("El directorio redirigió a una conexión no segura.");
                    }

                    JSONArray array = new JSONArray(readAll(connection.getInputStream()));
                    List<OnlineStation> stations = parseStations(array);
                    StationStore.saveSearchCache(appContext, cacheKey, stations);
                    callback.onResult(stations);
                    return;
                } catch (Exception e) {
                    last = e;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }

            List<OnlineStation> cached = StationStore.getCachedSearch(appContext, cacheKey);
            if (!cached.isEmpty()) {
                callback.onCachedResult(cached);
            } else {
                callback.onError(last == null
                        ? "No fue posible consultar el directorio online."
                        : "No se pudo consultar Radio Browser. Comprueba Internet e inténtalo de nuevo.");
            }
        });
    }

    /** Accept common Spanish country names while Radio Browser stores country names in English. */
    static String normalizeCountryName(String value) {
        if (value == null) return "";
        String raw = value.trim();
        if (raw.isEmpty()) return "";
        String key = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT)
                .replace(".", "").replace(" ", "");
        switch (key) {
            case "eeuu":
            case "estadosunidos":
            case "usa":
            case "us": return "United States";
            case "espana": return "Spain";
            case "mexico": return "Mexico";
            case "republicadominicana": return "Dominican Republic";
            case "reinounido":
            case "granbretana": return "United Kingdom";
            case "alemania": return "Germany";
            case "francia": return "France";
            case "italia": return "Italy";
            case "brasil": return "Brazil";
            case "canada": return "Canada";
            case "japon": return "Japan";
            case "paisesbajos":
            case "holanda": return "Netherlands";
            case "suiza": return "Switzerland";
            case "suecia": return "Sweden";
            case "noruega": return "Norway";
            case "rusia": return "Russia";
            case "peru": return "Peru";
            case "coreadelsur": return "South Korea";
            case "nuevazelanda": return "New Zealand";
            default: return raw;
        }
    }

    private static List<OnlineStation> parseStations(JSONArray array) {
        List<OnlineStation> stations = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;

            String stream = item.optString("url_resolved", "");
            if (stream.isEmpty()) stream = item.optString("url", "");
            Uri streamUri = Uri.parse(stream);
            if (!"https".equalsIgnoreCase(streamUri.getScheme()) || streamUri.getHost() == null) continue;
            if (item.has("lastcheckok") && item.optInt("lastcheckok", 1) == 0) continue;

            String id = item.optString("stationuuid", "");
            if (!seen.add(id.isEmpty() ? stream : id)) continue;
            String name = item.optString("name", "").trim();
            if (name.isEmpty()) name = "Emisora sin nombre";

            stations.add(new OnlineStation(id, name, item.optString("country", ""),
                    stream, item.optString("codec", ""), item.optInt("bitrate", 0),
                    item.optString("tags", ""), item.optString("language", ""),
                    safeHttps(item.optString("favicon", "")),
                    safeHttps(item.optString("homepage", ""))));
        }
        return stations;
    }

    private static String safeHttps(String value) {
        Uri uri = Uri.parse(value == null ? "" : value);
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? value : "";
    }

    private static synchronized List<String> getServers() {
        long now = System.currentTimeMillis();
        if (!cachedServers.isEmpty() && now - serversLoadedAt < SERVER_CACHE_MS) {
            return new ArrayList<>(cachedServers);
        }

        LinkedHashSet<String> servers = new LinkedHashSet<>();
        HttpURLConnection connection = null;
        try {
            connection = open("https://all.api.radio-browser.info/json/servers");
            if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300
                    && "https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                JSONArray array = new JSONArray(readAll(connection.getInputStream()));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    String host = item.optString("name", "").trim().toLowerCase();
                    if (host.matches("[a-z0-9.-]+\\.api\\.radio-browser\\.info")) {
                        servers.add("https://" + host);
                    }
                }
            }
        } catch (Exception ignored) {
            // La lista fija mantiene el servicio disponible si falla el descubrimiento.
        } finally {
            if (connection != null) connection.disconnect();
        }
        for (String server : FALLBACK_SERVERS) servers.add(server);
        cachedServers = new ArrayList<>(servers);
        serversLoadedAt = now;
        return new ArrayList<>(cachedServers);
    }

    private static HttpURLConnection open(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(7000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "RadioNova/1.1.0 (Android; https://github.com/Cumba35/Nova-Workspace)");
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static String readAll(InputStream input) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
                if (out.length() > 2_000_000) throw new IllegalStateException("Respuesta demasiado grande");
            }
        }
        return out.toString();
    }
}
