# Validación RLS social heredada — 18 de noviembre de 2026

## Alcance

Auditoría de solo lectura de las políticas sociales históricas de Supabase en Nova-Nexus (producción) y NOVA-STAGING. La infraestructura Supabase heredada está congelada para nuevas funciones conforme a la política de transición de Nova Guard. Este expediente no autoriza añadir nuevas tablas, políticas ni servicios a Supabase.

## Corrección de la hipótesis anterior

Una primera lectura aislada de las políticas de comentarios, reacciones y marcadores sugirió que sus comprobaciones EXISTS solo confirmaban la existencia de la publicación padre. Esa conclusión omitía un detalle esencial de PostgreSQL: la consulta subordinada a nova_social_posts se ejecuta bajo el rol del usuario solicitante y, por tanto, está sujeta a las políticas RLS de esa tabla.

La política nova_social_posts_read_visible aplica visibilidad pública/seguidores, perfil privado y bloqueos en ambas direcciones. Las políticas de SELECT/INSERT de las tablas hijas consultan esa tabla padre con una subconsulta ordinaria; no se identificó en las expresiones inspeccionadas un SECURITY DEFINER ni otro bypass que anule esa RLS.

**Conclusión corregida:** la hipótesis de fuga por esas políticas hijas no quedó demostrada y la prueba de regresión en staging la contradijo. No aplicar una migración para cambiar esas políticas basándose en la hipótesis anterior.

## Verificación ejecutada en NOVA-STAGING

Proyecto: NOVA-STAGING, PostgreSQL 17.11.0.019.

Se ejecutó una prueba transaccional con tres identidades ficticias y datos temporales:
- Usuario A: perfil privado y publicación marcada como pública.
- Usuario B: lector no relacionado.
- Usuario C: perfil público y publicación pública.

Con el rol authenticated y los claims JWT de B se verificó:
1. La publicación de A no es visible.
2. El comentario de A no es visible.
3. La reacción de A no es visible.
4. La publicación pública de C sí es visible.
5. El comentario y la reacción de C sí son visibles.
6. Una publicación de seguidores de D y su comentario sí son visibles para B porque existe un follow aceptado.
7. Las publicaciones y comentarios de E y F no son visibles cuando B bloquea a E o F bloquea a B, respectivamente.
8. INSERT de comentario, reacción y marcador sobre la publicación invisible de A es rechazado por RLS.

Resultado devuelto por PostgreSQL:

PASS: private/public/follower/block parent visibility is inherited by child reads; invisible-parent child writes are denied

La transacción terminó con ROLLBACK; no quedaron usuarios, perfiles, publicaciones, comentarios ni reacciones de prueba. No se modificaron políticas, datos persistentes, extensiones ni configuración de Supabase.

El mismo conjunto de expresiones de políticas fue inspeccionado en producción. La prueba transaccional descrita se ejecutó en staging, no en producción.

## Artefacto reproducible

tests/legacy_supabase/nova_social_parent_rls_smoke.sql contiene el test rollback-only. Está diseñado para ejecutarse solo contra NOVA-STAGING o una copia local desechable con el esquema histórico. Usa identidades UUID ficticias fijas, crea fixtures dentro de una transacción y termina con ROLLBACK. No es una migración.

La extensión pgTAP no estaba instalada en staging durante esta auditoría; por eso el smoke test usa PL/pgSQL y aserciones explícitas, sin instalar extensiones.

## Investigación de alternativas públicas

- https://supabase.com/docs/guides/database/postgres/row-level-security — documenta que las políticas son evaluadas por PostgreSQL y deben probarse junto con grants.
- https://supabase.com/docs/guides/database/postgres/testing-row-level-security — recomienda pruebas positivas y negativas para SELECT/INSERT/UPDATE/DELETE, simulando identidades autenticadas.
- https://github.com/unitautogen/rlsautotest (Apache-2.0): candidato de auditoría automatizada, actualmente beta. Su documentación advierte usar una copia desechable porque genera datos y ejecuta probes de operaciones.
- https://github.com/joseafg94/supabase-rls-recipes (MIT): buenas prácticas de threat model, pruebas negativas, permisos y guía de despliegue. Se utilizó como referencia de diseño, no se copió código.

La documentación oficial de Supabase recomienda ejecutar pruebas RLS contra una base local y comprobar también las operaciones de escritura. El smoke test aquí añadido cubre perfil privado, publicación pública, follow aceptado, bloqueos en ambas direcciones y escritura sobre padre invisible. No reemplaza una matriz completa de permisos para todas las tablas sociales ni prueba UPDATE/DELETE.

## Pendientes reales

- [ ] Completar matriz de pruebas para follows pendientes, posts no publicados, edición y borrado de comentarios/reacciones.
- [ ] Auditar todas las tablas sociales y sus grants, no solo las cuatro tablas revisadas.
- [ ] Revisar vistas, funciones SECURITY DEFINER, RPC, Edge Functions y Storage como superficies de bypass independientes.
- [ ] Mantener Supabase heredado congelado; cualquier transición debe seguir el plan de Nova Guard, con pruebas, backup y recuperación.
- [ ] Revaluar Leaked Password Protection en Auth: el Security Advisor de producción reporta que está desactivada. En staging, el asesor de seguridad devolvió cero lints durante esta revisión.

## Estado

**La hipótesis de fuga por herencia de RLS de la publicación padre queda refutada para los escenarios probados en staging: privado, público, seguidor aceptado y bloqueos en ambas direcciones. También se comprobó el rechazo de INSERT en tablas hijas para un padre invisible. No se aplicó cambio de política. El test rollback-only y esta auditoría quedan versionados en Nova Guard. La auditoría social completa sigue pendiente.**
