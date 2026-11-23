# Radio Nova

**Tipo:** Aplicación Android  
**Estado:** Desarrollo inicial — streaming online incorporado en código; radio terrestre pendiente de hardware  
**Identificador:** radio-nova  
**Versión de trabajo:** 1.3.0

## Objetivo

Radio Nova tendrá dos fuentes separadas:
1. **Radio terrestre sin Internet:** receptor FM/AM integrado cuando el fabricante exponga una API compatible, o receptor USB OTG cuyo chipset/protocolo esté soportado.
2. **Radio online:** búsqueda de emisoras por Internet, reproducción de streams mediante Media3, entrada manual de URLs y reproducción en segundo plano.

La radio terrestre debe funcionar sin datos móviles ni Wi-Fi, si el hardware lo permite. La radio online requiere conexión. La aplicación no confundirá un stream de Internet con una frecuencia recibida por antena.

## Estado de funciones

- Base Android Java 17 y Gradle: creada.
- Búsqueda mundial con Radio Browser por nombre, género, idioma, códec y país; campo de país vacío para consultar todos los países; descubrimiento dinámico de mirrors y nodos alternativos: incorporados en 0.7.0.
- Resultados guardados localmente para consulta offline: conserva hasta 12 búsquedas diferentes, cada una con hasta 50 emisoras, identificadas por país, filtro y consulta.
- Reproducción de URL HTTPS con Media3 1.11.1: código incorporado.
- Acceso obligatorio con cuenta registrada en Nova Apps para búsqueda mundial, reproducción online, reanudación y temporizador; usa el mismo proyecto Supabase Auth de Nova y no crea cuentas independientes.
- Inicio/cierre de sesión NOVA en Radio Nova; access/refresh tokens cifrados con AES-GCM mediante Android Keystore y renovación/verificación de sesión con Nova Auth.
- La radio terrestre, favoritos locales, recientes y gestión local de listas siguen disponibles sin iniciar sesión.
- Módulo HLS para streams .m3u8: incorporado.
- Gestión de foco de audio, auriculares y wake mode para audio en segundo plano: incorporada.
- Recuperación de streams fallidos al volver una red con Internet validado, con backoff exponencial limitado y variación aleatoria para evitar reintentos sincronizados: mejorada en 1.2.0.
- Solicitud de permiso de notificaciones en Android 13+: incorporada.
- Target SDK 35 y servicio de sesión multimedia accesible a controladores del sistema: configurados.
- Enlaces HTTP sin cifrar: bloqueados deliberadamente por seguridad.
- Servicio multimedia para segundo plano: MediaLibraryService de Media3 con árbol de favoritos y últimas emisoras, búsqueda local y metadatos de formato/calidad para navegadores multimedia compatibles (preparación de Android Auto) incorporado en 1.3.0.
- Corrección del error de doble inserción de campos en la pantalla.
- Permiso WAKE_LOCK y CI con Android SDK 35: incorporados.
- Receptor FM/AM integrado: pendiente de API y pruebas por dispositivo.
- Receptor USB OTG: detección básica; control del sintonizador pendiente.
- Directorio terrestre de frecuencias verificadas: pendiente.
- Favoritos y últimas emisoras guardados localmente con SharedPreferences: incorporados en 0.4.0.
- Controles básicos de pausar, reanudar y detener: incorporados; estado del reproductor sincronizado con la pantalla y consulta al volver a la app: incorporado en 1.2.0.
- Metadatos ICY de título/canción y actualización de los controles multimedia del sistema cuando la emisora los transmite: incorporados en 1.2.0.
- Ecualizador con presets desactivado, equilibrado, graves y voz: incorporado en 1.2.0 cuando el dispositivo admite el efecto Android; si no es compatible, la app informa de ello.
- Preferencia de calidad de audio (automática, ahorro hasta 64 kbps, equilibrada hasta 128 kbps y alta): incorporada en 1.3.0 mediante límites de selección de pistas Media3; solo cambia la calidad si el stream ofrece variantes/pistas alternativas. En streams MP3/AAC únicos no puede inventar otra calidad.
- Importación M3U/M3U8 de hasta 500 emisoras y exportación de favoritos: incorporadas; solo se importan enlaces HTTPS válidos.
- Caché offline multi-búsqueda: hasta 12 consultas diferentes y 50 resultados por consulta, con eliminación de las más antiguas.
- Descubrimiento dinámico de nodos Radio Browser con nodos de respaldo: incorporado.
- Reconexión automática limitada a cinco intentos con espera progresiva (2–30 s), cancelada si el usuario pausa manualmente: incorporada en 0.5.0.
- Importación y exportación de listas M3U desde el selector de archivos de Android: incorporadas en 0.6.0. Solo se importan URLs HTTPS válidas; se eliminan duplicados y se limita el archivo a 1 MiB, 500 emisoras y 5000 líneas.
- Colecciones M3U mediante `#EXTGRP`: importación y exportación de etiquetas de emisora incorporadas en 1.1.0.
- Temporizador de apagado de 15, 30, 60 y 90 minutos, con cancelación: incorporado en 0.8.0.
- Orden de resultados por popularidad, nombre o bitrate: incorporado en 1.1.0.
- Guardado manual de emisoras HTTPS sin iniciar la reproducción: incorporado en 1.1.0.
- Colecciones/etiquetas locales personalizadas por emisora, con edición y eliminación: incorporadas en 1.1.0.
- Compartir enlaces de emisoras y abrir la página oficial HTTPS cuando el directorio la proporciona: incorporado en 1.1.0; abrir páginas web mantiene el requisito de sesión NOVA.
- Copias JSON versión 2 preservan las etiquetas locales y siguen aceptando copias versión 1; no exportan tokens ni credenciales.
- Navegación multimedia externa: raíz Radio Nova con carpetas Favoritos y Últimas emisoras, paginación y búsqueda local; la conexión a la biblioteca sigue requiriendo sesión NOVA.

