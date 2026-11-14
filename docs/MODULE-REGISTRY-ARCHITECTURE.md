# NOVA Module Registry — seguridad y datos

Estado: base de registro de claves implementada en NOVA-STAGING; publicación de módulos y autorización de artefactos siguen pendientes. Producción no se ha modificado.
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

## Acciones implementadas

La Edge Function `nova-module-registry` está activa en NOVA-STAGING con `verify_jwt=true`. Las acciones disponibles son:
- `catalog.list`: devuelve únicamente módulos publicados y con clave activa.
- `publisher.register`: registra una clave pública Ed25519 vinculada al usuario autenticado.
- `publisher.keys.list`: lista solo las claves del propietario autenticado.
- `publisher.rotate`: registra una clave nueva y revoca la anterior, con compensación si no se confirma la revocación.
- `publisher.revoke`: revoca una clave activa que pertenece al usuario.

La autenticación se valida contra Supabase Auth. Nunca se aceptan roles desde `user_metadata` ni se exponen claves privadas o service_role.

## Integración NOVA IA

NOVA IA PR draft #44 ya contiene la consulta request-scoped a `publisher.keys.list`: toma el JWT Supabase de la solicitud, lo reenvía a Nova Guard y no lo persiste. La rama de Nova Guard descrita aquí no debe considerarse desplegada solo porque exista una Edge Function de staging; la versión desplegada y la rama del PR son estados distintos.

La integración entre sesión real, NOVA IA y Nova Guard todavía no ha pasado una prueba E2E. Los fallos actuales de GitHub Actions no muestran pasos ejecutados ni logs, por lo que no validan ni invalidan el código.

## Riesgos y trabajo bloqueante antes de publicar

- La rotación actual usa varias peticiones REST (insertar nueva clave y revocar la anterior); no es una única transacción de base de datos. La compensación reduce el riesgo, pero concurrencia o fallo de red pueden requerir reconciliación manual. Antes de depender de rotaciones en producción, mover la operación a una RPC transaccional y probar carreras.
- La escritura de auditoría se intenta después de la operación de clave y registra un error de servidor si falla; no comparte una transacción atómica con el cambio. Si la auditoría debe ser obligatoria, integrar escritura y mutación en la misma RPC/transacción.
- No hay fuente protegida de roles administrativos ni acciones activas de submit/review/publish/revoke de módulos.
- No están implementadas la autorización de grants por versión ni las URLs firmadas de subida/descarga.
- Debe verificarse el vector de firma canónica Ed25519 de extremo a extremo entre Java, Python y TypeScript.
- Faltan pruebas autenticadas con dos usuarios y dos publicadores, clave revocada, expiración de sesión, carreras de rotación, auditoría fallida, hash/firma alterados y permisos cruzados.

## Seguridad de publicación

La clave privada de firma permanece con el publicador y nunca se sube al registro. El backend y cada cliente deben verificar hash y firma antes de instalar. La activación local no ejecuta código descargado. Solo los módulos con estado published y publicador activo deben aparecer en el catálogo.

## Despliegue y separación

NOVA-STAGING es un proyecto Supabase independiente. El SQL en `supabase/staging/` es una referencia de bootstrap, no una migración CLI para producción. No aplicar ese archivo directamente en producción. Cualquier ampliación requiere migración revisada, pruebas aisladas, backup y plan de rollback. No mover tablas existentes de Nova Nexus ni crear bases separadas por módulo sin mapa de consumidores y pruebas.

## Estado — 14 nov 2026

- NOVA-STAGING: cuatro tablas privadas, RLS + FORCE RLS, bucket privado de 64 MiB y Edge Function activa (versión desplegada 3, JWT obligatorio).
- Código de la rama Nova Guard: cinco acciones de catálogo/gestión de claves; la rama modificada no se ha desplegado automáticamente.
- NOVA IA PR #44: integración request-scoped escrita; E2E pendiente.
- GitHub Actions: jobs fallan antes de mostrar pasos; no hay CI verificable.
- Producción: intacta; no se han fusionado PR ni desplegado cambios de estas ramas.
