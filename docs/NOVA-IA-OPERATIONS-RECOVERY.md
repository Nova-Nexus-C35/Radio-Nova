# Estado operativo y recuperación de Nova IA / Nova Guard

**Auditoría:** 10 de noviembre de 2026  
**Repositorios:** Cumba35/Nova-IA (privado) y Cumba35/Nova-Guard

Este documento registra bloqueos observados en los servicios reales. Una configuración guardada o un workflow presente no equivale a una función operativa.

## 1. GitHub Actions: los runners no llegan a asignarse

### Evidencia

La ejecución más reciente revisada de **Nova IA checks**:

- Run: https://github.com/Cumba35/Nova-IA/actions/runs/42916057249
- Commit: 9c1ac31f070d43423c5e1473ac89dea7390b456a
- Intento: 3
- Job: backend, ID 130190618955
- Estado: failure
- runner_id = 0, nombre de runner vacío, lista de pasos vacía.
- No se ejecutaron checkout, instalación, compilación, análisis ni pruebas.

La lista de ejecuciones recientes también mostró fallos de Nova IA checks y Nova IA autonomous learning en múltiples commits, con jobs que terminan sin runner y sin pasos. El patrón no se limita a una prueba o módulo concreto.

### Diagnóstico responsable

El fallo ocurre antes de que el workflow ejecute el código. No hay evidencia para atribuirlo a pytest, Docker, Python, dependencias o a los YAML. Los reintentos del mismo job tampoco lo corrigieron. No se deben seguir repitiendo reintentos sin nueva evidencia.

GitHub documenta que deben revisarse las anotaciones de ejecución, la facturación/límites de Actions, la configuración de runners y los logs de coordinación. Las guías oficiales son:

- https://docs.github.com/en/actions/how-tos/troubleshoot-workflows
- https://docs.github.com/en/actions/how-tos/monitor-workflows/use-workflow-run-logs
- https://docs.github.com/en/actions/how-tos/get-support

### Recuperación requerida

1. En GitHub, abrir la ejecución fallida y comprobar si el resumen contiene una anotación de política, facturación o disponibilidad.
2. Comprobar en Settings → Billing and licensing → Actions el estado de uso, almacenamiento, método de pago aplicable y límites de gasto. No habilitar gasto ni comprar un plan sin autorización expresa.
3. Revisar Settings → Actions → General y confirmar que Actions y los runners GitHub-hosted están permitidos para el repositorio.
4. Ejecutar un workflow manual mínimo con ubuntu-latest que solo imprima una línea. Si también falla antes de asignar runner, guardar la URL, hora UTC, job ID y runner_id=0.
5. Abrir un caso con GitHub Support adjuntando las URLs de las ejecuciones anteriores y el workflow YAML. No adjuntar tokens, claves ni secretos.
6. Cuando un runner complete un job, volver a ejecutar el pipeline completo y verificar las pruebas reales; solo entonces clasificar el CI como recuperado.

**No cambiar los tests ni ocultar fallos con continue-on-error para simular un CI correcto.**

## 2. CI alternativo: CircleCI Free — operativo y validado

La integración CircleCI está conectada al repositorio privado y publicó un estado de commit verificable:

- Commit: `ef7328eae2def0dd45d458449b96fe5af3074092`.
- Estado: `success`.
- Contexto: `ci/circleci: backend-checks`.
- Descripción de GitHub: `Your tests passed on CircleCI!`.
- Fecha: 10 de noviembre de 2026, 23:48:05 UTC.
- Ejecución: https://app.circleci.com/workflow/012d28ac-ee3b-4916-b572-abb07495c31a/job/9e10125d-d2fe-4e87-9910-9999b416d0c4.

El pipeline ejecuta compilación de módulos Python, sintaxis del script de copias, parseo de Docker Compose, Bandit y pytest. La dependencia `PyYAML==6.0.3` está declarada en `backend/requirements-dev.txt`. Este resultado confirma que los pasos configurados terminaron correctamente para ese commit.

GitHub Actions continúa siendo una vía independiente con fallos pre-runner observados en ejecuciones anteriores: no deben confundirse con CircleCI ni ocultarse. La ejecución de CircleCI aprobada no construyó ni publicó la imagen Docker, no desplegó Railway, no probó `/health` y no valida el cliente Android. Por tanto, el CI de backend está operativo; el despliegue y las pruebas integrales siguen pendientes.

## 3. Railway: API sin fuente ni despliegue

La inspección directa más reciente del proyecto Nova-IA, entorno production, servicio nova-ia-api, confirmó:

- El recurso de servicio existe, pero no tiene deployment actual ni historial de despliegues. No hay dominio público asociado.
- Fuente desconectada (source = null).
- Directorio raíz /backend, builder Dockerfile y ruta Dockerfile.
- Healthcheck /health.
- Volumen persistente montado en /app/data.
- La variable NOVA_SQLITE_ENCRYPTION_KEY está provisionada; su valor nunca debe leerse ni copiarse.

