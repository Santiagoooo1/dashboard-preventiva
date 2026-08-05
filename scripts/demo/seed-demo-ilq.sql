-- =============================================================================
-- seed-demo-ilq.sql — Fase 6.9I.1
--
-- Crea el dataset sintético DEMO_ILQ_PROFILAXIS_DRAGO: 220 pacientes,
-- 280 intervenciones, 25 campos y 18 meses de actividad.
--
-- DATOS COMPLETAMENTE SINTÉTICOS, generados por fórmula. No proceden de ningún
-- paciente real ni de ninguna exportación hospitalaria. Destinados
-- exclusivamente a demostración y pruebas.
--
-- DETERMINISTA: no se usa random(). Cada atributo se deriva del índice de la
-- intervención mediante aritmética modular con la semilla 6901 y multiplicadores
-- primos, de modo que dos ejecuciones producen exactamente los mismos datos.
--
-- IDEMPOTENTE: empieza borrando el dataset por su código, así que ejecutarlo
-- varias veces deja siempre un único dataset con los mismos 280 registros.
--
-- Las distribuciones NO representan epidemiología real: están elegidas para que
-- todas las categorías analíticas tengan casos suficientes que visualizar.
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

-- -----------------------------------------------------------------------------
-- 0. Idempotencia: se elimina la versión anterior de ESTE dataset (solo este).
--    Mismo alcance acotado que reset-demo-ilq.sql: nada fuera de
--    demo_ilq_profilaxis_drago.
-- -----------------------------------------------------------------------------
CREATE TEMP TABLE ds_previo ON COMMIT DROP AS
SELECT id FROM datasets_clinicos WHERE codigo = 'demo_ilq_profilaxis_drago';

DELETE FROM panel_metricas
WHERE panel_id IN (SELECT id FROM paneles_clinicos WHERE dataset_id IN (SELECT id FROM ds_previo))
   OR metrica_id IN (SELECT id FROM metricas_clinicas WHERE dataset_id IN (SELECT id FROM ds_previo));
DELETE FROM paneles_clinicos            WHERE dataset_id IN (SELECT id FROM ds_previo);
DELETE FROM metricas_clinicas           WHERE dataset_id IN (SELECT id FROM ds_previo);
DELETE FROM registros_clinicos_genericos WHERE dataset_id IN (SELECT id FROM ds_previo);
DELETE FROM mapeos_campo_importacion
WHERE campo_clinico_id IN (SELECT id FROM campos_clinicos WHERE dataset_id IN (SELECT id FROM ds_previo));
DELETE FROM campos_clinicos             WHERE dataset_id IN (SELECT id FROM ds_previo);
DELETE FROM plantillas_importacion      WHERE dataset_id IN (SELECT id FROM ds_previo);
DELETE FROM datasets_clinicos           WHERE id IN (SELECT id FROM ds_previo);

-- -----------------------------------------------------------------------------
-- 1. Dataset
-- -----------------------------------------------------------------------------
INSERT INTO datasets_clinicos (codigo, nombre, descripcion, activo, estado_dataset, hospital_id)
VALUES (
  'demo_ilq_profilaxis_drago',
  'DEMO_ILQ_PROFILAXIS_DRAGO',
  'Datos sintéticos destinados exclusivamente a demostración y pruebas del análisis de infección de localización quirúrgica, profilaxis antibiótica y prescripción en Drago.',
  true,
  'ACTIVO',
  (SELECT id FROM hospitales ORDER BY id LIMIT 1)
);

CREATE TEMP TABLE ds ON COMMIT DROP AS
SELECT id FROM datasets_clinicos WHERE codigo = 'demo_ilq_profilaxis_drago';

