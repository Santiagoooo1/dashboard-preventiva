-- =============================================================================
-- seed-dashboard-mvp-ilq.sql — Fase 6.9I.3-MVP
--
-- Crea el panel clínico mínimo de ILQ, profilaxis y Drago sobre el dataset
-- sintético: 14 métricas y 14 widgets (8 KPI + 6 gráficos).
--
-- ALCANCE ESTRICTO: solo toca elementos cuyo código empieza por 'mvp_ilq_',
-- dentro del dataset 'demo_ilq_profilaxis_drago'. NO toca:
--   · otros datasets ni sus métricas, paneles o widgets;
--   · los registros clínicos ni los campos del dataset sintético;
--   · usuarios, roles, hospitales, servicios;
--   · el esquema.
--
-- IDEMPOTENTE: actualiza lo que ya existe y crea lo que falta, resolviendo todo
-- por código estable. Ejecutarlo dos veces deja 1 panel, 14 métricas y 14
-- widgets, con los mismos ids que la primera vez (no hay borrado y recreación,
-- así el enlace al dashboard sigue siendo válido).
--
-- No define ninguna cifra clínica: todas se calculan a partir de los datos. Los
-- valores esperados se comprueban aparte, en verify-dashboard-mvp-ilq.sql.
--
-- USO:
--   docker exec -i preventiva_postgres psql -U preventiva_user -d preventiva_db \
--     -v ON_ERROR_STOP=1 < scripts/demo/seed-dashboard-mvp-ilq.sql
-- =============================================================================

\set ON_ERROR_STOP on

-- Centinela: solo la base local del proyecto.
DO $$
BEGIN
  IF current_database() <> 'preventiva_db' THEN
    RAISE EXCEPTION 'Base incorrecta: % (se esperaba preventiva_db). Abortado sin tocar nada.', current_database();
  END IF;
END $$;

-- Centinela: sin el dataset sintético no hay nada que sembrar.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM datasets_clinicos WHERE codigo = 'demo_ilq_profilaxis_drago') THEN
    RAISE EXCEPTION 'No existe el dataset demo_ilq_profilaxis_drago. Ejecuta antes seed-demo-ilq.sql.';
  END IF;
END $$;

BEGIN;

CREATE TEMP TABLE ds ON COMMIT DROP AS
SELECT id FROM datasets_clinicos WHERE codigo = 'demo_ilq_profilaxis_drago';

-- -----------------------------------------------------------------------------
-- 1. El panel
-- -----------------------------------------------------------------------------
UPDATE paneles_clinicos SET
  nombre = 'Vigilancia ILQ, profilaxis antibiótica y Drago',
  descripcion = 'Panel clínico mínimo para revisar infección de localización quirúrgica, '
                || 'adecuación de profilaxis antibiótica y registro de la prescripción en Drago.',
  orden = 0,
  activo = true
WHERE dataset_id IN (SELECT id FROM ds) AND codigo = 'mvp_ilq_profilaxis_drago';

INSERT INTO paneles_clinicos (dataset_id, codigo, nombre, descripcion, orden, activo)
SELECT (SELECT id FROM ds), 'mvp_ilq_profilaxis_drago',
       'Vigilancia ILQ, profilaxis antibiótica y Drago',
       'Panel clínico mínimo para revisar infección de localización quirúrgica, '
         || 'adecuación de profilaxis antibiótica y registro de la prescripción en Drago.',
       0, true
WHERE NOT EXISTS (
  SELECT 1 FROM paneles_clinicos
  WHERE dataset_id IN (SELECT id FROM ds) AND codigo = 'mvp_ilq_profilaxis_drago');

CREATE TEMP TABLE panel ON COMMIT DROP AS
SELECT id FROM paneles_clinicos
WHERE dataset_id IN (SELECT id FROM ds) AND codigo = 'mvp_ilq_profilaxis_drago';

-- -----------------------------------------------------------------------------
-- 2. Las 14 métricas
--
-- La configuración es la misma estructura que produce el backend
-- (ConfiguracionMetricaDto): filtros base, numerador, denominador, campoValor,
-- campoAgrupacion, tratamientoNulos y etiquetas.
--
-- Los booleanos viajan como true/false técnicos, nunca como "Sí"/"No": las
-- etiquetas clínicas son cosa de la presentación.
-- -----------------------------------------------------------------------------
CREATE TEMP TABLE mvp_metricas (
  codigo text, nombre text, descripcion text, tipo_metrica text,
  configuracion jsonb, unidad text, decimales int, orden int
) ON COMMIT DROP;

