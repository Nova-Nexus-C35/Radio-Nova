package com.nova.radio;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class M3uPlaylistParserTest {
    @Test public void parsesHttpsStationsAndNames() throws Exception {
        String playlist = "#EXTM3U\n"
                + "#EXTINF:-1,Radio Rebelde\n"
                + "https://radio.example.org/rebelde.mp3\n"
                + "#EXTINF:-1,Radio Progreso\n"
                + "https://radio.example.org/progreso.m3u8\n";
        List<OnlineStation> stations = M3uPlaylistParser.parse(
                new ByteArrayInputStream(playlist.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, stations.size());
        assertEquals("Radio Rebelde", stations.get(0).name);
        assertEquals("https://radio.example.org/rebelde.mp3", stations.get(0).streamUrl);
    }

    @Test public void ignoresInsecureAndDuplicateUrls() throws Exception {
        String playlist = "#EXTM3U\n"
                + "#EXTINF:-1,Insegura\nhttp://radio.example.org/live\n"
                + "#EXTINF:-1,Segura\nhttps://radio.example.org/live\n"
                + "#EXTINF:-1,Duplicada\nhttps://radio.example.org/live\n";
        List<OnlineStation> stations = M3uPlaylistParser.parse(
                new ByteArrayInputStream(playlist.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, stations.size());
        assertEquals("Segura", stations.get(0).name);
    }

    @Test public void preservesM3uCollectionGroups() throws Exception {
        String playlist = "#EXTM3U\n"
                + "#EXTGRP:Noticias\n"
                + "#EXTINF:-1,Radio Noticias\n"
                + "https://radio.example.org/news\n";
        List<OnlineStation> stations = M3uPlaylistParser.parse(
                new ByteArrayInputStream(playlist.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, stations.size());
        assertEquals("Noticias", stations.get(0).category);
    }

    @Test(expected = IOException.class)
    public void rejectsFilesLargerThanOneMiB() throws Exception {
        M3uPlaylistParser.parse(new ByteArrayInputStream(new byte[1_048_577]));
    }

    @Test public void parsesUtf8BomAndSkipsComments() throws Exception {
        String playlist = "\uFEFF#EXTM3U\n# comment\nhttps://radio.example.org/live\n";
        List<OnlineStation> stations = M3uPlaylistParser.parse(
                new ByteArrayInputStream(playlist.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, stations.size());
        assertTrue(stations.get(0).name.startsWith("Emisora importada"));
    }
}
