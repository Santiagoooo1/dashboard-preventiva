-- =============================================================================
--  ####  SCRIPT HISTÓRICO DESTRUCTIVO  ####
--
--  NO UTILIZAR COMO RESET HABITUAL.
--  NO EJECUTAR DESPUÉS DE IMPORTAR NUEVOS EXCEL.
--
--  Para el ciclo normal de la demo usa ../reset-demo-ilq.sql, que solo toca el
--  dataset sintético demo_ilq_profilaxis_drago.
-- =============================================================================
--
-- cleanup-pre-69i1-test-data.sql — Fase 6.9I.1 (archivado en 6.9I.1.1)
--
-- Reproduce la limpieza puntual que se ejecutó UNA VEZ, con autorización
-- expresa, antes de crear el dataset sintético. Se conserva por trazabilidad:
-- documenta qué se eliminó y en qué orden.
--
-- Eliminó:
--   · datasets de prueba resueltos por código:
--       DEMO_MVP_ILQ_part2                                (14 registros)
--       CESAREAS_2026_CESAREAS_2                          (31 registros)
--       ILQ_TRAUMA_2026_TRAUMATOLOGIA                     (28 registros)
--       ILQ_CIR_GASTRO_ESOF_PROSPECTIVA_CIR_G_E_PROSPECT  (16 registros)
--     con sus campos, registros, métricas, paneles, widgets, plantillas,
--     importaciones y copias de trabajo;
--   · métricas TEST_69E_* y sus widgets, vivieran donde vivieran;
--   · pipeline legacy completo: 91 cirugías y sus tablas 1:1
--     (infecciones_quirurgicas, profilaxis, medidas_preventivas,
--      seguimientos_postoperatorios, microbiologias) más las importaciones
--     Excel asociadas.
--
-- NO tocó: usuarios, roles, hospitales, servicios, plantillas_excel,
-- mapeos_columnas_excel, esquema ni tablas.
--
-- ¿POR QUÉ ESTÁ ARCHIVADO?
-- Los datasets que borra se identificaban por patrones (DEMO_%, TEST_%) y por
-- nombres concretos, y además vaciaba las tablas legacy. Cuando vuelvan a
-- importarse CESÁREAS, ILQ TRAUMA o ILQ CIR. GASTRO-ESOF. desde Excel, ejecutar
-- esto los destruiría. El reset recurrente NO puede comportarse así.
--
-- USO (requiere confirmación explícita):
--   docker exec -i preventiva_postgres psql -U preventiva_user -d preventiva_db \
--     -v CONFIRM_CLEANUP=SI < scripts/demo/archive/cleanup-pre-69i1-test-data.sql
--
-- Sin -v CONFIRM_CLEANUP=SI el script aborta antes del primer DELETE.
-- Haz un pg_dump antes. Siempre.
-- =============================================================================

\set ON_ERROR_STOP on

-- Sin la variable definida, se fija un valor que NO confirma.
\if :{?CONFIRM_CLEANUP}
\else
  \set CONFIRM_CLEANUP NO
\endif

-- --- Centinelas: se ejecutan ANTES de abrir la transacción --------------------

-- 1. Solo la base local del proyecto.
DO $$
BEGIN
  IF current_database() <> 'preventiva_db' THEN
    RAISE EXCEPTION 'Base incorrecta: % (se esperaba preventiva_db). Abortado sin tocar nada.', current_database();
  END IF;
END $$;

-- 2. Confirmación explícita.
--
-- El valor se pasa primero a un parámetro de sesión: psql NO interpola sus
-- variables dentro de un bloque $$ ... $$, así que comprobarlo directamente ahí
-- daría un «syntax error» en vez del aviso que toca.
SET demo.confirm_cleanup = :'CONFIRM_CLEANUP';

DO $$
BEGIN
  IF current_setting('demo.confirm_cleanup', true) IS DISTINCT FROM 'SI' THEN
    RAISE EXCEPTION
      'Limpieza NO confirmada (valor recibido: %). Este script es destructivo y borra datos importados desde Excel. Repite con -v CONFIRM_CLEANUP=SI solo si estás seguro.',
      COALESCE(current_setting('demo.confirm_cleanup', true), '(sin definir)');
  END IF;
END $$;

BEGIN;

