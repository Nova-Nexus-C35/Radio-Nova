# Recuperación del despliegue de Nova IA en Railway

**Última comprobación:** 10 de noviembre de 2026  
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

La conexión GitHub disponible en Railway no tiene acceso efectivo a ese repositorio privado, o tiene permisos pendientes/desactualizados. No se debe crear un segundo servicio ni publicar el repositorio para evitar este control.

## Procedimiento de recuperación

1. En GitHub, abrir Settings → Applications → Installed GitHub Apps.
2. Abrir la instalación de Railway y seleccionar Configure.
3. Si la instalación limita el acceso a repositorios seleccionados, incluir `Cumba35/Nova-IA`.
4. Aprobar cualquier actualización pendiente de permisos de Railway.
5. Confirmar que la cuenta GitHub vinculada a Railway tiene acceso de lectura al repositorio.
6. En Railway, abrir el proyecto `Nova-IA`, servicio `nova-ia-api`, Settings → Source.
7. Conectar `Cumba35/Nova-IA`, rama `main`. Mantener el directorio raíz `/backend`, Dockerfile `Dockerfile`, healthcheck `/health` y volumen `/app/data`.
8. Esperar a que el primer build y despliegue terminen. No considerar resuelto el bloqueo hasta que exista un deployment ID y su estado sea SUCCESS.
9. Verificar el endpoint `/health` mediante el dominio Railway y comprobar que no expone secretos ni información privada.
10. Ejecutar las pruebas de almacenamiento cifrado, lectura/descifrado, fallo cerrado sin clave, persistencia tras reinicio y recuperación desde backup cifrado.

Railway documenta que la aplicación GitHub debe tener acceso al repositorio y que las actualizaciones de permisos deben aceptarse antes de volver a conectar la fuente. Si el error persiste, se debe desconectar y reconectar el repositorio desde la configuración del servicio después de actualizar la instalación.

## Criterios de aceptación

- [ ] La fuente del servicio muestra `Cumba35/Nova-IA` y `main`.
- [ ] El primer despliegue termina en `SUCCESS`.
- [ ] El servicio queda `ONLINE`.
- [ ] `/health` responde correctamente.
- [ ] El backend rechaza el arranque si falta `NOVA_SQLITE_ENCRYPTION_KEY`.
- [ ] La base SQLite no contiene en claro los campos privados cifrados.
- [ ] El volumen conserva los datos después de un redeploy.
- [ ] Los logs no contienen claves, tokens ni payloads privados.
- [ ] Se prueba restauración desde una copia cifrada.

## Límites de seguridad

El backend FastAPI sigue documentado como plataforma en desarrollo. Aunque arranque, no debe anunciarse como API pública de producción hasta completar autenticación multiusuario, sesiones seguras, rate limiting, revisión de seguridad y pruebas de aislamiento. El token único del propietario es un control de desarrollo, no un sistema completo de identidad multiusuario.

La integración biométrica Android es independiente de este despliegue. Android debe usar BiometricPrompt y Android Keystore; no puede extraer plantillas biométricas protegidas por el sistema operativo. Solo muestras seleccionadas voluntariamente por el propietario y cifradas antes de subir pueden almacenarse, después de mostrar y registrar el consentimiento correspondiente.
