package com.nova.radio;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Radio Nova account gate. Uses the same Supabase Auth project as Nova Apps.
 * Access and refresh tokens are encrypted with Android Keystore AES-GCM.
 */
final class NovaAccountSession {
    private static final String BASE_URL = "https://suirbghmnuxixktzkrze.supabase.co";
    private static final String PUBLISHABLE_KEY = "sb_publishable_1ZmYJ5J-A8jGDIcUIOiY3g_dYOKdnIp";
    private static final String PREFS = "nova_radio_secure_session";
    private static final String KEY_ALIAS = "nova.radio.supabase.session.aes.v1";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final long EXPIRY_SKEW_SECONDS = 90L;

    static final class Session {
        final String accessToken;
        final String refreshToken;
        final String userId;
        final String email;
        final long expiresAt;

        Session(String accessToken, String refreshToken, String userId, String email, long expiresAt) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
            this.email = email;
            this.expiresAt = expiresAt;
        }
    }

    private static final class HttpFailure extends Exception {
        final int status;
        HttpFailure(int status, String message) { super(message); this.status = status; }
    }

    private NovaAccountSession() {}

    static boolean hasUsableSession(Context context) {
        Session session = readSession(context);
        return session != null && !session.accessToken.isEmpty()
                && !session.refreshToken.isEmpty()
                && session.expiresAt > System.currentTimeMillis() / 1000L + EXPIRY_SKEW_SECONDS;
    }

    static Session signIn(Context context, String email, String password) throws Exception {
        JSONObject payload = new JSONObject().put("email", email.trim()).put("password", password);
        JSONObject response = new JSONObject(http("POST",
                "auth/v1/token?grant_type=password", null, payload.toString()));
        Session session = sessionFrom(response);
        saveSession(context, session);
        try {
            verifyRemoteUser(session);
            return session;
        } catch (Exception error) {
            clear(context);
            throw error;
        }
    }

    static Session ensureSession(Context context) throws Exception {
        Session session = readSession(context);
        if (session == null) throw new IllegalStateException("Inicia sesión con tu cuenta de Nova Apps.");
        if (session.expiresAt <= System.currentTimeMillis() / 1000L + EXPIRY_SKEW_SECONDS) {
            session = refresh(context, session);
        }
        verifyRemoteUser(session);
        return session;
    }

    static void signOut(Context context) {
        Session session = readSession(context);
        try {
            if (session != null && !session.accessToken.isEmpty()) {
                http("POST", "auth/v1/logout", session.accessToken, "{}");
            }
        } catch (Exception ignored) {
            // Always erase local credentials, even without a network connection.
        } finally {
            clear(context);
        }
    }

    private static Session refresh(Context context, Session old) throws Exception {
        JSONObject payload = new JSONObject().put("refresh_token", old.refreshToken);
        try {
            JSONObject response = new JSONObject(http("POST",
                    "auth/v1/token?grant_type=refresh_token", null, payload.toString()));
            Session refreshed = sessionFrom(response);
            saveSession(context, refreshed);
            return refreshed;
        } catch (HttpFailure failure) {
            if (failure.status == 400 || failure.status == 401 || failure.status == 403) clear(context);
            throw failure;
        }
    }

    private static void verifyRemoteUser(Session session) throws Exception {
        JSONObject user = new JSONObject(http("GET", "auth/v1/user", session.accessToken, null));
        if (session.userId.isEmpty() || !session.userId.equals(user.optString("id"))) {
            throw new IllegalStateException("La cuenta NOVA no coincide con la sesión almacenada.");
        }
    }

    private static Session sessionFrom(JSONObject json) throws Exception {
        String access = json.optString("access_token");
        String refresh = json.optString("refresh_token");
        JSONObject user = json.optJSONObject("user");
        String userId = user == null ? "" : user.optString("id");
        if (access.isEmpty() || refresh.isEmpty() || userId.isEmpty()) {
            throw new IllegalStateException(json.optString("msg", "Nova Apps no devolvió una sesión válida."));
        }
        long expiry = json.optLong("expires_at");
        if (expiry <= 0) expiry = System.currentTimeMillis() / 1000L + json.optLong("expires_in", 3600L);
        return new Session(access, refresh, userId, user.optString("email"), expiry);
    }

    private static String http(String method, String path, String token, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + "/" + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("apikey", PUBLISHABLE_KEY);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) connection.setDoOutput(true);
        try {
            if (body != null) {
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            java.io.InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = "";
            if (stream != null) {
                try (java.io.InputStream input = stream;
                     java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    response = bytes.toString("UTF-8");
                }
            }
            if (code < 200 || code >= 300) {
                JSONObject error = null;
                try { error = new JSONObject(response); } catch (Exception ignored) {}
                String message = error == null ? "" : error.optString("msg",
                        error.optString("message", error.optString("error_description", error.optString("error", ""))));
                if (message.isEmpty()) message = "Error de autenticación HTTP " + code;
                throw new HttpFailure(code, message);
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static void saveSession(Context context, Session session) throws Exception {
        JSONObject json = new JSONObject()
                .put("access_token", session.accessToken)
                .put("refresh_token", session.refreshToken)
                .put("user_id", session.userId)
                .put("email", session.email)
                .put("expires_at", session.expiresAt);
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    private static Session readSession(Context context) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            byte[] iv = Base64.decode(prefs.getString("iv", null), Base64.NO_WRAP);
            byte[] payload = Base64.decode(prefs.getString("payload", null), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new javax.crypto.spec.GCMParameterSpec(128, iv));
            JSONObject json = new JSONObject(new String(cipher.doFinal(payload), StandardCharsets.UTF_8));
            return new Session(json.getString("access_token"), json.getString("refresh_token"),
                    json.getString("user_id"), json.optString("email"), json.optLong("expires_at"));
        } catch (Exception ignored) {
            clear(context);
            return null;
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            store.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {}
    }
}
