-- =============================================================================
-- reset-demo-ilq.sql — Fase 6.9I.1 (acotado en 6.9I.1.1)
--
-- Elimina ÚNICAMENTE el dataset sintético `demo_ilq_profilaxis_drago` y lo que
-- cuelga de él. Es el reset del ciclo normal de la demo y puede ejecutarse
-- cuantas veces haga falta.
--
-- ALCANCE ESTRICTO: nada fuera de ese dataset. En particular NO toca:
--   · otros datasets ni sus importaciones (CESÁREAS, ILQ TRAUMA,
--     ILQ CIR. GASTRO-ESOF. y cualquier Excel que se importe en el futuro);
--   · el pipeline legacy (cirugias, infecciones_quirurgicas, profilaxis,
--     medidas_preventivas, seguimientos_postoperatorios, microbiologias);
--   · usuarios, roles, hospitales, servicios;
--   · plantillas_excel ni mapeos_columnas_excel.
--
-- Todo se resuelve por el CÓDIGO del dataset y por relaciones reales: ningún id
-- fijo, ningún patrón LIKE, ningún nombre de fichero.
--
-- La limpieza histórica de datos de prueba anteriores está archivada en
-- archive/cleanup-pre-69i1-test-data.sql y NO forma parte de este flujo.
--
-- Idempotente: si el dataset no existe, no borra nada y termina correctamente.
--
-- USO:
--   docker exec -i preventiva_postgres psql -U preventiva_user -d preventiva_db \
--     -v ON_ERROR_STOP=1 < scripts/demo/reset-demo-ilq.sql
-- =============================================================================

\set ON_ERROR_STOP on

-- Centinela: solo la base local del proyecto.
DO $$
BEGIN
  IF current_database() <> 'preventiva_db' THEN
    RAISE EXCEPTION 'Base incorrecta: % (se esperaba preventiva_db). Abortado sin tocar nada.', current_database();
  END IF;
END $$;

BEGIN;

-- Único criterio de selección: el código estable del dataset sintético.
CREATE TEMP TABLE ds ON COMMIT DROP AS
SELECT id FROM datasets_clinicos WHERE codigo = 'demo_ilq_profilaxis_drago';

-- Traza de lo que se va a eliminar (0 filas = nada que hacer).
SELECT COALESCE(
  (SELECT 'Se eliminará el dataset ' || d.codigo || ' (id ' || d.id || ') con '
          || (SELECT count(*) FROM registros_clinicos_genericos r WHERE r.dataset_id = d.id) || ' registros y '
          || (SELECT count(*) FROM campos_clinicos c WHERE c.dataset_id = d.id) || ' campos.'
   FROM datasets_clinicos d WHERE d.id IN (SELECT id FROM ds)),
  'El dataset demo_ilq_profilaxis_drago no existe: no hay nada que eliminar.'
) AS aviso;

-- Dependencias, todas derivadas del dataset por clave foránea.
CREATE TEMP TABLE plantillas_ds ON COMMIT DROP AS
SELECT id FROM plantillas_importacion WHERE dataset_id IN (SELECT id FROM ds);

CREATE TEMP TABLE importaciones_ds ON COMMIT DROP AS
SELECT id FROM importaciones_genericas WHERE plantilla_id IN (SELECT id FROM plantillas_ds);

CREATE TEMP TABLE trabajos_ds ON COMMIT DROP AS
SELECT id FROM importaciones_trabajo
WHERE dataset_id IN (SELECT id FROM ds)
   OR plantilla_id IN (SELECT id FROM plantillas_ds)
   OR importacion_generica_id IN (SELECT id FROM importaciones_ds);

-- Orden hijo → padre: todas las FK del esquema son NO ACTION (sin cascada).

-- 1. Widgets de paneles o métricas de ESTE dataset.
DELETE FROM panel_metricas
WHERE panel_id   IN (SELECT id FROM paneles_clinicos  WHERE dataset_id IN (SELECT id FROM ds))
   OR metrica_id IN (SELECT id FROM metricas_clinicas WHERE dataset_id IN (SELECT id FROM ds));

-- 2. Paneles y métricas del dataset.
DELETE FROM paneles_clinicos  WHERE dataset_id IN (SELECT id FROM ds);
DELETE FROM metricas_clinicas WHERE dataset_id IN (SELECT id FROM ds);

-- 3. Copias de trabajo de importación y su detalle.
DELETE FROM eventos_importacion_trabajo WHERE importacion_trabajo_id IN (SELECT id FROM trabajos_ds);
DELETE FROM filas_importacion_trabajo   WHERE importacion_trabajo_id IN (SELECT id FROM trabajos_ds);
DELETE FROM importaciones_trabajo       WHERE id IN (SELECT id FROM trabajos_ds);

-- 4. Registros clínicos e importaciones del dataset.
DELETE FROM registros_clinicos_genericos WHERE dataset_id IN (SELECT id FROM ds);
DELETE FROM errores_importacion_generica WHERE importacion_generica_id IN (SELECT id FROM importaciones_ds);
DELETE FROM importaciones_genericas      WHERE id IN (SELECT id FROM importaciones_ds);

-- 5. Mapeos, campos, plantillas y, por último, el dataset.
DELETE FROM mapeos_campo_importacion
WHERE plantilla_id     IN (SELECT id FROM plantillas_ds)
   OR campo_clinico_id IN (SELECT id FROM campos_clinicos WHERE dataset_id IN (SELECT id FROM ds));

DELETE FROM campos_clinicos        WHERE dataset_id IN (SELECT id FROM ds);
DELETE FROM plantillas_importacion WHERE id IN (SELECT id FROM plantillas_ds);
DELETE FROM datasets_clinicos      WHERE id IN (SELECT id FROM ds);

COMMIT;