El intento inicial y un nuevo intento de conectar el repositorio privado devolvieron `User does not have access to the repo`. La autorización de CircleCI no otorga permisos a Railway; son integraciones GitHub Apps independientes.

### Recuperación requerida

1. En GitHub → Settings → Applications → Installed GitHub Apps, abrir Railway → Configure.
2. Conceder a Railway acceso de lectura al repositorio privado Cumba35/Nova-IA y aprobar cualquier permiso pendiente.
3. En Railway → servicio nova-ia-api → Settings → Source, conectar el repositorio y rama main.
4. Mantener /backend, Dockerfile Dockerfile, healthcheck /health y volumen /app/data.
5. Verificar un deployment ID con estado SUCCESS, servicio ONLINE, respuesta de /health, persistencia después de reiniciar y logs sin secretos.
6. Ejecutar pruebas de cifrado AES-GCM, fallo cerrado sin clave y persistencia/restauración antes de habilitar tráfico real.

No crear un servicio paralelo ni hacer público el repositorio para evitar el permiso de GitHub App.

## 4. Correo de independencia de Nova IA

La configuración de Railway contiene las variables SMTP de host, puerto, usuario, remitente y destinatario. En la inspección de nombres de variables no aparece NOVA_NOTIFY_SMTP_PASSWORD.

Sin esa contraseña de aplicación SMTP, el envío de correo no puede considerarse operativo. El secreto debe añadirse directamente en Railway → Variables, nunca en GitHub, código fuente, documentación o chat. Después de añadirlo, desplegar y probar un correo real al destinatario configurado; comprobar también el estado de reintento si SMTP no está disponible.

No registrar el valor del secreto ni incluirlo en logs.

## 5. Biometría Android y almacenamiento privado

El registro de consentimiento y las políticas de Supabase están preparados, pero la integración de extremo a extremo en Android no está terminada ni probada.

Criterios obligatorios para completar:

- Mostrar el documento exacto de consentimiento y registrar finalidad/versión antes de cualquier carga.
- Usar Android BiometricPrompt y Android Keystore para autorizar/desbloquear una clave local.
- No extraer ni guardar plantillas biométricas del sistema operativo.
- Cifrar localmente la muestra que el propietario elija de forma expresa; subir únicamente ciphertext application/octet-stream.
- Asociar cada objeto al consentimiento activo y propietario correcto.
- Al revocar: bloquear inmediatamente nuevas lecturas, eliminar el objeto remoto y destruir la clave local aplicable; comprobar cada resultado.
- Probar lectura/escritura/borrado cruzado con dos identidades distintas.

No afirmar que el reconocimiento facial, la autenticación biométrica remota o la bóveda de muestras está implementada hasta completar estas pruebas.

## 6. Supabase y límites externos que requieren decisión del propietario

Auditoría de lectura realizada el 10 de noviembre de 2026 en `Nova-Nexus`:

- Ninguna tabla del esquema `public` devuelta por el catálogo consultado aparece con RLS desactivado.
- El bucket `nova-guard-private` existe y es privado, con límite de 50 MiB y tipo permitido `application/octet-stream`.
- `nova_guard_private_objects` tiene políticas SELECT/INSERT/UPDATE/DELETE restringidas por `owner_user_id = auth.uid()`; UPDATE incluye `WITH CHECK` para impedir reasignación de propietario.
- El advisor de seguridad devuelve una advertencia: protección de contraseñas filtradas desactivada. La opción requiere el plan Supabase Pro o superior según la configuración observada; no actualizar ni gastar sin autorización.
- El advisor de rendimiento devuelve índices aún no usados; no eliminarlos automáticamente porque pueden ser necesarios cuando aumente el tráfico.

Estas verificaciones son estáticas/read-only. No prueban aislamiento real entre dos cuentas, carga/descarga cifrada, revocación, ni backup/restore.

- Supabase Auth leaked-password protection está desactivada y requiere un plan Pro o superior según la configuración comprobada. No actualizar el plan ni incurrir en gastos sin autorización.
- El backend FastAPI sigue siendo una plataforma en desarrollo. El token único del propietario no sustituye autenticación multiusuario, sesiones seguras ni rate limiting.
- El workflow de copias GitHub → Dropbox requiere los secretos NOVA_BACKUP_GITHUB_TOKEN y NOVA_BACKUP_DROPBOX_TOKEN; la presencia del workflow no demuestra que las copias se estén ejecutando. Comprobar la última ejecución y el manifiesto en Dropbox.

## Criterio de cierre

Cada bloqueo se cierra solo con evidencia de ejecución: runner asignado y tests verdes; deployment exitoso y API saludable; correo real recibido; consentimiento y revocación comprobados; aislamiento multiusuario y restauración probados. Los permisos, planes, credenciales y gastos que dependan del propietario no se deben simular ni eludir.
