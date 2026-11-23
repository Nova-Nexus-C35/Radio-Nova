package com.nova.radio;

/**
 * Contrato común para receptores terrestres y streaming.
 * Una fuente solo debe declararse disponible tras comprobar el hardware/API real.
 */
public interface RadioSource {
    enum Kind { INTERNAL_FM_AM, USB_TUNER, ONLINE_STREAM }

    Kind getKind();
    boolean isAvailable();
    String getStatus();
    void tune(double frequencyKhz, Callback callback);
    void stop();

    interface Callback {
        void onSuccess(String message);
        void onError(String message);
    }
}
