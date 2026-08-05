# Datos de demostración — ILQ, profilaxis y Drago

Scripts para dejar el entorno **local** con un dataset sintético reproducible de
infección de localización quirúrgica (ILQ), adecuación de profilaxis y
prescripción en Drago.

> **Nunca ejecutar contra producción ni contra una base con datos reales.**
> Haz siempre el backup del paso 1 antes de tocar nada.

## Dos flujos, separados a propósito

| | Flujo normal | Flujo histórico |
|---|---|---|
| Scripts | `reset` → `seed` → `verify` | `archive/cleanup-pre-69i1-test-data.sql` |
| Alcance | **solo** `demo_ilq_profilaxis_drago` | datasets de prueba antiguos + pipeline legacy |
| Repetible | sí, tantas veces como quieras | **no**, se ejecutó una vez |
| Confirmación | no hace falta | exige `-v CONFIRM_CLEANUP=SI` |

**El flujo normal nunca toca otros datasets.** Cuando vuelvan a importarse
CESÁREAS, ILQ TRAUMA, ILQ CIR. GASTRO-ESOF. o cualquier Excel nuevo, podrás
regenerar la demo sin miedo: el reset solo borra el dataset sintético.

**El script histórico sí los borraría.** Está archivado por trazabilidad y
**no debe ejecutarse después de importar nuevos datos desde Excel.**

## Requisitos

- Docker con el contenedor `preventiva_postgres` arriba (`docker compose up -d`).
- Base `preventiva_db`, usuario `preventiva_user`.
- Comprobar antes de nada que estás en la base correcta:

```cmd
docker exec preventiva_postgres psql -U preventiva_user -d preventiva_db -tAc "SELECT current_database();"
```

Debe responder `preventiva_db`.

## 1. Backup (obligatorio antes del reset)

```cmd
mkdir backups
docker exec preventiva_postgres pg_dump -U preventiva_user -d preventiva_db --clean --if-exists --no-owner --no-privileges > backups\preventiva_antes_de_demo.sql
```

Verifica que el fichero pesa más de 0 bytes y contiene `CREATE TABLE`. La carpeta
`backups/` está en `.gitignore`: los volcados **no se versionan** porque pueden
contener datos clínicos.

## 2. Reset (flujo normal)

```cmd
docker exec -i preventiva_postgres psql -U preventiva_user -d preventiva_db -v ON_ERROR_STOP=1 < scripts\demo\reset-demo-ilq.sql
```

**Elimina exclusivamente el dataset `demo_ilq_profilaxis_drago`** y lo que
cuelga de él, dentro de una transacción (si algo falla, `ROLLBACK` completo):

- sus widgets, paneles y métricas;
- sus registros clínicos, campos y mapeos;
- sus plantillas, importaciones y copias de trabajo.

**Nunca toca**: otros datasets ni sus importaciones, el pipeline legacy
(`cirugias`, `infecciones_quirurgicas`, `profilaxis`, `medidas_preventivas`,
`seguimientos_postoperatorios`, `microbiologias`), usuarios, roles, hospitales,
servicios, `plantillas_excel` ni `mapeos_columnas_excel`.

El dataset se resuelve por **código estable**, nunca por id ni por patrones
`LIKE`: los identificadores cambian entre entornos y un patrón podría capturar
datos que no son de la demo.

Si el dataset no existe, el script informa y termina correctamente sin borrar
nada. Antes de eliminar imprime un aviso con lo que va a borrar.

## 3. Seed

```cmd
docker exec -i preventiva_postgres psql -U preventiva_user -d preventiva_db -v ON_ERROR_STOP=1 < scripts\demo\seed-demo-ilq.sql
```

Crea el dataset `demo_ilq_profilaxis_drago` (**DEMO_ILQ_PROFILAXIS_DRAGO**) con:

| Concepto | Valor |
|---|---|
| Intervenciones | 280 |
| Pacientes distintos | 220 |
| Pacientes con 2 intervenciones | 60 |
| Campos clínicos | 25 |
| Periodo | 2025-01-01 → 2026-06-30 (18 meses, ninguno vacío) |
| Semilla | **6901** |

El seed **empieza borrando su propio dataset**, así que es seguro ejecutarlo
varias veces seguidas: siempre deja un único dataset con los mismos datos.

