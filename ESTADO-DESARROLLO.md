# Radio Nova — Estado de desarrollo

**Versión de trabajo:** 1.3.0 (versionCode 14)  
**Plataforma:** Android  
**Lenguaje:** Java 17  
**Identificador:** com.nova.radio

## Código incorporado

- Proyecto Android Gradle inicial.
- Puerta de acceso NOVA para funciones de Internet: misma autenticación Supabase Auth que Nova Apps, sin registro de usuarios paralelo.
- NovaLoginActivity: inicio de sesión con cuenta existente y acceso a Nova Apps para crear una cuenta.
- NovaAccountSession: tokens de acceso y renovación cifrados con AES-GCM en Android Keystore; renovación y comprobación remota de identidad.
- Búsqueda mundial, reproducción HTTPS, reanudación, temporizador y conexión de controladores multimedia sujetos a una sesión NOVA vigente; radio terrestre y datos locales sin cuenta.
- Cierre de sesión que detiene la reproducción y elimina las credenciales locales.
- Primera pantalla nativa con selector visual de recepción terrestre y radio online.
- Corregido un fallo de inicio: los campos de búsqueda y URL se insertaban dos veces en el contenedor y podían provocar el cierre de la app.
- Añadido permiso WAKE_LOCK para apoyar la reproducción de audio con la pantalla apagada.
- CI reforzado: instalación explícita de Android SDK 35, diagnóstico de Java/Gradle y generación del APK debug.
- Contrato RadioSource para separar las fuentes.
- Modelos Station (terrestre) y OnlineStation (streaming).
- Directorio SQLite local preparado para datos terrestres sin conexión.
- Cliente RadioBrowserClient para buscar emisoras de todo el mundo, filtrar por país, descubrir mirrors de Radio Browser y usar nodos alternativos de respaldo.
- Caché persistente de hasta 12 búsquedas distintas (máximo 50 emisoras cada una), segmentadas por país/filtro/consulta para mostrar resultados sin conexión.
- Metadatos adicionales de género, idioma, icono y página oficial cuando el directorio los proporciona.
- Validación HTTPS compartida y testeable, con rechazo de URLs con credenciales o espacios.
- Servicio InternetRadioService con AndroidX Media3 ExoPlayer y MediaLibraryService de Media3 para streaming, navegación multimedia y reproducción en segundo plano.
- Actualizadas las dependencias Media3 a 1.11.1 e incorporado el módulo HLS para streams .m3u8.
- Servicio ajustado para gestionar foco de audio, desconexión de auriculares y bloqueo de CPU durante reproducción en segundo plano.
- Permiso de notificaciones solicitado al iniciar reproducción en Android 13 o superior.
- Target SDK actualizado a 35 y servicio multimedia exportado para permitir controladores del sistema.
- Validación de streams limitada a HTTPS para mantener coherencia con cleartextTraffic=false.
- Entrada manual de URLs HTTPS; HTTP sin cifrar se bloquea por seguridad.
- Detección inicial de dispositivos USB conectados.
- Favoritos persistentes y últimas emisoras guardados localmente sin cuenta ni conexión.
- Importación de listas M3U/M3U8 con límite de tamaño/líneas y filtrado HTTPS; exportación de favoritos en M3U.
- Controles básicos de pausa, reanudación y detención enviados al servicio Media3.
- Investigación de repositorios y avisos de licencias documentados.
- Descubrimiento dinámico de nodos Radio Browser con respaldo estático y caché temporal de nodos.
- Caché local multi-búsqueda con límite de 12 consultas y 50 resultados por consulta.
- Filtros del directorio por nombre, género, idioma y códec.
- Reconexión automática limitada a cinco intentos con espera progresiva de 2 a 30 segundos.
- Registro de conectividad Android para reintentar una emisión fallida cuando vuelve una red con Internet validado; backoff exponencial limitado con jitter y respeto de pausa/detención manual.
- Cliente HTTP de streaming con tiempos de espera explícitos y bloqueo de redirecciones entre protocolos.
- Importación M3U mediante selector SAF y exportación de favoritos a M3U; solo se aceptan direcciones HTTPS, se eliminan duplicados y se limitan líneas/entradas.

## Estado de la radio online

La base de código incluye búsqueda mundial de emisoras, filtros por país/idioma/género y reproducción por URL. La reproducción utiliza Media3 y un servicio multimedia. El error de doble inserción de vistas fue corregido en 0.2.1. Se incorporó el módulo HLS y se mejoró la gestión de audio en segundo plano. Todavía requiere compilación confirmada y pruebas en dispositivo para verificar codecs, HLS, notificación, controles de pantalla bloqueada, audio focus y comportamiento en segundo plano.

## Pendiente antes de considerar funcional la radio terrestre

