# Radio Nova

Repositorio propietario de Radio Nova.

## Objetivo confirmado

Aplicación para sintonizar emisoras de radio cubanas sin Internet cuando el dispositivo disponga de receptor FM compatible y el fabricante permita el acceso a ese hardware. La aplicación no puede crear recepción FM por software si el hardware no existe o el sistema lo bloquea.

## Requisitos y validaciones pendientes

- Identificar los teléfonos Android objetivo y comprobar receptor FM, API y restricciones del fabricante.
- Diseñar una capa de compatibilidad por dispositivo.
- Separar recepción FM real de streaming por Internet; el streaming no cumple el requisito offline.
- Añadir catálogo de emisoras, controles de sintonía, favoritos y persistencia local.
- Probar en hardware real antes de afirmar que funciona sin Internet.

## Estado actual

La documentación de requisitos está registrada en `docs/Nova-Nexus/REQUISITOS-RADIO-NOVA.md`. El repositorio es pequeño y la reconstrucción de la aplicación, integración de hardware y pruebas en dispositivos están pendientes.

## Referencias

- [Nova Workspace — índice maestro](https://github.com/Nova-Nexus-C35/Nova-Workspace/blob/main/ECOSYSTEM-TRANSITION-INDEX.md)
- [Nova Nexus — administración](https://github.com/Nova-Nexus-C35/Nova-Nexus)
