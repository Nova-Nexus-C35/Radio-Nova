# Radio online — diseño y estado

## Requisito de cuenta NOVA — versión 0.9.0

- La radio por Internet requiere una cuenta registrada en Nova Apps y una sesión NOVA válida.
- Radio Nova utiliza el mismo proyecto Supabase Auth de Nova Apps; no crea un sistema de registro independiente.
- El registro se inicia desde la pantalla de acceso y abre https://cumba35.github.io/Nova-APPS/.
- El usuario inicia sesión en Radio Nova con el correo y la contraseña de su cuenta NOVA.
- Los tokens se cifran con AES-GCM mediante Android Keystore; se contempla renovación de refresh token y validación de identidad con `auth/v1/user`.
- La búsqueda de Radio Browser, reproducción online, reanudación, temporizador y sesión multimedia requieren una sesión local válida.
- La radio terrestre, favoritos, recientes e importación/exportación local M3U siguen sin requerir cuenta.
- Cerrar sesión detiene la reproducción y elimina las credenciales guardadas en el dispositivo.
- La puerta de acceso de cliente no sustituye la autorización de un servicio privado: Radio Browser y las URLs públicas de emisoras siguen siendo servicios de terceros. No se envía el token NOVA a Radio Browser ni a los servidores de streaming.

## Estado del streaming

- AndroidX Media3 1.11.1 con ExoPlayer y MediaSessionService.
- Búsqueda mundial mediante Radio Browser, país opcional, filtros por nombre/género/idioma/códec/país y descubrimiento dinámico de mirrors.
- Caché offline de hasta 12 consultas diferentes, máximo 50 emisoras por consulta.
- Reproducción HTTPS y HLS, validación de enlaces, timeouts y bloqueo de redirecciones entre protocolos.
- Reconexión limitada a cinco intentos con backoff y recuperación al volver la conectividad.
- Favoritos y recientes locales; importación/exportación M3U validada.
- Temporizador 15/30/60/90 minutos.
- Español principal e inglés secundario.

## Próximas mejoras priorizadas

1. Android Auto con árbol de navegación por países, favoritos y recientes.
2. Metadatos ICY: canción, artista, programa, carátula y nombre de estación cuando la fuente los ofrezca.
3. Estado de reproducción sincronizado con Media3: conectando, buffering, reproduciendo, pausado, error y reconectando.
4. Ecualizador y normalización de volumen opcionales, comprobando compatibilidad del dispositivo.
5. Backup/restore JSON de favoritos, recientes, categorías, configuración y emisoras personalizadas.
6. Detección de streams desde la página oficial de una emisora, con confirmación antes de guardar URL.
7. Chromecast/Google Cast y UPnP/DLNA como módulos opcionales.
8. Mapa mundial con selección de emisoras por país/ciudad, sin exigir ubicación precisa.
9. Widgets y accesos rápidos.
10. Estadísticas locales y alarmas.
11. Driver FM/AM/SDR separado, con matriz de dispositivos compatibles y revisión de licencias.

## Límites

- La búsqueda online y la reproducción requieren Internet y sesión NOVA.
- Los favoritos y la biblioteca guardada localmente se pueden consultar sin conexión, pero reproducir un stream siempre necesita red.
- Radio Browser es un directorio comunitario; no garantiza que una estación siga activa.
- La autenticación de la app no convierte los streams públicos en recursos privados ni transmite credenciales NOVA a emisoras.
- La recepción terrestre necesita un sintonizador integrado con API accesible o un receptor USB RF compatible; un adaptador USB de audio no equivale a un sintonizador.
- La integración de drivers de terceros requiere comprobar licencia, dependencias, avisos y compatibilidad.

## Historial técnico

- 0.8.0: temporizador de apagado, caché multi-búsqueda 12 × 50 y recuperación de conectividad con ConnectivityManager.
- 0.7.0: catálogo mundial, filtros por país y selector Español/English.
- 0.6.0: importación/exportación M3U con límites y validación HTTPS.
- 0.5.0: filtros, mirrors dinámicos, caché, reconexión limitada y validador HTTPS.
- 0.3.0: Media3 1.11.1, HLS, audio focus, auriculares y target SDK 35.


## Segunda ronda de mejoras — 2026-11-13

Nuevas referencias: Aerial (colecciones por actividad), RadioDroid (catálogo), Freqcast (detección de streams desde webs, ICY y timeshift), PureCarRadio (voz/Android Auto y bitrate), Onda (M3U desde carpeta SAF), Syok (servicio único y cola persistente), Fogwave (similares por género y ecualizador), STATIC (etiquetas, mapa, auto-gain y backup), M3U Radio Player (interrupciones y compatibilidad ICY) y Jimeikin (biblioteca local/servidor). Licencias y restricciones están anotadas en REFERENCIAS-GITHUB.md.

### Orden de trabajo recomendado

1. Mostrar estado real del stream y metadatos disponibles (ICY, canción, artista, bitrate y codec).
2. Reconexión robusta con backoff limitado y restauración de la última emisora tras recuperar conectividad.
3. Añadir emisora pegando su web oficial: detección de URL pública, validación segura y confirmación del usuario.
4. Variantes de calidad por emisora y selector automático/manual.
5. Favoritos con etiquetas privadas, colecciones y emisoras similares por género.
6. Android Auto con árbol de navegación simple y controles grandes.
7. Biblioteca M3U desde carpeta seleccionada con SAF.
8. Ecualizador/auto-gain opcionales, probando compatibilidad de AudioEffects y sin alterar el sonido por defecto.
9. Mapa y estadísticas locales opcionales.

Toda función que consulta Internet o reproduce streams debe requerir una sesión NOVA válida; los datos locales permanecen accesibles sin cuenta. No reutilizar código GPL/AGPL en la aplicación sin una decisión expresa sobre distribución y cumplimiento de licencias. No adoptar BASS para una distribución comercial sin revisar su licencia.
