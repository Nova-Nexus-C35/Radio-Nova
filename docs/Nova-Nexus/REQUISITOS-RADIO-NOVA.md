# Radio Nova — requisitos recuperados desde Nova-Nexus

Fuente: `Nova-Nexus/docs/02_APLICACIONES/APLICACIONES-NOVA.md` (rama main).

## Objetivo confirmado

Sintonizar emisoras cubanas sin Internet. Para ello se necesita receptor FM compatible y soporte del fabricante; una aplicación no puede crear recepción FM si el hardware no existe o el sistema operativo lo bloquea.

## Validaciones técnicas pendientes

- Identificar los teléfonos Android objetivo.
- Verificar receptor FM, API, permisos y restricciones del fabricante en cada dispositivo.
- Separar la recepción FM local del streaming por Internet.
- No afirmar funcionamiento offline hasta probarlo en hardware real.
- Registrar modelos compatibles e incompatibles y limitaciones por versión de Android.

## Estado

Este documento conserva únicamente requisitos de Radio Nova. Se detectó que una versión anterior contenía bloques de inventario de Nova Apps y Nova Novels que no pertenecen a este producto; esos bloques se retiran de esta ficha porque sus fuentes e informes ya viven en los repositorios propietarios correspondientes. No se borran ni modifican las fuentes originales.

## Referencias

- [Nova Workspace — índice maestro](https://github.com/Nova-Nexus-C35/Nova-Workspace/blob/main/ECOSYSTEM-TRANSITION-INDEX.md)
- [Nova Nexus — administración](https://github.com/Nova-Nexus-C35/Nova-Nexus)


## Código fuente recuperado — 2026-11-23

Se importaron 29 archivos de la implementación Android que estaba conservada en `Nova-Workspace/Projects/Applications/radio-nova/`. La aplicación existente prioriza streaming online y reproducción multimedia; no debe confundirse con la recepción FM terrestre offline que constituye el objetivo original. La recepción integrada/USB sigue pendiente de compatibilidad y pruebas por dispositivo.

El README upstream se preservó sin su sección de contacto en `docs/UPSTREAM-README-SANITIZED.md`. El manifiesto `SOURCE-TRANSFER-MANIFEST.json` registra procedencia y SHA. La importación no demuestra que el build Gradle haya sido ejecutado en este repositorio.
