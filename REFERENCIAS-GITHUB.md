# Investigación de repositorios públicos para Radio Nova

**Fecha de revisión:** 2026-11-13 (segunda revisión)  
**Alcance:** búsqueda temática en repositorios públicos de GitHub. No existe una forma práctica de garantizar que se hayan inspeccionado literalmente todos los repositorios públicos; esta selección recoge proyectos relevantes encontrados con consultas por FM Android, SDR USB OTG, radio online, Media3 y listas de emisoras cubanas.

## Referencias prioritarias

| Proyecto | Qué aporta | Licencia/condición | Decisión |
|---|---|---|---|
| [Nordis FM Radio](https://github.com/NordisApps/nordis-fm-radio) | Acceso a sintonizadores FM internos, búsqueda de frecuencias, RDS, favoritos y salida por altavoz/auriculares. Declara pruebas en Samsung Galaxy A10s y M30s. | GPL-3.0. | Referencia técnica; no copiar código sin asumir obligaciones GPL. Su compatibilidad no se extrapola a otros teléfonos. |
| [NoNameRadio](https://github.com/eosphor/NoNameRadio) | Radio Browser, Media3/ExoPlayer, HLS, MediaSession, notificaciones, búsqueda, favoritos, historial, resiliencia de red y arquitectura por capas. | GPL-3.0. | Referencia arquitectónica y funcional; no incorporar código fuente GPL al proyecto actual sin una decisión explícita de licencia. |
| [STATIC Internet Radio](https://github.com/c77Xa7/static_internet_radio) | Radio Browser, búsqueda por nombre/género, URLs manuales, colecciones locales, etiquetas, Media3, metadatos ICY, temporizador y respaldo/restauración. | MIT. | Referencia útil para diseño de biblioteca personal y UX; revisar cada archivo antes de reutilizarlo. |
| [Android Media Player](https://github.com/pgerasymchuk/android-media-player) | Ejemplo didáctico de Media3, HLS, MediaSessionService, reproducción en segundo plano, notificación y pantalla bloqueada. | No confirmada en esta revisión. | Referencia de arquitectura; no copiar archivos hasta verificar licencia. |
| [9GRadio](https://github.com/rkarikari/9GRadio) | Receptores RTL-SDR por USB OTG, identificación automática de dongles, múltiples demoduladores, escáner, memoria de frecuencias, grabación IQ/audio, RDS y espectro. | GPL-3.0; incorpora dependencias con licencias propias. | Referencia de capacidades SDR y pruebas de hardware. No copiar código en el módulo Java actual sin decisión de relicenciamiento y cumplimiento GPL. |
| [SIR Android](https://github.com/cascadiacollections/sir-android) | Reproductor Shoutcast, separación modular, directorio, persistencia, favoritos, temporizador, ecualizador, reconexión progresiva y superficies Android Auto/tiles/widgets. | MIT según el repositorio. | Referencia funcional; no se copió código. |
| [Swift Radio Android](https://github.com/fethica/Swift-Radio-Android) | Media3, HLS, catálogo JSON local/remoto, búsqueda, metadatos, artwork y Android Auto. | MIT. | Referencia para un catálogo curado y futuro soporte de Android Auto; no se copió código. |
| [radioMii Android](https://github.com/haecksenwerk/radioMii-Android) | Navegación Material 3 sobre Radio Browser, favoritos y distribución directa de APK. | MIT. | Referencia de UX y publicación; no se copió código. |
| [Freqcast](https://github.com/z0rats/freqcast) | Descubrimiento de streams desde páginas web, verificación de URLs, reconexión, metadatos ICY, temporizador, Android Auto e importación/exportación M3U/PLS/OPML/JSON. | GPL-3.0. | Referencia de funciones; no copiar código en el proyecto actual sin adoptar y cumplir GPL-3.0. |
| [RavenSDR](https://github.com/JamesBurnettUSA/RavenSDR) | RTL-SDR por USB OTG, espectro waterfall, escaneo, demodulación AM/NFM, squelch y bookmarks. | GPL-3.0; librtlsdr GPL-2.0, libusb LGPL-2.1, KissFFT BSD-3-Clause. | Referencia técnica para un módulo SDR separado; no se copió código ni firmware. |
| [OpenSDR Radio Host](https://github.com/rafal-dot/opensdr-app/) | Receptor RTL-SDR Android con FM/AM, NFM/WFM/SSB, espectro y waterfall; separa host/núcleo de un contrato de integración. | Host y core GPL-2.0-or-later; contrato compartido Radio API MIT; dependencias con licencias propias. | Referencia para una futura arquitectura de drivers intercambiables; no se integra el núcleo GPL en esta etapa. |
| [Radio playlists](https://github.com/AlonDrilich/radio-playlists) | Listas M3U por país y género, con una lista para Cuba; flujo de actualización automática. | Datos/listas CC0 1.0; scripts MIT. Los nombres, logos y streams siguen perteneciendo a sus titulares. | Fuente auxiliar de descubrimiento. No tratar los enlaces como permanentes ni como autorización sobre el contenido. |

## Datos cubanos encontrados

El repositorio radio-playlists incluye countries/cu.m3u, una lista de emisoras identificadas como Cuba. Entre las entradas visibles aparecen Radio Reloj, Radio Rebelde, Radio Progreso, Radio Cadena Agramonte, Radio Caribe y otras.

La lista se considera **fuente de candidatos**, no directorio oficial ni garantía de funcionamiento. Cada enlace debe probarse en el momento de incorporarlo, guardar fecha/resultado de comprobación y permitir reportar enlaces caídos. No se deben presentar estaciones online como frecuencias terrestres verificadas.

## API recomendada para el directorio online

[Radio Browser API](https://docs.radio-browser.info/) permite buscar estaciones por país, nombre, codec, idioma y etiquetas, y devuelve enlaces de reproducción y metadatos. La documentación pide enviar un User-Agent descriptivo y recomienda descubrir los servidores disponibles en lugar de depender permanentemente de un único nodo. El servicio es comunitario y no garantiza disponibilidad continua.

La aplicación implementa búsqueda internacional con país opcional, descubrimiento de mirrors mediante `/json/servers`, nodos alternativos, filtros por nombre/género/idioma/códec/país y caché local separada por consulta. La documentación de Radio Browser indica que el endpoint de mirrors se usa como alternativa cuando el cliente no puede realizar la consulta DNS necesaria. Pendiente: sincronizar más búsquedas cacheadas, reconexión de streams y contabilización de clicks con una política de privacidad transparente.

## Mejoras implementadas tras la segunda revisión

- Descubrimiento dinámico de mirrors y respaldo con servidores conocidos.
- Búsqueda filtrada por nombre, género, idioma o códec.
- Caché persistente de la última búsqueda (hasta 50 resultados HTTPS) para consultar el catálogo sin conexión.
- Metadatos de tags, idioma, favicon y página web añadidos al modelo.
- Validación de URLs HTTPS compartida por interfaz y servicio; rechaza credenciales, espacios, direcciones malformadas y URLs demasiado largas.
- Pruebas unitarias JUnit para los casos principales del validador, incorporadas al workflow CI.

## Decisiones de licencias

- No se copió código fuente de 9GRadio, NoNameRadio, Nordis FM ni OpenSDR en la base actual; se usaron como referencias de diseño y compatibilidad.
- SIR Android se mantiene como referencia MIT; no se importaron sus archivos fuente.
- El streaming se implementa usando AndroidX Media3 como dependencia publicada, no copiando archivos de los repositorios investigados.
- La reutilización de datos CC0 de Radio Browser no concede derechos sobre nombres comerciales, logotipos, marcas o emisiones.
- Antes de integrar código de terceros, comprobar licencia del repositorio, licencias de dependencias, avisos de copyright y obligaciones de distribución.
- Mantener un inventario de dependencias y avisos de terceros en cada versión publicada.

## Hallazgos funcionales añadidos en esta revisión

- Los proyectos MIT muestran patrones reutilizables conceptualmente para catálogo curado, favoritos/recientes, Android Auto, temporizador, ecualizador y widgets. No se han incorporado archivos fuente de esos repositorios.
- Freqcast demuestra una función valiosa de descubrimiento de streams desde la página web de una emisora, además de importación/exportación de listas. Su código es GPL-3.0 y se mantiene solo como referencia hasta tomar una decisión de licencia.
- RavenSDR documenta soporte para familias RTL2832U, escaneo y demodulación AM/NFM; su implementación depende de librerías con licencias copyleft y exige una decisión de distribución antes de integrarla.

## Próximas investigaciones

1. Probar enlaces de la lista cubana y comparar con resultados vivos de Radio Browser.
2. Buscar repositorios Java/Kotlin con controladores FM específicos de fabricantes.
3. Identificar perfiles USB OTG y chipsets SDR con documentación y licencias compatibles.
4. Revisar soporte de HLS, AAC, MP3, OGG/Opus y streams ICY.
5. Probar reproducción en segundo plano, recuperación tras pérdida de red y ahorro de batería.


## Referencias adicionales para catálogo internacional e idiomas — 2026-11-13

- [RadioScout](https://github.com/double77x/RadioScout) / [ficha F-Droid](https://f-droid.org/en/packages/io.github.double77x.radioscout/): referencia de catálogo mundial, filtros por país/idioma/género/bitrate, historial y estadísticas locales. La ficha indica que funciona sin cuenta y no ofrece sincronización remota de favoritos.
- [AMARadio](https://github.com/ounben/AMARadio) / [sitio oficial](https://amaradio.org/): referencia de directorio mundial con base local, sincronización opcional del catálogo, fallback de mirrors, filtros por país/idioma/tags, Android Auto y accesibilidad multilingüe. Su proyecto declara GPLv3; se usa solo como referencia y no se copia código.
- [Open Open Radio](https://github.com/charlyghislain/openopenradio): referencia MIT para catálogo global por país/idioma/género, favoritos, reproducción en segundo plano y Android Auto. No se copió código.
- [Radio Browser API](https://docs.radio-browser.info/): confirma endpoints y parámetros para filtrar por país, idioma, etiquetas y códec, además de la lista dinámica de servidores. La API es comunitaria y puede tener interrupciones.

### Decisión para Radio Nova 0.7.0

Se implementa consulta mundial con país opcional y caché local por país/filtro/consulta. El refresco actual recupera hasta 50 resultados por consulta; no descarga el directorio completo y no sincroniza favoritos entre dispositivos. La sincronización remota del catálogo y el respaldo cloud quedan como mejoras futuras, sujetas a diseño de privacidad y consumo de datos. Se añadió un selector persistente Español/English con español por defecto; quedan pendientes textos secundarios y notificaciones para completar la traducción.

## Segunda ampliación de investigación — 2026-11-13

| Repositorio | Funciones observadas | Licencia indicada por el repositorio | Aplicación a Radio Nova |
|---|---|---|---|
| [STATIC Internet Radio](https://github.com/c77Xa7/static_internet_radio) | Media3, Room, metadatos ICY, temporizador, Android Auto, Chromecast opcional y backup/restore local. | MIT (ver LICENSE del proyecto). | Referencia para caché multi-búsqueda y temporizador; no se copió código. |
| [Twinsen Radio](https://github.com/szczepl/twinsen-radio) | Android Auto con árbol de emisoras, Radio Browser, M3U, variantes de stream, recuperación silenciosa y selección de calidad. | GPL-3.0. | Referencia para futuro Android Auto y variantes de calidad; sin código copiado por obligaciones GPL. |
| [RadioTime](https://github.com/mattgdot/RadioTime) | Media3, Room, WorkManager, favoritos, sincronización en segundo plano, temporizador y Android Auto. | GPL-3.0. | Inspiración para futura sincronización local/cloud; sin código copiado. |
| [RadioUpnp](https://github.com/watea/RadioUpnp) | UPnP/DLNA, Google Cast, temporizador, alarmas, historial y exportación JSON/CSV. | MIT. | Referencia para posible módulo de transmisión a dispositivos externos; no se incorpora código ni dependencia. |
| [StreamPlayer / live-player](https://github.com/codejq/live-player) | Recuperación al volver la red, buffer adaptativo, servicio persistente y controles de auriculares. | README menciona MIT si existe LICENSE; verificar archivo LICENSE exacto antes de reutilizar. | Referencia de resiliencia; no se copia código ni se añaden watchdogs agresivos por riesgo de batería. |
| [DVB-T & FM Radio Android](https://github.com/pantgr/dvb-fm-android) | RTL2832U, FM SDR, RDS, escaneo y soporte OTG. | GPL-2.0. | Referencia de hardware; no se integra su driver GPL ni sus dependencias. Requiere dongle compatible. |
| [GNU Radio Android FM](https://github.com/bastibl/android-fm) | Receptor FM con RTL-SDR y HackRF mediante GNU Radio. | Revisar licencia y dependencias antes de cualquier reutilización. | Referencia de arquitectura SDR; no se copia código. |
| [InternetRadio](https://github.com/armanmaurya/InternetRadio) | Catálogo mundial, filtros por país/idioma/etiquetas, Android TV y Android Auto. | Ver LICENSE del repositorio antes de reutilizar. | Referencia para filtros y soporte de pantallas grandes. |

### Mejoras aplicadas en 0.8.0

1. Temporizador de apagado propio con presets 15/30/60/90 minutos y cancelación. Se gestiona en el servicio Media3, no mediante un temporizador de la Activity.
2. Caché offline multi-búsqueda: hasta 12 consultas distintas, hasta 50 emisoras por consulta, expulsión de las más antiguas y validación HTTPS al recuperar.
3. Recuperación al volver la conectividad: callback de ConnectivityManager propio, respetando pausa manual y backoff limitado; inspirado en patrones de StreamPlayer, sin copiar su código.
4. El botón de catálogo se describe como actualización de resultados, no como descarga completa del directorio mundial.

### Pendientes priorizados a partir de esta revisión

- Estado de reproducción y metadatos ICY sincronizados en la pantalla principal.
- Android Auto mediante MediaLibrarySession y árbol navegable de favoritos/recientes/géneros; requiere declarar y probar la categoría de aplicación de audio y el flujo de seguridad.
- Mejorar el diagnóstico de red y la recuperación de streams con pruebas de cambios Wi-Fi/datos, manteniendo límite de reintentos y sin watchdogs que despierten el teléfono indefinidamente.
- Backup/restore completo de favoritos y emisoras personalizadas en JSON, manteniendo M3U como formato interoperable.
- Chromecast/UPnP como módulos opcionales, no dependencias obligatorias.
- Driver RTL-SDR separado y legalmente compatible; la recepción FM/AM terrestre no se resuelve con streaming online.

No existe una búsqueda que garantice inspeccionar literalmente todos los repositorios públicos de GitHub. Esta revisión es temática y selectiva; se documentan repositorios relevantes, licencias declaradas y decisiones de no reutilización cuando no hay certeza suficiente.

## Tercera ampliación de investigación — 2026-11-13

Se realizaron búsquedas temáticas adicionales en GitHub para radio por Internet Android, reproductores Kotlin/Media3, Android Auto, ecualización, catálogo Radio Browser y receptores FM/SDR. Se identificaron y contrastaron metadatos de repositorios adicionales. La búsqueda no puede garantizar una enumeración literal de todos los repositorios públicos ni todos sus forks.

| Repositorio | Enfoque / ideas para evaluar | Licencia declarada en GitHub | Decisión para Radio Nova |
|---|---|---|---|
| [OmniBeat](https://github.com/Vikindor/omnibeat) | Reproductor Android moderno para emisoras de Internet; revisar catálogo y experiencia de reproducción. | AGPL-3.0 | Referencia funcional; no copiar código sin asumir obligaciones AGPL. |
| [NoNameRadio](https://github.com/eosphor/NoNameRadio) | Media3/ExoPlayer, HLS, MediaSession y resiliencia de red. | GPL-3.0 | Referencia de arquitectura; no se copia código. |
| [Arcanos Radio Kotlin](https://github.com/fibelatti/arcanos-radio-kotlin) | Proyecto didáctico de radio en Kotlin, Rx y arquitectura MVP. | MIT | Referencia para patrones de arquitectura; no se copia código. |
| [Nordis FM Radio](https://github.com/NordisApps/nordis-fm-radio) | Acceso al sintonizador FM real del teléfono y controles específicos de hardware. | GPL-3.0 | Investigar compatibilidad por fabricante; no integrar su código en el módulo actual. |
| [AndroidRadio](https://github.com/masselstine/AndroidRadio) | Modelo cliente/servidor para explorar y seleccionar streams. | MIT | Posible referencia para catálogo administrado; revisar seguridad y estado antes de adoptar. |
| [NeriPlayer](https://github.com/cwuom/NeriPlayer) | Reproductor Android con streaming desde varias fuentes, control local, letras y sincronización autohospedada. | GPL-3.0 | Referencia para sincronización y biblioteca; no copiar código GPL. |
| [ExoBoostPlayer](https://github.com/abbasiandev/ExoBoostPlayer) | Manejo de errores, recuperación automática y cambio adaptativo de calidad en reproductores ExoPlayer. | Apache-2.0 | Investigar únicamente los patrones de recuperación aplicables a audio; no añadir funciones de análisis de vídeo innecesarias. |
| [CassetteCat](https://github.com/samyyy2311/CassetteCat) | Radio por Internet junto a archivos locales, servidores Jellyfin/Navidrome/Subsonic, ecualizador, letras, Android Auto y registro de escucha. | GPL-3.0 | Referencia para biblioteca unificada y estadísticas locales; no copiar código. |
| [OpenSDR Radio Host](https://github.com/rafal-dot/opensdr-app) | RTL-SDR con AM/FM/SSB, espectro, waterfall y servicio IPC local. | NOASSERTION en la API consultada | No reutilizar código hasta resolver licencia y licencias de dependencias. |
| [OnAir SDR](https://github.com/adswill/OnAir) | Receptor SDR multiplataforma, FM/RDS y múltiples familias de hardware SDR. | GPL-3.0 | Referencia para módulo SDR separado; no copiar código sin plan de cumplimiento GPL. |
| [VibeSDR](https://github.com/9A4AM/VibeSDR) | Backends SDR remotos y recepción USB RTL-SDR; motor DSP propio. | GPL-3.0 | Referencia de integración multi-backend; no copiar código. |
| [Android Audio Stream Player](https://github.com/it-drafter/android-audio-stream-player) | Reproducción Android de radio por Internet y podcasts de audio. | MIT | Revisar manejo de streams y controles multimedia; no se copia código. |
| [Internet Radio Player Android](https://github.com/murano500k/Internet-Radio-Player-Android) | Cliente Android de radio online. | No confirmada en la ficha consultada | Solo referencia conceptual; no reutilizar código hasta localizar licencia. |
| [Intent Radio](https://github.com/smblott-github/intent_radio) | Reproductor de radio online orientado a intents de Android. | MIT | Evaluar automatización e integración con otras apps. |
| [Online Radio for TV](https://github.com/Jayant-Gade/Online-Radio-For-TV) | Interfaz de radio orientada a Android TV. | Pendiente de verificar | Referencia para futuro diseño de pantalla grande; no copiar código. |
| [WPRK MultiPlatform](https://github.com/MwaiBanda/WPRK-MultiPlatform) | Proyecto de radio multiplataforma. | Pendiente de verificar | Investigar modelo multiplataforma; no copiar código. |
| [RadioScout](https://github.com/double77x/RadioScout) | Catálogo internacional, filtros y estadísticas/historial local. | MIT | Referencia para estadísticas y exploración mundial. |
| [AMARadio](https://github.com/ounben/AMARadio) | Directorio mundial, sincronización de catálogo, filtros y Android Auto. | GPL-3.0 | Referencia funcional; sin código copiado. |

### Ideas que se repiten en los proyectos investigados

1. Android Auto con navegación por países, géneros, favoritos y recientes.
2. Metadatos ICY y carátulas de la emisión.
3. Ecualizador y normalización de volumen.
4. Catálogo local con sincronización controlada y fallback de mirrors.
5. Importación/exportación JSON además de M3U.
6. Descubrimiento de URLs de stream desde páginas web de emisoras.
7. Chromecast y DLNA/UPnP opcionales.
8. Estadísticas locales, historial y widgets.
9. Receptores FM internos por fabricante y módulo SDR USB independiente.
10. Arquitectura de fuentes intercambiables y recuperación de red limitada.

### Selección de implementación

La prioridad es conservar Radio Nova ligero y Java/Media3, implementando primero con código propio las funciones compatibles con la arquitectura actual. Las funciones que requieren servicios o hardware externos (Cast, DLNA, Android Auto, FM/SDR) deben ser módulos opcionales y no bloquear la radio básica. Las licencias GPL/AGPL y las dependencias copyleft se mantienen como referencias, no como código fuente incorporado.

## Decisión de cuenta NOVA — versión 0.9.0

El modo Internet de Radio Nova exige una cuenta registrada en Nova Apps. La autenticación usa el mismo proyecto Supabase Auth y no crea una base de usuarios paralela. El registro se realiza en Nova Apps; Radio Nova inicia sesión y guarda tokens cifrados localmente. No se comparte el token con Radio Browser ni con servidores de emisoras. La radio terrestre y los datos locales continúan sin requerir cuenta.


## Segunda ampliación de investigación — 2026-11-13

Se revisaron README y licencias de más proyectos Android públicos. Se amplía el conjunto de referencias; no se copió código ni se añadió ninguna dependencia por esta investigación.

| Proyecto | Ideas aprovechables | Licencia observada | Decisión para Radio Nova |
|---|---|---|---|
| [Aerial](https://github.com/shapeshed/aerial) | Colecciones editoriales por ambiente/actividad, reproducción sencilla y controles del sistema. | Apache-2.0 | Catálogo curado por estados de ánimo, sin convertirlo en requisito para escuchar. |
| [RadioDroid](https://github.com/segler-alex/RadioDroid) | Directorio Radio Browser, navegación por metadatos y descubrimiento internacional. | GPL-3.0 indicada por su README | Referencia funcional; no copiar código sin aceptar copyleft. |
| [Freqcast](https://github.com/z0rats/freqcast) | Añadir emisora pegando la web oficial, detectar streams, ICY, timeshift, alarmas y exportación OPML/PLS. | GPL-3.0 | Priorizar buscador de stream por página oficial; timeshift como opción posterior y con límites. |
| [PureCarRadio](https://github.com/antoxa78/PureCarRadio) | Interfaz para coche, búsqueda por voz, metadatos, bitrate/codec y visualizador de audio. | MIT | Referencia para diseño de coche y datos técnicos; visualizador solo si no afecta rendimiento. |
| [Onda](https://github.com/basiliskus/onda) | Leer listas M3U desde una carpeta elegida, conservar grupos y sincronización externa mediante archivos. | MIT | Considerar una biblioteca por carpetas y playlists externas; no reemplazar el almacenamiento interno actual. |
| [Syok Player](https://github.com/AmirinNS/syok-player) | Arquitectura con un único servicio de reproducción, cola persistente, interfaz automotriz y cargas de carátulas acotadas. | MIT | Revisar arquitectura de servicio y persistencia de cola antes de ampliar Android Auto. |
| [Fogwave](https://github.com/kalarina-bit/Fogwave) | Estaciones similares por género, ecualizador con presets, loudness y reconexión automática. | GPL-3.0 | Añadir recomendaciones por género y evaluar audio processing sin incorporar código GPL. |
| [STATIC](https://github.com/c77Xa7/static_internet_radio) | Etiquetas personalizadas, mapa, niveles de popularidad, horarios en vivo, auto-gain, backup ZIP y Android Auto. | MIT | Etiquetas privadas, mapa opcional y auto-gain configurable son ideas de alto valor. |
| [M3U Radio Player Android](https://github.com/Barippi/M3URadioPlayerAndroid) | ICY/Vorbis metadata, manejo de llamadas/auriculares y proxy local para casos de compatibilidad TLS/decodificación. | MIT para app; BASS tiene condiciones propias no comerciales en el README | Solo referencia de casos extremos; no adoptar BASS ni el proxy sin auditoría de seguridad y licencia. |
| [Jimeikin](https://github.com/wanderwildwood/jimeikin) | Biblioteca híbrida local/servidor, playlists M3U y preferencia por copias offline. | GPL-3.0 | Inspiración para integrar bibliotecas locales de forma clara; no es un reproductor de radio dedicado. |

### Mejoras adicionales priorizadas

1. **Añadir emisora desde su web:** aceptar URL del sitio oficial, buscar enlaces de audio en HTML y validar el stream antes de guardarlo; mostrar al usuario qué URL se detectó y pedir confirmación. Ejecutar solo tras iniciar sesión NOVA, limitar dominios/redirecciones y bloquear destinos privados/locales para evitar SSRF.
2. **Calidad de stream:** si una emisora ofrece varias URLs, guardar variantes por bitrate/formato y permitir selección automática/manual según conectividad y consumo de datos.
3. **Resiliencia de reproducción:** reintentos con espera incremental y aleatoria, límite de intentos, botón de reintentar y recuperación al volver la red; nunca bucles infinitos ni gasto excesivo de batería/datos.
4. **Carátula y metadatos:** separar nombre de emisora de canción actual; mostrar bitrate, codec, carátula y estado “sin metadatos” cuando la fuente no los proporcione.
5. **Favoritos inteligentes:** etiquetas propias, orden manual, emisoras similares por género y colecciones temáticas; mantener todo local salvo sincronización explícita de NOVA.
6. **Android Auto seguro:** navegación corta por Favoritos, Recientes, Países y Géneros; controles grandes, paginación y carátulas con tamaño limitado.
7. **Bibliotecas externas:** permitir seleccionar una carpeta M3U mediante Storage Access Framework y volver a escanearla; nunca pedir acceso total al almacenamiento.
8. **Mapa opcional:** explorar emisoras por país/ciudad usando ubicación aproximada solo con permiso explícito; no solicitar ubicación para la búsqueda normal.
9. **Estadísticas privadas:** minutos escuchados, emisoras frecuentes y franjas horarias, calculadas localmente y con opción de borrar.

### Reglas de privacidad y cuenta

- Cualquier llamada a directorios online, resolución de páginas oficiales, sincronización y streaming debe pasar por el control de sesión NOVA antes de iniciarse.
- Nunca enviar tokens NOVA a Radio Browser, sitios de emisoras ni servicios de carátulas.
- La reproducción por URL directa también debe exigir sesión cuando se trata de modo Internet.
- Los favoritos, etiquetas, listas importadas, historial y estadísticas guardadas siguen disponibles offline.
- La ubicación es opcional; el mapa no debe ser condición para buscar o escuchar.
- No extraer ni retransmitir audio protegido; el detector solo descubre URLs públicas y no elude autenticación, DRM ni controles de acceso.
