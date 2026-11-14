# NOVA Module Registry — seguridad y datos

Estado: diseño de esquema; no desplegado ni aplicado en Supabase.  
Autoridad: Nova Guard.

## Objetivo

Centralizar la confianza de publicadores, el catálogo firmado, las autorizaciones remotas y la auditoría del ciclo de vida de módulos NOVA. Los módulos instalados y sus datos de usuario siguen siendo propiedad del dispositivo/módulo; este registro solo guarda metadatos y referencias de artefactos.

## Modelo de datos propuesto

Las tablas se ubicarán en el esquema privado `private`, sin acceso directo desde Data API:

### `nova_module_publishers`
- `publisher_id` (ID opaco y estable del publicador) y `publisher_key_id` (ID único de cada clave; coincide con `artifact.publisherKeyId` del manifiesto).
- `owner_user_id` y `created_by` para vincular el publicador a una cuenta autenticada; la propiedad no se deduce de metadatos editables del perfil.
- `public_key_base64` (clave pública Ed25519 DER SubjectPublicKeyInfo en Base64; nunca clave privada).
- `algorithm` (actualmente Ed25519), `status` (active/revoked), `created_at`, `revoked_at`, `supersedes_key_id`.
- Rotar crea una nueva fila/ID de clave y conserva la anterior para verificar firmas históricas y auditar; no se sobrescribe silenciosamente.

### `nova_module_registry`
- `module_id`, `version` (clave única).
- `manifest` JSONB validado contra el contrato versionado del Workspace.
- `execution_mode` (local/remote/hybrid).
- `artifact_sha256`, `signature_base64`, `publisher_id`.
- `artifact_storage_path` dentro del bucket privado `nova-modules`; nunca una URL pública permanente.
- `created_by`, `reviewed_by`, `reviewed_at`, `published_at`, `revoked_at` y motivo de revisión/revocación.
- `status` (draft/review/published/revoked), timestamps.
- Nunca guardar código de módulo en filas JSON ni URL pública sin firma.

### `nova_module_user_grants`
- `owner_user_id`, `module_id`, `version`.
- Permisos aprobados, estado, `granted_by`, `granted_at`, `expires_at`, `revoked_at` y motivo de revocación.
- Una concesión identifica una versión concreta; una nueva versión no hereda permisos nuevos automáticamente.
- Revocar debe impedir nuevas descargas/invocaciones remotas inmediatamente; las sesiones/cachés locales deben respetar expiración corta y consultar revocación.

### `nova_module_audit`
- Actor, rol verificado, módulo/versión, acción, resultado, fecha, identificador de solicitud y metadatos técnicos permitidos.
- No registrar tokens, claves, payloads privados, URLs firmadas, contenido del usuario ni datos personales innecesarios.

## Límites de acceso

- RLS habilitado y FORCE RLS en todas las tablas privadas.
- Revocar privilegios de `PUBLIC`, `anon` y `authenticated`; conceder acceso de datos solo al rol de servidor `service_role` desde Edge Functions.
- Los clientes Android/web nunca reciben la clave `service_role`.
- Las Edge Functions exigen JWT cuando operan en nombre de usuario y verifican rol/ownership desde datos confiables del servidor, nunca desde `user_metadata`. El backend NOVA IA consume únicamente la clave pública DER en Base64; el formato de firma canónica se comparte con el runtime Java 17.
- Las operaciones administrativas de publicación exigen rol verificado por backend, aprobación y auditoría.
- El bucket `nova-modules` será privado; clientes no obtienen enlaces públicos permanentes. Descargas autenticadas con URL firmada de corta duración después de autorización.

## Contrato de API y permisos de servidor

- La identidad del publicador y los roles administrativos se verifican con Supabase Auth y tablas de roles confiables consultadas en servidor. No se aceptan roles desde `user_metadata`, parámetros del cliente ni claims no verificados.
- Las tablas `private.*` no se exponen a Data API ni reciben grants para `PUBLIC`, `anon` o `authenticated`. RLS y FORCE RLS se mantienen como defensa adicional.
- Si el Edge Function necesita RPC, se usa un esquema API dedicado y funciones `SECURITY INVOKER`; el permiso EXECUTE se concede solo a `service_role`, revocándolo expresamente a `PUBLIC`, `anon` y `authenticated`. No se introduce una función `SECURITY DEFINER` pública para saltarse RLS.
- Acciones mínimas: `catalog.list`, `publisher.register`, `publisher.rotate`, `publisher.revoke`, `module.submit`, `module.review`, `module.publish`, `module.revoke`, `grant.request`, `grant.approve`, `grant.revoke`, `artifact.upload-url` y `artifact.download-url`.
- El catálogo público devuelve únicamente metadatos de módulos `published`; no incluye rutas privadas ni enlaces de descarga.
- La descarga requiere JWT válido, módulo publicado, publicador activo y concesión vigente para la versión/permisos solicitados. La URL firmada es de vida corta; su caducidad limita el intervalo de exposición tras una revocación.
- Las operaciones administrativas y de permisos se auditan en la misma transacción que el cambio de estado.

## Firma y publicación

1. El publicador firma el manifiesto canónico y el SHA-256 del artefacto con su clave privada fuera de Git.
2. Nova Guard conserva únicamente la clave pública de confianza y su estado de revocación.
3. El backend verifica hash, firma, estado del publicador, compatibilidad, licencias y permisos.
4. Solo el estado `published` se expone al catálogo.
5. Revocar un publicador bloquea nuevas instalaciones y actualizaciones; se conserva el registro de auditoría.
6. Los clientes verifican otra vez firma/hash antes de instalar, aunque el backend ya haya validado.

## Separación de datos existente

La inspección de Nova-Nexus encontró 91 tablas en `public` y `private`, todas con RLS habilitado. No se moverán masivamente tablas existentes ni se creará una base por módulo sin mapa de consumidores y pruebas. La primera fase mantiene las tablas de cuentas, identidad, memoria, banco y auditoría donde están; los módulos nuevos usan namespaces y APIs. La separación física solo se considera si hay una justificación de aislamiento, escala o ciclo de vida.

## Despliegue por etapas

1. Revisar este modelo y el contrato de manifiesto.
2. Crear migración mediante Supabase CLI en un entorno de desarrollo/staging.
3. Aplicar RLS, grants, bucket privado y pruebas de acceso cruzado.
4. Implementar Edge Functions de catálogo, publicación, grants y descarga.
5. Probar con dos usuarios y dos publicadores de prueba; verificar revocación.
6. Ejecutar pruebas con dos usuarios, publicador revocado, permisos insuficientes, versiones incompatibles, hash alterado y URL expirada.
7. Desplegar a producción solo tras backup, aprobación de seguridad y rollback probado.

## Estado

No hay tablas nuevas, bucket ni Edge Functions de registro de módulos creados por este documento. La inspección de solo lectura del proyecto Supabase Nova-Nexus (14 nov 2026) confirmó que no aparecen tablas `nova_module_*` ni una Edge Function `nova-module-registry`; no se aplicó ningún cambio remoto.

El runtime compartido de verificación de manifiestos/Ed25519 se está preparando en `Cumba35/Nova-Nexus`, PR draft #16. Ese código verifica el contrato y la firma, pero aún no consulta este registro persistente.

La generación de la migración sigue bloqueada hasta disponer de Supabase CLI local: no está instalado en el entorno de trabajo actual. No se improvisará un nombre de migración ni se aplicará DDL directamente a producción. Esta especificación no es una afirmación de implementación.