## Compatibilidad: límites que la aplicación debe respetar

### Receptor integrado

- Detectar el hardware y la API real disponible; no inferir compatibilidad solo por el modelo comercial.
- Muchos teléfonos Android no incluyen receptor FM o no permiten que aplicaciones de terceros lo controlen.
- Algunos receptores necesitan auriculares con cable como antena.
- FM, AM y onda corta son capacidades distintas: solo se ofrecerán las bandas que soporte el chip y el fabricante.
- Android no ofrece una API pública universal para controlar todos los chips FM/AM. La integración puede requerir API del fabricante, servicio del sistema o una biblioteca/driver autorizado para el dispositivo.

### Receptor externo USB OTG

- Admitir receptores USB de radiofrecuencia identificados y probados, conectados mediante USB Host/OTG.
- Implementar una capa de control por perfiles de dispositivo (fabricante, VID/PID, chipset, protocolo, bandas y capacidades).
- Solicitar permiso USB al usuario al conectar el receptor y permitir desconectarlo de forma segura.
- Verificar alimentación eléctrica, compatibilidad OTG, ancho de banda, antena y controladores.
- Un adaptador USB de audio no es necesariamente un sintonizador de radio. Solo se aceptarán dispositivos que realmente incluyan un sintonizador RF y cuyo protocolo esté documentado o implementado.
- No prometer compatibilidad universal con cualquier receptor USB. Los dispositivos sin perfil/controlador compatible deben mostrarse como no compatibles, sin simular recepción.

## Emisoras cubanas

- Directorio online actualizable y caché local.
- Organización por provincia, municipio/localidad, nombre, frecuencia y banda cuando los datos estén disponibles y verificados.
- Frecuencias terrestres verificadas antes de incluirlas como datos oficiales; registrar fuente y fecha.
- Permitir agregar frecuencias manualmente y marcar favoritos.
- No afirmar que todas las emisoras se reciben en cualquier localidad: la recepción depende de cobertura, potencia, propagación, antena, banda y ubicación.
- Las listas de streams online son fuentes de candidatos; cada enlace debe comprobarse y puede dejar de funcionar.

