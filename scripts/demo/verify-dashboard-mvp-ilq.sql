-- =============================================================================
-- verify-dashboard-mvp-ilq.sql — Fase 6.9I.3-MVP
--
-- Comprueba el panel MVP y las cifras clínicas que debe producir.
--
-- Solo lee: ni un INSERT, ni un UPDATE, ni un DELETE.
--
-- Las cifras esperadas NO están en la aplicación: se calculan aquí a partir de
-- los datos y se comparan con lo que dice la estructura sembrada. Si el dataset
-- sintético se regenera con la misma semilla, siguen valiendo.
--
-- USO:
--   docker exec -i preventiva_postgres psql -U preventiva_user -d preventiva_db \
--     < scripts/demo/verify-dashboard-mvp-ilq.sql
-- =============================================================================

\pset footer off

WITH d AS (
  SELECT id FROM datasets_clinicos WHERE codigo = 'demo_ilq_profilaxis_drago'
),
p AS (
  SELECT id FROM paneles_clinicos
  WHERE dataset_id IN (SELECT id FROM d) AND codigo = 'mvp_ilq_profilaxis_drago' AND activo
),
-- Los booleanos se interpretan con el mismo vocabulario que el motor
-- (RegistroClinicoGenericoValueReader): si aquí se leyeran de otra forma, la
-- comprobación no valdría de nada.
b AS (
  SELECT r.*,
    upper(r.datos_dinamicos->>'infeccionLocalizacionQuirurgica') IN ('SI','S','TRUE','1','X') AS ilq_si,
    r.datos_dinamicos->>'infeccionLocalizacionQuirurgica' IS NOT NULL AS ilq_doc,
    upper(r.datos_dinamicos->>'profilaxisIndicada') IN ('SI','S','TRUE','1','X') AS prof_ind,
    r.datos_dinamicos->>'adecuacionProfilaxis' AS adec,
    r.datos_dinamicos->>'prescripcionProfilaxisDrago' AS drago,
    r.datos_dinamicos->>'localizacionInfeccion' AS loc,
    r.datos_dinamicos->>'motivoInadecuacionProfilaxis' AS motivo
  FROM registros_clinicos_genericos r
  WHERE r.dataset_id IN (SELECT id FROM d)
),
c AS (
  SELECT
    count(*) AS intervenciones,
    count(DISTINCT paciente_codigo) AS pacientes,
    count(*) FILTER (WHERE ilq_si) AS ilq,
    count(*) FILTER (WHERE ilq_doc) AS ilq_doc,
    count(*) FILTER (WHERE prof_ind) AS indicadas,
    count(*) FILTER (WHERE adec = 'ADECUADA') AS adecuada,
    count(*) FILTER (WHERE adec = 'INADECUADA') AS inadecuada,
    count(*) FILTER (WHERE adec = 'NO_APLICA') AS no_aplica,
    count(*) FILTER (WHERE prof_ind AND adec IS NOT NULL AND adec <> 'NO_APLICA') AS evaluables,
    count(*) FILTER (WHERE prof_ind AND adec = 'ADECUADA') AS adecuada_ind,
    count(*) FILTER (WHERE prof_ind AND adec = 'INADECUADA') AS inadecuada_ind,
    count(*) FILTER (WHERE prof_ind AND upper(drago) IN ('SI','S','TRUE','1','X')) AS drago_si,
    count(*) FILTER (WHERE prof_ind AND upper(drago) IN ('NO','N','FALSE','0')) AS drago_no,
    count(*) FILTER (WHERE prof_ind AND drago IS NULL) AS drago_nulo,
    count(*) FILTER (WHERE prof_ind AND drago IS NOT NULL) AS drago_con_dato,
    count(DISTINCT loc) FILTER (WHERE ilq_si) AS localizaciones,
    count(DISTINCT motivo) FILTER (WHERE adec = 'INADECUADA') AS motivos,
    count(DISTINCT date_trunc('month', fecha_evento)) AS meses
  FROM b
),
comprobaciones(n, concepto, obtenido, esperado) AS (
  -- --- Estructura ---
  SELECT  1, 'el panel MVP existe y está activo', (SELECT count(*)::text FROM p), '1'
  UNION ALL SELECT 2, 'métricas mvp_ilq_ en el dataset',
    (SELECT count(*)::text FROM metricas_clinicas
     WHERE dataset_id IN (SELECT id FROM d) AND codigo LIKE 'mvp\_ilq\_%' AND activa), '14'
  UNION ALL SELECT 3, 'widgets activos del panel',
    (SELECT count(*)::text FROM panel_metricas WHERE panel_id IN (SELECT id FROM p) AND activa), '14'
  UNION ALL SELECT 4, 'widgets KPI',
    (SELECT count(*)::text FROM panel_metricas
     WHERE panel_id IN (SELECT id FROM p) AND activa AND tipo_visualizacion = 'KPI'), '8'
  UNION ALL SELECT 5, 'widgets gráficos (no KPI)',
    (SELECT count(*)::text FROM panel_metricas
     WHERE panel_id IN (SELECT id FROM p) AND activa AND tipo_visualizacion <> 'KPI'), '6'
  UNION ALL SELECT 6, 'órdenes de widget sin duplicados (1..14)',
    (SELECT count(DISTINCT orden)::text FROM panel_metricas WHERE panel_id IN (SELECT id FROM p) AND activa), '14'
  UNION ALL SELECT 7, 'toda métrica mvp tiene exactamente un widget',
    (SELECT count(*)::text FROM metricas_clinicas m
     WHERE m.dataset_id IN (SELECT id FROM d) AND m.codigo LIKE 'mvp\_ilq\_%'
       AND (SELECT count(*) FROM panel_metricas pm
            WHERE pm.metrica_id = m.id AND pm.panel_id IN (SELECT id FROM p)) = 1), '14'

  -- --- KPI 1 a 3: recuentos ---
  UNION ALL SELECT 8,  'KPI 1 · intervenciones', (SELECT intervenciones::text FROM c), '280'
  UNION ALL SELECT 9,  'KPI 2 · pacientes únicos', (SELECT pacientes::text FROM c), '220'
  UNION ALL SELECT 10, 'KPI 3 · casos de ILQ', (SELECT ilq::text FROM c), '24'

  -- --- KPI 4: tasa de ILQ ---
  UNION ALL SELECT 11, 'KPI 4 · numerador (ILQ = sí)', (SELECT ilq::text FROM c), '24'
  UNION ALL SELECT 12, 'KPI 4 · denominador (ILQ documentada)', (SELECT ilq_doc::text FROM c), '269'
  UNION ALL SELECT 13, 'KPI 4 · tasa de ILQ %',
    (SELECT round(ilq * 100.0 / ilq_doc, 2)::text FROM c), '8.92'

  -- --- KPI 5: adecuación ---
  UNION ALL SELECT 14, 'KPI 5 · numerador (adecuada entre indicadas)', (SELECT adecuada_ind::text FROM c), '199'
  UNION ALL SELECT 15, 'KPI 5 · denominador (casos evaluables)', (SELECT evaluables::text FROM c), '249'
  UNION ALL SELECT 16, 'KPI 5 · adecuación %',
    (SELECT round(adecuada_ind * 100.0 / evaluables, 2)::text FROM c), '79.92'

  -- --- KPI 6 ---
  UNION ALL SELECT 17, 'KPI 6 · profilaxis inadecuadas', (SELECT inadecuada_ind::text FROM c), '50'

  -- --- KPI 7 y 8: Drago ---
  UNION ALL SELECT 18, 'KPI 7 · numerador (prescrita en Drago)', (SELECT drago_si::text FROM c), '195'
  UNION ALL SELECT 19, 'KPI 7 · denominador (profilaxis indicadas)', (SELECT indicadas::text FROM c), '249'
  UNION ALL SELECT 20, 'KPI 7 · prescripción en Drago %',
    (SELECT round(drago_si * 100.0 / indicadas, 2)::text FROM c), '78.31'
  UNION ALL SELECT 21, 'KPI 8 · numerador (con dato en Drago)', (SELECT drago_con_dato::text FROM c), '234'
  UNION ALL SELECT 22, 'KPI 8 · completitud de Drago %',
    (SELECT round(drago_con_dato * 100.0 / indicadas, 2)::text FROM c), '93.98'

  -- --- Gráficos ---
  UNION ALL SELECT 23, 'G2 · localizaciones distintas', (SELECT localizaciones::text FROM c), '4'
  UNION ALL SELECT 24, 'G2 · total de casos localizados', (SELECT ilq::text FROM c), '24'
  UNION ALL SELECT 25, 'G3 · adecuación ADECUADA', (SELECT adecuada::text FROM c), '199'
  UNION ALL SELECT 26, 'G3 · adecuación INADECUADA', (SELECT inadecuada::text FROM c), '50'
  UNION ALL SELECT 27, 'G3 · adecuación NO_APLICA', (SELECT no_aplica::text FROM c), '31'
  UNION ALL SELECT 28, 'G4 · motivos distintos', (SELECT motivos::text FROM c), '9'
  UNION ALL SELECT 29, 'G4 · total de inadecuaciones', (SELECT inadecuada::text FROM c), '50'
  UNION ALL SELECT 30, 'G5 · Drago sí (entre indicadas)', (SELECT drago_si::text FROM c), '195'
  UNION ALL SELECT 31, 'G5 · Drago no (entre indicadas)', (SELECT drago_no::text FROM c), '39'
  UNION ALL SELECT 32, 'G5 · Drago sin dato (entre indicadas)', (SELECT drago_nulo::text FROM c), '15'
  UNION ALL SELECT 33, 'G5 · las tres categorías suman las indicadas',
    (SELECT (drago_si + drago_no + drago_nulo)::text FROM c), (SELECT indicadas::text FROM c)
  UNION ALL SELECT 34, 'G1 · meses con datos', (SELECT meses::text FROM c), '18'

  -- --- Configuración: lo que hace que las cifras salgan bien ---
  UNION ALL SELECT 35, 'KPI 4 · el denominador excluye los no documentados',
    (SELECT (configuracion->'denominador'->'filtros'->0->>'operador') FROM metricas_clinicas
     WHERE dataset_id IN (SELECT id FROM d) AND codigo = 'mvp_ilq_tasa'), 'NOT_NULL'
  UNION ALL SELECT 36, 'KPI 5 · el denominador excluye NO_APLICA',
    (SELECT count(*)::text FROM metricas_clinicas
     WHERE dataset_id IN (SELECT id FROM d) AND codigo = 'mvp_ilq_profilaxis_adecuada'
       AND configuracion->'denominador'->'filtros' @> '[{"operador": "NE", "valor": "NO_APLICA"}]'), '1'
  UNION ALL SELECT 37, 'KPI 5 · filtros base: solo profilaxis indicada',
    (SELECT (configuracion->'filtros'->0->>'campo') FROM metricas_clinicas
     WHERE dataset_id IN (SELECT id FROM d) AND codigo = 'mvp_ilq_profilaxis_adecuada'), 'profilaxisIndicada'
  UNION ALL SELECT 38, 'G5 · Drago incluye «Sin dato» como categoría',
    (SELECT (configuracion->>'tratamientoNulos') FROM metricas_clinicas
     WHERE dataset_id IN (SELECT id FROM d) AND codigo = 'mvp_ilq_drago_distribucion'),
    'INCLUIR_COMO_CATEGORIA'
  UNION ALL SELECT 39, 'G4 · los motivos no se agrupan en «Otros»',
    (SELECT coalesce(configuracion->>'maxCategorias', 'sin recorte') FROM metricas_clinicas
     WHERE dataset_id IN (SELECT id FROM d) AND codigo = 'mvp_ilq_motivos_inadecuacion'), 'sin recorte'
  UNION ALL SELECT 40, 'G1 · la serie agrupa por mes sobre fechaEvento',
    (SELECT (configuracion_widget->>'granularidad') || '/' || (configuracion_widget->>'campoFecha')
     FROM panel_metricas pm JOIN metricas_clinicas m ON m.id = pm.metrica_id
     WHERE pm.panel_id IN (SELECT id FROM p) AND m.codigo = 'mvp_ilq_tasa_mensual'), 'MES/fechaEvento'
  UNION ALL SELECT 41, 'G6 · la comparativa agrupa por procedimiento',
    (SELECT (configuracion_widget->>'campoAgrupacion')
     FROM panel_metricas pm JOIN metricas_clinicas m ON m.id = pm.metrica_id
     WHERE pm.panel_id IN (SELECT id FROM p) AND m.codigo = 'mvp_ilq_casos_procedimiento'), 'procedimiento'
  UNION ALL SELECT 42, 'los ocho KPI ocupan un cuarto de fila (4 por fila)',
    (SELECT count(*)::text FROM panel_metricas
     WHERE panel_id IN (SELECT id FROM p) AND tipo_visualizacion = 'KPI' AND ancho = 3), '8'

  -- --- Aislamiento: no se ha tocado nada más ---
  UNION ALL SELECT 43, 'otros datasets conservan sus métricas',
    (SELECT count(*)::text FROM metricas_clinicas WHERE dataset_id NOT IN (SELECT id FROM d)), '48'
  UNION ALL SELECT 44, 'otros datasets conservan sus paneles',
    (SELECT count(*)::text FROM paneles_clinicos WHERE dataset_id NOT IN (SELECT id FROM d)), '4'
  UNION ALL SELECT 45, 'el dataset sintético conserva sus 25 columnas',
    (SELECT count(*)::text FROM campos_clinicos WHERE dataset_id IN (SELECT id FROM d) AND activo), '25'
  UNION ALL SELECT 46, 'el dataset sintético conserva sus 280 registros',
    (SELECT intervenciones::text FROM c), '280'
  UNION ALL SELECT 47, 'no hay métricas mvp fuera del dataset sintético',
    (SELECT count(*)::text FROM metricas_clinicas
     WHERE codigo LIKE 'mvp\_ilq\_%' AND dataset_id NOT IN (SELECT id FROM d)), '0'
)
SELECT n AS "#", concepto, obtenido, esperado,
       CASE WHEN obtenido IS NOT DISTINCT FROM esperado THEN 'OK' ELSE 'ERROR' END AS estado
FROM comprobaciones
ORDER BY n;
