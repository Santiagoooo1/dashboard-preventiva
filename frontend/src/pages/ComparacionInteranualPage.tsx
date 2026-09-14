import { useEffect, useMemo, useState } from 'react'
import type {
  CampoMetricaMetadataDto,
  ComparacionInteranualResponseDto,
  DatasetClinicoResponseDto,
  Granularidad,
  TipoComparacionInteranual,
} from '../api/types'
import { listarDatasets } from '../api/datasetApi'
import { obtenerMetadataMetricas } from '../api/metricasApi'
import { compararInteranual } from '../api/comparacionesApi'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { FormField } from '../components/FormField'
import { MatrizCategoriasInteranual, MatrizInteranual } from '../components/comparacion/MatrizInteranual'
import { ResumenAnual } from '../components/comparacion/ResumenAnual'
import { GraficaInteranual } from '../components/comparacion/GraficaInteranual'
import { SelectorDatasets } from '../components/comparacion/SelectorDatasets'
import {
  ETIQUETA_COMPARACION,
  comparacionesPara,
  conceptosComunes as interseccionConceptos,
} from '../components/comparacion/conceptosComparables'
import styles from '../components/comparacion/Comparacion.module.css'

/**
 * Umbral de categorías por encima del cual una tabla por columnas deja de
 * poder leerse. Es el mismo orden de magnitud que ya usa el sistema para
 * decidir si un campo sirve como dimensión.
 */
const MAX_CATEGORIAS_LEGIBLES = 25

type Vista = 'tabla' | 'grafica'

/**
 * Comparación de un concepto clínico entre años (Fase 6.9P).
 *
 * <p>Sirve tanto si cada año está en su propio dataset como si un mismo
 * dataset trae varios: en ambos casos se manda la misma petición y el backend
 * devuelve una serie por año.
 *
 * <p>La lista de variables no son los campos del primer dataset, sino los
 * conceptos que existen —con el mismo tipo— en TODOS los seleccionados. Ofrecer
 * uno que falte en algún año llevaría a una comparación que el backend va a
 * rechazar, y el usuario no entendería por qué se lo ofrecimos.
 */