1. Identificar un teléfono con receptor FM/AM y una API/servicio de fabricante accesible.
2. Implementar adaptadores únicamente para hardware y protocolos documentados.
3. Enumerar USB OTG, solicitar permiso al conectar y validar chipset/protocolo.
4. Incorporar frecuencias cubanas verificadas con fuente y fecha; no inventar frecuencias.
5. Añadir sintonizador, escaneo, favoritos y controles de reproducción.
6. Compilar APK y probar recepción real, incluida prueba en modo avión.
7. Completar matriz de compatibilidad con modelos físicamente probados.

## Pendiente de radio online

1. Confirmar la compilación CI de 0.8.0 y corregir cualquier error de integración Media3.
2. Mejorar caché del catálogo por filtros y expiración visible.
3. Sincronizar el estado real del reproductor en la interfaz y controles completos.
4. Mejorar la política de reconexión con detección de conectividad y estados visuales; completar estados visuales y evaluar reconexión al recuperar conectividad.
5. Verificar enlaces de emisoras cubanas y registrar fecha/resultado.
6. Probar MP3, AAC y HLS en teléfonos reales.

## Limitación importante

Android no dispone de una API pública universal para controlar todos los sintonizadores FM/AM. Detectar un dispositivo USB no demuestra que sea un receptor de radio. Hasta validar hardware real, la interfaz debe informar de la limitación y no simular recepción.

## Compilación

El workflow .github/workflows/radio-nova-android.yml compila el módulo :app:assembleDebug desde esta carpeta y publica el APK como artefacto de GitHub Actions. Se reforzó el workflow para preparar explícitamente Android SDK 35 y generar el APK debug. La compilación de 0.5.0 sigue pendiente de confirmación mediante un resultado exitoso de CI.

## Resultado de verificación CI — 2026-11-13

El workflow de Radio Nova usa Ubuntu 24.04, Java 17, Gradle 8.9 y Android SDK 35; ejecuta JUnit, Lint y assembleDebug y publica el APK como artefacto si todo pasa. El run 43371807995 terminó con fallo en el job `build`, sin pasos reportados (`steps=null`); las ejecuciones previas mostraron `runner_id=0`. Por eso el estado **no confirma un error de compilación Java/Gradle** ni permite obtener el APK. No existe APK verificado. La compilación queda pendiente de una ejecución real del runner.


## Actualización 0.4.0 — 2026-11-13

Se incorporó almacenamiento local de favoritos y hasta 20 emisoras recientes, sin cuenta ni conexión. La pantalla permite volver a reproducir una emisora guardada y quitarla de favoritos. Se añadieron acciones de pausar, reanudar y detener mediante el servicio Media3. Estos controles envían comandos; todavía no hay una vista sincronizada del estado real de ExoPlayer. La versión 0.4.0 requirió una compilación CI exitosa y pruebas en dispositivo. La 0.5.0 también debe verificarse.

## Actualización 0.5.0 — 2026-11-13

Se añadieron importación/exportación M3U segura, filtros por nombre/género/idioma/códec y reconexión limitada del reproductor. También se añadió descubrimiento de mirrors mediante el endpoint oficial de Radio Browser, manteniendo nodos alternativos si falla la consulta. La búsqueda puede filtrarse por nombre, género, idioma o códec. La búsqueda conserva hasta 12 consultas distintas y hasta 50 resultados HTTPS por consulta en el almacenamiento local, y puede mostrarlos si el directorio no responde. Se enriqueció el modelo con tags, idioma, favicon y página de la emisora. La reproducción ahora usa timeouts explícitos y reconexión progresiva limitada a cinco intentos; pausar manualmente cancela los reintentos. Se incorporó un validador HTTPS común que rechaza credenciales en URL, espacios, hosts ausentes, direcciones malformadas y valores excesivamente largos. Se añadieron pruebas JUnit y el workflow ahora ejecuta `:app:testDebugUnitTest` además de Lint y ensamblado debug. La compilación real y las pruebas siguen pendientes de un runner disponible.


## Actualización 0.6.0 — 2026-11-13

Se añadió importación de listas M3U/M3U8 desde el selector de archivos Android y exportación de favoritos a M3U. El importador acepta únicamente URLs HTTPS válidas, descarta duplicados, limita el número de líneas y emisoras y evita leer archivos ilimitados. Se añadieron pruebas unitarias para el parser. La versión 0.6.0 requiere CI exitoso y pruebas en dispositivo.


## Actualización 0.7.0 — 2026-11-13 (histórico)

