# Contratos comunes NOVA — versión 1

Este directorio define un formato de manifiesto versionado para aplicaciones y módulos. El manifiesto es metadato del producto; no concede permisos por sí mismo y nunca contiene tokens, contraseñas, claves privadas, sesiones ni datos personales.

## Principios obligatorios

1. Denegar por defecto: una capacidad no declarada no se considera autorizada.
2. La declaración de permisos describe intención; el backend debe volver a comprobar cada operación.
3. Acciones destructivas, publicación, exportación y administración requieren aprobación explícita y auditable.
4. El propietario de un recurso conserva el control de sus datos; los roles visuales no bastan.
5. Los permisos se limitan por recurso y alcance: self, participant, group, tenant, ecosystem u owner-only.
6. Separar datos públicos, privados, sensibles, financieros y biométricos.
7. Los secretos solo viven en almacenes seguros del runtime, nunca en manifiestos, APK, logs o Git.
8. Las capacidades locales/offline y las dependencias de red se declaran explícitamente.
9. Versionar el contrato y rechazar claves desconocidas.
10. La revocación debe surtir efecto en servidor sin esperar la expiración de un token con privilegios incrustados.

## Archivos

- contracts/nova-module-manifest.schema.json: JSON Schema Draft 2020-12.
- manifests/: manifiestos no secretos de referencia.
- scripts/validate_module_manifests.py: validador Python de biblioteca estándar.
- tests/test_validate_module_manifests.py: pruebas unitarias.

## Límite

El contrato y el validador están versionados; todavía no existe un servicio NOVA central de identidad/autorización que ejecute estas políticas. No se deben presentar como autorización operativa ni migrar datos privados hasta implementar y probar ese servicio con aislamiento de cuentas y recuperación.
