package com.nova.radio;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.media.audiofx.Equalizer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Metadata;
import androidx.media3.extractor.metadata.icy.IcyInfo;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.LibraryResult;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Servicio de reproducción online.
 * Media3 administra la sesión, notificación y controles del sistema.
 */
@androidx.media3.common.util.UnstableApi
@UnstableApi
public final class InternetRadioService extends MediaLibraryService {
    public static final String ACTION_PLAY = "com.nova.radio.action.PLAY";
    public static final String ACTION_STOP = "com.nova.radio.action.STOP";
    public static final String ACTION_PAUSE = "com.nova.radio.action.PAUSE";
    public static final String ACTION_RESUME = "com.nova.radio.action.RESUME";
    public static final String ACTION_SLEEP_TIMER = "com.nova.radio.action.SLEEP_TIMER";
    public static final String ACTION_CANCEL_SLEEP_TIMER = "com.nova.radio.action.CANCEL_SLEEP_TIMER";
    public static final String ACTION_SET_EQUALIZER = "com.nova.radio.action.SET_EQUALIZER";
    public static final String ACTION_PLAYER_UPDATE = "com.nova.radio.action.PLAYER_UPDATE";
    public static final String ACTION_QUERY_STATUS = "com.nova.radio.action.QUERY_STATUS";
    public static final String ACTION_SET_QUALITY = "com.nova.radio.action.SET_QUALITY";
    public static final String EXTRA_QUALITY_MODE = "quality_mode";
    public static final String EXTRA_EQUALIZER_PRESET = "equalizer_preset";
    public static final String EXTRA_STATUS = "player_status";
    public static final String EXTRA_TITLE = "player_title";
    public static final String EXTRA_ARTIST = "player_artist";
    public static final String EXTRA_SLEEP_DURATION_MS = "sleep_duration_ms";
    public static final String EXTRA_URL = "stream_url";
    public static final String EXTRA_NAME = "station_name";

