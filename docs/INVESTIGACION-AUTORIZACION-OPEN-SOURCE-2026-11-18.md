# Investigación pública: contratos y autorización NOVA — 18-11-2026

## Alcance

Se revisaron los README y patrones de los repositorios públicos:
- https://github.com/apache/casbin
- https://github.com/openfga/openfga
- https://github.com/cerbos/cerbos

La revisión es arquitectónica; no se copió código ni se añadió una dependencia externa al ecosistema.

## Patrones aprovechables

### Apache Casbin
- Modelos ACL, RBAC, RBAC con dominios/tenants y ABAC.
- Separación explícita entre sujeto, objeto, acción, efecto y matcher.
- Útil como referencia para permisos por rol y atributos, con denegación prioritaria.

### OpenFGA
- Autorización fina basada en relaciones entre sujetos, recursos y relaciones.
- Adecuado como referencia para propiedad, miembros de grupos, participantes y herencia de acceso.
- Su despliegue como servicio y almacenamiento persistente no debe asumirse disponible en NOVA; el README distingue claramente el modo en memoria de producción.

### Cerbos
- Separación entre la aplicación y un Policy Decision Point (PDP).
- Decisiones contextuales sobre principal, acción y recurso.
- Políticas declarativas y posibilidad de probar decisiones antes de desplegarlas.

## Decisión técnica recomendada para NOVA

1. Mantener Nova Guard como fuente canónica del contrato y de las políticas versionadas.
2. Mantener el manifiesto v1.0 compatible: describe capacidades y permisos solicitados, nunca concede acceso.
3. Diseñar un PDP propio de NOVA detrás de una interfaz estable, sin acoplar todos los clientes a Casbin, OpenFGA o Cerbos.
4. Evaluar el modelo híbrido:
   - RBAC para roles generales y administración de funciones.
   - ABAC para estado, nivel de verificación, consentimiento y contexto.
   - Relaciones para propiedad, participantes, grupos y pertenencia a espacios.
5. Toda decisión de acceso se evalúa en servidor: principal autenticado, acción, recurso, propietario, tenant/grupo, atributos, consentimiento y estado de revocación.
6. Denegar por defecto; una ausencia, error o versión desconocida de política nunca se convierte en permiso.
7. La interfaz y los manifiestos no son controles de seguridad. No confiar en roles enviados por el cliente, flags locales ni botones ocultos.
8. Registrar decisiones y cambios de política sin registrar secretos ni contenido privado.
9. Separar la aprobación explícita de una operación de la autorización: approval: true en un manifiesto significa que el módulo declara que la operación requiere aprobación; no demuestra que el usuario la haya aprobado.
10. Mantener modo offline limitado a datos y acciones locales autorizados previamente. Las operaciones privilegiadas, publicación, exportación sensible y cambios de permisos requieren comprobación en servidor y, cuando corresponda, confirmación específica.

## Riesgos detectados en la fase estática

- El validador Python no limitaba repository.default_branch a 80 caracteres ni dependencies[].version_range a 40, aunque el JSON Schema sí lo hace.
- El proceso de validación podía lanzar TypeError al registrar IDs duplicados si un manifiesto malformado contenía un ID no hashable (por ejemplo, una lista).
- La suite original no cubría esos tres casos.
- No se encontró una implementación canónica de autorización runtime en Nova Guard; la documentación y el registro de Nexus ya declaran que esa fase está pendiente.

## Estado

Esta investigación no habilita un motor externo ni activa permisos. El siguiente trabajo debe implementar y probar el PDP propio, con casos positivos y negativos, dos cuentas independientes, aislamiento por propietario/tenant, revocación inmediata y auditoría. No migrar datos privados ni declarar autorización operativa antes de superar esas pruebas.
