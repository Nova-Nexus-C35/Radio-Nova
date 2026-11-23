package com.nova.radio;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class RadioBrowserCountryNormalizationTest {
    @Test public void mapsSpanishCountryNamesToDirectoryNames() {
        assertEquals("United States", RadioBrowserClient.normalizeCountryName("Estados Unidos"));
        assertEquals("Spain", RadioBrowserClient.normalizeCountryName("España"));
        assertEquals("United Kingdom", RadioBrowserClient.normalizeCountryName("Reino Unido"));
        assertEquals("Brazil", RadioBrowserClient.normalizeCountryName("Brasil"));
    }

    @Test public void preservesEnglishNamesAndEmptyValues() {
        assertEquals("Canada", RadioBrowserClient.normalizeCountryName("Canada"));
        assertEquals("", RadioBrowserClient.normalizeCountryName(" "));
        assertEquals("", RadioBrowserClient.normalizeCountryName(null));
    }
}
