# Recuperación del despliegue de Nova IA en Railway

**Última comprobación:** 10 de noviembre de 2026 (CircleCI validado; Railway aún sin despliegue)  
**Proyecto Railway:** `Nova-IA`  
**Entorno:** `production`  
**Servicio:** `nova-ia-api`  
**Repositorio:** `Cumba35/Nova-IA`, rama `main`

## Estado verificado

- El servicio Railway existe y está provisionado.
- No tiene fuente conectada (`source = null`).
- No tiene despliegue anterior (`latestDeployment = null`).
- No tiene despliegues en el historial consultado.
- Está configurado con directorio raíz `/backend`.
- El builder es Dockerfile y la ruta configurada es `Dockerfile` dentro del directorio raíz.
- El Dockerfile del backend usa Python 3.12 y arranca Uvicorn en el puerto 8000.
- El healthcheck configurado es `/health`.
- El volumen persistente `nova-ia-persistent-data` está montado en `/app/data`.
- La variable `NOVA_SQLITE_ENCRYPTION_KEY` existe en el servicio. Los valores de secretos no se leen ni se copian a Git.
- Las variables de servicio están presentes, pero el hecho de que existan no demuestra que la aplicación haya arrancado.

## Bloqueo confirmado

El intento de conectar la fuente con Railway para `Cumba35/Nova-IA` devuelve:

`User does not have access to the repo`

Se volvió a intentar conectar `Cumba35/Nova-IA` a la fuente del servicio existente desde la integración de Railway y el resultado volvió a ser `User does not have access to the repo`. La autorización de CircleCI es independiente y no concede permisos a Railway. No se debe crear un segundo servicio ni publicar el repositorio para evitar este control.

## Alternativa preparada: desplegar desde CircleCI sin conectar Railway a GitHub

Para evitar el error de permisos de la GitHub App de Railway, se añadió a `Cumba35/Nova-IA/.circleci/config.yml` el trabajo `railway-deploy`. CircleCI ya tiene acceso al repositorio privado y puede subir el código al servicio existente usando Railway CLI con un Project Token. El trabajo solo se ejecuta cuando se solicita explícitamente el parámetro `deploy_railway=true`, y siempre después de que `backend-checks` termine correctamente. No crea otro servicio.

Pasos pendientes del propietario:
1. En Railway, dentro del proyecto `Nova-IA`, crear un **Project Token** limitado a ese proyecto.
2. En CircleCI → proyecto `Cumba35/Nova-IA` → Project Settings → Environment Variables, guardar el token como `RAILWAY_TOKEN`. No pegarlo en GitHub ni en el chat.
3. En CircleCI, iniciar un pipeline para la rama `main` con `deploy_railway=true`.
4. Verificar el deployment `SUCCESS`, estado `ONLINE`, dominio y respuesta `/health`.

La ruta conserva los IDs del servicio existente, el directorio raíz Railway `/backend` y el volumen `/app/data`. El parámetro está desactivado por defecto; un push normal no despliega producción. Esta vía es preferible a cambiar los permisos de Railway GitHub App y mantiene privado el repositorio.

## Procedimiento de recuperación

1. En GitHub, abrir Settings → Applications → Installed GitHub Apps.
2. Abrir la instalación de Railway y seleccionar Configure.
3. Si la instalación limita el acceso a repositorios seleccionados, incluir `Cumba35/Nova-IA`.
4. Aprobar cualquier actualización pendiente de permisos de Railway. La autorización de CircleCI no sustituye este paso.
5. Confirmar que la cuenta GitHub vinculada a Railway tiene acceso de lectura al repositorio.
6. En Railway, abrir el proyecto `Nova-IA`, servicio `nova-ia-api`, Settings → Source.
7. Conectar `Cumba35/Nova-IA`, rama `main`. Mantener el directorio raíz `/backend`, Dockerfile `Dockerfile`, healthcheck `/health` y volumen `/app/data`.
8. Esperar a que el primer build y despliegue terminen. No considerar resuelto el bloqueo hasta que exista un deployment ID y su estado sea SUCCESS.
9. Verificar el endpoint `/health` mediante el dominio Railway y comprobar que no expone secretos ni información privada.
10. Ejecutar las pruebas de almacenamiento cifrado, lectura/descifrado, fallo cerrado sin clave, persistencia tras reinicio y recuperación desde backup cifrado.

Railway documenta que la aplicación GitHub debe tener acceso al repositorio y que las actualizaciones de permisos deben aceptarse antes de volver a conectar la fuente. Si el error persiste, se debe desconectar y reconectar el repositorio desde la configuración del servicio después de actualizar la instalación.

## Revisión adicional del 10 de noviembre de 2026