## Funciones previstas

1. Selector de fuente: receptor integrado / receptor USB OTG / streaming online.
2. Detección y diagnóstico de compatibilidad de hardware.
3. Escaneo automático y sintonización manual cuando el hardware lo permita.
4. FM; AM/onda corta solo cuando el hardware lo permita.
5. Búsqueda online internacional por nombre, país, género/etiquetas, idioma y códec, con metadatos y nodos de directorio dinámicos.
6. Reproducción manual de URLs HTTPS y streams compatibles con Media3.
7. Favoritos y emisoras recientes persistentes en el teléfono, con importación/exportación M3U; notas de recepción pendientes.
8. Directorio online mundial con caché offline de hasta 12 búsquedas, 50 resultados por consulta.
9. Intensidad de señal y metadatos solo si la fuente los proporciona.
10. Salida por altavoz, auriculares o dispositivo Bluetooth cuando sea posible.
11. Controles básicos de reproducción, notificación y temporizador de apagado con presets/cancelación.
12. Modo avión de prueba para recepción terrestre.

## Arquitectura técnica

- RadioSource: interfaz común para fuentes.
- InternalRadioSource: adaptadores específicos para APIs/servicios de fabricantes compatibles.
- UsbRadioSource: enumeración USB Host, permisos y perfiles de chipset/protocolo.
- InternetRadioService: Media3 ExoPlayer + MediaLibraryService para reproducción online en segundo plano y navegación multimedia (Favoritos/Últimas emisoras); requiere sesión NOVA para el catálogo online.
- RadioBrowserClient: búsqueda asíncrona en el directorio Radio Browser.
- StationDirectory: base local con emisoras, frecuencias, localidad, banda, fuente y fecha de verificación.
- CompatibilityRegistry: matriz de teléfonos, chips y receptores USB probados.

La validación HTTPS rechaza HTTP, URLs malformadas, credenciales embebidas y direcciones excesivamente largas. Las pruebas unitarias se ejecutan en CI.

No se incluirán drivers propietarios ni firmware de terceros sin licencia y autorización. Se priorizarán protocolos documentados y bibliotecas con licencia compatible.

## Privacidad y permisos

- Solicitar permisos USB únicamente al conectar un receptor externo.
- No recopilar ubicación continuamente. La localidad se puede seleccionar manualmente.
- No enviar historial de escucha a servidores propios sin consentimiento.
- La recepción terrestre, favoritos, recientes e importación/exportación local no requieren cuenta ni conexión.
- Toda función de radio por Internet requiere una sesión válida de una cuenta registrada en Nova Apps. El registro se realiza en Nova Apps; Radio Nova solo inicia/cierra sesión.
- La búsqueda online transmite la consulta al servicio Radio Browser y requiere Internet.

## Documentación del proyecto

- REFERENCIAS-GITHUB.md: investigación de repositorios públicos y licencias.
- PLAN-STREAMING-ONLINE.md: arquitectura y tareas pendientes de radio online.
- THIRD-PARTY-NOTICES.md: dependencias y avisos de terceros.
- COMPATIBILIDAD-USB.md: matriz de hardware USB probado.
- ESTADO-DESARROLLO.md: estado real y próximos pasos.

## Criterios de aceptación

- La radio online reproduce streams reales, mantiene audio en segundo plano y muestra errores de conexión.
- La recepción terrestre demuestra recepción RF real sin Internet en hardware identificado.
- No declarar soporte para un modelo/chip/receptor que no haya sido probado.
- No marcar la aplicación como terminada o publicable hasta que existan código fuente, pruebas reproducibles, matriz de compatibilidad y compilación verificable.


## Actualización 0.7.0 — catálogo mundial e idiomas