-- -----------------------------------------------------------------------------
-- 2. Campos clínicos (25)
--
-- es_comun = true SOLO en los cinco que tienen columna propia en
-- registros_clinicos_genericos; el resto vive en datos_dinamicos (JSONB).
-- Debe coincidir con RegistroClinicoGenericoValueReader.CAMPOS_COMUNES y con
-- FiltroSqlBuilder.COLUMNAS_COMUNES, o los filtros leerían del sitio erróneo.
-- -----------------------------------------------------------------------------
INSERT INTO campos_clinicos (dataset_id, codigo, etiqueta, tipo_dato, es_comun, obligatorio, orden, activo)
SELECT (SELECT id FROM ds), c.codigo, c.etiqueta, c.tipo, c.comun, c.oblig, c.orden, true
FROM (VALUES
  ('pacienteCodigo',              'Paciente / HC',                        'TEXTO',    true,  true,   1),
  ('fechaEvento',                 'Fecha de intervención',                'FECHA',    true,  true,   2),
  ('procedimiento',               'Procedimiento quirúrgico',             'TEXTO',    true,  true,   3),
  ('infeccionLocalizacionQuirurgica','Infección de localización quirúrgica','BOOLEANO',false, false,  4),
  ('localizacionInfeccion',       'Localización de la infección',         'TEXTO',    false, false,  5),
  ('fechaInfeccion',              'Fecha de diagnóstico de ILQ',          'FECHA',    false, false,  6),
  ('profilaxisIndicada',          'Profilaxis indicada',                  'BOOLEANO', false, false,  7),
  ('profilaxisAntibiotica',       'Profilaxis administrada',              'BOOLEANO', false, false,  8),
  ('adecuacionProfilaxis',        'Adecuación de la profilaxis',          'TEXTO',    false, false,  9),
  ('motivoInadecuacionProfilaxis','Motivo de inadecuación',               'TEXTO',    false, false, 10),
  ('prescripcionProfilaxisDrago', 'Prescripción de profilaxis en Drago',  'BOOLEANO', false, false, 11),
  ('sexo',                        'Sexo',                                 'TEXTO',    true,  false, 12),
  ('edad',                        'Edad',                                 'ENTERO',   true,  false, 13),
  ('asa',                         'Clasificación ASA',                    'TEXTO',    false, false, 14),
  ('cirugiaUrgente',              'Cirugía urgente',                      'BOOLEANO', false, false, 15),
  ('gradoContaminacion',          'Grado de contaminación quirúrgica',    'TEXTO',    false, false, 16),
  ('cie10',                       'Código CIE-10',                        'TEXTO',    false, false, 17),
  ('cultivoIlq',                  'Cultivo de ILQ',                       'BOOLEANO', false, false, 18),
  ('microorganismo',              'Microorganismo aislado',               'TEXTO',    false, false, 19),
  ('duracionIntervencionMinutos', 'Duración de la intervención',          'ENTERO',   false, false, 20),
  ('antibioticoProfilaxis',       'Antibiótico de profilaxis',            'TEXTO',    false, false, 21),
  ('momentoAdministracionMinutos','Minutos respecto a la incisión',       'ENTERO',   false, false, 22),
  ('redosificacionIndicada',      'Redosificación indicada',              'BOOLEANO', false, false, 23),
  ('redosificacionRealizada',     'Redosificación realizada',             'BOOLEANO', false, false, 24),
  ('alergiaAntibiotico',          'Alergia antibiótica registrada',       'BOOLEANO', false, false, 25)
) AS c(codigo, etiqueta, tipo, comun, oblig, orden);

