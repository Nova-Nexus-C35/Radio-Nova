# Nova Guard

Centro canónico de seguridad, privacidad, identidad y protección de datos del ecosistema Nova.

Repositorio privado: https://github.com/Cumba35/Nova-Guard

## Propósito y límites

Nova Guard gobierna las políticas, los esquemas, las migraciones, los permisos, la auditoría y los contratos de almacenamiento seguro de Nova. No es un almacén Git para contraseñas, claves privadas, fotografías faciales ni datos biométricos en claro.

**Los datos privados reales se almacenan fuera del historial Git**, en servicios privados autenticados. El proyecto Supabase Nova-Nexus conserva actualmente la base de datos y Storage; Nova Guard versiona su arquitectura y las migraciones de seguridad. El objetivo de la integración es que Nova IA y los clientes Nova accedan a los datos cifrados únicamente mediante permisos explícitos, mínimos y revocables.

Un repositorio GitHub privado no equivale a una bóveda cifrada: commits, clones, cachés y copias pueden conservar archivos incluso después de borrarlos de la rama principal.

## Arquitectura

| Capa | Función |
|---|---|
| Nova Guard (este repositorio) | Fuente canónica de políticas, contratos, migraciones, documentación y pruebas de seguridad. |
| Supabase privado | Base de datos y almacenamiento de objetos cifrados, con RLS y políticas de Storage. |
| Nova Password Manager | Bóveda de credenciales cifradas en el cliente; la clave maestra no se envía al servidor. |
| Nova IA | Consumidor autorizado de datos estrictamente necesarios, sin acceso general a credenciales ni claves. |
| Clientes Android | Cifrado antes de subir, autenticación reforzada y claves locales protegidas por Android Keystore. |

### Regla de cifrado

- Las contraseñas y los secretos se cifran en el cliente mediante el gestor de contraseñas.
- Los archivos privados, las copias de memoria y los datos biométricos que se incorporen al nuevo almacén deben cifrarse antes de subirlos.
- El bucket `nova-guard-private` solo admite `application/octet-stream`; el contenido debe ser ciphertext, no archivos originales.
- El esquema de metadatos no almacena claves, contraseñas, plantillas biométricas ni contenido personal en claro.
- Las claves de cifrado nunca se versionan en Git ni se escriben en conversaciones, logs o metadatos.
- Android Keystore protege las claves locales y puede exigir autenticación del usuario. Nova no debe intentar extraer las plantillas biométricas protegidas por el sistema operativo.

### Identidad y biometría

Los datos biométricos son de máxima sensibilidad. Su almacenamiento requiere consentimiento explícito y específico, cifrado en el dispositivo, acceso exclusivo del propietario, registro de operaciones y un mecanismo de revocación y eliminación.

La autenticación biométrica nativa del dispositivo debe limitarse a confirmar la presencia/autorización local del propietario. No se debe extraer ni copiar la plantilla biométrica del sistema. Si el propietario crea una plantilla o muestra biométrica propia para una función específica de Nova, se conserva únicamente como ciphertext en el almacenamiento privado y se separa de las fotografías de perfil.

Nova IA no debe realizar identificación facial de terceros ni convertir imágenes ilustrativas en pruebas de identidad. Una fotografía de perfil no es una plantilla biométrica ni acredita identidad.

## Datos y servicios existentes

- Base de datos: proyecto Supabase Nova-Nexus.
- Bóveda de contraseñas: `nova_password_vaults` y `nova_password_entries`, con cifrado de cliente y RLS forzado.
- Bóveda personal cifrada existente: `nova_private_personal_vaults` y `nova_private_personal_records`.
- Memoria de usuario: `nova_user_memory_items`, servida por las funciones `nova-memory` y `nova-ia`.
- Esquema facial existente: `nova_face_profiles` y `nova_face_samples`.
- Funciones Edge: `nova-memory` y `nova-ia`, entre otras.

La existencia de tablas o funciones no significa que el flujo de extremo a extremo esté terminado. El cifrado, los permisos y las integraciones deben probarse en cada cliente y servicio.

## Implementación aplicada

### Fundación de almacenamiento privado — 10 de noviembre de 2026

Migraciones aplicadas en Supabase y versionadas aquí:

- `20261110222244_nova_guard_private_storage_foundation.sql`
- `20261110222306_nova_guard_private_vault_least_privilege.sql`

Cambios verificados:

1. Creación del bucket privado `nova-guard-private`, limitado a 50 MiB y a `application/octet-stream`.
2. Creación de `public.nova_guard_private_objects` para indexar metadatos de objetos cifrados, sin almacenar su contenido ni claves.
3. RLS y FORCE RLS en el índice de objetos.
4. Políticas de Storage limitadas al propietario y a rutas cuyo primer segmento es su UUID.
5. FORCE RLS en las tablas de bóveda personal cifrada y en las tablas de muestras/perfiles faciales.
6. Eliminación de privilegios heredados innecesarios y concesión exclusiva de SELECT, INSERT, UPDATE y DELETE donde corresponde.
7. Eliminación del acceso directo de administradores autenticados a los perfiles y muestras biométricas del propietario; las operaciones de backend deben comprobar autorización y quedar auditadas.
8. Restricción del bucket heredado `nova-face-images` al propietario.