INSERT INTO mvp_metricas VALUES

-- --- KPI 1: cuántas intervenciones se están analizando ---
('mvp_ilq_intervenciones', 'Intervenciones analizadas',
 'Número de intervenciones incluidas en el análisis con los filtros activos.',
 'CONTEO', '{"filtros": []}', NULL, 0, 1),

-- --- KPI 2: pacientes, NO registros (un paciente puede reintervenirse) ---
('mvp_ilq_pacientes_unicos', 'Pacientes únicos',
 'Pacientes distintos incluidos en el análisis. No coincide con el número de intervenciones: '
 || 'un mismo paciente puede tener más de una.',
 'CONTEO_DISTINTO', '{"filtros": [], "campoValor": "pacienteCodigo"}', NULL, 0, 2),

-- --- KPI 3: casos de ILQ ---
('mvp_ilq_casos', 'Casos de ILQ',
 'Intervenciones con infección de localización quirúrgica documentada como sí.',
 'CONTEO',
 '{"filtros": [{"campo": "infeccionLocalizacionQuirurgica", "operador": "EQ", "valor": true}]}',
 NULL, 0, 3),

-- --- KPI 4: tasa de ILQ ---
-- El denominador excluye las intervenciones sin ILQ documentada: contarlas como
-- "sin infección" daría una tasa artificialmente baja.
('mvp_ilq_tasa', 'Tasa de ILQ',
 'Porcentaje de infección de localización quirúrgica sobre las intervenciones con ILQ documentada. '
 || 'Las intervenciones sin dato quedan fuera del denominador.',
 'PORCENTAJE',
 '{"filtros": [],
   "numerador": {"filtros": [{"campo": "infeccionLocalizacionQuirurgica", "operador": "EQ", "valor": true}]},
   "denominador": {"filtros": [{"campo": "infeccionLocalizacionQuirurgica", "operador": "NOT_NULL", "valor": null}]},
   "etiquetaNumerador": "Casos de ILQ",
   "etiquetaDenominador": "Intervenciones con ILQ documentada"}',
 '%', 2, 4),

-- --- KPI 5: adecuación de la profilaxis ---
-- Base: solo donde la profilaxis estaba indicada. Denominador: además, solo
-- donde la adecuación es evaluable (NO_APLICA no es ni bien ni mal).
('mvp_ilq_profilaxis_adecuada', 'Profilaxis adecuada',
 'Porcentaje de profilaxis adecuada entre los casos en que estaba indicada y la adecuación es evaluable. '
 || 'Se excluyen los NO_APLICA y los casos sin dato.',
 'PORCENTAJE',
 '{"filtros": [{"campo": "profilaxisIndicada", "operador": "EQ", "valor": true}],
   "numerador": {"filtros": [{"campo": "adecuacionProfilaxis", "operador": "EQ", "valor": "ADECUADA"}]},
   "denominador": {"filtros": [
      {"campo": "adecuacionProfilaxis", "operador": "NOT_NULL", "valor": null},
      {"campo": "adecuacionProfilaxis", "operador": "NE", "valor": "NO_APLICA"}]},
   "etiquetaNumerador": "Profilaxis adecuada",
   "etiquetaDenominador": "Casos evaluables"}',
 '%', 2, 5),

-- --- KPI 6: recuento absoluto de inadecuaciones ---
('mvp_ilq_profilaxis_inadecuadas', 'Profilaxis inadecuadas',
 'Número de intervenciones con profilaxis indicada en las que la adecuación fue inadecuada.',
 'CONTEO',
 '{"filtros": [
     {"campo": "profilaxisIndicada", "operador": "EQ", "valor": true},
     {"campo": "adecuacionProfilaxis", "operador": "EQ", "valor": "INADECUADA"}]}',
 NULL, 0, 6),

