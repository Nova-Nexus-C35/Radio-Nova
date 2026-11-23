package com.nova.radio;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

/** Directorio local: disponible sin conexión y sin recopilar ubicación. */
public final class StationDirectory extends SQLiteOpenHelper {
    private static final String DB_NAME = "radio_nova.db";
    private static final int DB_VERSION = 1;

    public StationDirectory(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE stations (" +
                "id TEXT PRIMARY KEY, name TEXT NOT NULL, province TEXT, municipality TEXT, " +
                "frequency_khz REAL NOT NULL, band TEXT NOT NULL, source TEXT, verified_on TEXT, " +
                "favorite INTEGER NOT NULL DEFAULT 0)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Las migraciones futuras deben preservar los favoritos del usuario.
    }

    public void save(Station station) {
        ContentValues values = new ContentValues();
        values.put("id", station.id);
        values.put("name", station.name);
        values.put("province", station.province);
        values.put("municipality", station.municipality);
        values.put("frequency_khz", station.frequencyKhz);
        values.put("band", station.band.name());
        values.put("source", station.source);
        values.put("verified_on", station.verifiedOn);
        getWritableDatabase().insertWithOnConflict("stations", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<Station> search(String text) {
        List<Station> result = new ArrayList<>();
        String q = "%" + (text == null ? "" : text.trim()) + "%";
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,name,province,municipality,frequency_khz,band,source,verified_on " +
                "FROM stations WHERE name LIKE ? OR province LIKE ? OR municipality LIKE ? " +
                "ORDER BY province, municipality, name", new String[]{q,q,q})) {
            while (c.moveToNext()) {
                Station.Band band;
                try { band = Station.Band.valueOf(c.getString(5)); }
                catch (Exception ignored) { band = Station.Band.UNKNOWN; }
                result.add(new Station(c.getString(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getDouble(4), band, c.getString(6), c.getString(7)));
            }
        }
        return result;
    }
}