## 4. Verificación

```cmd
docker exec -i preventiva_postgres psql -U preventiva_user -d preventiva_db < scripts\demo\verify-demo-ilq.sql
```

Devuelve 47 comprobaciones con `OK`/`ERROR`: conteos, periodo, coherencia
ILQ ↔ localización ↔ cultivo ↔ microorganismo, coherencia
profilaxis ↔ adecuación ↔ motivo, estados de Drago, ausencia de restos de fases
anteriores, huérfanos y preservación de usuarios/roles.

Las tres últimas (45–47) son las que cierran la Fase 6.9I.1.1: comprueban que el
reset conserva `plantillas_excel`, `mapeos_columnas_excel` y los campos clínicos
de los demás datasets.

**Si alguna sale ERROR, la carga no es válida** y no debe construirse ningún
indicador clínico sobre ella.

## Flujo histórico (archivado — no forma parte del ciclo normal)

`archive/cleanup-pre-69i1-test-data.sql` documenta la limpieza que se ejecutó
**una sola vez**, con autorización expresa, antes de crear el dataset sintético:
eliminó `DEMO_MVP_ILQ_part2`, `CESAREAS_2026_CESAREAS_2`,
`ILQ_TRAUMA_2026_TRAUMATOLOGIA`, `ILQ_CIR_GASTRO_ESOF_PROSPECTIVA…`, las métricas
`TEST_69E_*` y el pipeline legacy completo (91 cirugías y sus tablas hijas).

> ⚠️ **Este script histórico no debe ejecutarse después de importar nuevos datos
> desde Excel.** Borraría esas importaciones y vaciaría las tablas legacy.

Exige confirmación explícita; sin ella aborta antes del primer `DELETE`:

```cmd
docker exec -i preventiva_postgres psql -U preventiva_user -d preventiva_db -v CONFIRM_CLEANUP=SI < scripts\demo\archive\cleanup-pre-69i1-test-data.sql
```

Además comprueba que la base sea `preventiva_db` y aborta en cualquier otra.

## 5. Restaurar el backup

```cmd
docker exec -i preventiva_postgres psql -U preventiva_user -d preventiva_db < backups\preventiva_antes_de_demo.sql
```

El volcado se genera con `--clean --if-exists`, así que recrea los objetos desde
cero sin necesidad de borrar la base a mano.

## Datos sintéticos

Todos los datos los genera una fórmula: **no proceden de ningún paciente real ni
de ninguna exportación hospitalaria**. Los códigos de paciente son `P0001`…`P0220`.

Nada de `random()`: cada atributo se deriva del índice de la intervención con
aritmética modular (multiplicadores primos y la semilla 6901), de modo que dos
ejecuciones producen exactamente los mismos registros. Verificado comparando el
hash MD5 del contenido entre ejecuciones sucesivas.

**Las distribuciones no representan epidemiología real.** Están elegidas para que
cada categoría analítica tenga casos suficientes que visualizar: ~24 ILQ, las 4
localizaciones, 50 profilaxis inadecuadas repartidas entre los 9 motivos, y los
tres estados de Drago (sí / no / sin documentar).

## Reglas de coherencia garantizadas

- **ILQ = sí** → localización y fecha de diagnóstico informadas, la fecha dentro
  de los 30 días siguientes a la intervención.
- **ILQ = no o sin documentar** → sin localización, sin fecha, sin microorganismo.
- **Microorganismo** solo cuando el cultivo es positivo.
- **Profilaxis no indicada** → adecuación `NO_APLICA`, sin motivo, Drago sin dato.
- **Adecuada** → sin motivo, con antibiótico y administración dentro de la ventana.
- **Inadecuada** → motivo obligatorio y coherente con los campos relacionados
  (`AUSENCIA_PRESCRIPCION` → no administrada y Drago = no;
  `MOMENTO_INADECUADO` → administración fuera de ventana;
  `REDOSIFICACION_NO_REALIZADA` → indicada pero no realizada;
  `ALERGIA_NO_CONSIDERADA` → alergia registrada).
- **`null` nunca equivale a `no`**: el estado sin documentar es un valor propio,
  pensado para poder medir completitud.
