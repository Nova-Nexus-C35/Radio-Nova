package com.nova.radio;

import org.junit.Test;
import static org.junit.Assert.*;

public class StreamUrlValidatorTest {
    @Test public void acceptsValidHttpsStream() {
        assertTrue(StreamUrlValidator.isAllowedHttps("https://radio.example.org/live.mp3"));
        assertTrue(StreamUrlValidator.isAllowedHttps("HTTPS://radio.example.org:8443/live"));
    }

    @Test public void rejectsUnencryptedHttp() {
        assertFalse(StreamUrlValidator.isAllowedHttps("http://radio.example.org/live.mp3"));
    }

    @Test public void rejectsMissingHostAndMalformedUrls() {
        assertFalse(StreamUrlValidator.isAllowedHttps("https:///live.mp3"));
        assertFalse(StreamUrlValidator.isAllowedHttps("not a url"));
        assertFalse(StreamUrlValidator.isAllowedHttps("https://"));
    }

    @Test public void rejectsCredentialsWhitespaceAndControlCharacters() {
        assertFalse(StreamUrlValidator.isAllowedHttps("https://user:pass@radio.example.org/live"));
        assertFalse(StreamUrlValidator.isAllowedHttps(" https://radio.example.org/live"));
        assertFalse(StreamUrlValidator.isAllowedHttps("https://radio.example.org/live\n"));
    }

    @Test public void rejectsNullEmptyAndOversizedValues() {
        assertFalse(StreamUrlValidator.isAllowedHttps(null));
        assertFalse(StreamUrlValidator.isAllowedHttps(""));
        assertFalse(StreamUrlValidator.isAllowedHttps("https://a.example/" + repeat('x', 2050)));
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
