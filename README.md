# Nova Guard

Centro canónico de seguridad, privacidad, identidad y protección de datos del ecosistema Nova.

Repositorio privado: https://github.com/Cumba35/Nova-Guard

## Propósito y límites

Nova Guard gobierna las políticas, los esquemas, las migraciones, los permisos, la auditoría y los contratos de almacenamiento seguro de Nova. No es un almacén Git para contraseñas, claves privadas, fotografías faciales ni datos biométricos en claro.

**Los datos privados reales nunca se almacenan en GitHub.** La infraestructura Supabase heredada conserva actualmente recursos históricos, pero queda congelada para nuevas funciones y no debe ampliarse ni borrarse durante la transición. Nova Guard versiona contratos y políticas para el futuro núcleo propio de NOVA. El objetivo es que Nova IA y los clientes accedan a los datos cifrados únicamente mediante permisos explícitos, mínimos y revocables.

Un repositorio GitHub privado no equivale a una bóveda cifrada: commits, clones, cachés y copias pueden conservar archivos incluso después de borrarlos de la rama principal.

## Contratos compartidos NOVA — 18 de noviembre de 2026

Se añadió el contrato versionado de manifiestos de módulos en [contracts/nova-module-manifest.schema.json](contracts/nova-module-manifest.schema.json), 20 manifiestos de referencia en manifests/, y el validador sin dependencias externas scripts/validate_module_manifests.py con pruebas en tests/. Estos archivos describen capacidades y permisos solicitados; **no conceden permisos ni sustituyen la autorización en servidor**. Las acciones delete, publish, export y admin exigen aprobación explícita. Los campos desconocidos y los campos de credenciales se rechazan.

La validación de contratos todavía no se ha ejecutado en un runner ni equivale a pruebas de autorización. Véase [docs/CONTRATOS-COMUNES-NOVA.md](docs/CONTRATOS-COMUNES-NOVA.md).

## Arquitectura

| Capa | Función |
|---|---|
| Nova Guard (este repositorio) | Fuente canónica de políticas, contratos, migraciones, documentación y pruebas de seguridad. |
| Persistencia heredada | Recursos Supabase históricos, congelados para nuevas funciones hasta una migración NOVA verificada. |
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

La versión de consentimiento `nova-biometric-consent-v1` está documentada en [docs/BIOMETRIC-CONSENT.md](docs/BIOMETRIC-CONSENT.md). La base de datos exige el SHA-256 exacto de ese documento (`770b63acc8cb0a4c9042e3fddc229904ed1178b3c2f5e03b21253e4838cd0b05`). El registro de consentimientos por finalidad está en `nova_guard_biometric_consents`; cada objeto biométrico requiere un consentimiento activo y un objeto cifrado ya subido. La revocación bloquea nuevas lecturas y marca los objetos como revocados; el cliente debe eliminar además los objetos físicos y las claves locales.

Los datos biométricos son de máxima sensibilidad. Su almacenamiento requiere consentimiento explícito y específico, cifrado en el dispositivo, acceso exclusivo del propietario, registro de operaciones y un mecanismo de revocación y eliminación.

La autenticación biométrica nativa del dispositivo debe limitarse a confirmar la presencia/autorización local del propietario. No se debe extraer ni copiar la plantilla biométrica del sistema. Si el propietario crea una plantilla o muestra biométrica propia para una función específica de Nova, se conserva únicamente como ciphertext en el almacenamiento privado y se separa de las fotografías de perfil.

Nova IA no debe realizar identificación facial de terceros ni convertir imágenes ilustrativas en pruebas de identidad. Una fotografía de perfil no es una plantilla biométrica ni acredita identidad.

## Datos y servicios existentes

- Base de datos heredada: proyecto Supabase Nova-Nexus (no ampliar ni borrar durante la migración).
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
- `20261110222558_nova_guard_encrypt_personal_memory.sql`
- `20261110222633_nova_guard_isolate_memory_rpcs.sql`
- `20261110222732_nova_guard_memory_upsert_fix.sql`
- `20261110223651_nova_guard_biometric_consent_registry.sql`
- `20261110223721_nova_guard_biometric_consent_hardening.sql`
- `20261110223850_nova_guard_biometric_consent_document_hash.sql`
- `20261110225225_nova_guard_biometric_consent_fk_index.sql`

Cambios verificados:

1. Creación del bucket privado `nova-guard-private`, limitado a 50 MiB y a `application/octet-stream`.
2. Creación de `public.nova_guard_private_objects` para indexar metadatos de objetos cifrados, sin almacenar su contenido ni claves.
3. RLS y FORCE RLS en el índice de objetos.
4. Políticas de Storage limitadas al propietario y a rutas cuyo primer segmento es su UUID.
5. FORCE RLS en las tablas de bóveda personal cifrada y en las tablas de muestras/perfiles faciales.
6. Eliminación de privilegios heredados innecesarios y concesión exclusiva de SELECT, INSERT, UPDATE y DELETE donde corresponde.
7. Eliminación del acceso directo de administradores autenticados a los perfiles y muestras biométricas del propietario; las operaciones de backend deben comprobar autorización y quedar auditadas.
8. Restricción del bucket heredado `nova-face-images` al propietario.
9. Registro de consentimiento biométrico por finalidad, con historial de revocación inmutable para el cliente.
10. Restricción de registro de objetos biométricos a consentimiento activo y a objetos cifrados previamente subidos.
11. Las lecturas del bucket privado requieren metadatos activos; los objetos biométricos además requieren consentimiento no revocado.

Los controles de RLS, permisos, bucket y políticas se consultaron después de aplicar las migraciones. Los cuatro registros existentes de memoria personal se cifraron en la base de datos; se verificó que los cuatro pueden descifrarse mediante la clave privada y que no queda acceso directo a la tabla para los roles cliente. Una prueba de escritura/lectura/borrado mediante RPC con el rol autenticado terminó correctamente y se revirtió para no dejar datos de prueba.

Las funciones Edge `nova-memory` v12 y `nova-ia` v35 están activas, con JWT obligatorio, y ya usan las RPCs cifradas. Sus fuentes se sincronizaron en Nova-Nexus. El asesor de seguridad ya no muestra la advertencia de funciones `SECURITY DEFINER` expuestas; queda la advertencia de contraseñas filtradas.

**Alcance del cifrado de memoria:** es cifrado en reposo mediante claves por propietario custodiadas por Supabase Vault; no es cifrado de extremo a extremo. Nova IA puede recibir el texto descifrado únicamente después de la autenticación y de comprobar los consentimientos del propietario. Supabase documenta Vault como una función en etapa alpha pública; debe mantenerse aislada detrás de estas RPCs y reevaluarse para una solución de gestión de claves de disponibilidad general.

El backend FastAPI de Nova IA también incorpora cifrado AES-256-GCM para los campos de contenido de su SQLite operativo, con una clave dedicada en el gestor de secretos de Railway y el historial local desactivado por defecto. La variable secreta está configurada. El servicio Railway `nova-ia-api` existe en `Nova-IA/production`, conserva el volumen `/app/data` y tiene healthcheck `/health`, pero está Offline, no tiene despliegues y su fuente está desconectada. El intento de conectar `Cumba35/Nova-IA` desde Railway devolvió `User does not have access to the repo`; el propietario debe conceder a la GitHub App de Railway acceso al repositorio privado y resolver cualquier permiso pendiente antes de reintentar la conexión.

Durante la inspección no había perfiles ni muestras biométricas, consentimientos biométricos ni objetos faciales guardados. El documento de consentimiento y el registro de base de datos ya están preparados; falta integrar el cliente Android para mostrar el consentimiento, registrar la aceptación, cifrar y subir los objetos y ejecutar la eliminación al revocar. El bucket nuevo y la tabla de metadatos están preparados, pero todavía falta integrar el cliente Android para cifrar y subir plantillas/muestras biométricas. No se debe afirmar que el reconocimiento facial o biométrico esté implementado.

## Pendientes críticos antes de declarar la migración completa

- [ ] Crear el flujo de inicialización y recuperación de claves del propietario en los clientes, sin enviar la clave maestra al servidor.
- [ ] Conectar Nova IA y los clientes Android al bucket `nova-guard-private` y a `nova_guard_private_objects`.
- [x] Cifrar la memoria personal existente y adaptar `nova-memory` y `nova-ia` para escribir/leer mediante RPCs autenticadas.
- [ ] Planificar la evolución desde cifrado en reposo de Supabase Vault hacia cifrado de extremo a extremo para memoria si se requiere que ni el servicio de base de datos pueda descifrarla.
- [ ] Implementar en el cliente Android el flujo de consentimiento `nova-biometric-consent-v1`, el cifrado local, la subida de objetos y su eliminación al revocar, sin almacenar plantillas del sistema operativo.
- [ ] Verificar aislamiento con dos identidades de prueba, incluidos intentos de lectura, escritura, actualización y borrado cruzados.
- [ ] Activar la protección de Supabase Auth contra contraseñas filtradas tras habilitar un plan Pro o superior. El asesor la detecta desactivada; la función no está incluida en Free. El mínimo de 12 caracteres de la app no sustituye la comprobación de contraseñas filtradas.
- [ ] Definir rotación de claves, recuperación, copias cifradas y pruebas de restauración.
- [x] Añadir el índice faltante para `nova_guard_private_objects.biometric_consent_id`; el asesor ya no reporta claves foráneas sin índice en Nova Guard.
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

