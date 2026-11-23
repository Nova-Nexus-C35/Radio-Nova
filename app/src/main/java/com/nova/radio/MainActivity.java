package com.nova.radio;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.widget.Toast;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;

import java.util.List;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Pantalla inicial: fuentes terrestres y streaming por Internet separadas. */
public final class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private EditText searchInput;
    private EditText countryInput;
    private Spinner searchFilter;
    private Spinner sortSelector;
    private EditText stationNameInput;
    private Spinner equalizerSelector;
    private Spinner qualitySelector;
    private TextView playbackInfo;
    private boolean playerReceiverRegistered;
    private final BroadcastReceiver playerUpdateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (playbackInfo == null || !InternetRadioService.ACTION_PLAYER_UPDATE.equals(intent.getAction())) return;
            String title = intent.getStringExtra(InternetRadioService.EXTRA_TITLE);
            String artist = intent.getStringExtra(InternetRadioService.EXTRA_ARTIST);
            String state = intent.getStringExtra(InternetRadioService.EXTRA_STATUS);
            StringBuilder text = new StringBuilder(t("Estado: ", "Status: ")).append(state == null ? "" : state);
            if (title != null && !title.isEmpty()) text.append("\n").append(title);
            if (artist != null && !artist.isEmpty() && !artist.equals(title)) text.append("\n").append(artist);
            playbackInfo.setText(text.toString());
        }
    };
    private Spinner languageSelector;
    private boolean english;
    private EditText urlInput;
    private LinearLayout results;
    private LinearLayout savedStations;
    private static final int REQUEST_NOTIFICATIONS = 501;
    private static final int REQUEST_IMPORT_M3U = 502;
    private static final int REQUEST_EXPORT_M3U = 503;
    private static final int REQUEST_NOVA_LOGIN = 504;
    private static final int REQUEST_BACKUP_EXPORT = 505;
    private static final int REQUEST_BACKUP_IMPORT = 506;
    private Button accountButton;
    private Runnable pendingNovaAction;
    private boolean notificationPermissionRequested;
    private String pendingStreamUrl;
    private String pendingStationName;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        english = getPreferences(MODE_PRIVATE).getBoolean("english", false);
        buildScreen();
        showHardwareStatus();
    }

    private String t(String es, String en) { return english ? en : es; }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(30), dp(22), dp(24));
        root.setBackgroundColor(Color.rgb(9, 17, 35));
        scroll.addView(root);

        languageSelector = new Spinner(this);
        languageSelector.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Español", "English"}));
        languageSelector.setSelection(english ? 1 : 0);
        languageSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            private boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    android.view.View view, int position, long id) {
                if (first) { first = false; return; }
                boolean next = position == 1;
                if (english != next) {
                    String previousCountry = countryInput == null ? "" : countryInput.getText().toString();
                    String previousQuery = searchInput == null ? "" : searchInput.getText().toString();
                    english = next;
                    getPreferences(MODE_PRIVATE).edit().putBoolean("english", english).apply();
                    buildScreen();
                    countryInput.setText(previousCountry);
                    searchInput.setText(previousQuery);
                    showHardwareStatus();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        root.addView(languageSelector);

        TextView title = new TextView(this);
        title.setText("RADIO NOVA");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(t("Radio internacional · terrestre y online", "International radio · terrestrial and online"));
        subtitle.setTextColor(Color.rgb(135, 190, 230));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(5), 0, dp(22));
        root.addView(subtitle);

        addSection(t("RADIO TERRESTRE · SIN INTERNET", "TERRESTRIAL RADIO · OFFLINE"),
                t("FM/AM integrado o receptor USB OTG compatible. La compatibilidad se comprobará con el hardware real.", "Built-in FM/AM or compatible USB OTG receiver. Hardware compatibility must be verified."));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setPadding(dp(14), dp(13), dp(14), dp(13));
        status.setBackgroundColor(Color.rgb(22, 37, 62));
        root.addView(status);

        Button refreshUsb = makeButton(t("ACTUALIZAR DISPOSITIVOS USB", "REFRESH USB DEVICES"));
        refreshUsb.setOnClickListener(v -> showHardwareStatus());
        root.addView(refreshUsb);

        addSection(t("RADIO ONLINE · CUENTA NOVA OBLIGATORIA", "ONLINE RADIO · NOVA ACCOUNT REQUIRED"),
                t("Para buscar y reproducir por Internet debes iniciar sesión con una cuenta registrada en Nova Apps. El modo terrestre y los datos locales no requieren cuenta.", "Sign in with a registered Nova Apps account to search and play Internet radio. Terrestrial mode and local data do not require an account."));

        accountButton = makeButton(NovaAccountSession.hasUsableSession(this)
                ? t("CERRAR SESIÓN NOVA", "SIGN OUT OF NOVA")
                : t("INICIAR SESIÓN NOVA", "SIGN IN TO NOVA"));
        accountButton.setOnClickListener(v -> {
            if (NovaAccountSession.hasUsableSession(this)) {
                accountButton.setEnabled(false);
                sendPlayerAction(InternetRadioService.ACTION_STOP);
                new Thread(() -> {
                    NovaAccountSession.signOut(this);
                    runOnUiThread(() -> {
                        updateAccountButton();
                        Toast.makeText(this, t("Sesión de NOVA cerrada.", "NOVA session signed out."), Toast.LENGTH_SHORT).show();
                    });
                }).start();
            } else {
                requireNovaAccount(() -> updateAccountButton());
            }
        });
        root.addView(accountButton);

        addSection(t("MIS EMISORAS", "MY STATIONS"), t("Favoritos y últimas emisoras guardados en este teléfono. Funcionan sin cuenta.", "Favorites and recent stations saved on this phone. No account required."));
        savedStations = new LinearLayout(this);
        savedStations.setOrientation(LinearLayout.VERTICAL);
        root.addView(savedStations);
        renderSavedStations();

        Button importM3u = makeButton(t("IMPORTAR LISTA M3U", "IMPORT M3U PLAYLIST"));
        importM3u.setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("*/*");
            startActivityForResult(picker, REQUEST_IMPORT_M3U);
        });
        root.addView(importM3u);

        Button exportM3u = makeButton(t("EXPORTAR FAVORITOS M3U", "EXPORT FAVORITES M3U"));
        exportM3u.setOnClickListener(v -> {
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            create.addCategory(Intent.CATEGORY_OPENABLE);
            create.setType("audio/x-mpegurl");
            create.putExtra(Intent.EXTRA_TITLE, "radio-nova-favoritos.m3u");
            startActivityForResult(create, REQUEST_EXPORT_M3U);
        });
        root.addView(exportM3u);

        Button backupStations = makeButton(t("CREAR COPIA DE SEGURIDAD", "CREATE BACKUP"));
        backupStations.setOnClickListener(v -> {
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            create.addCategory(Intent.CATEGORY_OPENABLE);
            create.setType("application/json");
            create.putExtra(Intent.EXTRA_TITLE, "radio-nova-backup.json");
            startActivityForResult(create, REQUEST_BACKUP_EXPORT);
        });
        root.addView(backupStations);

        Button restoreStations = makeButton(t("RESTAURAR COPIA DE SEGURIDAD", "RESTORE BACKUP"));
        restoreStations.setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("application/json");
            startActivityForResult(picker, REQUEST_BACKUP_IMPORT);
        });
        root.addView(restoreStations);

        countryInput = new EditText(this);
        countryInput.setSingleLine(true);
        countryInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        countryInput.setHint(t("País (vacío = todos; admite nombres en español)", "Country (blank = all; English names also accepted)"));
        styleInput(countryInput);
        root.addView(countryInput);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        searchInput.setHint(t("Buscar emisora (ej. Radio Rebelde)", "Search station (e.g. Radio Rebelde)"));
        styleInput(searchInput);
        root.addView(searchInput);

        searchFilter = new Spinner(this);
        String[] filterLabels = english ? new String[]{"Name", "Genre", "Language", "Codec", "Country"} : new String[]{"Nombre", "Género", "Idioma", "Códec", "País"};
        searchFilter.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, filterLabels));
        root.addView(searchFilter);

        sortSelector = new Spinner(this);
        String[] sortLabels = english
                ? new String[]{"Sort: popularity", "Sort: name A–Z", "Sort: highest bitrate"}
                : new String[]{"Ordenar: popularidad", "Ordenar: nombre A–Z", "Ordenar: mayor calidad"};
        sortSelector.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, sortLabels));
        root.addView(sortSelector);

        Button search = makeButton(t("BUSCAR EMISORAS", "SEARCH STATIONS"));
        search.setOnClickListener(v -> searchStations());
        root.addView(search);

        Button syncCatalog = makeButton(t("ACTUALIZAR RESULTADOS MUNDIALES", "REFRESH WORLDWIDE RESULTS"));
        syncCatalog.setOnClickListener(v -> {
            searchInput.setText("");
            searchStations();
        });
        root.addView(syncCatalog);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint(t("https://servidor/emision.mp3 o .m3u8", "https://server/stream.mp3 or .m3u8"));
        styleInput(urlInput);
        root.addView(urlInput);

        stationNameInput = new EditText(this);
        stationNameInput.setSingleLine(true);
        stationNameInput.setHint(t("Nombre de la emisora (opcional)", "Station name (optional)"));
        styleInput(stationNameInput);
        root.addView(stationNameInput);

        Button play = makeButton(t("REPRODUCIR DIRECCIÓN", "PLAY URL"));
        play.setOnClickListener(v -> playUrl(urlInput.getText().toString().trim(), t("Emisión online", "Online stream")));
        root.addView(play);

        Button saveManual = makeButton(t("GUARDAR EN FAVORITOS SIN REPRODUCIR", "SAVE TO FAVORITES WITHOUT PLAYING"));
        saveManual.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            String name = stationNameInput.getText().toString().trim();
            if (!StreamUrlValidator.isAllowedHttps(url)) {
                addResultText(t("Introduce una dirección HTTPS válida.", "Enter a valid HTTPS address."));
                return;
            }
            StationStore.addFavorite(this, name.isEmpty() ? t("Emisora guardada", "Saved station") : name, url);
            renderSavedStations();
            Toast.makeText(this, t("Emisora guardada en favoritos.", "Station saved to favorites."), Toast.LENGTH_SHORT).show();
        });
        root.addView(saveManual);

        addSection(t("REPRODUCCIÓN ACTUAL", "NOW PLAYING"),
                t("La canción y el artista aparecerán cuando la emisora transmita metadatos compatibles.", "Song and artist information appears when the station provides compatible metadata."));
        playbackInfo = new TextView(this);
        playbackInfo.setText(t("Estado: sin información del reproductor", "Status: no player information"));
        playbackInfo.setTextColor(Color.WHITE);
        playbackInfo.setTextSize(13);
        playbackInfo.setPadding(dp(14), dp(12), dp(14), dp(12));
        playbackInfo.setBackgroundColor(Color.rgb(22, 37, 62));
        root.addView(playbackInfo);

        addSection(t("CALIDAD DE AUDIO", "AUDIO QUALITY"),
                t("Automática, ahorro de datos, equilibrada o alta. Solo puede cambiar la calidad si la emisión ofrece varias pistas/variantes; un stream MP3 único conserva su calidad original.", "Automatic, data saver, balanced or high. Quality can only change when the stream offers multiple audio tracks/variants; a single MP3 stream keeps its original quality."));
        qualitySelector = new Spinner(this);
        String[] qualityLabels = english
                ? new String[]{"Automatic", "Data saver (max 64 kbps)", "Balanced (max 128 kbps)", "High (highest available)"}
                : new String[]{"Automática", "Ahorro de datos (máx. 64 kbps)", "Equilibrada (máx. 128 kbps)", "Alta (máxima disponible)"};
        qualitySelector.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, qualityLabels));
        String savedQuality = getSharedPreferences("radio_nova_playback", MODE_PRIVATE)
                .getString("quality_mode", "auto");
        qualitySelector.setSelection("data".equals(savedQuality) ? 1
                : "balanced".equals(savedQuality) ? 2 : "high".equals(savedQuality) ? 3 : 0);
        root.addView(qualitySelector);
        Button applyQuality = makeButton(t("APLICAR CALIDAD", "APPLY QUALITY"));
        applyQuality.setOnClickListener(v -> setQualityMode(qualitySelector.getSelectedItemPosition()));
        root.addView(applyQuality);

        addSection(t("ECUALIZADOR", "EQUALIZER"),
                t("Presets de audio locales. Su disponibilidad depende del dispositivo y del efecto de audio de Android.", "Local audio presets. Availability depends on the device and Android audio effects."));
        equalizerSelector = new Spinner(this);
        String[] eqLabels = english
                ? new String[]{"Off", "Balanced", "Bass boost", "Voice"}
                : new String[]{"Desactivado", "Equilibrado", "Refuerzo de graves", "Voz"};
        equalizerSelector.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, eqLabels));
        root.addView(equalizerSelector);
        Button applyEqualizer = makeButton(t("APLICAR ECUALIZADOR", "APPLY EQUALIZER"));
        applyEqualizer.setOnClickListener(v -> setEqualizerPreset(equalizerSelector.getSelectedItemPosition()));
        root.addView(applyEqualizer);

        Button pause = makeButton(t("PAUSAR", "PAUSE"));
        pause.setOnClickListener(v -> sendPlayerAction(InternetRadioService.ACTION_PAUSE));
        root.addView(pause);

        Button resume = makeButton(t("REANUDAR", "RESUME"));
        resume.setOnClickListener(v -> sendPlayerAction(InternetRadioService.ACTION_RESUME));
        root.addView(resume);

        Button stop = makeButton(t("DETENER REPRODUCCIÓN", "STOP PLAYBACK"));
        stop.setOnClickListener(v -> sendPlayerAction(InternetRadioService.ACTION_STOP));
        root.addView(stop);

        addSection(t("TEMPORIZADOR DE APAGADO", "SLEEP TIMER"),
                t("Detiene la radio automáticamente después del tiempo elegido.", "Automatically stops playback after the selected time."));
        Button timer15 = makeButton(t("APAGAR EN 15 MIN", "STOP IN 15 MIN"));
        timer15.setOnClickListener(v -> setSleepTimer(15));
        root.addView(timer15);
        Button timer30 = makeButton(t("APAGAR EN 30 MIN", "STOP IN 30 MIN"));
        timer30.setOnClickListener(v -> setSleepTimer(30));
        root.addView(timer30);
        Button timer60 = makeButton(t("APAGAR EN 60 MIN", "STOP IN 60 MIN"));
        timer60.setOnClickListener(v -> setSleepTimer(60));
        root.addView(timer60);
        Button timer90 = makeButton(t("APAGAR EN 90 MIN", "STOP IN 90 MIN"));
        timer90.setOnClickListener(v -> setSleepTimer(90));
        root.addView(timer90);
        Button cancelTimer = makeButton(t("CANCELAR TEMPORIZADOR", "CANCEL SLEEP TIMER"));
        cancelTimer.setOnClickListener(v -> sendPlayerAction(InternetRadioService.ACTION_CANCEL_SLEEP_TIMER));
        root.addView(cancelTimer);

        TextView foot = new TextView(this);
        foot.setText(t("La radio online consume datos móviles o Wi-Fi. Las emisoras y sus enlaces pueden cambiar o dejar de funcionar. La recepción terrestre no depende de estos servicios.", "Online radio uses mobile data or Wi-Fi. Stations and stream links may change or stop working. Terrestrial reception does not depend on these services."));
        foot.setTextColor(Color.LTGRAY);
        foot.setTextSize(12);
        foot.setPadding(0, dp(18), 0, 0);
        root.addView(foot);

        setContentView(scroll);
    }

    private void searchStations() {
        if (!requireNovaAccount(this::searchStations)) return;
        String query = searchInput.getText().toString().trim();
        if (query.length() > 100) {
            addResultText(t("La búsqueda es demasiado larga. Usa hasta 100 caracteres.", "Search is too long. Use up to 100 characters."));
            return;
        }
        results.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText(t("Buscando emisoras de todo el mundo…", "Searching stations worldwide…"));
        loading.setTextColor(Color.WHITE);
        results.addView(loading);

        String[] filterKeys = {"name", "tag", "language", "codec", "country"};
        String filter = filterKeys[Math.max(0, Math.min(filterKeys.length - 1,
                searchFilter.getSelectedItemPosition()))];
        String country = countryInput.getText().toString().trim();
        RadioBrowserClient.searchStations(MainActivity.this, query, country, filter,
                new RadioBrowserClient.Callback() {
            @Override public void onResult(List<OnlineStation> stations) {
                runOnUiThread(() -> renderStationResults(stations, false));
            }

            @Override public void onCachedResult(List<OnlineStation> stations) {
                runOnUiThread(() -> renderStationResults(stations, true));
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    results.removeAllViews();
                    addResultText(english ? "Could not contact Radio Browser. Check your connection and try again." : message);
                });
            }
        });
    }

    private void renderStationResults(List<OnlineStation> stations, boolean cached) {
        results.removeAllViews();
        if (cached) addResultText(t("Sin conexión al directorio. Mostrando la última búsqueda guardada en este teléfono.", "Directory unavailable. Showing the last search saved on this phone."));
        if (stations.isEmpty()) {
            addResultText(t("No se encontraron emisoras. Prueba otro nombre o pega un enlace directo.", "No stations found. Try another name or paste a direct URL."));
            return;
        }
        List<OnlineStation> orderedStations = new java.util.ArrayList<>(stations);
        int sortMode = sortSelector == null ? 0 : sortSelector.getSelectedItemPosition();
        if (sortMode == 1) {
            orderedStations.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        } else if (sortMode == 2) {
            orderedStations.sort((a, b) -> Integer.compare(b.bitrate, a.bitrate));
        }
        for (OnlineStation station : orderedStations) {
            StationStore.saveStationMetadata(this, station);
            Button item = makeButton(station.name + "  ·  " + station.codecLabel());
            item.setOnClickListener(v -> playUrl(station.streamUrl, station.name));
            results.addView(item);

            String details = station.detailLabel();
            if (!station.country.isEmpty()) {
                details = details.isEmpty() ? station.country : details + " · " + station.country;
            }
            if (!station.language.isEmpty()) {
                details = details.isEmpty() ? station.language : details + " · " + station.language;
            }
            if (!details.isEmpty()) {
                TextView detail = new TextView(this);
                detail.setText(details);
                detail.setTextColor(Color.LTGRAY);
                detail.setTextSize(11);
                detail.setPadding(dp(8), 0, dp(8), dp(6));
                results.addView(detail);
            }

            Button favorite = makeButton(StationStore.isFavorite(this, station.streamUrl)
                    ? t("★ Quitar de favoritos", "★ Remove from favorites") : t("☆ Guardar en favoritos", "☆ Save to favorites"));
            favorite.setOnClickListener(v -> {
                StationStore.toggleFavorite(this, station.name, station.streamUrl);
                renderSavedStations();
                favorite.setText(StationStore.isFavorite(this, station.streamUrl)
                        ? t("★ Quitar de favoritos", "★ Remove from favorites") : t("☆ Guardar en favoritos", "☆ Save to favorites"));
            });
            results.addView(favorite);

            Button share = makeButton(t("COMPARTIR EMISORA", "SHARE STATION"));
            share.setOnClickListener(v -> shareStation(station.name, station.streamUrl));
            results.addView(share);
            if (!station.homepage.isEmpty()) {
                Button website = makeButton(t("ABRIR PÁGINA OFICIAL", "OPEN OFFICIAL WEBSITE"));
                website.setOnClickListener(v -> openOfficialWebsite(station.homepage));
                results.addView(website);
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_NOVA_LOGIN) {
            Runnable action = pendingNovaAction;
            pendingNovaAction = null;
            if (resultCode == RESULT_OK) {
                updateAccountButton();
                Toast.makeText(this, t("Cuenta NOVA verificada. Modo Internet activado.", "NOVA account verified. Internet mode enabled."), Toast.LENGTH_SHORT).show();
                if (action != null) action.run();
            } else {
                Toast.makeText(this, t("Inicia sesión en NOVA para usar la radio por Internet.", "Sign in to NOVA to use Internet radio."), Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_BACKUP_EXPORT) {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new java.io.IOException("Cannot create backup");
                StationBackup.write(this, output);
                Toast.makeText(this, t("Copia de seguridad creada. No contiene datos de cuenta.", "Backup created. It contains no account data."), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, t("No se pudo crear la copia.", "Could not create backup."), Toast.LENGTH_LONG).show();
            }
            return;
        } else if (requestCode == REQUEST_BACKUP_IMPORT) {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException("Cannot open backup");
                int restored = StationBackup.restore(this, input);
                renderSavedStations();
                Toast.makeText(this, t("Restauración completada. Elementos procesados: ", "Restore completed. Items processed: ") + restored, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, t("Copia no válida o dañada. No se pudo restaurar.", "Backup is invalid or damaged. Restore failed."), Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_M3U) {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException(t("No se pudo abrir el archivo.", "Could not open the file."));
                List<OnlineStation> imported = M3uPlaylistParser.parse(input);
                for (OnlineStation station : imported) {
                    StationStore.addFavorite(this, station.name, station.streamUrl);
                    if (!station.category.isEmpty()) StationStore.setCategory(this, station.streamUrl, station.category);
                }
                renderSavedStations();
                Toast.makeText(this, t("Importadas ", "Imported ") + imported.size() + t(" emisoras HTTPS.", " HTTPS stations."), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, t("No se pudo importar la lista M3U.", "Could not import the M3U playlist."), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_EXPORT_M3U) {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new java.io.IOException(t("No se pudo crear el archivo.", "Could not create the file."));
                output.write(StationStore.exportFavoritesM3u(this).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, t("Favoritos exportados.", "Favorites exported."), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, t("No se pudo exportar la lista M3U.", "Could not export the M3U playlist."), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            String url = pendingStreamUrl;
            String name = pendingStationName;
            pendingStreamUrl = null;
            pendingStationName = null;
            if (url != null) {
                if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    addResultText(t("Sin permiso de notificaciones: la reproducción puede continuar, pero Android podría limitar los controles visibles.", "Without notification permission, playback may continue but Android may limit visible controls."));
                }
                playUrl(url, name);
            }
        }
    }

    private void setQualityMode(int position) {
        if (!requireNovaAccount(() -> setQualityMode(position))) return;
        String[] modes = {"auto", "data", "balanced", "high"};
        int index = Math.max(0, Math.min(modes.length - 1, position));
        String mode = modes[index];
        getSharedPreferences("radio_nova_playback", MODE_PRIVATE).edit()
                .putString("quality_mode", mode).apply();
        Intent intent = new Intent(this, InternetRadioService.class);
        intent.setAction(InternetRadioService.ACTION_SET_QUALITY);
        intent.putExtra(InternetRadioService.EXTRA_QUALITY_MODE, mode);
        try {
            startService(intent);
            Toast.makeText(this, t("Preferencia de calidad guardada.", "Quality preference saved."), Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, t("No se pudo aplicar la calidad ahora.", "Could not apply quality now."), Toast.LENGTH_SHORT).show();
        }
    }

    private void setEqualizerPreset(int position) {
        if (!requireNovaAccount(() -> setEqualizerPreset(position))) return;
        String[] presets = {"off", "balanced", "bass", "voice"};
        int index = Math.max(0, Math.min(presets.length - 1, position));
        Intent intent = new Intent(this, InternetRadioService.class);
        intent.setAction(InternetRadioService.ACTION_SET_EQUALIZER);
        intent.putExtra(InternetRadioService.EXTRA_EQUALIZER_PRESET, presets[index]);
        try {
            startService(intent);
            Toast.makeText(this, t("Ajuste de audio solicitado.", "Audio preset requested."), Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, t("El ecualizador no está disponible ahora.", "Equalizer is not available now."), Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(InternetRadioService.ACTION_PLAYER_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(playerUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(playerUpdateReceiver, filter);
        }
        playerReceiverRegistered = true;
        if (NovaAccountSession.hasUsableSession(this)) {
            Intent query = new Intent(this, InternetRadioService.class);
            query.setAction(InternetRadioService.ACTION_QUERY_STATUS);
            try { startService(query); } catch (RuntimeException ignored) { }
        }
    }

    @Override protected void onStop() {
        if (playerReceiverRegistered) {
            try { unregisterReceiver(playerUpdateReceiver); } catch (IllegalArgumentException ignored) { }
            playerReceiverRegistered = false;
        }
        super.onStop();
    }

    private void playUrl(String url, String name) {
        if (!requireNovaAccount(() -> playUrl(url, name))) return;
        if (!StreamUrlValidator.isAllowedHttps(url)) {
            addResultText(t("Introduce una dirección HTTPS válida. Los enlaces HTTP sin cifrar se bloquean por seguridad.", "Enter a valid HTTPS URL. Unencrypted HTTP links are blocked for security."));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                && !notificationPermissionRequested) {
            notificationPermissionRequested = true;
            pendingStreamUrl = url;
            pendingStationName = name;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            addResultText(t("Autoriza las notificaciones para mostrar los controles de reproducción.", "Allow notifications to show playback controls."));
            return;
        }

        Intent i = new Intent(this, InternetRadioService.class);
        i.setAction(InternetRadioService.ACTION_PLAY);
        i.putExtra(InternetRadioService.EXTRA_URL, url);
        i.putExtra(InternetRadioService.EXTRA_NAME, name);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            StationStore.addRecent(this, name, url);
            renderSavedStations();
            addResultText(t("Solicitud de reproducción enviada: ", "Playback request sent: ") + name);
        } catch (RuntimeException e) {
            addResultText(t("No se pudo iniciar el servicio de radio. Mantén la aplicación abierta e inténtalo otra vez.", "Could not start the radio service. Keep the app open and try again."));
        }
    }

    private void setSleepTimer(int minutes) {
        if (!requireNovaAccount(() -> setSleepTimer(minutes))) return;
        Intent intent = new Intent(this, InternetRadioService.class);
        intent.setAction(InternetRadioService.ACTION_SLEEP_TIMER);
        intent.putExtra(InternetRadioService.EXTRA_SLEEP_DURATION_MS, minutes * 60_000L);
        try {
            startService(intent);
            addResultText(t("Si hay una emisora activa, se apagará en ", "If a station is playing, it will stop in ") + minutes + t(" minutos.", " minutes."));
        } catch (RuntimeException e) {
            addResultText(t("No se pudo activar el temporizador. Inicia una emisora e inténtalo otra vez.", "Could not set the timer. Start a station and try again."));
        }
    }

    private void sendPlayerAction(String action) {
        if (InternetRadioService.ACTION_RESUME.equals(action)
                && !requireNovaAccount(() -> sendPlayerAction(action))) return;
        Intent intent = new Intent(this, InternetRadioService.class);
        intent.setAction(action);
        try {
            startService(intent);
            if (InternetRadioService.ACTION_STOP.equals(action)) {
                addResultText(t("Reproducción detenida.", "Playback stopped."));
            } else if (InternetRadioService.ACTION_PAUSE.equals(action)) {
                addResultText(t("Solicitud de pausa enviada.", "Pause request sent."));
            } else if (InternetRadioService.ACTION_CANCEL_SLEEP_TIMER.equals(action)) {
                addResultText(t("Temporizador cancelado.", "Sleep timer cancelled."));
            } else {
                addResultText(t("Solicitud de reanudación enviada.", "Resume request sent."));
            }
        } catch (RuntimeException e) {
            addResultText(t("No se pudo controlar la reproducción. Abre la emisora primero.", "Could not control playback. Start a station first."));
        }
    }

    private boolean requireNovaAccount(Runnable afterLogin) {
        if (NovaAccountSession.hasUsableSession(this)) return true;
        pendingNovaAction = afterLogin;
        Intent login = new Intent(this, NovaLoginActivity.class);
        login.putExtra("english", english);
        startActivityForResult(login, REQUEST_NOVA_LOGIN);
        return false;
    }

    private void updateAccountButton() {
        if (accountButton != null) {
            accountButton.setEnabled(true);
            accountButton.setText(NovaAccountSession.hasUsableSession(this)
                    ? t("CERRAR SESIÓN NOVA", "SIGN OUT OF NOVA")
                    : t("INICIAR SESIÓN NOVA", "SIGN IN TO NOVA"));
        }
    }

    private void renderSavedStations() {
        if (savedStations == null) return;
        savedStations.removeAllViews();
        addSavedGroup(t("FAVORITOS", "FAVORITES"), StationStore.getFavorites(this));
        addSavedGroup(t("RECIENTES", "RECENT"), StationStore.getRecent(this));
    }

    private void addSavedGroup(String heading, List<OnlineStation> stations) {
        TextView title = new TextView(this);
        title.setText(heading);
        title.setTextColor(Color.rgb(70, 205, 230));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextSize(12);
        title.setPadding(0, dp(8), 0, dp(4));
        savedStations.addView(title);
        if (stations.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(t("Todavía no hay emisoras.", "No stations yet."));
            empty.setTextColor(Color.LTGRAY);
            empty.setTextSize(12);
            savedStations.addView(empty);
            return;
        }
        for (OnlineStation station : stations) {
            Button play = makeButton(station.name);
            play.setOnClickListener(v -> playUrl(station.streamUrl, station.name));
            savedStations.addView(play);
            String category = StationStore.getCategory(this, station.streamUrl);
            if (!category.isEmpty()) {
                TextView categoryLabel = new TextView(this);
                categoryLabel.setText(t("Colección: ", "Collection: ") + category);
                categoryLabel.setTextColor(Color.rgb(135, 190, 230));
                categoryLabel.setTextSize(12);
                savedStations.addView(categoryLabel);
            }
            Button categoryButton = makeButton(t("ASIGNAR / EDITAR COLECCIÓN", "SET / EDIT COLLECTION"));
            categoryButton.setOnClickListener(v -> editStationCategory(station));
            savedStations.addView(categoryButton);
            Button shareButton = makeButton(t("COMPARTIR EMISORA", "SHARE STATION"));
            shareButton.setOnClickListener(v -> shareStation(station.name, station.streamUrl));
            savedStations.addView(shareButton);
            Button favorite = makeButton(StationStore.isFavorite(this, station.streamUrl)
                    ? t("★ Quitar de favoritos", "★ Remove from favorites") : t("☆ Guardar en favoritos", "☆ Save to favorites"));
            favorite.setOnClickListener(v -> {
                StationStore.toggleFavorite(this, station.name, station.streamUrl);
                renderSavedStations();
            });
            savedStations.addView(favorite);
        }
    }

    private void editStationCategory(OnlineStation station) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(t("Ej. Noticias, Música, Cuba…", "e.g. News, Music, Cuba…"));
        input.setText(StationStore.getCategory(this, station.streamUrl));
        new android.app.AlertDialog.Builder(this)
                .setTitle(t("Colección de la emisora", "Station collection"))
                .setMessage(t("Organiza tus emisoras con etiquetas locales. Déjalo vacío para quitar la etiqueta.", "Organize stations with local tags. Leave empty to remove the tag."))
                .setView(input)
                .setNegativeButton(t("Cancelar", "Cancel"), null)
                .setNeutralButton(t("Quitar", "Remove"), (dialog, which) -> {
                    StationStore.setCategory(this, station.streamUrl, "");
                    renderSavedStations();
                })
                .setPositiveButton(t("Guardar", "Save"), (dialog, which) -> {
                    StationStore.setCategory(this, station.streamUrl, input.getText().toString());
                    renderSavedStations();
                }).show();
    }

    private void shareStation(String name, String url) {
        if (!StreamUrlValidator.isAllowedHttps(url)) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, name);
        share.putExtra(Intent.EXTRA_TEXT, name + "\n" + url);
        startActivity(Intent.createChooser(share, t("Compartir emisora", "Share station")));
    }

    private void openOfficialWebsite(String homepage) {
        if (!requireNovaAccount(() -> openOfficialWebsite(homepage))) return;
        if (!StreamUrlValidator.isAllowedHttps(homepage)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(homepage)));
        } catch (android.content.ActivityNotFoundException ignored) {
            Toast.makeText(this, t("No hay navegador disponible.", "No browser is available."), Toast.LENGTH_SHORT).show();
        }
    }

    private void addResultText(String message) {
        TextView t = new TextView(this);
        t.setText(message);
        t.setTextColor(Color.WHITE);
        t.setTextSize(13);
        t.setPadding(dp(8), dp(10), dp(8), dp(10));
        results.addView(t);
    }

    private void addSection(String heading, String detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackgroundColor(Color.rgb(18, 31, 53));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        root.addView(card, lp);

        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextColor(Color.rgb(70, 205, 230));
        h.setTextSize(15);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(h);

        TextView d = new TextView(this);
        d.setText(detail);
        d.setTextColor(Color.WHITE);
        d.setTextSize(13);
        d.setPadding(0, dp(6), 0, 0);
        card.addView(d);
    }

    private void styleInput(EditText input) {
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackgroundColor(Color.rgb(25, 42, 68));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        input.setLayoutParams(lp);
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(24, 103, 137)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private void showHardwareStatus() {
        UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
        java.util.Collection<android.hardware.usb.UsbDevice> devices =
                usb == null ? java.util.Collections.emptyList() : usb.getDeviceList().values();
        StringBuilder detail = new StringBuilder();
        detail.append(t("Dispositivos USB detectados: ", "USB devices detected: ")).append(devices.size());
        for (android.hardware.usb.UsbDevice device : devices) {
            String vendor = Integer.toHexString(device.getVendorId()).toUpperCase();
            String product = Integer.toHexString(device.getProductId()).toUpperCase();
            detail.append("\n• ")
                    .append(device.getProductName() == null ? t("Dispositivo USB", "USB device") : device.getProductName())
                    .append(" (VID:PID ")
                    .append(vendor).append(":").append(product).append(")");
            if (device.getManufacturerName() != null) {
                detail.append(" · ").append(device.getManufacturerName());
            }
            detail.append(usb != null && usb.hasPermission(device)
                    ? t(" · permiso concedido", " · permission granted") : t(" · permiso pendiente", " · permission pending"));
        }
        detail.append(t("\nSintonizador FM/AM interno: pendiente de comprobar en este teléfono.", "\\nBuilt-in FM/AM tuner: not yet verified on this phone."))
                .append(t("\nLa detección USB no confirma que el dispositivo sea un receptor de radio compatible.", "\\nUSB detection does not confirm that the device is a compatible radio receiver."));
        status.setText(detail.toString());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