-- --- KPI 7: prescripción efectiva en Drago ---
('mvp_ilq_drago_prescripcion', 'Prescripción en Drago',
 'Porcentaje de profilaxis indicadas que constan prescritas en Drago.',
 'PORCENTAJE',
 '{"filtros": [{"campo": "profilaxisIndicada", "operador": "EQ", "valor": true}],
   "numerador": {"filtros": [{"campo": "prescripcionProfilaxisDrago", "operador": "EQ", "valor": true}]},
   "denominador": {"filtros": [{"campo": "profilaxisIndicada", "operador": "EQ", "valor": true}]},
   "etiquetaNumerador": "Prescrita en Drago",
   "etiquetaDenominador": "Profilaxis indicadas"}',
 '%', 2, 7),

-- --- KPI 8: calidad del registro, no del acto clínico ---
-- Mide si el dato CONSTA, con independencia de si es sí o no. Sin este
-- indicador, el KPI 7 no es interpretable: un porcentaje bajo podría deberse a
-- que no se prescribe o a que no se registra, y son problemas distintos.
('mvp_ilq_drago_completitud', 'Registro de Drago completado',
 'Porcentaje de profilaxis indicadas con dato registrado en Drago, sea sí o no. '
 || 'Mide la calidad del registro, no la prescripción.',
 'PORCENTAJE',
 '{"filtros": [{"campo": "profilaxisIndicada", "operador": "EQ", "valor": true}],
   "numerador": {"filtros": [{"campo": "prescripcionProfilaxisDrago", "operador": "NOT_NULL", "valor": null}]},
   "denominador": {"filtros": [{"campo": "profilaxisIndicada", "operador": "EQ", "valor": true}]},
   "etiquetaNumerador": "Casos con dato en Drago",
   "etiquetaDenominador": "Profilaxis indicadas"}',
 '%', 2, 8),

-- --- GRÁFICO 1: evolución mensual de la tasa de ILQ ---
-- Métrica propia en vez de reutilizar mvp_ilq_tasa: el panel mantiene una
-- relación 1:1 entre métrica y widget, de modo que editar el KPI no altera la
-- serie ni al revés. El cálculo es idéntico, y cada mes resuelve su propio
-- numerador y denominador (el motor de series aplica la métrica dentro de cada
-- periodo, nunca sobre el total).
('mvp_ilq_tasa_mensual', 'Evolución mensual de la tasa de ILQ',
 'Tasa de ILQ calculada mes a mes. Cada punto usa el numerador y el denominador de su propio mes.',
 'PORCENTAJE',
 '{"filtros": [],
   "numerador": {"filtros": [{"campo": "infeccionLocalizacionQuirurgica", "operador": "EQ", "valor": true}]},
   "denominador": {"filtros": [{"campo": "infeccionLocalizacionQuirurgica", "operador": "NOT_NULL", "valor": null}]},
   "etiquetaNumerador": "Casos de ILQ del mes",
   "etiquetaDenominador": "Intervenciones del mes con ILQ documentada"}',
 '%', 2, 9),

-- --- GRÁFICO 2: dónde se localizan las infecciones ---
('mvp_ilq_localizacion', 'Localización de las ILQ',
 'Reparto de las infecciones de localización quirúrgica según su localización.',
 'DISTRIBUCION',
 '{"campoAgrupacion": "localizacionInfeccion",
   "filtros": [
     {"campo": "infeccionLocalizacionQuirurgica", "operador": "EQ", "valor": true},
     {"campo": "localizacionInfeccion", "operador": "NOT_NULL", "valor": null}],
   "tratamientoNulos": "EXCLUIR"}',
 NULL, 0, 10),

-- --- GRÁFICO 3: resultado de la adecuación ---
('mvp_ilq_adecuacion_distribucion', 'Resultado de adecuación de profilaxis',
 'Reparto de las intervenciones según el resultado de la adecuación de la profilaxis.',
 'DISTRIBUCION',
 '{"campoAgrupacion": "adecuacionProfilaxis", "filtros": [], "tratamientoNulos": "EXCLUIR"}',
 NULL, 0, 11),

