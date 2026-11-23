# Radio Nova

Aplicación Android de radio del ecosistema NOVA. El código fuente y las pruebas de esta aplicación pertenecen a este repositorio.

## Fuente importada

Se transfirieron 29 archivos desde `Nova-Workspace/Projects/Applications/radio-nova/`, incluyendo Gradle, manifiesto Android, clases Java, pruebas unitarias y documentación. El README de origen se preservó en `docs/UPSTREAM-README-SANITIZED.md`; se retiró su sección de contacto para no publicar datos personales. Los SHA de origen están en `SOURCE-TRANSFER-MANIFEST.json`.

## Estado funcional observado en el código

- Android nativo, Java 17, Media3 1.11.1.
- Streaming de emisoras por Internet, reproducción en segundo plano y controles multimedia.
- Búsqueda mundial mediante Radio Browser, filtros y caché local de resultados.
- Favoritos y últimas emisoras guardados localmente.
- Importación/exportación M3U/M3U8 y copias JSON.
- Temporizador de apagado, reconexión limitada, selección de calidad cuando el stream ofrece variantes y ecualizador cuando el dispositivo lo soporta.
- Acceso NOVA para funciones de Internet; las funciones locales de favoritos y archivos no requieren cuenta.

## Objetivo offline y limitaciones de hardware

La recepción terrestre de emisoras cubanas sin Internet sigue pendiente. Requiere un receptor FM/AM compatible y un controlador/API soportado por el fabricante o un receptor USB OTG con protocolo identificado. El código actual documenta la abstracción y el catálogo de compatibilidad, pero no acredita recepción FM real en un teléfono concreto. No prometer funcionamiento offline de FM/AM hasta probarlo en hardware.

## Seguridad y privacidad

- Streaming y búsqueda online requieren Internet.
- Favoritos, recientes, M3U y respaldo local se almacenan en el dispositivo.
- No se guardan tokens ni contraseñas en archivos de respaldo.
- No se debe recopilar ubicación continuamente ni transmitir historial de escucha sin consentimiento.
- No importar firmware o drivers propietarios sin licencia y autorización.

## Validación pendiente

1. Ejecutar pruebas Gradle y compilar APK debug.
2. Probar streaming, MediaLibraryService, notificaciones y reconexión en Android 9+.
3. Probar multitarea, permisos y consumo de batería.
4. Probar receptor FM/AM y USB OTG en dispositivos identificados; registrar matriz real de compatibilidad.
5. No declarar Radio Nova finalizada hasta validar recepción terrestre offline si sigue siendo requisito de aceptación.

## Ubicación en NOVA

- [Nova Workspace — almacenamiento e índice de transición](https://github.com/Nova-Nexus-C35/Nova-Workspace/blob/main/ECOSYSTEM-TRANSITION-INDEX.md)
- [Nova Nexus — administración del ecosistema](https://github.com/Nova-Nexus-C35/Nova-Nexus)


## Bloqueo de compilación observado — 2026-11-23

El proyecto no incluye `gradle/wrapper/gradle-wrapper.jar` ni se encontró un workflow de Android build activo. El código y las pruebas se transfirieron, pero no se afirma que el APK compile hasta restaurar un mecanismo reproducible de Gradle y ejecutar build/test en CI. La recepción FM/AM offline sigue siendo una función pendiente independiente.