- El cliente Radio Browser ya no fija el código de país CU: busca en el directorio mundial y admite un país opcional.
- La pantalla incluye campo de país (vacío = todos), filtro por país y filtros existentes por nombre, género/etiquetas, idioma y códec.
- Los resultados muestran país e idioma cuando el directorio los proporciona.
- La caché local distingue país + filtro + consulta, evitando mezclar resultados de países distintos.
- Se añadió selector persistente Español/English, con español como valor inicial y traducción de los rótulos/acciones principales. Aún quedan mensajes secundarios por localizar.
- La acción de sincronización actualiza hasta 50 resultados por consulta. No descarga todo el directorio ni sincroniza en la nube entre dispositivos; los favoritos siguen locales y se pueden transferir mediante M3U.
- La versión de trabajo se elevó a 0.7.0 (versionCode 8). Compilación y pruebas en dispositivo pendientes de CI exitoso.

- La búsqueda de país acepta también nombres habituales en español (por ejemplo, Estados Unidos, España, Reino Unido y Brasil) y los normaliza a los nombres del directorio.


## Actualización 0.8.0 — 2026-11-13

- Temporizador de apagado integrado en la pantalla: 15, 30, 60 y 90 minutos, con cancelación manual.
- El servicio programa el apagado, detiene el reproductor, limpia la sesión y cancela los reintentos cuando vence. El temporizador vive en el proceso del servicio; Android podría terminarlo bajo condiciones extremas o al forzar la detención.
- Caché del directorio reestructurada: hasta 12 consultas diferentes, con 50 resultados por consulta.
- Recuperación de streaming al restablecerse la conectividad mediante ConnectivityManager y permiso ACCESS_NETWORK_STATE. Se usa SHA-256 de la clave de búsqueda para evitar guardar consultas como nombres de preferencias y se eliminan las entradas más antiguas.
- Búsqueda mundial, filtro por país y nombres habituales de países en español se mantienen.
- Se estudiaron repositorios públicos de radio online, Android Auto, sincronización, cast y SDR. Se incorporan solo funciones propias; no se copió código de terceros.
- Versión de trabajo 0.8.0, versionCode 9. Compilación y pruebas Android pendientes de CI exitoso.


## Actualización 0.9.0 — autenticación NOVA

Se incorporó `NovaAccountSession` y `NovaLoginActivity`. El acceso online exige iniciar sesión con una cuenta registrada en Nova Apps; la app abre la página de Nova Apps para el registro y no crea usuarios en un backend separado. La sesión se almacena cifrada mediante Android Keystore, con refresh token, expiración y verificación del usuario contra Supabase Auth. Se protege el inicio de streams, la búsqueda, la reanudación, el temporizador y la entrega de la sesión Media3. Se conserva el modo terrestre y los datos locales sin cuenta. Pendiente: CI real, prueba de login/renovación/cierre de sesión, comprobación de bloqueo tras token inválido y pruebas de reproducción con y sin sesión.


## Verificación inicial de 0.9.0 — 2026-11-13

El workflow se disparó con los cambios de autenticación, pero el job más reciente (run 43368599393) terminó antes de asignar un runner: `runner_id=0`, `steps=[]`. Por tanto no se ejecutó Gradle, no se validaron las pruebas ni se generó APK. Se hizo una revisión estática básica de los cuatro archivos Java nuevos/modificados y sus delimitadores están balanceados; esto no equivale a compilación ni prueba funcional. La integración de inicio de sesión, renovación, cierre de sesión y bloqueo del servicio sigue pendiente de validación real en CI y dispositivo.


## Actualización 1.0.0 — copia de seguridad local

- Se añadió `StationBackup`: formato JSON versionado, límite de 1 MiB, máximo de 500 elementos por colección y validación HTTPS al restaurar.
- La copia exporta favoritos y últimas emisoras; excluye credenciales, tokens, sesión NOVA y datos privados de autenticación.
- La restauración combina con los datos locales existentes, descarta enlaces no HTTPS y no borra datos previos.
- La pantalla incluye acciones para crear/restaurar la copia mediante el selector SAF de Android. Disponible sin cuenta y sin Internet.
- Versión de trabajo 1.0.0 / versionCode 11. CI y prueba de restauración en Android pendientes; no se declara APK compilado.


## Actualización 1.1.0 — experiencia de usuario

- Orden de resultados por popularidad (orden del directorio), nombre A–Z o bitrate más alto.
- Campo opcional para nombrar emisoras añadidas manualmente y guardarlas en favoritos sin reproducirlas.
- Colecciones/etiquetas locales por emisora; se pueden crear, editar o quitar desde favoritos y recientes. No se sincronizan con servicios externos.
- Compartir emisoras mediante el selector Android, con nombre y URL HTTPS.
- Acceso a la página oficial HTTPS de una emisora cuando el directorio la informa; mantiene el requisito de sesión NOVA para funciones online.
- Copia de seguridad JSON versión 2 conserva las colecciones y sigue restaurando copias versión 1; nunca incluye tokens ni datos de cuenta.
- Importación/exportación M3U preserva etiquetas de colección mediante `#EXTGRP`; se añadió prueba unitaria del parser.
- Versión de trabajo 1.1.0 (versionCode 12). Compilación, pruebas y funcionamiento en dispositivo pendientes de CI y validación Android.