-- --- GRÁFICO 4: por qué falla la profilaxis ---
-- Sin maxCategorias: los nueve motivos se muestran enteros. Agruparlos en
-- "Otros" escondería precisamente lo que hay que corregir.
('mvp_ilq_motivos_inadecuacion', 'Motivos de inadecuación',
 'Motivos registrados en las profilaxis inadecuadas.',
 'DISTRIBUCION',
 '{"campoAgrupacion": "motivoInadecuacionProfilaxis",
   "filtros": [
     {"campo": "adecuacionProfilaxis", "operador": "EQ", "valor": "INADECUADA"},
     {"campo": "motivoInadecuacionProfilaxis", "operador": "NOT_NULL", "valor": null}],
   "tratamientoNulos": "EXCLUIR"}',
 NULL, 0, 12),

-- --- GRÁFICO 5: estado del registro en Drago ---
-- Aquí sí se incluyen los nulos: "sin documentar" es un estado propio y
-- confundirlo con "no prescrita" cambiaría el diagnóstico del problema.
('mvp_ilq_drago_distribucion', 'Estado de prescripción en Drago',
 'Reparto de las profilaxis indicadas entre prescritas en Drago, no prescritas y sin documentar.',
 'DISTRIBUCION',
 '{"campoAgrupacion": "prescripcionProfilaxisDrago",
   "filtros": [{"campo": "profilaxisIndicada", "operador": "EQ", "valor": true}],
   "tratamientoNulos": "INCLUIR_COMO_CATEGORIA"}',
 NULL, 0, 13),

-- --- GRÁFICO 6: qué procedimientos concentran las ILQ ---
-- La agrupación por procedimiento vive en el widget (COMPARATIVA), no en la
-- métrica: así el mismo conteo puede reagruparse sin duplicar la métrica.
('mvp_ilq_casos_procedimiento', 'Casos de ILQ por procedimiento',
 'Casos de ILQ desglosados por procedimiento quirúrgico.',
 'CONTEO',
 '{"filtros": [{"campo": "infeccionLocalizacionQuirurgica", "operador": "EQ", "valor": true}]}',
 NULL, 0, 14);

-- Actualiza las que ya existían (conserva el id).
UPDATE metricas_clinicas m SET
  nombre = v.nombre, descripcion = v.descripcion, tipo_metrica = v.tipo_metrica,
  configuracion = v.configuracion, unidad = v.unidad, decimales = v.decimales,
  orden = v.orden, activa = true
FROM mvp_metricas v
WHERE m.dataset_id IN (SELECT id FROM ds) AND m.codigo = v.codigo;

-- Crea las que faltaban.
INSERT INTO metricas_clinicas
  (dataset_id, codigo, nombre, descripcion, tipo_metrica, configuracion, unidad, decimales, orden, activa)
SELECT (SELECT id FROM ds), v.codigo, v.nombre, v.descripcion, v.tipo_metrica,
       v.configuracion, v.unidad, v.decimales, v.orden, true
FROM mvp_metricas v
WHERE NOT EXISTS (
  SELECT 1 FROM metricas_clinicas m
  WHERE m.dataset_id IN (SELECT id FROM ds) AND m.codigo = v.codigo);

-- -----------------------------------------------------------------------------
-- 3. Los 14 widgets
--
-- ancho 3 = cuatro por fila en escritorio (rejilla de 12 columnas), que es lo
-- que agrupa los ocho KPI en dos filas de cuatro.
--
-- tipo_resultado_widget se fija explícitamente en vez de dejar que se deduzca
-- de la visualización: así el widget no cambia de forma si mañana cambia esa
-- deducción.
-- -----------------------------------------------------------------------------
CREATE TEMP TABLE mvp_widgets (
  codigo_metrica text, tipo_visualizacion text, ancho int, orden int,
  tipo_resultado_widget text, configuracion_widget jsonb, titulo text
) ON COMMIT DROP;