-- -----------------------------------------------------------------------------
-- Datasets de prueba, resueltos por CÓDIGO (nunca por id: cambian de entorno).
-- -----------------------------------------------------------------------------
CREATE TEMP TABLE datasets_a_borrar ON COMMIT DROP AS
SELECT id FROM datasets_clinicos
WHERE codigo IN (
        'DEMO_MVP_ILQ_part2',
        'CESAREAS_2026_CESAREAS_2',
        'ILQ_TRAUMA_2026_TRAUMATOLOGIA',
        'ILQ_CIR_GASTRO_ESOF_PROSPECTIVA_CIR_G_E_PROSPECT'
      );

CREATE TEMP TABLE plantillas_a_borrar ON COMMIT DROP AS
SELECT id FROM plantillas_importacion WHERE dataset_id IN (SELECT id FROM datasets_a_borrar);

CREATE TEMP TABLE importaciones_a_borrar ON COMMIT DROP AS
SELECT id FROM importaciones_genericas WHERE plantilla_id IN (SELECT id FROM plantillas_a_borrar);

CREATE TEMP TABLE trabajos_a_borrar ON COMMIT DROP AS
SELECT id FROM importaciones_trabajo
WHERE dataset_id IN (SELECT id FROM datasets_a_borrar)
   OR plantilla_id IN (SELECT id FROM plantillas_a_borrar)
   OR importacion_generica_id IN (SELECT id FROM importaciones_a_borrar);

-- Orden hijo → padre: todas las FK del esquema son NO ACTION.
DELETE FROM panel_metricas
WHERE panel_id IN (SELECT id FROM paneles_clinicos WHERE dataset_id IN (SELECT id FROM datasets_a_borrar))
   OR metrica_id IN (SELECT id FROM metricas_clinicas WHERE dataset_id IN (SELECT id FROM datasets_a_borrar));

-- Métricas de prueba: podían vivir en un dataset conservado.
DELETE FROM panel_metricas
WHERE metrica_id IN (SELECT id FROM metricas_clinicas WHERE codigo LIKE 'TEST\_69E%');

DELETE FROM paneles_clinicos  WHERE dataset_id IN (SELECT id FROM datasets_a_borrar);
DELETE FROM metricas_clinicas WHERE dataset_id IN (SELECT id FROM datasets_a_borrar);
DELETE FROM metricas_clinicas WHERE codigo LIKE 'TEST\_69E%';

DELETE FROM eventos_importacion_trabajo WHERE importacion_trabajo_id IN (SELECT id FROM trabajos_a_borrar);
DELETE FROM filas_importacion_trabajo   WHERE importacion_trabajo_id IN (SELECT id FROM trabajos_a_borrar);
DELETE FROM importaciones_trabajo       WHERE id IN (SELECT id FROM trabajos_a_borrar);

DELETE FROM registros_clinicos_genericos WHERE dataset_id IN (SELECT id FROM datasets_a_borrar);
DELETE FROM errores_importacion_generica WHERE importacion_generica_id IN (SELECT id FROM importaciones_a_borrar);
DELETE FROM importaciones_genericas      WHERE id IN (SELECT id FROM importaciones_a_borrar);

DELETE FROM mapeos_campo_importacion
WHERE plantilla_id IN (SELECT id FROM plantillas_a_borrar)
   OR campo_clinico_id IN (SELECT id FROM campos_clinicos WHERE dataset_id IN (SELECT id FROM datasets_a_borrar));

DELETE FROM campos_clinicos        WHERE dataset_id IN (SELECT id FROM datasets_a_borrar);
DELETE FROM plantillas_importacion WHERE id IN (SELECT id FROM plantillas_a_borrar);
DELETE FROM datasets_clinicos      WHERE id IN (SELECT id FROM datasets_a_borrar);

-- -----------------------------------------------------------------------------
-- Pipeline legacy (modelo Cirugia, anterior al genérico).
-- Se vacía por completo: en el momento de ejecutarlo solo contenía datos de
-- pruebas. ESTA ES LA PARTE MÁS PELIGROSA SI SE REEJECUTA MÁS ADELANTE.
-- -----------------------------------------------------------------------------
DELETE FROM microbiologias WHERE infeccion_id IN (SELECT id FROM infecciones_quirurgicas);
DELETE FROM infecciones_quirurgicas;
DELETE FROM medidas_preventivas;
DELETE FROM profilaxis;
DELETE FROM seguimientos_postoperatorios;
DELETE FROM cirugias;

DELETE FROM errores_importacion_excel;
DELETE FROM importaciones_excel;

-- plantillas_excel y mapeos_columnas_excel se conservan: son configuración que
-- siembra DataSeeder por servicio quirúrgico, no datos de pruebas.

COMMIT;
