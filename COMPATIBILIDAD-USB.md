# Matriz de compatibilidad de Radio Nova

Esta tabla se completa únicamente con dispositivos que hayan sido probados físicamente. No se debe inferir compatibilidad por el conector USB o por el nombre comercial.

| Fuente | Fabricante/modelo | VID:PID | Chipset | Bandas | Protocolo/API | Teléfono probado | Resultado |
|---|---|---|---|---|---|---|---|
| Integrado | Pendiente de pruebas | — | — | — | API/servicio del fabricante | — | No verificado |
| USB OTG | Pendiente de selección y pruebas | — | — | — | Driver/protocolo por implementar | — | No verificado |

## Requisitos para añadir un receptor USB

Registrar:
- Fabricante y modelo exacto.
- USB VID y PID.
- Chipset y revisión.
- Bandas y rango de frecuencias.
- Protocolo de control documentado y licencia.
- Requisitos de energía y antena.
- Versión mínima de Android y modo USB Host.
- Teléfonos probados y resultado de recepción real.
- Prueba en modo avión sin Wi-Fi ni datos móviles.

## Reglas

1. No clasificar un adaptador USB de audio como receptor RF salvo que incorpore un sintonizador real.
2. No instalar firmware o drivers propietarios sin autorización/licencia.
3. No mostrar un dispositivo como compatible hasta completar una prueba reproducible.
4. Mantener separado el resultado de recepción terrestre del resultado de streaming online.
5. Registrar fallos de alimentación OTG, permisos USB, antena y estabilidad.

## Estado

Matriz inicial creada. No se ha certificado todavía ningún modelo integrado ni receptor USB externo. La compatibilidad se irá incorporando después de pruebas con hardware real.


## Diagnóstico incorporado en Radio Nova 0.2.1

La pantalla de radio terrestre consulta el inventario USB de Android y muestra:
- Nombre del dispositivo cuando el sistema lo proporciona.
- VID:PID en hexadecimal.
- Si la aplicación ya tiene permiso USB.
- Botón para actualizar el inventario.

Esta función solo diagnostica la conexión. No solicita permiso automáticamente ni controla el sintonizador hasta que exista un perfil compatible y una implementación de driver validada.
