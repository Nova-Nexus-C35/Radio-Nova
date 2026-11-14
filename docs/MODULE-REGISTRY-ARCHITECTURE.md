# NOVA Module Registry — seguridad y datos

Estado: base de registro de claves implementada en NOVA-STAGING. La rama PR incorpora ahora operaciones atómicas para registro, rotación, revocación y auditoría; la nueva migración y Edge Function aún no se han desplegado en staging. Producción no se ha modificado.
Autoridad: Nova Guard.

## Objetivo y límites

Centralizar la confianza de publicadores y, por fases, el catálogo, permisos y auditoría de módulos NOVA. El registro conserva metadatos y referencias; no ejecuta código descargado ni sustituye la verificación local.

## Modelo de datos en NOVA-STAGING

El esquema privado `private` contiene cuatro tablas:
- `nova_module_publishers`: publicador, ID de clave, propietario autenticado, clave pública Ed25519 DER/SPKI, estado active/revoked y relación de rotación.
- `nova_module_registry`: metadatos/manifest, versión, hash, firma, ruta privada de artefacto, estado y revisión.
- `nova_module_user_grants`: permisos por usuario y versión exacta.
- `nova_module_audit`: actor, acción, resultado, fecha y metadatos técnicos permitidos.

RLS y FORCE RLS están habilitados. Los privilegios directos de PUBLIC, anon y authenticated están revocados; la Edge Function usa service_role exclusivamente en servidor. El bucket `nova-modules` es privado y limita objetos a 64 MiB.

## Acciones implementadas en código

La Edge Function `nova-module-registry` desplegada actualmente en NOVA-STAGING exige JWT. La rama de este PR añade:
- `catalog.list`: devuelve únicamente módulos publicados y con clave activa.
- `publisher.register`: registra una clave pública Ed25519 vinculada al usuario autenticado.
- `publisher.keys.list`: lista solo las claves del propietario autenticado.
- `publisher.rotate`: usa una RPC transaccional que bloquea la clave anterior, inserta la nueva, revoca la anterior y escribe auditoría en la misma transacción.
- `publisher.revoke`: bloquea y revoca la clave activa y escribe auditoría en la misma transacción.

Las RPC `nova_register_publisher_key`, `nova_rotate_publisher_key` y `nova_revoke_publisher_key` se definen en `supabase/staging/nova-module-key-atomic-operations.sql`. Solo `service_role` puede ejecutarlas; la Edge Function valida primero la sesión con Supabase Auth y obtiene el ID del usuario. La función SQL no confía en roles ni IDs de usuario enviados por el cliente: el ID se pasa desde la sesión verificada del servidor.

El registro inicial usa un advisory transaction lock por publisher ID para evitar dos registros iniciales concurrentes. Rotación y revocación bloquean la fila activa con `SELECT ... FOR UPDATE`. La escritura de auditoría de éxito ocurre dentro de la misma transacción que la mutación: si la auditoría falla, toda la operación se revierte. Los intentos denegados se registran aparte y no cambian claves.

La migración y el nuevo código Edge Function están en la rama, pero aún no se han aplicado ni desplegado en NOVA-STAGING. No considerar activadas las RPC hasta desplegar ambos componentes en staging y verificarlos.

La autenticación se valida contra Supabase Auth. Nunca se aceptan roles desde `user_metadata` ni se exponen claves privadas o service_role.

## Integración NOVA IA

NOVA IA PR draft #44 ya contiene la consulta request-scoped a `publisher.keys.list`: toma el JWT Supabase de la solicitud, lo reenvía a Nova Guard y no lo persiste. La rama de Nova Guard descrita aquí no debe considerarse desplegada solo porque exista una Edge Function de staging; la versión desplegada y la rama del PR son estados distintos.

La integración entre sesión real, NOVA IA y Nova Guard todavía no ha pasado una prueba E2E. Los fallos actuales de GitHub Actions no muestran pasos ejecutados ni logs, por lo que no validan ni invalidan el código.

## Riesgos y trabajo bloqueante antes de publicar

- Aplicar la migración atómica en NOVA-STAGING y desplegar la Edge Function desde la misma rama, luego probar rollback real ante fallo de auditoría y carreras de rotación.
- No existe aún una fuente protegida de roles administrativos ni acciones activas de submit/review/publish/revoke de módulos.
- No están implementadas la autorización de grants por versión ni las URLs firmadas de subida/descarga.
- Debe verificarse el vector de firma canónica Ed25519 de extremo a extremo entre Java, Python y TypeScript.
- Faltan pruebas autenticadas con dos usuarios y dos publicadores, clave revocada, expiración de sesión, carreras de rotación, auditoría fallida, hash/firma alterados y permisos cruzados.
- Los intentos denegados se auditan en una llamada separada; la atomicidad obligatoria aplica a los eventos de éxito asociados a cambios de clave.

## Seguridad de publicación

La clave privada de firma permanece con el publicador y nunca se sube al registro. El backend y cada cliente deben verificar hash y firma antes de instalar. La activación local no ejecuta código descargado. Solo los módulos con estado published y publicador activo deben aparecer en el catálogo.

## Despliegue y separación

NOVA-STAGING es un proyecto Supabase independiente. Los SQL en `supabase/staging/` son referencias específicas de staging y no migraciones para producción. No aplicar estos archivos directamente en producción. Cualquier ampliación requiere migración revisada, pruebas aisladas, backup y plan de rollback. No mover tablas existentes de Nova Nexus ni crear bases separadas por módulo sin mapa de consumidores y pruebas.

## Estado — 14 nov 2026

- NOVA-STAGING: cuatro tablas privadas, RLS + FORCE RLS, bucket privado de 64 MiB y Edge Function activa (versión desplegada 3, JWT obligatorio).
- Código de la rama Nova Guard: cinco acciones; registro/rotación/revocación ahora usan RPC transaccionales en código, pendientes de despliegue conjunto a staging.
- NOVA IA PR #44: integración request-scoped escrita; E2E pendiente.
- GitHub Actions: jobs fallan antes de mostrar pasos; no hay CI verificable.
- Producción: intacta; no se han fusionado PR ni desplegado cambios de estas ramas.
