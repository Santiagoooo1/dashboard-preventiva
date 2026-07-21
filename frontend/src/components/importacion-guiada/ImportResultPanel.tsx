import { useState } from 'react'
import { Link } from 'react-router'
import type { ErrorImportacionGenericaResponseDto } from '../../api/types'
import type { ResultadoAsistente } from '../../utils/importacionGuiada/orquestador'
import { obtenerErroresImportacionGenerica } from '../../api/importacionesApi'
import { mensajeAmableFallo } from '../../utils/importacionGuiada/sugerenciasErrores'
import { Card } from '../Card'
import { ErrorBanner } from '../ErrorBanner'
import { ImportErrorsTable } from '../importacion/ImportErrorsTable'
import styles from './ImportacionGuiada.module.css'

interface ImportResultPanelProps {
  resultado: ResultadoAsistente
  /** Vuelve al paso Columnas conservando archivo y configuración (dataset parcial). */
  onVolverAColumnas?: () => void
}

// Mensaje propio y en lenguaje claro: el mensaje del backend puede incluir
// referencias técnicas (rutas de API) que no deben verse en este flujo.
function mensajeAmable(estado: string): string {
  if (estado === 'IMPORTADA') return 'Los registros se han importado correctamente.'
  if (estado === 'IMPORTADA_CON_ERRORES') {
    return 'Los registros se han importado. Algunas filas generaron advertencias que puedes revisar.'
  }
  return 'La importación no se ha completado.'
}

export function ImportResultPanel({ resultado, onVolverAColumnas }: ImportResultPanelProps) {
  const { importacion, validacionFilas, datasetId, error, pasoFallido } = resultado
  const huboExito = importacion !== null && importacion.estado !== 'RECHAZADA'
  const { mensaje: mensajeFallo, detalleTecnico } = mensajeAmableFallo(pasoFallido, error)

  const [advertencias, setAdvertencias] = useState<ErrorImportacionGenericaResponseDto[] | null>(null)
  const [cargandoAdv, setCargandoAdv] = useState(false)
  const [errorAdv, setErrorAdv] = useState<string | null>(null)

  const verAdvertencias = async () => {
    if (!importacion) return
    setErrorAdv(null)
    setCargandoAdv(true)
    try {
      setAdvertencias(await obtenerErroresImportacionGenerica(importacion.importacionId))
    } catch (err) {
      setErrorAdv(err instanceof Error ? err.message : 'No se pudieron cargar las advertencias.')
    } finally {
      setCargandoAdv(false)
    }
  }

  const tieneAdvertencias = importacion !== null && importacion.totalAdvertencias > 0

  return (
    <div>
      {huboExito ? (
        <Card title="Importación completada">
          <ul className={styles.resumen}>
            <li>
              Estado:{' '}
              <span className={importacion.estado === 'IMPORTADA' ? styles.estadoOk : styles.estadoAviso}>
                {importacion.estado === 'IMPORTADA' ? 'Importado' : 'Importado con advertencias'}
              </span>
            </li>
            <li>Columnas creadas: {resultado.camposCreados}</li>
            <li>Filas leídas: {importacion.filasLeidas}</li>
            <li>Filas importadas: {importacion.filasImportadas}</li>
            <li>Filas con error: {importacion.filasConError}</li>
            <li>Advertencias: {importacion.totalAdvertencias}</li>
          </ul>
          <p>{mensajeAmable(importacion.estado)}</p>

          {tieneAdvertencias && (
            <>
              <div className={styles.acciones}>
                <button type="button" className="btn btnSecondary" disabled={cargandoAdv} onClick={verAdvertencias}>
                  {cargandoAdv ? 'Cargando…' : 'Ver advertencias'}
                </button>
              </div>
              <ErrorBanner mensaje={errorAdv} />
              {advertencias && (
                <div style={{ marginTop: 'var(--spacing-md)' }}>
                  <ImportErrorsTable errores={advertencias} />
                </div>
              )}
            </>
          )}
        </Card>
      ) : (
        <Card title="No se pudo completar la importación">
          <ErrorBanner mensaje={mensajeFallo} />
          {detalleTecnico && (
            <details className={styles.opcionSecundaria}>
              <summary>Detalle técnico para soporte</summary>
              <p>{detalleTecnico}</p>
            </details>
          )}
          {datasetId !== null && <p>Se creó un dashboard parcial, pero no se importaron registros.</p>}
          {validacionFilas && validacionFilas.errores.length > 0 && (
            <>
              <h4>Errores en las filas</h4>
              <ImportErrorsTable errores={validacionFilas.errores} />
            </>
          )}
        </Card>
      )}

      <div className={styles.ctas}>
        {huboExito && datasetId !== null && (
          <>
            <Link className="btn btnPrimary" to={`/datasets/${datasetId}`}>
              Ver dataset
            </Link>
            <Link className="btn btnSecondary" to={`/datasets/${datasetId}/metricas/nueva`}>
              Crear indicadores
            </Link>
            <Link className="btn btnSecondary" to={`/datasets/${datasetId}/paneles/nuevo`}>
              Crear panel
            </Link>
            <Link className="btn btnSecondary" to={`/datasets/${datasetId}`}>
              Configuración avanzada
            </Link>
          </>
        )}
        {!huboExito && datasetId !== null && (
          <>
            <Link className="btn btnPrimary" to={`/datasets/${datasetId}`}>
              Ver dataset parcial
            </Link>
            <Link className="btn btnSecondary" to={`/datasets/${datasetId}`}>
              Archivar desde modo avanzado
            </Link>
            <button type="button" className="btn btnSecondary" onClick={onVolverAColumnas}>
              Continuar con una nueva importación corregida
            </button>
          </>
        )}
        {datasetId === null && (
          <Link className="btn btnSecondary" to="/datasets">
            Ir a datasets
          </Link>
        )}
      </div>
    </div>
  )
}
