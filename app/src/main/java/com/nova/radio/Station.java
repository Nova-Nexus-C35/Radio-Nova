package com.nova.radio;

public final class Station {
    public enum Band { FM, AM, SHORTWAVE, UNKNOWN }

    public final String id;
    public final String name;
    public final String province;
    public final String municipality;
    public final double frequencyKhz;
    public final Band band;
    public final String source;
    public final String verifiedOn;

    public Station(String id, String name, String province, String municipality,
                   double frequencyKhz, Band band, String source, String verifiedOn) {
        this.id = id;
        this.name = name;
        this.province = province;
        this.municipality = municipality;
        this.frequencyKhz = frequencyKhz;
        this.band = band;
        this.source = source;
        this.verifiedOn = verifiedOn;
    }
}