Se amplió Radio Browser de búsqueda limitada a Cuba a búsqueda internacional. El usuario puede dejar el país vacío para consultar el catálogo mundial o escribir un país para limitar los resultados; el filtro también admite nombre, género/etiquetas, idioma, códec y país. Cada resultado muestra el país y el idioma cuando Radio Browser proporciona esos datos. Se añadió una acción para actualizar el catálogo/resultados mundiales; por ahora sincroniza hasta 50 resultados por consulta y conserva la caché local identificada por país, filtro y texto de búsqueda. No es una sincronización en la nube entre dispositivos ni descarga todo el directorio.

Se añadió selector persistente Español/English, con español como opción inicial y traducción de los rótulos y acciones principales. Quedan pendientes algunos mensajes secundarios y textos de notificación para completar la localización al 100 %. La búsqueda mundial requiere Internet; la última consulta guardada puede mostrarse sin conexión. Los favoritos continúan siendo locales y se pueden transferir con M3U.

- La búsqueda de país acepta también nombres habituales en español (por ejemplo, Estados Unidos, España, Reino Unido y Brasil) y los normaliza a los nombres del directorio.


## Actualización 0.8.0 — mejoras inspiradas en proyectos públicos

- Temporizador de apagado con opciones de 15/30/60/90 minutos y cancelación. El servicio detiene el stream, limpia la sesión y cancela tareas de reconexión al vencer.
- Caché offline multi-búsqueda: hasta 12 consultas distintas, con 50 emisoras cada una; se conservan las más recientes y se valida HTTPS al leer.
- Recuperación controlada cuando Android notifica que vuelve una red con Internet: incorporada; respeta pausa manual y los límites de reintento.
- El botón de catálogo se describe como actualización de resultados, no como descarga completa del directorio.
- Se revisaron SIR Android, STATIC Internet Radio, Twinsen Radio, RadioTime, RadioUpnp, StreamPlayer y proyectos RTL-SDR. Se aplicaron funciones propias compatibles; no se copió código externo.

El temporizador se ejecuta dentro del servicio de reproducción; Android podría terminarlo bajo condiciones extremas o al forzar la detención de la aplicación. La recepción FM/AM real sigue dependiendo de un sintonizador y driver compatibles.


## Actualización 0.9.0 — acceso con cuenta NOVA

Se añadió una puerta de autenticación para el modo Internet. Radio Nova utiliza el mismo proyecto Supabase Auth que Nova Apps; no mantiene un registro de usuarios separado. El usuario inicia sesión con el correo y la contraseña de su cuenta NOVA, o abre Nova Apps para registrarse. Los tokens se almacenan cifrados mediante AES-GCM en Android Keystore y se renuevan/verifican con Supabase Auth. La búsqueda de Radio Browser, reproducción por URL, reanudación, temporizador y acceso a la sesión multimedia requieren una sesión local vigente. Pausar/detener y cancelar un temporizador siguen disponibles para poder controlar una reproducción existente. La recepción terrestre, favoritos, recientes y archivos M3U locales continúan disponibles sin cuenta. La integración necesita compilación y pruebas reales; no se declara validada hasta que CI ejecute el build y se pruebe en Android.


## Versión 1.0.0 — copia de seguridad local

Se añade exportación e importación de una copia JSON desde el selector de archivos Android. La copia incluye favoritos y emisoras recientes; no contiene contraseñas, tokens, identificadores de sesión ni datos de cuenta NOVA. La restauración valida el formato y la versión, limita el archivo a 1 MiB y procesa únicamente URLs HTTPS válidas. La restauración combina elementos con los existentes y no elimina favoritos actuales. El respaldo y la restauración funcionan sin iniciar sesión y sin conexión. La compilación y las pruebas en dispositivo siguen pendientes de CI exitoso.


## Estado de verificación 1.3.0

La integración de biblioteca multimedia y la preparación de Android Auto están incorporadas en el código. El selector de calidad solo puede elegir variantes que ofrezca la fuente. Android Auto aún no se ha probado en vehículo. El run de CI 43371807995 no produjo logs de pasos ni APK; no se declara compilación verificada hasta obtener un run exitoso y probar la app en Android.
