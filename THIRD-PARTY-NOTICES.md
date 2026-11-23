# Avisos de terceros — Radio Nova

## Dependencias incorporadas

### AndroidX Media3 1.11.1
- Artefactos: androidx.media3:media3-exoplayer, androidx.media3:media3-exoplayer-hls, androidx.media3:media3-session, androidx.media3:media3-common.
- Uso: decodificación y reproducción de streams, sesiones multimedia, controles del sistema y servicio en segundo plano.
- Licencia: Apache License 2.0 (verificar y conservar los avisos de la versión exacta al distribuir).
- Proyecto: https://github.com/androidx/media

### JUnit 4.13.2 (solo pruebas)
- Artefacto: `junit:junit:4.13.2`.
- Uso: pruebas unitarias del validador HTTPS y parser M3U; no forma parte de la aplicación distribuida.
- Licencia: Eclipse Public License 1.0.
- Proyecto: https://github.com/junit-team/junit4

## Servicios y datos externos

### Radio Browser
- API: https://docs.radio-browser.info/
- Uso: búsqueda y metadatos de emisoras online.
- El servicio es comunitario y no ofrece garantía de disponibilidad.
- Los streams, nombres y logotipos pertenecen a sus respectivos titulares. La presencia en el directorio no equivale a una licencia para redistribuir contenido.

### Radio playlists / 72FM
- Repositorio: https://github.com/AlonDrilich/radio-playlists
- Licencia de scripts: MIT.
- Licencia de listas/datos generados: CC0 1.0, derivados del directorio Radio Browser.
- No se ha incorporado el archivo M3U al APK. Se utiliza como referencia para identificar candidatos de emisoras cubanas.

## Código de repositorios consultados

No se han copiado archivos fuente de repositorios GPL-3.0 en esta etapa. Las referencias se documentan en REFERENCIAS-GITHUB.md. Si en el futuro se incorpora código de un tercero, registrar aquí repositorio, autor, archivo, commit, licencia, modificaciones y avisos requeridos.


## Repositorios públicos revisados — sin código incorporado