-- -----------------------------------------------------------------------------
-- 3. Intervenciones
--
-- Semilla 6901. Los multiplicadores (3, 5, 7, 11, 13, 17, 23) son primos y
-- coprimos con los tamaños de los ciclos, lo que reparte las categorías sin
-- dejar huecos. `(idx*7) % 18` recorre los 18 meses completos, así que ningún
-- mes queda vacío.
-- -----------------------------------------------------------------------------
CREATE TEMP TABLE gen ON COMMIT DROP AS
WITH base AS (
  SELECT
    i AS idx,
    6901 AS semilla,
    -- 1..220 → primera intervención; 221..280 → segunda de P0001..P0060.
    'P' || lpad((((i - 1) % 220) + 1)::text, 4, '0') AS paciente,
    (i > 220) AS es_segunda,
    -- Las segundas se concentran en los últimos 6 meses: así suelen quedar
    -- por detrás de la primera del mismo paciente.
    CASE WHEN i <= 220 THEN (i * 7) % 18 ELSE 12 + ((i * 5) % 6) END AS mes_offset,
    ((i * 13) % 27) + 1 AS dia
  FROM generate_series(1, 280) AS i
),
fechas AS (
  SELECT b.*,
         (DATE '2025-01-01' + (b.mes_offset || ' months')::interval + ((b.dia - 1) || ' days')::interval)::date AS fecha_evento
  FROM base b
),
clinico AS (
  SELECT f.*,
    (ARRAY['COLECISTECTOMIA','APENDICECTOMIA','HERNIA_INGUINAL','PROTESIS_CADERA','PROTESIS_RODILLA',
           'CIRUGIA_COLORRECTAL','CESAREA','HISTERECTOMIA','BYPASS_CORONARIO','MASTECTOMIA'])[((f.idx * 3) % 10) + 1] AS procedimiento,
    (ARRAY['K80','K35','K40','M16','M17','C18','O82','D25','I25','C50'])[((f.idx * 3) % 10) + 1] AS cie10,
    CASE WHEN (f.idx * 11) % 2 = 0 THEN 'MUJER' ELSE 'HOMBRE' END AS sexo,
    18 + ((f.idx * 17) % 65) AS edad,
    (ARRAY['I','II','III','IV'])[((f.idx * 7) % 4) + 1] AS asa,
    (ARRAY['LIMPIA','LIMPIA_CONTAMINADA','CONTAMINADA','SUCIA'])[((f.idx * 5) % 4) + 1] AS grado_contaminacion,
    (f.idx % 4 = 0) AS cirugia_urgente,
    30 + ((f.idx * 23) % 210) AS duracion,
    -- ILQ: null (no evaluado) < true < false. El grupo null permite medir
    -- completitud; null NUNCA significa "no hubo infección".
    CASE WHEN f.idx % 25 = 0 THEN NULL
         WHEN f.idx % 11 = 0 THEN true
         ELSE false END AS ilq,
    -- Profilaxis no indicada en 1 de cada 9 intervenciones.
    (f.idx % 9 <> 0) AS profilaxis_indicada
  FROM fechas f
),
profilaxis AS (
  SELECT c.*,
    CASE WHEN NOT c.profilaxis_indicada THEN 'NO_APLICA'
         WHEN c.idx % 5 = 0             THEN 'INADECUADA'
         ELSE 'ADECUADA' END AS adecuacion
  FROM clinico c
),
motivos AS (
  SELECT p.*,
    -- El índice combina idx/5 con idx/9. Con `(idx/5) % 9` a secas, el residuo 0
    -- solo se alcanzaba en múltiplos de 45, que son precisamente los excluidos
    -- por no tener profilaxis indicada: un motivo quedaba sin ningún caso.
    -- Sumar idx/9 rompe esa periodicidad y los nueve motivos reciben 5-6 casos.
    CASE WHEN p.adecuacion = 'INADECUADA'
         THEN (ARRAY['ANTIBIOTICO_INCORRECTO','DOSIS_INADECUADA','MOMENTO_INADECUADO','DURACION_INADECUADA',
                     'REDOSIFICACION_NO_REALIZADA','ALERGIA_NO_CONSIDERADA','AUSENCIA_PRESCRIPCION',
                     'OTRO','SIN_DOCUMENTAR'])[(((p.idx / 5) + (p.idx / 9)) % 9) + 1]
         END AS motivo
  FROM profilaxis p
)
SELECT m.*,
  -- --- Coherencia de ILQ -----------------------------------------------------
  CASE WHEN m.ilq THEN (ARRAY['INCISIONAL_SUPERFICIAL','INCISIONAL_PROFUNDA','ORGANO_ESPACIO','SIN_ESPECIFICAR'])[((m.idx / 11) % 4) + 1] END AS localizacion,
  CASE WHEN m.ilq THEN (m.fecha_evento + ((((m.idx * 3) % 29) + 1) || ' days')::interval)::date END AS fecha_infeccion,
  CASE WHEN m.ilq THEN ((m.idx / 11) % 2 = 0) END AS cultivo,
  CASE WHEN m.ilq AND ((m.idx / 11) % 2 = 0)
       THEN (ARRAY['STAPHYLOCOCCUS_AUREUS','ESCHERICHIA_COLI','ENTEROCOCCUS_FAECALIS','KLEBSIELLA_PNEUMONIAE',
                   'PSEUDOMONAS_AERUGINOSA','STAPHYLOCOCCUS_EPIDERMIDIS','POLIMICROBIANO','OTRO'])[((m.idx / 22) % 8) + 1]
       END AS microorganismo,
  -- --- Coherencia de profilaxis ---------------------------------------------
  -- Administrada salvo que no esté indicada o el motivo sea la ausencia de
  -- prescripción (no se puede administrar lo que no se prescribió).
  CASE WHEN NOT m.profilaxis_indicada THEN false
       WHEN m.motivo = 'AUSENCIA_PRESCRIPCION' THEN false
       ELSE true END AS profilaxis_administrada,
  CASE WHEN NOT m.profilaxis_indicada OR m.motivo = 'AUSENCIA_PRESCRIPCION' THEN NULL
       ELSE (ARRAY['CEFAZOLINA','AMOXICILINA_CLAVULANICO','CLINDAMICINA','VANCOMICINA','METRONIDAZOL','CEFUROXIMA'])[((m.idx * 5) % 6) + 1]
       END AS antibiotico,
  -- Ventana adecuada: entre 60 y 1 minutos ANTES de la incisión (negativo).
  -- MOMENTO_INADECUADO se sale de esa ventana a propósito.
  CASE WHEN NOT m.profilaxis_indicada OR m.motivo = 'AUSENCIA_PRESCRIPCION' THEN NULL
       WHEN m.motivo = 'MOMENTO_INADECUADO' THEN 25 + ((m.idx * 3) % 40)
       ELSE -1 - ((m.idx * 7) % 59) END AS momento_minutos,
  -- Redosificación: se indica en intervenciones largas (> 180 min).
  CASE WHEN NOT m.profilaxis_indicada THEN NULL
       WHEN m.motivo = 'REDOSIFICACION_NO_REALIZADA' THEN true
       ELSE (30 + ((m.idx * 23) % 210)) > 180 END AS redosificacion_indicada,
  CASE WHEN NOT m.profilaxis_indicada THEN NULL
       WHEN m.motivo = 'REDOSIFICACION_NO_REALIZADA' THEN false
       WHEN (30 + ((m.idx * 23) % 210)) > 180 THEN true
       ELSE NULL END AS redosificacion_realizada,
  CASE WHEN m.motivo = 'ALERGIA_NO_CONSIDERADA' THEN true
       ELSE (m.idx % 13 = 0) END AS alergia,
  -- --- Coherencia de Drago ---------------------------------------------------
  -- Solo tiene interés cuando la profilaxis está indicada. `null` = estado no
  -- documentado, distinto de "no prescrito".
  CASE WHEN NOT m.profilaxis_indicada THEN NULL
       WHEN m.motivo = 'AUSENCIA_PRESCRIPCION' THEN false
       WHEN m.idx % 17 = 0 THEN NULL
       WHEN m.idx % 7 = 0 THEN false
       ELSE true END AS drago
