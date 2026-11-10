# Consentimiento de datos biométricos del propietario

**Versión:** `nova-biometric-consent-v1`  
**Responsable:** propietario de la cuenta Nova  
**Sistema:** Nova Guard

## Alcance

Este consentimiento se aplica exclusivamente a datos biométricos propios del propietario y a la finalidad que seleccione al activar una función. No autoriza capturar, identificar, clasificar ni almacenar datos biométricos de otras personas.

## Qué puede almacenarse

- Plantillas o representaciones faciales creadas expresamente por el propietario para una función de verificación de Nova.
- Muestras cifradas que el propietario seleccione voluntariamente para esa misma función.
- Metadatos mínimos de la inscripción, versión del modelo y referencia de la clave del dispositivo.

Los archivos deben cifrarse en el dispositivo antes de subirse al bucket privado `nova-guard-private`. El bucket solo acepta objetos binarios cifrados. No se permite guardar imágenes, plantillas, vectores faciales ni datos biométricos en claro.

## Qué nunca se extrae del dispositivo

La autenticación biométrica nativa de Android (huella, rostro u otra modalidad del sistema) permanece bajo el control del sistema operativo y del hardware seguro. Nova no extrae ni copia las plantillas biométricas protegidas por Android. Android Keystore puede proteger una clave que requiera autenticación biométrica local, pero esa clave no es una plantilla biométrica.

## Acceso y uso

- El propietario conserva el control de sus datos y puede consultar el registro de consentimientos.
- Nova IA solo puede acceder a objetos biométricos con autenticación válida, finalidad autorizada y consentimiento activo.
- Los datos biométricos no se usan para entrenar modelos generales ni para identificar a terceros.
- No se comparten con otros usuarios ni se publican.
- Los logs y metadatos no deben contener muestras, vectores, imágenes, claves ni contenido biométrico.

## Revocación y eliminación

El propietario puede revocar cada finalidad. La revocación bloquea nuevas lecturas desde Nova Guard y marca los objetos asociados como revocados. Después, el cliente debe eliminar los objetos del almacenamiento y retirar las claves locales asociadas. El registro de revocación se conserva como evidencia de auditoría; no conserva el contenido biométrico.

## Riesgos y límites

El cifrado protege los objetos almacenados, pero no elimina los riesgos de un dispositivo comprometido, una clave perdida o un cliente malicioso. Si se pierde la clave necesaria para descifrar los objetos, Nova no puede reconstruir la plantilla. La autenticación biométrica del dispositivo y el reconocimiento facial propio de Nova son funciones distintas y no deben confundirse.

## Regla de activación

No se debe crear ni subir ningún objeto biométrico hasta que el cliente muestre este documento, obtenga una aceptación explícita para la finalidad correspondiente y registre la versión `nova-biometric-consent-v1`. La aceptación de almacenamiento no habilita automáticamente reconocimiento facial, verificación de identidad ni entrenamiento.
