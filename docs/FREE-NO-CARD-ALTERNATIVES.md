# Alternativas gratuitas sin tarjeta — Nova IA

**Fecha:** 10 de noviembre de 2026

Se preparó una ruta alternativa para no depender de GitHub Actions ni de Railway cuando sus permisos o runners bloquean el avance.

## Opción seleccionada para CI

**CircleCI Free**: 0 USD, sin tarjeta según su página oficial; 30.000 créditos al mes para proyectos privados y hasta 6.000 minutos con la clase Docker pequeña. Se añadió una configuración inicial en `Cumba35/Nova-IA/.circleci/config.yml`.

La integración aún necesita autorización del propietario en CircleCI para acceder al repositorio privado. La primera ejecución real debe terminar correctamente antes de considerar recuperado el CI.

## Hosting gratuito: decisión técnica

- **Render Free** tiene instancias con 256 MB, suspensión tras 15 minutos inactivo y disco efímero. No sirve para alojar el contenedor actual de Nova IA, que instala PyTorch/Transformers y usa SQLite cifrado local.
- **Cloudflare Workers Free** puede alojar una API ligera y soporta FastAPI/ASGI, pero limita cada solicitud a 10 ms de CPU y 128 MB. No puede ejecutar el modelo PyTorch/Transformers actual. Es candidato para un plano de control ligero, no para inferencia.
- **Supabase Nova-Nexus** se conserva como almacenamiento persistente existente, sujeto a límites del plan gratuito y pruebas de RLS. La migración del SQLite operativo todavía no está implementada.
- La inferencia del modelo independiente debe permanecer local en Android/hardware propio mientras no exista un host gratuito con memoria, CPU y almacenamiento persistente suficientes.

No se ha seleccionado una plataforma que requiera tarjeta, saldo de prueba o verificación de facturación. No se ha movido tráfico ni datos.

## Documentación técnica

El análisis completo, límites y pasos de activación están en [Nova IA — alternativas gratuitas sin tarjeta](https://github.com/Cumba35/Nova-IA/blob/main/backend/FREE-ALTERNATIVES.md).

## Estado

- Configuración CircleCI preparada en el repositorio Nova IA.
- Primer pipeline real pendiente de autorización.
- Railway se mantiene sin conexión; no se creó un servicio duplicado.
- Migración de API ligera y almacenamiento a Cloudflare/Supabase pendiente de diseño, implementación y pruebas.
- No se considera operativa ninguna alternativa hasta verificar pipeline, endpoint, persistencia, autenticación y recuperación.