Los controles de RLS, permisos, bucket y políticas se consultaron después de aplicar las migraciones. Los cuatro registros existentes de memoria personal se cifraron en la base de datos; se verificó que los cuatro pueden descifrarse mediante la clave privada y que no queda acceso directo a la tabla para los roles cliente. Una prueba de escritura/lectura/borrado mediante RPC con el rol autenticado terminó correctamente y se revirtió para no dejar datos de prueba.

Las funciones Edge `nova-memory` v12 y `nova-ia` v35 están activas, con JWT obligatorio, y ya usan las RPCs cifradas. Sus fuentes se sincronizaron en Nova-Nexus. El asesor de seguridad ya no muestra la advertencia de funciones `SECURITY DEFINER` expuestas; queda la advertencia de contraseñas filtradas.

**Alcance del cifrado de memoria:** es cifrado en reposo mediante claves por propietario custodiadas por Supabase Vault; no es cifrado de extremo a extremo. Nova IA puede recibir el texto descifrado únicamente después de la autenticación y de comprobar los consentimientos del propietario. Supabase documenta Vault como una función en etapa alpha pública; debe mantenerse aislada detrás de estas RPCs y reevaluarse para una solución de gestión de claves de disponibilidad general.

Durante la inspección no había perfiles ni muestras biométricas ni objetos faciales guardados. El bucket nuevo y la tabla de metadatos están preparados, pero todavía falta integrar el cliente Android para cifrar y subir plantillas/muestras biométricas. No se debe afirmar que el reconocimiento facial o biométrico esté implementado.

## Pendientes críticos antes de declarar la migración completa

- [ ] Crear el flujo de inicialización y recuperación de claves del propietario en los clientes, sin enviar la clave maestra al servidor.
- [ ] Conectar Nova IA y los clientes Android al bucket `nova-guard-private` y a `nova_guard_private_objects`.
- [x] Cifrar la memoria personal existente y adaptar `nova-memory` y `nova-ia` para escribir/leer mediante RPCs autenticadas.
- [ ] Planificar la evolución desde cifrado en reposo de Supabase Vault hacia cifrado de extremo a extremo para memoria si se requiere que ni el servicio de base de datos pueda descifrarla.
- [ ] Implementar en el cliente Android el registro biométrico cifrado, la revocación del consentimiento y la eliminación de objetos, sin almacenar plantillas del sistema operativo.
- [ ] Verificar aislamiento con dos identidades de prueba, incluidos intentos de lectura, escritura, actualización y borrado cruzados.
- [ ] Activar la protección de Supabase Auth contra contraseñas filtradas. El asesor la detecta desactivada. El expediente actual del proyecto indica que Supabase está en el plan Free y que la protección nativa requiere Pro o superior; el mínimo de 12 caracteres de la app no sustituye la comprobación de contraseñas filtradas.
- [ ] Definir rotación de claves, recuperación, copias cifradas y pruebas de restauración.
- [ ] Ejecutar los asesores de seguridad y las pruebas end-to-end después de cada integración.

## Reglas obligatorias

1. No guardar contraseñas, tokens, claves, semillas de recuperación ni secretos en Git.
2. No subir datos personales, archivos privados, fotografías faciales ni plantillas biométricas en claro.
3. No conceder a Nova IA acceso permanente o global a los datos del propietario.
4. Toda operación privilegiada debe verificar identidad, consentimiento, alcance, vigencia y autorización en el servidor.
5. La revocación debe impedir nuevas operaciones inmediatamente y dejar constancia de auditoría.
6. Los identificadores de almacenamiento deben ser opacos y las rutas deben quedar aisladas por propietario.
7. Los logs nunca deben incluir payloads, credenciales, claves, tokens ni datos biométricos.
8. No eliminar el sistema anterior ni sus datos hasta completar y verificar la migración y la recuperación.
9. Toda modificación de seguridad debe incluir migración versionada, pruebas y verificación posterior.
10. Nunca declarar una función como activa o segura basándose únicamente en que existe su tabla, bucket, endpoint o documentación.

## Estado

**Fundación de almacenamiento aplicada. Memoria personal cifrada y conectada a Nova IA. Integración del cliente biométrico, migración de archivos privados, endurecimiento de la autenticación y revisión del almacenamiento SQLite local de Nova IA aún pendientes.**

Este estado debe actualizarse con commits y resultados de pruebas verificables, no con estimaciones.