## Estado operativo de Nova IA

La auditoría de GitHub Actions, Railway, notificaciones SMTP y biometría Android, con bloqueos confirmados y criterios de recuperación, está en [docs/NOVA-IA-OPERATIONS-RECOVERY.md](docs/NOVA-IA-OPERATIONS-RECOVERY.md). No declarar operativas las funciones sin pruebas reales.

## Despliegue de Nova IA en Railway

La auditoría del 10 de noviembre de 2026 confirmó que `nova-ia-api` está provisionado con raíz `/backend`, Dockerfile, healthcheck `/health`, volumen `/app/data` y secreto `NOVA_SQLITE_ENCRYPTION_KEY`, pero `source = null`, no tiene despliegues y permanece Offline. La conexión con `Cumba35/Nova-IA` falla con `User does not have access to the repo`. El procedimiento y los criterios de aceptación están en [docs/RAILWAY-DEPLOYMENT-RECOVERY.md](docs/RAILWAY-DEPLOYMENT-RECOVERY.md). No declarar el despliegue completado hasta verificar un deployment `SUCCESS`, servicio `ONLINE` y pruebas funcionales.

## Estado

**Fundación de almacenamiento aplicada. Memoria personal cifrada y conectada a Nova IA. El índice de la FK biométrica se añadió y verificó. Código de cifrado del SQLite operativo añadido y clave provisionada en Railway; el despliegue sigue bloqueado por permisos de la GitHub App. Integración biométrica Android, migración de archivos privados y protección contra contraseñas filtradas siguen pendientes.**

Este estado debe actualizarse con commits y resultados de pruebas verificables, no con estimaciones.

Las alternativas gratuitas sin tarjeta para CI/CD y hosting, con límites y plan de migración, están documentadas en [docs/FREE-NO-CARD-ALTERNATIVES.md](docs/FREE-NO-CARD-ALTERNATIVES.md).


## Política de transición del propietario — 2026-11-18

GitHub y ChatGPT son las herramientas autorizadas para desarrollar y mantener NOVA. Dropbox se conserva como excepción aprobada para archivos, copias y recuperación con acciones explícitas y credenciales seguras. No incorporar nuevos proveedores externos. Los servicios heredados (incluido Supabase/Railway/Google Drive) no se amplían; conservar datos y operaciones existentes hasta sustituirlos de forma probada. No crear ni activar workflows automáticos en repositorios privados NOVA. Las autenticaciones existentes pueden mantenerse y repararse; esta regla no prohíbe autenticación.


### Validación local de manifiestos

Desde la raíz del repositorio, cuando exista un entorno Python autorizado:

```sh
python scripts/validate_module_manifests.py
python -m unittest discover -s tests
```

Estas instrucciones están documentadas, pero esta sesión no ejecutó el intérprete sobre el repositorio. No considerar los contratos verificados hasta ejecutar ambas comprobaciones y revisar los resultados.

## Robustez del validador — 18 de noviembre de 2026

Se corrigió el tratamiento de IDs malformados durante la detección de duplicados: los valores JSON no textuales ya no se usan como claves de conjunto, evitando una excepción inesperada. Se añadió una prueba unitaria para ID no hashable y duplicado válido. Commits: `b38ed721681bf6c0d8265b718fd1a308954d8ccd` y `ee98158f7d13469454f09a106585f670c7245e6d`. La ejecución de las pruebas sigue pendiente; el cambio no se declara probado.

## NOVA Authorization Core — reference implementation (18 November 2026)

Se añadió `nova_guard/authorization.py`, un PDP de referencia sin dependencias externas. Aplica denegación predeterminada, permisos declarados por módulo, clases de datos, alcance por propietario/participante/grupo/tenant/ecosistema, concesiones explícitas con expiración/revocación, evidencia de aprobación ligada a sujeto/recurso/acción y auditoría de metadatos con fallo cerrado si el sink no está disponible. La biometría queda limitada al propietario y el alcance de ecosistema exige el rol y la concesión explícitos `nova.platform_admin`.

Archivos: `nova_guard/authorization.py`, `tests/test_authorization.py` y `docs/NOVA-AUTHORIZATION-CORE.md`. Esta entrega es una biblioteca PDP de referencia, no un servicio desplegado: no contiene proveedor de identidad, almacenamiento autoritativo de concesiones, flujo de aprobación, API ni sink durable. Las pruebas se añadieron pero no se ejecutaron en esta sesión. Por tanto, Nexus debe mantener `runtime_authorization: not-verified` y ninguna aplicación debe tratar este módulo como protección activa hasta integrar y verificar un adaptador de servidor real.