## CI 1.1.0 — 2026-11-13

GitHub Actions run [43370064259](https://github.com/Cumba35/Nova-Workspace/actions/runs/43370064259) terminó en fallo antes de asignar runner (`runner_id=0`, `steps=[]`). No ejecutó Gradle, pruebas unitarias, Lint ni ensamblado APK. Se revisó el código y se añadió una prueba unitaria para importar grupos `#EXTGRP`, pero el resultado de CI sigue sin verificar. No hay APK 1.1.0 confirmado.


## Actualización 1.2.0 — reproducción y audio

- Se añadió el panel de reproducción actual con estado sincronizado por eventos internos del servicio; la pantalla consulta el estado al volver a primer plano si hay sesión NOVA.
- Se procesa `IcyInfo` mediante `Player.Listener.onMetadata`; cuando la emisora proporciona título dinámico, se muestra en la pantalla y se actualizan los metadatos del elemento multimedia para reflejarlos también en los controles/notificación del sistema. No todas las emisoras transmiten ICY ni todos los formatos lo incluyen.
- Reconexión mejorada: solo se dispara por una red con Internet validado, mantiene cinco intentos como máximo, backoff exponencial de 2–30 s con variación aleatoria y mensaje claro al agotarlos. La pausa/detención cancela reintentos.
- Ecualizador Android con presets desactivado, equilibrado, graves y voz. Se crea sobre la sesión de audio del reproductor, limita los niveles al rango informado por el efecto y libera recursos al detener el servicio. Si el dispositivo no ofrece un efecto compatible, se informa y continúa la reproducción sin ecualizador.
- Las acciones de estado y ecualizador quedan protegidas por sesión NOVA en el servicio; los favoritos, listas y copias locales siguen funcionando sin cuenta.
- Versión de trabajo 1.2.0 (versionCode 13). Compilación, pruebas automáticas y pruebas de audio en dispositivo pendientes de un runner CI funcional.


## CI 1.2.0 — 2026-11-13

El workflow [43370856735](https://github.com/Cumba35/Nova-Workspace/actions/runs/43370856735) terminó en fallo antes de asignar runner (`runner_id=0`, `steps=[]`). Se solicitó reintentar el job, pero este estado no demuestra un fallo de código ni una compilación. No se ejecutaron Gradle, JUnit, Lint ni `assembleDebug`; no hay APK 1.2.0 confirmado. Se requiere que GitHub asigne un runner y ejecute los pasos.


## Actualización 1.3.0 — biblioteca multimedia y calidad

- `InternetRadioService` usa `MediaLibraryService` de Media3 y expone raíz Radio Nova, Favoritos y Últimas emisoras; incluye búsqueda local, paginación y elementos HTTPS reproducibles.
- La biblioteca externa exige sesión NOVA válida para raíz, carpetas, elementos y resultados de búsqueda.
- `StationStore` conserva país, códec y bitrate para enriquecer favoritos y recientes.
- Selector de calidad: automática, ahorro (máximo 64 kbps), equilibrada (máximo 128 kbps) y alta. Solo puede cambiar calidad cuando el stream ofrece variantes/pistas alternativas; no cambia un stream único MP3/AAC.
- Declaración multimedia para Android Auto incorporada. Esto prepara la integración, pero no demuestra funcionamiento en un coche ni certificación.
- versionCode 14 / versionName 1.3.0. La compilación y pruebas reales siguen pendientes del runner y de un dispositivo Android.

## CI 1.3.0 — estado actual

El run [43371807995](https://github.com/Cumba35/Nova-Workspace/actions/runs/43371807995) terminó con fallo en el job `build`, sin pasos reportados por la API. Los runs previos de Android y de la política de notas también fallaron antes de ejecutar pasos. No se puede atribuir el fallo a Gradle o al código fuente sin logs de compilación. No hay APK verificado.


## Reintento de CI — 2026-11-13 05:40 UTC

Se solicitó un nuevo reintento del job Android del run [43373073721](https://github.com/Cumba35/Nova-Workspace/actions/runs/43373073721). El job volvió a terminar en `failure` y GitHub no reportó pasos (`steps=null`, `logs_url=null`). En paralelo, el workflow de política de notas también falla sin pasos, lo que indica que el bloqueo ocurre antes de ejecutar comandos del workflow; no hay evidencia de que sea un error Java, Gradle, Android SDK ni una etiqueta concreta de runner. No se debe seguir repitiendo el mismo job indefinidamente: el propietario debe revisar que Actions esté habilitado y que la cuenta/repositorio tenga capacidad de GitHub-hosted runners/minutos disponible. Hasta entonces no se puede generar un APK en CI.