FROM motivos m;

-- -----------------------------------------------------------------------------
-- 4. Registros
-- -----------------------------------------------------------------------------
INSERT INTO registros_clinicos_genericos
  (dataset_id, paciente_codigo, fecha_evento, procedimiento, sexo, edad, hospital_id, datos_dinamicos, fecha_creacion)
SELECT
  (SELECT id FROM ds),
  g.paciente,
  g.fecha_evento,
  g.procedimiento,
  g.sexo,
  g.edad,
  (SELECT hospital_id FROM datasets_clinicos WHERE id = (SELECT id FROM ds)),
  -- strip_nulls deja fuera las claves sin valor: leerlas devuelve null igual,
  -- y el JSONB no se llena de nulos explícitos.
  jsonb_strip_nulls(jsonb_build_object(
    'infeccionLocalizacionQuirurgica', g.ilq,
    'localizacionInfeccion',           g.localizacion,
    'fechaInfeccion',                  g.fecha_infeccion,
    'profilaxisIndicada',              g.profilaxis_indicada,
    'profilaxisAntibiotica',           g.profilaxis_administrada,
    'adecuacionProfilaxis',            g.adecuacion,
    'motivoInadecuacionProfilaxis',    g.motivo,
    'prescripcionProfilaxisDrago',     g.drago,
    'asa',                             g.asa,
    'cirugiaUrgente',                  g.cirugia_urgente,
    'gradoContaminacion',              g.grado_contaminacion,
    'cie10',                           g.cie10,
    'cultivoIlq',                      g.cultivo,
    'microorganismo',                  g.microorganismo,
    'duracionIntervencionMinutos',     g.duracion,
    'antibioticoProfilaxis',           g.antibiotico,
    'momentoAdministracionMinutos',    g.momento_minutos,
    'redosificacionIndicada',          g.redosificacion_indicada,
    'redosificacionRealizada',         g.redosificacion_realizada,
    'alergiaAntibiotico',              g.alergia
  )),
  TIMESTAMP '2026-01-01 00:00:00'   -- fija: el seed no depende de la fecha actual
FROM gen g
ORDER BY g.idx;

COMMIT;