export function ComparacionInteranualPage() {
  const [datasets, setDatasets] = useState<DatasetClinicoResponseDto[]>([])
  const [seleccionados, setSeleccionados] = useState<number[]>([])
  const [camposPorDataset, setCamposPorDataset] = useState<Map<number, CampoMetricaMetadataDto[]>>(new Map())

  const [codigo, setCodigo] = useState('')
  const [tipo, setTipo] = useState<TipoComparacionInteranual>('TASA')
  const [granularidad, setGranularidad] = useState<Granularidad>('MES')
  const [vista, setVista] = useState<Vista>('tabla')

  const [comparacion, setComparacion] = useState<ComparacionInteranualResponseDto | null>(null)
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listarDatasets()
      .then(setDatasets)
      .catch((e) => setError(e instanceof Error ? e.message : 'No se pudieron cargar los datasets.'))
  }, [])

  // La metadata de cada dataset se pide una vez y se guarda: la intersección se
  // recalcula al marcar o desmarcar sin volver a la red.
  useEffect(() => {
    const pendientes = seleccionados.filter((id) => !camposPorDataset.has(id))
    if (pendientes.length === 0) return
    Promise.all(pendientes.map((id) => obtenerMetadataMetricas(id).then((m) => [id, m.campos] as const)))
      .then((cargados) => {
        setCamposPorDataset((actual) => {
          const siguiente = new Map(actual)
          cargados.forEach(([id, campos]) => siguiente.set(id, campos))
          return siguiente
        })
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'No se pudieron leer los campos.'))
  }, [seleccionados, camposPorDataset])

  // La intersección vive en un módulo compartido con el editor de informes: son
  // las mismas reglas clínicas y duplicarlas acabaría ofreciendo opciones
  // distintas en cada pantalla.
  const conceptosComunes = useMemo(
    () => interseccionConceptos(seleccionados, camposPorDataset),
    [seleccionados, camposPorDataset],
  )

  const campoElegido = conceptosComunes.find((c) => c.codigo === codigo)
  // Memoizado: es dependencia de un efecto, y recrear el array en cada render
  // lo dispararía siempre.
  const opcionesTipo = useMemo(
    () => (campoElegido ? comparacionesPara(campoElegido.tipoDato) : []),
    [campoElegido],
  )

  // Si el campo cambia y el tipo elegido ya no aplica, se ajusta solo en vez de
  // dejar una combinación que el backend rechazaría.
  useEffect(() => {
    if (opcionesTipo.length > 0 && !opcionesTipo.includes(tipo)) {
      setTipo(opcionesTipo[0])
    }
  }, [opcionesTipo, tipo])

  const alternarDataset = (id: number) => {
    setComparacion(null)
    setSeleccionados((actual) =>
      actual.includes(id) ? actual.filter((x) => x !== id) : [...actual, id],
    )
  }

  const limpiarSeleccion = () => {
    setComparacion(null)
    setSeleccionados([])
  }

  const comparar = async () => {
    if (seleccionados.length === 0 || !codigo) return
    setCargando(true)
    setError(null)
    try {
      setComparacion(
        await compararInteranual({
          datasetIds: seleccionados,
          codigoCanonico: codigo,
          tipoComparacion: tipo,
          granularidad,
        }),
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo calcular la comparación.')
      setComparacion(null)
    } finally {
      setCargando(false)
    }
  }

  const demasiadasCategorias =
    comparacion?.tipoComparacion === 'DISTRIBUCION' &&
    comparacion.categorias.length > MAX_CATEGORIAS_LEGIBLES

  return (
    <div>
      <Card title="Comparar años">
        <p>
          Compara un mismo indicador entre varios años. Sirve tanto si cada año está en un dataset distinto como si
          un mismo dataset contiene varios años: los años se deducen de las fechas de los registros, no del nombre.
        </p>

        <ErrorBanner mensaje={error} />

        <FormField label="Datasets a comparar">
          <SelectorDatasets
            datasets={datasets}
            seleccionados={seleccionados}
            onAlternar={alternarDataset}
            onLimpiar={limpiarSeleccion}
          />
        </FormField>

        <div className={styles.grid}>
          <FormField
            label="Variable"
            help={
              seleccionados.length > 1
                ? 'Solo se ofrecen los conceptos que existen, con el mismo tipo, en todos los seleccionados.'
                : undefined
            }
          >
            <select value={codigo} onChange={(e) => setCodigo(e.target.value)} disabled={conceptosComunes.length === 0}>
              <option value="">
                {seleccionados.length === 0 ? 'Elige antes los datasets' : 'Selecciona una variable'}
              </option>
              {conceptosComunes
                .filter((c) => comparacionesPara(c.tipoDato).length > 0)
                .map((c) => (
                  <option key={c.codigo} value={c.codigo}>
                    {c.etiqueta}
                  </option>
                ))}
            </select>
          </FormField>

          <FormField label="Qué comparar">
            <select
              value={tipo}
              onChange={(e) => setTipo(e.target.value as TipoComparacionInteranual)}
              disabled={opcionesTipo.length <= 1}
            >
              {opcionesTipo.map((t) => (
                <option key={t} value={t}>
                  {ETIQUETA_COMPARACION[t]}
                </option>
              ))}
            </select>
          </FormField>

          <FormField label="Granularidad">
            <select value={granularidad} onChange={(e) => setGranularidad(e.target.value as Granularidad)}>
              <option value="MES">Mes</option>
              <option value="TRIMESTRE">Trimestre</option>
              <option value="ANIO">Año</option>
            </select>
          </FormField>
        </div>

        <div className={styles.pestanas}>
          <button
            type="button"
            className="btn btnPrimary"
            disabled={cargando || seleccionados.length === 0 || !codigo}
            onClick={comparar}
          >
            {cargando ? 'Calculando…' : 'Comparar'}
          </button>
        </div>
      </Card>

      {comparacion && !comparacion.comparable && (
        <Card title="No se puede comparar">
          {/* El backend explica el motivo en términos clínicos; no se muestra
              una tabla vacía que se leería como «no hubo casos». */}
          <p>{comparacion.motivoNoComparable}</p>
        </Card>
      )}

      {comparacion?.comparable && (
        <Card title={comparacion.etiquetaConcepto}>
          {comparacion.advertencias.length > 0 && (
            <div className={styles.avisos}>
              {comparacion.advertencias.map((a, i) => (
                <p key={i} className={styles.aviso}>
                  {a.mensaje}
                </p>
              ))}
            </div>
          )}

          <div className={styles.pestanas}>
            <button
              type="button"
              className={vista === 'tabla' ? 'btn btnPrimary' : 'btn btnSecondary'}
              onClick={() => setVista('tabla')}
            >
              Tabla
            </button>
            <button
              type="button"
              className={vista === 'grafica' ? 'btn btnPrimary' : 'btn btnSecondary'}
              onClick={() => setVista('grafica')}
            >
              Gráfica
            </button>
          </div>

          <ResumenAnual comparacion={comparacion} />

          {demasiadasCategorias ? (
            <p className={styles.nota}>
              Esta variable tiene {comparacion.categorias.length} categorías: demasiadas para una tabla por columnas.
              Prueba con otra variable o compara solo el total por año.
            </p>
          ) : vista === 'tabla' ? (
            comparacion.tipoComparacion === 'DISTRIBUCION' ? (
              <MatrizCategoriasInteranual comparacion={comparacion} />
            ) : (
              <MatrizInteranual comparacion={comparacion} />
            )
          ) : (
            <GraficaInteranual comparacion={comparacion} />
          )}
        </Card>
      )}
    </div>
  )
}
