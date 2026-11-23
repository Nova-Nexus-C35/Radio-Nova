package com.nova.radio;

import java.net.URI;
import java.net.URISyntaxException;

/** Validación común y testeable de direcciones de streaming HTTPS. */
public final class StreamUrlValidator {
    private StreamUrlValidator() {}

    public static boolean isAllowedHttps(String value) {
        if (value == null || value.isEmpty() || value.length() > 2048
                || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return false;
        }
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isEmpty()
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException ignored) {
            return false;
        }
    }
}
