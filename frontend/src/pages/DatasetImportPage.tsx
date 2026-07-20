import { useState } from 'react'
import { Link, useParams } from 'react-router'
import type {
  ErrorImportacionGenericaResponseDto,
  ImportacionGenericaResponseDto,
  ValidacionFilasImportacionGenericaResponseDto,
  ValidacionImportacionGenericaResponseDto,
} from '../api/types'
import { obtenerFrontendMetadata } from '../api/datasetApi'
import { listarPlantillasImportacion } from '../api/plantillasImportacionApi'
import {
  importarGenerico,
  obtenerErroresImportacionGenerica,
  validarFilasImportacionGenerica,
  validarImportacionGenerica,
} from '../api/importacionesApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { FileDropzone } from '../components/importacion/FileDropzone'
import { ImportValidationSummary } from '../components/importacion/ImportValidationSummary'
import { ImportErrorsTable } from '../components/importacion/ImportErrorsTable'
import styles from './DatasetImportPage.module.css'

type Paso = 'inicial' | 'columnas' | 'filas' | 'importado'

function claseEstado(estado: string): string {
  if (estado === 'IMPORTADA') return styles.estadoOk
  if (estado === 'RECHAZADA') return styles.estadoError
  return styles.estadoAviso
}

export function DatasetImportPage() {
  const { datasetId } = useParams<{ datasetId: string }>()

  const { data, loading, error } = useApiResource(
    async (signal) => {
      const [metadata, plantillas] = await Promise.all([
        obtenerFrontendMetadata(datasetId ?? '', signal),
        listarPlantillasImportacion(datasetId ?? '', signal),
      ])
      return { metadata, plantillas }
    },
    [datasetId],
  )

  const [plantillaId, setPlantillaId] = useState('')
  const [archivo, setArchivo] = useState<File | null>(null)
  const [indiceHoja, setIndiceHoja] = useState('0')
  const [filaCabecera, setFilaCabecera] = useState('')
  const [paso, setPaso] = useState<Paso>('inicial')
  const [ocupado, setOcupado] = useState(false)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)

  const [validacionColumnas, setValidacionColumnas] = useState<ValidacionImportacionGenericaResponseDto | null>(null)
  const [validacionFilas, setValidacionFilas] = useState<ValidacionFilasImportacionGenericaResponseDto | null>(null)
  const [resultado, setResultado] = useState<ImportacionGenericaResponseDto | null>(null)
  const [erroresImportacion, setErroresImportacion] = useState<ErrorImportacionGenericaResponseDto[] | null>(null)

  const plantillaSeleccionada = data?.plantillas.find((p) => String(p.id) === plantillaId) ?? null

  // Cambiar plantilla o archivo invalida cualquier resultado anterior.
  const reiniciar = () => {
    setPaso('inicial')
    setValidacionColumnas(null)
    setValidacionFilas(null)
    setResultado(null)
    setErroresImportacion(null)
    setErrorAccion(null)
  }

  const alCambiarPlantilla = (id: string) => {
    setPlantillaId(id)
    const plantilla = data?.plantillas.find((p) => String(p.id) === id)
    setFilaCabecera(plantilla?.filaCabecera === null || plantilla?.filaCabecera === undefined ? '' : String(plantilla.filaCabecera))
    reiniciar()
  }

  const alCambiarArchivo = (nuevo: File | null) => {
    setArchivo(nuevo)
    reiniciar()
  }

  const payload = () => ({
    archivo: archivo as File,
    plantillaId: Number(plantillaId),
    indiceHoja: indiceHoja === '' ? 0 : Number(indiceHoja),
    filaCabecera: filaCabecera === '' ? undefined : Number(filaCabecera),
  })

  const ejecutar = async (accion: () => Promise<void>) => {
    setErrorAccion(null)
    setOcupado(true)
    try {
      await accion()
    } catch (err) {
      setErrorAccion(err instanceof Error ? err.message : 'Error inesperado.')
    } finally {
      setOcupado(false)
    }
  }

  const validarColumnas = () =>
    ejecutar(async () => {
      const res = await validarImportacionGenerica(payload())
      setValidacionColumnas(res)
      setValidacionFilas(null)
      setPaso('columnas')
    })

  const validarFilas = () =>
    ejecutar(async () => {
      const res = await validarFilasImportacionGenerica(payload())
      setValidacionFilas(res)
      setPaso('filas')
    })

  const importar = () =>
    ejecutar(async () => {
      const res = await importarGenerico(payload())
      setResultado(res)
      setPaso('importado')
    })

  const verErrores = () =>
    ejecutar(async () => {
      if (!resultado) return
      const res = await obtenerErroresImportacionGenerica(resultado.importacionId)
      setErroresImportacion(res)
    })

  const sinCampos = data !== null && data.metadata.campos.length === 0
  const sinPlantillas = data !== null && data.plantillas.length === 0

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <Breadcrumbs
              items={[
                { label: 'Datasets', to: '/datasets' },
                { label: data.metadata.dataset.codigo, to: `/datasets/${datasetId}` },
                { label: 'Importar datos' },
              ]}
            />
            <h1>Importar datos</h1>
            <p className={styles.descripcion}>
              Sube un archivo con registros clínicos para validarlo e incorporarlo al dataset.
            </p>
            <p className={styles.aviso}>
              Los datos importados se guardarán como registros clínicos del dataset y podrán usarse en métricas,
              paneles y dashboards.
            </p>

            {sinCampos && (
              <div className={styles.avisoSinCampos} role="alert">
                <span>Este dataset todavía no tiene campos clínicos. Defínelos antes de importar datos.</span>
                <Link className="btn btnPrimary" to={`/datasets/${datasetId}/campos`}>
                  Definir campos
                </Link>
              </div>
            )}

            <ErrorBanner mensaje={errorAccion} />

            {sinPlantillas ? (
              <div>
                <p className="stateEmpty">Este dataset no tiene plantillas de importación.</p>
                <Link className="btn btnPrimary" to={`/datasets/${datasetId}/plantillas/nueva`}>
                  Crear plantilla de importación
                </Link>
              </div>
            ) : (
              <>
                <Card title="1. Elige la plantilla y el archivo">
                  <div className={styles.selector}>
                    <label htmlFor="plantilla">Plantilla de importación</label>
                    <select
                      id="plantilla"
                      value={plantillaId}
                      onChange={(e) => alCambiarPlantilla(e.target.value)}
                    >
                      <option value="">— seleccionar plantilla —</option>
                      {data.plantillas.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.nombre}
                        </option>
                      ))}
                    </select>
                  </div>
                  {plantillaSeleccionada && (
                    <ul className={styles.datosPlantilla}>
                      <li>Origen: {plantillaSeleccionada.origen ?? '—'}</li>
                      <li>Fila de cabecera: {plantillaSeleccionada.filaCabecera ?? '—'}</li>
                      <li>Activa: {plantillaSeleccionada.activa ? 'Sí' : 'No'}</li>
                      <li>
                        <Link to={`/datasets/${datasetId}/plantillas/${plantillaSeleccionada.id}/mapeos`}>
                          Ver mapeos
                        </Link>
                      </li>
                    </ul>
                  )}

                  <div style={{ marginTop: 'var(--spacing-md)' }}>
                    <FileDropzone archivo={archivo} onArchivoSeleccionado={alCambiarArchivo} />
                  </div>

                  <details className={styles.avanzadas} style={{ marginTop: 'var(--spacing-md)' }}>
                    <summary>Opciones avanzadas</summary>
                    <div className={styles.avanzadasGrid}>
                      <label>
                        Índice de hoja (Excel)
                        <input
                          type="number"
                          step={1}
                          min={0}
                          value={indiceHoja}
                          onChange={(e) => {
                            setIndiceHoja(e.target.value)
                            reiniciar()
                          }}
                        />
                      </label>
                      <label>
                        Fila de cabecera
                        <input
                          type="number"
                          step={1}
                          min={0}
                          value={filaCabecera}
                          placeholder="según plantilla"
                          onChange={(e) => {
                            setFilaCabecera(e.target.value)
                            reiniciar()
                          }}
                        />
                      </label>
                    </div>
                  </details>

                  <div className={styles.acciones} style={{ marginTop: 'var(--spacing-md)' }}>
                    <button
                      type="button"
                      className="btn btnPrimary"
                      disabled={!plantillaId || !archivo || ocupado}
                      onClick={validarColumnas}
                    >
                      {ocupado && paso === 'inicial' ? 'Validando…' : 'Validar archivo'}
                    </button>
                  </div>
                </Card>

                {validacionColumnas && (
                  <Card title="2. Validación de columnas">
                    <ImportValidationSummary validacion={validacionColumnas} />
                    <div className={styles.acciones} style={{ marginTop: 'var(--spacing-md)' }}>
                      <button type="button" className="btn btnPrimary" disabled={ocupado} onClick={validarFilas}>
                        Validar filas
                      </button>
                    </div>
                  </Card>
                )}

                {validacionFilas && (
                  <Card title="3. Validación de filas">
                    <ul className={styles.metricas}>
                      <li>
                        Importable:{' '}
                        <span className={validacionFilas.importable ? styles.estadoOk : styles.estadoError}>
                          {validacionFilas.importable ? 'Sí' : 'No'}
                        </span>
                      </li>
                      <li>Filas leídas: {validacionFilas.totalFilasLeidas}</li>
                      <li>Válidas: {validacionFilas.filasValidas}</li>
                      <li>Con error: {validacionFilas.filasConError}</li>
                      <li>Con advertencia: {validacionFilas.filasConAdvertencia}</li>
                      <li>Advertencias: {validacionFilas.totalAdvertencias}</li>
                    </ul>
                    <p>{validacionFilas.resumen}</p>

                    {validacionFilas.erroresBloqueantes.length > 0 && (
                      <>
                        <h4>Errores que impiden importar</h4>
                        <ImportErrorsTable errores={validacionFilas.erroresBloqueantes} />
                      </>
                    )}
                    {validacionFilas.errores.length > 0 && (
                      <>
                        <h4>Todos los errores y advertencias</h4>
                        <ImportErrorsTable errores={validacionFilas.errores} />
                      </>
                    )}

                    {!validacionFilas.importable && (
                      <p className={styles.noImportable} role="alert">
                        El archivo no se puede importar. Corrige los datos del archivo o ajusta la plantilla y sus
                        mapeos antes de volver a intentarlo.
                      </p>
                    )}

                    <div className={styles.acciones} style={{ marginTop: 'var(--spacing-md)' }}>
                      <button
                        type="button"
                        className="btn btnPrimary"
                        disabled={!validacionFilas.importable || ocupado}
                        onClick={importar}
                      >
                        Importar datos
                      </button>
                    </div>
                  </Card>
                )}

                {resultado && (
                  <Card title="4. Resultado de la importación">
                    <ul className={styles.metricas}>
                      <li>
                        Estado: <span className={claseEstado(resultado.estado)}>{resultado.estado}</span>
                      </li>
                      <li>Filas leídas: {resultado.filasLeidas}</li>
                      <li>Filas importadas: {resultado.filasImportadas}</li>
                      <li>Filas con error: {resultado.filasConError}</li>
                      <li>Advertencias: {resultado.totalAdvertencias}</li>
                      <li>Importación nº {resultado.importacionId}</li>
                    </ul>
                    <p>{resultado.mensaje}</p>

                    <div className={styles.acciones}>
                      <button type="button" className="btn btnSecondary" disabled={ocupado} onClick={verErrores}>
                        Ver errores de esta importación
                      </button>
                    </div>

                    {erroresImportacion && (
                      <div style={{ marginTop: 'var(--spacing-md)' }}>
                        <ImportErrorsTable errores={erroresImportacion} />
                      </div>
                    )}

                    <div className={styles.ctas}>
                      <Link className="btn btnPrimary" to={`/datasets/${datasetId}/metricas`}>
                        Ver métricas del dataset
                      </Link>
                      <Link className="btn btnSecondary" to={`/datasets/${datasetId}/paneles`}>
                        Ver paneles
                      </Link>
                      <Link className="btn btnSecondary" to={`/datasets/${datasetId}/metricas/nueva`}>
                        Crear métrica
                      </Link>
                      <Link className="btn btnSecondary" to={`/datasets/${datasetId}`}>
                        Volver al dataset
                      </Link>
                    </div>
                  </Card>
                )}
              </>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