    private ExoPlayer player;
    private MediaLibrarySession mediaSession;
    private static final String LIBRARY_ROOT = "radio_nova_root";
    private static final String LIBRARY_FAVORITES = "radio_nova_favorites";
    private static final String LIBRARY_RECENTS = "radio_nova_recents";
    private final MediaLibrarySession.Callback libraryCallback = new MediaLibrarySession.Callback() {
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession session, MediaSession.ControllerInfo browser,
                @Nullable LibraryParams params) {
            if (!NovaAccountSession.hasUsableSession(InternetRadioService.this)) {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
            }
            return Futures.immediateFuture(LibraryResult.ofItem(
                    folderItem(LIBRARY_ROOT, "Radio Nova"), params));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession session, MediaSession.ControllerInfo browser,
                String parentId, int page, int pageSize, @Nullable LibraryParams params) {
            if (!NovaAccountSession.hasUsableSession(InternetRadioService.this)) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params));
            }
            List<MediaItem> items = new java.util.ArrayList<>();
            if (LIBRARY_ROOT.equals(parentId)) {
                items.add(folderItem(LIBRARY_FAVORITES, "Favoritos"));
                items.add(folderItem(LIBRARY_RECENTS, "Últimas emisoras"));
            } else if (LIBRARY_FAVORITES.equals(parentId)) {
                for (OnlineStation station : StationStore.getFavorites(InternetRadioService.this)) {
                    items.add(libraryStationItem(station));
                }
            } else if (LIBRARY_RECENTS.equals(parentId)) {
                for (OnlineStation station : StationStore.getRecent(InternetRadioService.this)) {
                    items.add(libraryStationItem(station));
                }
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(
                    pageItems(items, page, pageSize), params));
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String mediaId) {
            if (!NovaAccountSession.hasUsableSession(InternetRadioService.this)) {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
            }
            if (LIBRARY_ROOT.equals(mediaId)) {
                return Futures.immediateFuture(LibraryResult.ofItem(folderItem(LIBRARY_ROOT, "Radio Nova"), null));
            }
            if (LIBRARY_FAVORITES.equals(mediaId)) {
                return Futures.immediateFuture(LibraryResult.ofItem(folderItem(LIBRARY_FAVORITES, "Favoritos"), null));
            }
            if (LIBRARY_RECENTS.equals(mediaId)) {
                return Futures.immediateFuture(LibraryResult.ofItem(folderItem(LIBRARY_RECENTS, "Últimas emisoras"), null));
            }
            OnlineStation station = findLibraryStation(mediaId);
            if (station == null) {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
            }
            return Futures.immediateFuture(LibraryResult.ofItem(libraryStationItem(station), null));
        }

        @Override
        public ListenableFuture<LibraryResult<Void>> onSearch(
                MediaLibrarySession session, MediaSession.ControllerInfo browser,
                String query, @Nullable LibraryParams params) {
            return Futures.immediateFuture(LibraryResult.ofVoid(params));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String query,
                int page, int pageSize, @Nullable LibraryParams params) {
            if (!NovaAccountSession.hasUsableSession(InternetRadioService.this)) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params));
            }
            String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
            java.util.LinkedHashMap<String, OnlineStation> matches = new java.util.LinkedHashMap<>();
            for (OnlineStation station : StationStore.getFavorites(InternetRadioService.this)) {
                if (station.name.toLowerCase(java.util.Locale.ROOT).contains(needle)) matches.put(station.streamUrl, station);
            }
            for (OnlineStation station : StationStore.getRecent(InternetRadioService.this)) {
                if (station.name.toLowerCase(java.util.Locale.ROOT).contains(needle)) matches.put(station.streamUrl, station);
            }
            List<MediaItem> items = new java.util.ArrayList<>();
            for (OnlineStation station : matches.values()) items.add(libraryStationItem(station));
            return Futures.immediateFuture(LibraryResult.ofItemList(pageItems(items, page, pageSize), params));
        }
    };
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private final Handler sleepHandler = new Handler(Looper.getMainLooper());
    private int reconnectAttempts;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private String currentUrl;
    private boolean userPaused;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Equalizer equalizer;
    private String currentStationName = "Radio Nova";
    private String equalizerPreset = "off";
    private final Runnable sleepStopTask = () -> {
        currentUrl = null;
        reconnectHandler.removeCallbacksAndMessages(null);
        reconnectAttempts = 0;
        userPaused = false;
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        stopSelf();
    };

    @Override public void onCreate() {
        super.onCreate();
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("RadioNova/1.3.0 (Android)")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(false);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(httpFactory))
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        applyQualityMode(getSharedPreferences("radio_nova_playback", MODE_PRIVATE)
                .getString("quality_mode", "auto"));
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                publishPlayerUpdate("Error de conexión. Preparando reconexión…");
                scheduleReconnect();
            }

            @Override public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    reconnectAttempts = 0;
                    applyEqualizerPreset(equalizerPreset);
                    publishPlayerUpdate("Reproduciendo");
                } else if (playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                    publishPlayerUpdate("Conectando…");
                } else if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    publishPlayerUpdate("Emisión finalizada");
                }
            }

            @Override public void onMediaMetadataChanged(MediaMetadata metadata) {
                publishPlayerUpdate(playerStatusLabel(), metadata);
            }

            @Override public void onMetadata(Metadata metadata) {
                for (int i = 0; i < metadata.length(); i++) {
                    Metadata.Entry entry = metadata.get(i);
                    if (entry instanceof IcyInfo) {
                        String title = ((IcyInfo) entry).title;
                        if (title != null && !title.trim().isEmpty()) {
                            MediaMetadata dynamic = new MediaMetadata.Builder()
                                    .setTitle(title.trim())
                                    .setArtist(currentStationName)
                                    .build();
                            if (player != null && player.getCurrentMediaItem() != null) {
                                androidx.media3.common.MediaItem current = player.getCurrentMediaItem();
                                androidx.media3.common.MediaItem updated = current.buildUpon()
                                        .setMediaMetadata(dynamic).build();
                                player.replaceMediaItem(player.getCurrentMediaItemIndex(), updated);
                            }
                            publishPlayerUpdate("Reproduciendo · metadatos de emisora", dynamic);
                        }
                    }
                }
            }
        });
        player.setAudioAttributes(
                androidx.media3.common.AudioAttributes.DEFAULT, true);
        mediaSession = new MediaLibrarySession.Builder(this, player, libraryCallback).build();
        registerNetworkCallback();
    }

    @Nullable
    @Override public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return NovaAccountSession.hasUsableSession(this) ? mediaSession : null;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (intent == null) return START_NOT_STICKY;

        if ((ACTION_PLAY.equals(intent.getAction()) || ACTION_RESUME.equals(intent.getAction())
                || ACTION_SLEEP_TIMER.equals(intent.getAction())
                || ACTION_SET_EQUALIZER.equals(intent.getAction())
                || ACTION_SET_QUALITY.equals(intent.getAction())
                || ACTION_QUERY_STATUS.equals(intent.getAction()))
                && !NovaAccountSession.hasUsableSession(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_SLEEP_TIMER.equals(intent.getAction())) {
            long duration = intent.getLongExtra(EXTRA_SLEEP_DURATION_MS, 0L);
            if (player == null || player.getMediaItemCount() == 0) {
                stopSelf();
                return START_NOT_STICKY;
            }
            if (duration >= 60_000L && duration <= 24L * 60L * 60L * 1000L) {
                sleepHandler.removeCallbacks(sleepStopTask);
                sleepHandler.postDelayed(sleepStopTask, duration);
            }
            return START_NOT_STICKY;
        }

        if (ACTION_QUERY_STATUS.equals(intent.getAction())) {
            publishPlayerUpdate(playerStatusLabel());
            if (player == null || player.getMediaItemCount() == 0) stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_SET_QUALITY.equals(intent.getAction())) {
            applyQualityMode(intent.getStringExtra(EXTRA_QUALITY_MODE));
            if (player == null || player.getMediaItemCount() == 0) stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_SET_EQUALIZER.equals(intent.getAction())) {
            if (player == null || player.getMediaItemCount() == 0) {
                publishPlayerUpdate("Inicia una emisora para usar el ecualizador");
                stopSelf();
                return START_NOT_STICKY;
            }
            String preset = intent.getStringExtra(EXTRA_EQUALIZER_PRESET);
            applyEqualizerPreset(preset == null ? "off" : preset);
            return START_NOT_STICKY;
        }

        if (ACTION_CANCEL_SLEEP_TIMER.equals(intent.getAction())) {
            sleepHandler.removeCallbacks(sleepStopTask);
            if (player == null || player.getMediaItemCount() == 0) stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(intent.getAction())) {
            sleepHandler.removeCallbacks(sleepStopTask);
            reconnectHandler.removeCallbacksAndMessages(null);
            reconnectAttempts = 0;
            userPaused = false;
            currentUrl = null;
            if (player != null) {
                player.stop();
                player.clearMediaItems();
            }
            publishPlayerUpdate("Detenido");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_PAUSE.equals(intent.getAction())) {
            userPaused = true;
            reconnectHandler.removeCallbacksAndMessages(null);
            if (player != null) player.pause();
            publishPlayerUpdate("En pausa");
            return START_NOT_STICKY;
        }

        if (ACTION_RESUME.equals(intent.getAction())) {
            userPaused = false;
            if (player != null && player.getMediaItemCount() > 0) player.play();
            publishPlayerUpdate("Reanudando…");
            return START_NOT_STICKY;
        }

        if (ACTION_PLAY.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            String name = intent.getStringExtra(EXTRA_NAME);
            reconnectHandler.removeCallbacksAndMessages(null);
            reconnectAttempts = 0;
            userPaused = false;
            if (!isAllowedStream(url)) {
                currentUrl = null;
                if (player != null) player.stop();
                stopSelf();
                return START_NOT_STICKY;
            }
            currentUrl = url;
            currentStationName = name == null || name.trim().isEmpty() ? "Radio Nova" : name.trim();
            publishPlayerUpdate("Conectando…");

            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(currentStationName)
                    .setArtist("Radio online")
                    .build();
            MediaItem item = new MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(metadata)
                    .build();

            player.setMediaItem(item);
            player.prepare();
            player.play();
        }
        return START_NOT_STICKY;
    }

    /** Reintenta una emisión fallida cuando Android informa que vuelve una red con Internet. */
    private void registerNetworkCallback() {
        Object service = getSystemService(CONNECTIVITY_SERVICE);
        if (!(service instanceof ConnectivityManager)) return;
        connectivityManager = (ConnectivityManager) service;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                reconnectOnValidatedNetwork(network);
            }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    reconnectOnValidatedNetwork(network);
                }
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } else {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            }
        } catch (RuntimeException ignored) {
            networkCallback = null;
        }
    }
    private void reconnectOnValidatedNetwork(Network network) {
        reconnectHandler.post(() -> {
            if (player == null || currentUrl == null || userPaused || connectivityManager == null) return;
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null
                    || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return;
            if (player.getPlaybackState() == androidx.media3.common.Player.STATE_IDLE) {
                reconnectHandler.removeCallbacksAndMessages(null);
                reconnectAttempts = 0;
                publishPlayerUpdate("Conexión recuperada. Reconectando…");
                player.prepare();
                player.play();
            }
        });
    }

    private MediaItem folderItem(String id, String title) {
        MediaMetadata metadata = new MediaMetadata.Builder().setTitle(title)
                .setIsBrowsable(true).setIsPlayable(false).build();
        return new MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build();
    }

    private MediaItem libraryStationItem(OnlineStation station) {
        station = StationStore.enrichStation(this, station);
        String detail = station.codecLabel();
        if (!station.country.isEmpty()) detail = station.country + " · " + detail;
        MediaMetadata metadata = new MediaMetadata.Builder().setTitle(station.name)
                .setArtist(station.country.isEmpty() ? "Radio online" : station.country)
                .setAlbumTitle(detail)
                .setIsBrowsable(false).setIsPlayable(true).build();
        return new MediaItem.Builder().setMediaId(station.streamUrl)
                .setUri(station.streamUrl).setMediaMetadata(metadata).build();
    }

    private OnlineStation findLibraryStation(String mediaId) {
        for (OnlineStation station : StationStore.getFavorites(this)) {
            if (station.streamUrl.equals(mediaId)) return station;
        }
        for (OnlineStation station : StationStore.getRecent(this)) {
            if (station.streamUrl.equals(mediaId)) return station;
        }
        return null;
    }

    private List<MediaItem> pageItems(List<MediaItem> source, int page, int pageSize) {
        int start = Math.max(0, page) * Math.max(1, pageSize);
        if (start >= source.size()) return ImmutableList.of();
        int end = Math.min(source.size(), start + Math.max(1, pageSize));
        return ImmutableList.copyOf(source.subList(start, end));
    }

    private String playerStatusLabel() {
        if (player == null) return "Detenido";
        if (player.isPlaying()) return "Reproduciendo";
        if (player.getPlaybackState() == androidx.media3.common.Player.STATE_BUFFERING) return "Conectando…";
        return userPaused ? "En pausa" : "Preparado";
    }

    private void publishPlayerUpdate(String status) {
        publishPlayerUpdate(status, player == null ? null : player.getMediaMetadata());
    }

    private void publishPlayerUpdate(String status, MediaMetadata metadata) {
        Intent update = new Intent(ACTION_PLAYER_UPDATE);
        update.setPackage(getPackageName());
        update.putExtra(EXTRA_STATUS, status == null ? "" : status);
        String title = metadata == null || metadata.title == null
                ? currentStationName : metadata.title.toString();
        String artist = metadata == null || metadata.artist == null
                ? "Radio online" : metadata.artist.toString();
        update.putExtra(EXTRA_TITLE, title);
        update.putExtra(EXTRA_ARTIST, artist);
        sendBroadcast(update);
    }

    private void applyQualityMode(String mode) {
        String selected = mode == null ? "auto" : mode;
        int maxBitrate;
        boolean forceHighest = false;
        switch (selected) {
            case "data":
                maxBitrate = 64_000;
                break;
            case "balanced":
                maxBitrate = 128_000;
                break;
            case "high":
                maxBitrate = Integer.MAX_VALUE;
                forceHighest = true;
                break;
            default:
                selected = "auto";
                maxBitrate = Integer.MAX_VALUE;
                break;
        }
        getSharedPreferences("radio_nova_playback", MODE_PRIVATE).edit()
                .putString("quality_mode", selected).apply();
        if (player != null) {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setMaxAudioBitrate(maxBitrate)
                    .setForceHighestSupportedBitrate(forceHighest)
                    .build());
        }
        publishPlayerUpdate("Calidad preferida: " + selected
                + ". Solo cambia si la emisión ofrece varias calidades.");
    }

    private void applyEqualizerPreset(String preset) {
        if (preset == null) preset = "off";
        equalizerPreset = preset;
        if (player == null || player.getAudioSessionId() == 0) return;
        try {
            if ("off".equals(preset)) {
                if (equalizer != null) {
                    equalizer.setEnabled(false);
                    equalizer.release();
                    equalizer = null;
                }
                publishPlayerUpdate(playerStatusLabel());
                return;
            }
            if (equalizer == null) equalizer = new Equalizer(0, player.getAudioSessionId());
            if (equalizer == null) return;
            short bands = equalizer.getNumberOfBands();
            short[] range = equalizer.getBandLevelRange();
            for (short band = 0; band < bands; band++) {
                int level = 0;
                float position = bands <= 1 ? 0f : (float) band / (bands - 1);
                if ("bass".equals(preset)) {
                    level = position < 0.34f ? 700 : (position < 0.67f ? 250 : -100);
                } else if ("voice".equals(preset)) {
                    level = position < 0.20f ? -250 : (position < 0.80f ? 450 : -150);
                } else if ("balanced".equals(preset)) {
                    level = 150;
                }
                level = Math.max(range[0], Math.min(range[1], level));
                equalizer.setBandLevel(band, (short) level);
            }
            equalizer.setEnabled(true);
            publishPlayerUpdate(playerStatusLabel());
        } catch (RuntimeException ignored) {
            if (equalizer != null) {
                try { equalizer.release(); } catch (RuntimeException ignoredAgain) { }
                equalizer = null;
            }
            equalizerPreset = "off";
            publishPlayerUpdate("Ecualizador no compatible con este dispositivo");
        }
    }

    private void scheduleReconnect() {
        if (userPaused || currentUrl == null) return;
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            publishPlayerUpdate("No se pudo reconectar. Pulsa reproducir para volver a intentarlo.");
            return;
        }
        long baseDelaySeconds = Math.min(30L, 2L << reconnectAttempts);
        reconnectAttempts++;
        long delaySeconds = baseDelaySeconds + ThreadLocalRandom.current().nextLong(Math.max(1L, baseDelaySeconds / 5L + 1L));
        publishPlayerUpdate("Reconectando (" + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")…");
        reconnectHandler.removeCallbacksAndMessages(null);
        reconnectHandler.postDelayed(() -> {
            if (player == null || currentUrl == null) return;
            player.prepare();
            player.play();
        }, delaySeconds * 1000L);
    }

    private boolean isAllowedStream(String value) {
        return StreamUrlValidator.isAllowedHttps(value);
    }

    @Override public void onDestroy() {
        reconnectHandler.removeCallbacksAndMessages(null);
        sleepHandler.removeCallbacks(sleepStopTask);
        if (equalizer != null) {
            try { equalizer.release(); } catch (RuntimeException ignored) { }
            equalizer = null;
        }
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); }
            catch (RuntimeException ignored) { }
            networkCallback = null;
        }
        currentUrl = null;
        userPaused = false;
        if (player != null) {
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}