- [SIR Android](https://github.com/cascadiacollections/sir-android): MIT; patrón de directorio modular, temporizador, reconexión y superficies Android.
- [Swift Radio Android](https://github.com/fethica/Swift-Radio-Android): MIT; Media3, HLS, catálogo y Android Auto.
- [radioMii Android](https://github.com/haecksenwerk/radioMii-Android): MIT; navegación Material 3 y Radio Browser.
- [Freqcast](https://github.com/z0rats/freqcast): GPL-3.0; descubrimiento de streams desde webs, importación/exportación y reconexión. Solo referencia; no se copió código.
- [RavenSDR](https://github.com/JamesBurnettUSA/RavenSDR): GPL-3.0. Declara uso de librtlsdr (GPL-2.0), libusb (LGPL-2.1) y KissFFT (BSD-3-Clause). Solo referencia; no se copió código.
- [OpenSDR Radio Host](https://github.com/rafal-dot/opensdr-app/): host/núcleo GPL-2.0-or-later y contrato Radio API MIT; solo referencia de arquitectura modular, no se copió código.

La consulta de estos repositorios no incorpora automáticamente sus dependencias ni concede derechos sobre emisiones, marcas, logotipos o firmware. Cualquier futura integración deberá registrar el commit exacto, licencias transitivas, avisos y obligaciones de distribución.


El parser M3U/M3U8 de Radio Nova es código propio; limita el número de líneas y emisoras y solo acepta URLs HTTPS validadas. No incorpora contenido ni código de los reproductores externos citados.

## Investigación adicional — no se ha incorporado código

- [STATIC Internet Radio](https://github.com/c77Xa7/static_internet_radio): MIT; consultado como referencia para temporizador, Media3, Android Auto y backup local.
- [Twinsen Radio](https://github.com/szczepl/twinsen-radio): GPL-3.0; solo referencia funcional, sin código copiado.
- [RadioTime](https://github.com/mattgdot/RadioTime): GPL-3.0; solo referencia de Room/WorkManager y sincronización.
- [RadioUpnp](https://github.com/watea/RadioUpnp): MIT; solo referencia de UPnP/DLNA, Cast y temporizador.
- [DVB-T & FM Radio Android](https://github.com/pantgr/dvb-fm-android): GPL-2.0; solo referencia de RTL-SDR/FM/RDS.
- [GNU Radio Android FM](https://github.com/bastibl/android-fm): referencia de RTL-SDR/HackRF; no se copió código.
- [StreamPlayer](https://github.com/codejq/live-player): referencia de reconexión y resiliencia; no se incorporó código ni dependencias y su licencia debe verificarse antes de cualquier reutilización.

Las funciones añadidas en Radio Nova 0.8.0 (temporizador y caché multi-búsqueda) son implementaciones propias, no copias de los repositorios anteriores.

## Autenticación NOVA — versión 0.9.0

Radio Nova utiliza los endpoints públicos de Supabase Auth del mismo proyecto configurado por Nova Apps para autenticar cuentas existentes. La clave publishable de cliente no es una service-role key. No se incorporó código fuente de Nova IA: la integración de cliente se implementó de forma independiente usando HTTP/JSON y Android Keystore. Los tokens de sesión se cifran localmente con AES-GCM; nunca se registran en logs ni se envían a Radio Browser o a los servidores de streaming.

## Investigación adicional de GitHub — 2026-11-13

Se evaluaron como referencias conceptuales OmniBeat, NoNameRadio, Arcanos Radio Kotlin, Nordis FM Radio, AndroidRadio, NeriPlayer, ExoBoostPlayer, CassetteCat, OpenSDR Radio Host, OnAir, VibeSDR, Android Audio Stream Player, Internet Radio Player Android, Intent Radio, Online Radio for TV, WPRK MultiPlatform, RadioScout y AMARadio. No se copiaron archivos fuente de esos proyectos ni se añadieron dependencias nuevas a partir de ellos. Licencias declaradas y condiciones pendientes se registran en `REFERENCIAS-GITHUB.md`; cualquier reutilización futura requiere revisión de la licencia del repositorio y de cada dependencia.


## Referencias ampliadas — segunda ronda 2026-11-13

- [Aerial](https://github.com/shapeshed/aerial): Apache-2.0; referencia de colecciones temáticas y reproductor sencillo.
- [RadioDroid](https://github.com/segler-alex/RadioDroid): GPL-3.0 indicada por README; referencia de Radio Browser.
- [Freqcast](https://github.com/z0rats/freqcast): GPL-3.0; referencia de descubrimiento de streams desde webs, ICY y timeshift. No se copió código.
- [PureCarRadio](https://github.com/antoxa78/PureCarRadio): MIT; referencia de Android Auto, búsqueda por voz, bitrate y visualizador.
- [Onda](https://github.com/basiliskus/onda): MIT; referencia de playlists M3U y carpetas seleccionadas.
- [Syok Player](https://github.com/AmirinNS/syok-player): MIT; referencia de servicio único, cola persistente y ergonomía automotriz.
- [Fogwave](https://github.com/kalarina-bit/Fogwave): GPL-3.0; referencia de emisoras similares y efectos de audio.
- [STATIC](https://github.com/c77Xa7/static_internet_radio): MIT; referencia de etiquetas, mapa, auto-gain y respaldo ZIP.
- [M3U Radio Player Android](https://github.com/Barippi/M3URadioPlayerAndroid): app MIT; BASS tiene condiciones de licencia separadas y limitaciones para uso comercial. No se añadió BASS.
- [Jimeikin](https://github.com/wanderwildwood/jimeikin): GPL-3.0; referencia de biblioteca local/servidor, no es radio dedicada.

No se copió código fuente de estos repositorios ni se añadió ninguna dependencia. Revisar licencia de cada dependencia transitiva antes de integrar cualquier biblioteca.
