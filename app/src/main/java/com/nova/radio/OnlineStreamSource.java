package com.nova.radio;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

/** Adaptador de RadioSource para el servicio Media3 de streaming online. */
public final class OnlineStreamSource implements RadioSource {
    private final Context context;

    public OnlineStreamSource(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public Kind getKind() { return Kind.ONLINE_STREAM; }
    @Override public boolean isAvailable() { return true; }
    @Override public String getStatus() { return "Requiere conexión a Internet y una URL HTTPS"; }

    public void play(String streamUrl, String stationName, Callback callback) {
        Uri uri = streamUrl == null ? Uri.EMPTY : Uri.parse(streamUrl.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            if (callback != null) callback.onError(
                    "Usa una dirección HTTPS válida. Los enlaces HTTP sin cifrar se bloquean.");
            return;
        }

        Intent intent = new Intent(context, InternetRadioService.class);
        intent.setAction(InternetRadioService.ACTION_PLAY);
        intent.putExtra(InternetRadioService.EXTRA_URL, uri.toString());
        intent.putExtra(InternetRadioService.EXTRA_NAME, stationName);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            if (callback != null) callback.onSuccess("Solicitud de reproducción enviada al servicio.");
        } catch (RuntimeException e) {
            if (callback != null) callback.onError(
                    "Android no permitió iniciar la radio. Abre Radio Nova e inténtalo de nuevo.");
        }
    }

    @Override public void tune(double frequencyKhz, Callback callback) {
        if (callback != null) callback.onError("El streaming no permite sintonizar frecuencias RF.");
    }

    @Override public void stop() {
        Intent intent = new Intent(context, InternetRadioService.class);
        intent.setAction(InternetRadioService.ACTION_STOP);
        try { context.startService(intent); } catch (RuntimeException ignored) {}
    }
}
