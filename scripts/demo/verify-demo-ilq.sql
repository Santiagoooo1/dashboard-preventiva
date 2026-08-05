-- =============================================================================
-- verify-demo-ilq.sql — Fase 6.9I.1
--
-- Comprueba conteos, coherencia clínica e integridad tras ejecutar reset+seed.
-- Cada fila devuelve: comprobación, obtenido, esperado y OK/ERROR.
--
-- Si alguna fila sale ERROR, la carga demo NO es válida y no debe construirse
-- ningún indicador clínico sobre ella.
-- =============================================================================

WITH ds AS (
  SELECT id FROM datasets_clinicos WHERE codigo = 'demo_ilq_profilaxis_drago'
),
r AS (
  SELECT * FROM registros_clinicos_genericos WHERE dataset_id = (SELECT id FROM ds)
),
comprobaciones AS (

  -- 1. Dataset único
  SELECT 1 AS n, 'Dataset único' AS comprobacion,
         (SELECT count(*) FROM datasets_clinicos WHERE codigo='demo_ilq_profilaxis_drago')::text AS obtenido,
         '1' AS esperado

  -- 2. Campos
  UNION ALL SELECT 2, 'Campos clínicos',
         (SELECT count(*) FROM campos_clinicos WHERE dataset_id=(SELECT id FROM ds))::text, '25'

  -- 3. Registros
  UNION ALL SELECT 3, 'Intervenciones', (SELECT count(*) FROM r)::text, '280'

  -- 4. Pacientes distintos
  UNION ALL SELECT 4, 'Pacientes únicos', (SELECT count(DISTINCT paciente_codigo) FROM r)::text, '220'

  -- 4b. Pacientes con dos intervenciones
  UNION ALL SELECT 5, 'Pacientes con 2 intervenciones',
         (SELECT count(*) FROM (SELECT paciente_codigo FROM r GROUP BY 1 HAVING count(*)=2) x)::text, '60'

  UNION ALL SELECT 6, 'Ningún paciente con más de 2',
         (SELECT count(*) FROM (SELECT paciente_codigo FROM r GROUP BY 1 HAVING count(*)>2) x)::text, '0'

  -- 5. Periodo
  UNION ALL SELECT 7, 'Primera fecha >= 2025-01-01',
         (SELECT (min(fecha_evento) >= DATE '2025-01-01')::text FROM r), 'true'
  UNION ALL SELECT 8, 'Última fecha <= 2026-06-30',
         (SELECT (max(fecha_evento) <= DATE '2026-06-30')::text FROM r), 'true'

  -- 6. Todos los meses representados
  UNION ALL SELECT 9, 'Meses con actividad',
         (SELECT count(DISTINCT date_trunc('month', fecha_evento)) FROM r)::text, '18'

  -- 7. ILQ true → localización informada
  UNION ALL SELECT 10, 'ILQ sí sin localización (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'infeccionLocalizacionQuirurgica'='true'
            AND datos_dinamicos->>'localizacionInfeccion' IS NULL)::text, '0'

  -- 8. ILQ no/null → sin localización
  UNION ALL SELECT 11, 'Localización sin ILQ sí (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'localizacionInfeccion' IS NOT NULL
            AND COALESCE(datos_dinamicos->>'infeccionLocalizacionQuirurgica','') <> 'true')::text, '0'

  -- 9. Fecha de infección dentro de los 30 días posteriores
  UNION ALL SELECT 12, 'Fechas de ILQ fuera de rango (debe ser 0)',
         (SELECT count(*) FROM r
          WHERE datos_dinamicos->>'fechaInfeccion' IS NOT NULL
            AND ((datos_dinamicos->>'fechaInfeccion')::date < fecha_evento
              OR (datos_dinamicos->>'fechaInfeccion')::date > fecha_evento + 30))::text, '0'

  -- 10. Microorganismo solo con cultivo positivo
  UNION ALL SELECT 13, 'Microorganismo sin cultivo positivo (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'microorganismo' IS NOT NULL
            AND COALESCE(datos_dinamicos->>'cultivoIlq','') <> 'true')::text, '0'

  -- 11. Adecuada sin motivo
  UNION ALL SELECT 14, 'Adecuada CON motivo (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'adecuacionProfilaxis'='ADECUADA'
            AND datos_dinamicos->>'motivoInadecuacionProfilaxis' IS NOT NULL)::text, '0'

  -- 12. Inadecuada con motivo
  UNION ALL SELECT 15, 'Inadecuada SIN motivo (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'adecuacionProfilaxis'='INADECUADA'
            AND datos_dinamicos->>'motivoInadecuacionProfilaxis' IS NULL)::text, '0'

  -- 13. NO_APLICA solo si la profilaxis no estaba indicada
  UNION ALL SELECT 16, 'NO_APLICA con profilaxis indicada (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'adecuacionProfilaxis'='NO_APLICA'
            AND datos_dinamicos->>'profilaxisIndicada'='true')::text, '0'
  UNION ALL SELECT 17, 'Profilaxis no indicada sin NO_APLICA (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'profilaxisIndicada'='false'
            AND COALESCE(datos_dinamicos->>'adecuacionProfilaxis','') <> 'NO_APLICA')::text, '0'

  -- 14. Motivos solo en inadecuadas
  UNION ALL SELECT 18, 'Motivo fuera de INADECUADA (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'motivoInadecuacionProfilaxis' IS NOT NULL
            AND COALESCE(datos_dinamicos->>'adecuacionProfilaxis','') <> 'INADECUADA')::text, '0'
  UNION ALL SELECT 19, 'Motivos distintos representados',
         (SELECT count(DISTINCT datos_dinamicos->>'motivoInadecuacionProfilaxis') FROM r
          WHERE datos_dinamicos->>'motivoInadecuacionProfilaxis' IS NOT NULL)::text, '9'

  -- Coherencia motivo ↔ campos relacionados
  UNION ALL SELECT 20, 'AUSENCIA_PRESCRIPCION con Drago true (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'motivoInadecuacionProfilaxis'='AUSENCIA_PRESCRIPCION'
            AND datos_dinamicos->>'prescripcionProfilaxisDrago'='true')::text, '0'
  UNION ALL SELECT 21, 'REDOSIFICACION_NO_REALIZADA incoherente (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'motivoInadecuacionProfilaxis'='REDOSIFICACION_NO_REALIZADA'
            AND NOT (datos_dinamicos->>'redosificacionIndicada'='true'
                 AND datos_dinamicos->>'redosificacionRealizada'='false'))::text, '0'
  UNION ALL SELECT 22, 'ALERGIA_NO_CONSIDERADA sin alergia (debe ser 0)',
         (SELECT count(*) FROM r WHERE datos_dinamicos->>'motivoInadecuacionProfilaxis'='ALERGIA_NO_CONSIDERADA'
            AND COALESCE(datos_dinamicos->>'alergiaAntibiotico','') <> 'true')::text, '0'

  -- 15. Drago con los tres estados
  UNION ALL SELECT 23, 'Drago = true (mín. 1)',
         (SELECT (count(*) > 0)::text FROM r WHERE datos_dinamicos->>'prescripcionProfilaxisDrago'='true'), 'true'
  UNION ALL SELECT 24, 'Drago = false (mín. 20)',
         (SELECT (count(*) >= 20)::text FROM r WHERE datos_dinamicos->>'prescripcionProfilaxisDrago'='false'), 'true'
  UNION ALL SELECT 25, 'Drago sin documentar con profilaxis indicada (mín. 10)',
         (SELECT (count(*) >= 10)::text FROM r WHERE datos_dinamicos->>'profilaxisIndicada'='true'
            AND datos_dinamicos->>'prescripcionProfilaxisDrago' IS NULL), 'true'

  -- 16. Distribuciones mínimas
  UNION ALL SELECT 26, 'Casos ILQ (mín. 20)',
         (SELECT (count(*) >= 20)::text FROM r WHERE datos_dinamicos->>'infeccionLocalizacionQuirurgica'='true'), 'true'
  UNION ALL SELECT 27, 'Localizaciones distintas',
         (SELECT count(DISTINCT datos_dinamicos->>'localizacionInfeccion') FROM r
          WHERE datos_dinamicos->>'localizacionInfeccion' IS NOT NULL)::text, '4'
  UNION ALL SELECT 28, 'Profilaxis inadecuadas (mín. 40)',
         (SELECT (count(*) >= 40)::text FROM r WHERE datos_dinamicos->>'adecuacionProfilaxis'='INADECUADA'), 'true'
  UNION ALL SELECT 29, 'Estado ILQ sin documentar (mín. 1, para completitud)',
         (SELECT (count(*) > 0)::text FROM r WHERE datos_dinamicos->>'infeccionLocalizacionQuirurgica' IS NULL), 'true'
  UNION ALL SELECT 30, 'Procedimientos distintos', (SELECT count(DISTINCT procedimiento) FROM r)::text, '10'
  UNION ALL SELECT 31, 'Niveles ASA', (SELECT count(DISTINCT datos_dinamicos->>'asa') FROM r)::text, '4'
  UNION ALL SELECT 32, 'Grados de contaminación',
         (SELECT count(DISTINCT datos_dinamicos->>'gradoContaminacion') FROM r)::text, '4'
  UNION ALL SELECT 33, 'Urgentes y programadas',
         (SELECT count(DISTINCT datos_dinamicos->>'cirugiaUrgente') FROM r)::text, '2'
  UNION ALL SELECT 34, 'Microorganismos distintos',
         (SELECT (count(DISTINCT datos_dinamicos->>'microorganismo') >= 4)::text FROM r
          WHERE datos_dinamicos->>'microorganismo' IS NOT NULL), 'true'

  -- 17-18. Restos de fases anteriores
  UNION ALL SELECT 35, 'Métricas TEST_69E_ (debe ser 0)',
         (SELECT count(*) FROM metricas_clinicas WHERE codigo LIKE 'TEST\_69E%')::text, '0'
  UNION ALL SELECT 36, 'Dataset DEMO_MVP_ILQ_part2 (debe ser 0)',
         (SELECT count(*) FROM datasets_clinicos WHERE codigo='DEMO_MVP_ILQ_part2')::text, '0'

  -- 19. Huérfanos
  UNION ALL SELECT 37, 'Widgets huérfanos (debe ser 0)',
         (SELECT count(*) FROM panel_metricas pm
          WHERE NOT EXISTS (SELECT 1 FROM paneles_clinicos p WHERE p.id=pm.panel_id)
             OR NOT EXISTS (SELECT 1 FROM metricas_clinicas m WHERE m.id=pm.metrica_id))::text, '0'
  UNION ALL SELECT 38, 'Métricas sin dataset (debe ser 0)',
         (SELECT count(*) FROM metricas_clinicas m
          WHERE NOT EXISTS (SELECT 1 FROM datasets_clinicos d WHERE d.id=m.dataset_id))::text, '0'
  UNION ALL SELECT 39, 'Registros sin dataset (debe ser 0)',
         (SELECT count(*) FROM registros_clinicos_genericos g
          WHERE NOT EXISTS (SELECT 1 FROM datasets_clinicos d WHERE d.id=g.dataset_id))::text, '0'
  UNION ALL SELECT 40, 'Campos sin dataset (debe ser 0)',
         (SELECT count(*) FROM campos_clinicos c
          WHERE NOT EXISTS (SELECT 1 FROM datasets_clinicos d WHERE d.id=c.dataset_id))::text, '0'

  -- 20. Configuración compartida preservada
  --
  -- El reset recurrente solo puede tocar el dataset sintético, así que todo lo
  -- de abajo debe quedar exactamente igual tras ejecutarlo.
  UNION ALL SELECT 41, 'Usuarios preservados', (SELECT count(*) FROM usuarios)::text, '1'
  UNION ALL SELECT 42, 'Roles preservados', (SELECT count(*) FROM roles)::text, '2'
  UNION ALL SELECT 43, 'Hospitales preservados', (SELECT count(*) FROM hospitales)::text, '1'
  UNION ALL SELECT 44, 'Servicios preservados', (SELECT count(*) FROM servicios)::text, '8'
  UNION ALL SELECT 45, 'Plantillas Excel preservadas (DataSeeder)',
         (SELECT (count(*) > 0)::text FROM plantillas_excel), 'true'
  UNION ALL SELECT 46, 'Mapeos de columnas Excel preservados',
         (SELECT (count(*) > 0)::text FROM mapeos_columnas_excel), 'true'

  -- 21. Aislamiento: otros datasets no se ven afectados por el reset.
  --
  -- Cualquier dataset distinto del sintético (importaciones reales de Excel,
  -- centinelas de prueba) debe conservar sus campos y registros. Si el reset
  -- volviera a borrar por patrones, esta comprobación caería.
  UNION ALL SELECT 47, 'Otros datasets conservan sus campos',
         (SELECT count(*) FROM datasets_clinicos d
          WHERE d.codigo <> 'demo_ilq_profilaxis_drago'
            AND NOT EXISTS (SELECT 1 FROM campos_clinicos c WHERE c.dataset_id = d.id))::text, '0'
)
SELECT n AS "#", comprobacion, obtenido, esperado,
       CASE WHEN obtenido = esperado THEN 'OK' ELSE 'ERROR' END AS resultado
FROM comprobaciones
ORDER BY n;