- El servicio `nova-ia-api` sigue sin fuente conectada, sin deployment y sin dominio público asignado.
- Se confirmó que el volumen `nova-ia-persistent-data` continúa montado en `/app/data` (500 MB, región `iad`). No se ha borrado ni recreado.
- Se volvió a intentar `connect_service_source` sobre el servicio existente con `Cumba35/Nova-IA`, rama `main`; Railway rechazó la operación por falta de acceso al repositorio.
- No se desplegó nada ni se alteraron variables/secretos.
- La lista actual de variables no incluye `NOVA_NOTIFY_SMTP_PASSWORD`; el correo SMTP no puede considerarse configurado. No añadir contraseñas al repositorio ni al chat.
- CircleCI publicó un estado `SUCCESS` verificable para `backend-checks` en el commit `ef7328eae2def0dd45d458449b96fe5af3074092`, contexto `ci/circleci: backend-checks`, el 10 de noviembre de 2026 a las 23:48:05 UTC. Ejecución: https://app.circleci.com/workflow/012d28ac-ee3b-4916-b572-abb07495c31a/job/9e10125d-d2fe-4e87-9910-9999b416d0c4. Se añadió después el trabajo opcional `railway-deploy` para desplegar mediante Project Token. El commit `3b1a91ce2c5cfa9fc0a8ef9cdbcf9ea7c11883c9` terminó con `backend-checks` y `railway-deploy` en `success`; el segundo trabajo fue un no-op porque el parámetro `deploy_railway` estaba desactivado. Aún falta que el propietario guarde `RAILWAY_TOKEN` en CircleCI y solicite el despliegue real. Esto no equivale a un build/deploy de producción. Railway continúa sin fuente, despliegues ni dominio.

## Nueva integración de almacenamiento del modelo (10–11 de noviembre de 2026)

Se añadió al repositorio privado Nova IA `backend/nova_ia/dropbox_storage.py` y su integración en `backend/training/bootstrap_model.py`. Dropbox se usa como caché durable primaria del modelo SafeTensors fijado; S3 queda como respaldo. La integración usa OAuth refresh y sesiones de carga para archivos grandes, valida el manifiesto/licencia/SHA-256 antes de activar el modelo y no registra tokens.

La ruta predeterminada está limitada a la carpeta de aplicación Dropbox `/models` (App Folder), con scopes mínimos `files.content.read`, `files.content.write` y `files.metadata.read`. La conexión Dropbox de ChatGPT no se reutiliza como token de Railway.

**Pendiente de configuración en Railway:** `DROPBOX_APP_KEY`, `DROPBOX_APP_SECRET` y `DROPBOX_REFRESH_TOKEN`. La lista de variables del servicio no contiene esos tres valores. El adaptador está implementado, pero no se ha podido probar una carga/restauración real ni confirmar que el modelo esté almacenado en Dropbox. Tras configurar los secretos, activar `NOVA_BOOTSTRAP_MODEL_ON_START=true` y verificar el primer arranque.

También se añadió `scripts/build_android.sh`, `docs/ANDROID-BUILD.md` y descarga CMake reproducible de llama.cpp. El build Android no se ha ejecutado; el pipeline CircleCI actual no incluye una tarea Android. No presentar el APK como compilado o probado.

## Entrenamiento automático: arquitectura segura preparada, worker aún no desplegado

El backend ahora expone `GET /internal/training/dataset`, protegido por `NOVA_TRAINING_EXPORT_TOKEN` independiente de `NOVA_OWNER_TOKEN`. Solo exporta ejemplos aprobados de `nova-curriculum` y repositorios públicos, descifrados dentro del API y filtrados antes de salir. El contenedor trainer no monta SQLite ni recibe `NOVA_SQLITE_ENCRYPTION_KEY` ni el token de propietario.

Se corrigió `Dockerfile.training` para copiar `nova_ia` y se actualizó Compose: el trainer separado puede acceder al API por la red interna, comparte solo `nova-models` y usa Dropbox para guardar los adaptadores LoRA. El API sincroniza adaptadores verificados cada 300 segundos por defecto. El estado del API ya no afirma que los cambios automáticos de pesos estén activos sin un worker comprobado.

**Pendiente en Railway:** crear un segundo servicio `nova-model-trainer` con `Dockerfile.training`, configurar `NOVA_TRAINING_EXPORT_TOKEN` en ambos servicios, `NOVA_TRAINING_DATA_URL` con el dominio privado del API y los secretos Dropbox. No se ha creado porque consume recursos adicionales y aún falta autorización de despliegue. No pasar al trainer el token de propietario ni la clave SQLite.

## Criterios de aceptación

- [ ] La fuente del servicio muestra `Cumba35/Nova-IA` y `main`.
- [ ] El primer despliegue termina en `SUCCESS`.
- [ ] El servicio queda `ONLINE`.
- [ ] Dropbox OAuth App Folder está autorizada y el modelo puede restaurarse/subirse con verificación SHA-256.
- [ ] `/health` responde correctamente.
- [ ] El script Android genera un APK y las pruebas JVM/native terminan correctamente.
- [ ] El backend rechaza el arranque si falta `NOVA_SQLITE_ENCRYPTION_KEY`.
- [ ] La base SQLite no contiene en claro los campos privados cifrados.
- [ ] El volumen conserva los datos después de un redeploy.
- [ ] Los logs no contienen claves, tokens ni payloads privados.
- [ ] Se prueba restauración desde una copia cifrada.

## Límites de seguridad

El backend FastAPI sigue documentado como plataforma en desarrollo. Aunque arranque, no debe anunciarse como API pública de producción hasta completar autenticación multiusuario, sesiones seguras, rate limiting, revisión de seguridad y pruebas de aislamiento. El token único del propietario es un control de desarrollo, no un sistema completo de identidad multiusuario.

La integración biométrica Android es independiente de este despliegue. Android debe usar BiometricPrompt y Android Keystore; no puede extraer plantillas biométricas protegidas por el sistema operativo. Solo muestras seleccionadas voluntariamente por el propietario y cifradas antes de subir pueden almacenarse, después de mostrar y registrar el consentimiento correspondiente.
