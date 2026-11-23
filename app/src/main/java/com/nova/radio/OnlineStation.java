package com.nova.radio;

/** Emisora del directorio online; no representa una frecuencia RF. */
public final class OnlineStation {
    public final String id;
    public final String name;
    public final String country;
    public final String streamUrl;
    public final String codec;
    public final int bitrate;
    public final String tags;
    public final String language;
    public final String favicon;
    public final String homepage;
    public final String category;

    public OnlineStation(String id, String name, String country, String streamUrl,
                         String codec, int bitrate) {
        this(id, name, country, streamUrl, codec, bitrate, "", "", "", "", "");
    }

    public OnlineStation(String id, String name, String country, String streamUrl,
                         String codec, int bitrate, String tags, String language,
                         String favicon, String homepage) {
        this(id, name, country, streamUrl, codec, bitrate, tags, language, favicon, homepage, "");
    }

    public OnlineStation(String id, String name, String country, String streamUrl,
                         String codec, int bitrate, String tags, String language,
                         String favicon, String homepage, String category) {
        this.id = id == null ? "" : id;
        this.name = name == null || name.trim().isEmpty() ? "Emisora online" : name.trim();
        this.country = country == null ? "" : country;
        this.streamUrl = streamUrl == null ? "" : streamUrl;
        this.codec = codec == null ? "" : codec;
        this.bitrate = Math.max(0, bitrate);
        this.tags = tags == null ? "" : tags;
        this.language = language == null ? "" : language;
        this.favicon = favicon == null ? "" : favicon;
        this.homepage = homepage == null ? "" : homepage;
        this.category = category == null ? "" : category;
    }

    public String codecLabel() {
        String c = codec.isEmpty() ? "Audio" : codec;
        return bitrate > 0 ? c + " · " + bitrate + " kbps" : c;
    }

    public String detailLabel() {
        String detail = tags.trim();
        if (detail.length() > 90) detail = detail.substring(0, 87) + "…";
        return detail;
    }
}