INSERT INTO mvp_widgets VALUES
-- Fila 1: volumen y resultado
('mvp_ilq_intervenciones',          'KPI',    3,  1, 'ACTUAL', NULL, NULL),
('mvp_ilq_pacientes_unicos',        'KPI',    3,  2, 'ACTUAL', NULL, NULL),
('mvp_ilq_casos',                   'KPI',    3,  3, 'ACTUAL', NULL, NULL),
('mvp_ilq_tasa',                    'KPI',    3,  4, 'ACTUAL', NULL, NULL),
-- Fila 2: proceso (profilaxis y Drago)
('mvp_ilq_profilaxis_adecuada',     'KPI',    3,  5, 'ACTUAL', NULL, NULL),
('mvp_ilq_profilaxis_inadecuadas',  'KPI',    3,  6, 'ACTUAL', NULL, NULL),
('mvp_ilq_drago_prescripcion',      'KPI',    3,  7, 'ACTUAL', NULL, NULL),
('mvp_ilq_drago_completitud',       'KPI',    3,  8, 'ACTUAL', NULL, NULL),
-- Gráficos
('mvp_ilq_tasa_mensual',            'LINEAS', 12, 9, 'SERIE_TEMPORAL',
   '{"granularidad": "MES", "campoFecha": "fechaEvento", "campoSegmentacion": null, "campoAgrupacion": null}',
   'Evolución mensual de la tasa de ILQ'),
('mvp_ilq_localizacion',            'DONUT',  6, 10, 'ACTUAL', NULL, NULL),
('mvp_ilq_adecuacion_distribucion', 'DONUT',  6, 11, 'ACTUAL', NULL, NULL),
-- Nueve categorías necesitan el ancho completo para leerse.
('mvp_ilq_motivos_inadecuacion',    'BARRAS', 12, 12, 'ACTUAL', NULL, NULL),
('mvp_ilq_drago_distribucion',      'DONUT',  6, 13, 'ACTUAL', NULL, NULL),
('mvp_ilq_casos_procedimiento',     'BARRAS', 6, 14, 'COMPARATIVA',
   '{"granularidad": null, "campoFecha": null, "campoSegmentacion": null, "campoAgrupacion": "procedimiento"}',
   NULL);

-- Actualiza los widgets ya existentes de ESTE panel.
UPDATE panel_metricas pm SET
  tipo_visualizacion = w.tipo_visualizacion,
  ancho = w.ancho,
  orden = w.orden,
  tipo_resultado_widget = w.tipo_resultado_widget,
  configuracion_widget = w.configuracion_widget,
  titulo_personalizado = w.titulo,
  activa = true
FROM mvp_widgets w
JOIN metricas_clinicas m ON m.codigo = w.codigo_metrica AND m.dataset_id IN (SELECT id FROM ds)
WHERE pm.panel_id IN (SELECT id FROM panel) AND pm.metrica_id = m.id;

-- Crea los que faltaban.
INSERT INTO panel_metricas
  (panel_id, metrica_id, tipo_visualizacion, ancho, orden, tipo_resultado_widget,
   configuracion_widget, titulo_personalizado, activa)
SELECT (SELECT id FROM panel), m.id, w.tipo_visualizacion, w.ancho, w.orden,
       w.tipo_resultado_widget, w.configuracion_widget, w.titulo, true
FROM mvp_widgets w
JOIN metricas_clinicas m ON m.codigo = w.codigo_metrica AND m.dataset_id IN (SELECT id FROM ds)
WHERE NOT EXISTS (
  SELECT 1 FROM panel_metricas pm
  WHERE pm.panel_id IN (SELECT id FROM panel) AND pm.metrica_id = m.id);

COMMIT;

-- Traza de lo que ha quedado.
SELECT 'panel: ' || p.codigo || ' (id ' || p.id || ')' AS resultado
FROM paneles_clinicos p
WHERE p.dataset_id IN (SELECT id FROM datasets_clinicos WHERE codigo = 'demo_ilq_profilaxis_drago')
  AND p.codigo = 'mvp_ilq_profilaxis_drago'
UNION ALL
SELECT 'métricas mvp_ilq_: ' || count(*) FROM metricas_clinicas
WHERE dataset_id IN (SELECT id FROM datasets_clinicos WHERE codigo = 'demo_ilq_profilaxis_drago')
  AND codigo LIKE 'mvp\_ilq\_%'
UNION ALL
SELECT 'widgets del panel: ' || count(*) FROM panel_metricas
WHERE panel_id IN (SELECT p.id FROM paneles_clinicos p
                   WHERE p.dataset_id IN (SELECT id FROM datasets_clinicos WHERE codigo = 'demo_ilq_profilaxis_drago')
                     AND p.codigo = 'mvp_ilq_profilaxis_drago');
