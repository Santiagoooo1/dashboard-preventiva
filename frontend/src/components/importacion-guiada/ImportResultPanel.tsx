import { useState } from 'react'
import { Link } from 'react-router'
import type { ErrorImportacionGenericaResponseDto } from '../../api/types'
import type { ResultadoAsistente } from '../../utils/importacionGuiada/orquestador'
import { obtenerErroresImportacionGenerica } from '../../api/importacionesApi'
import { mensajeAmableFallo } from '../../utils/importacionGuiada/sugerenciasErrores'
import { Card } from '../Card'
import { ErrorBanner } from '../ErrorBanner'
import { ImportErrorsTable } from '../importacion/ImportErrorsTable'
import { DashboardInicialCta } from './DashboardInicialCta'
import { TrazabilidadImportacionPanel } from '../trazabilidad/TrazabilidadImportacionPanel'
import styles from './ImportacionGuiada.module.css'

interface ImportResultPanelProps {
  resultado: ResultadoAsistente
  /** Vuelve al paso Columnas conservando archivo y configuración (dataset parcial). */
  onVolverAColumnas?: () => void
  /** Reinicia el asistente desde cero para crear otro dashboard. */
  onCrearOtroDashboard?: () => void
  /** Presente cuando la importación se hizo desde una copia de trabajo corregida en la app. */
  extra?: { filasExcluidas: number; resumen: string; importacionTrabajoId: number }
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

export function ImportResultPanel({
  resultado,
  onVolverAColumnas,
  onCrearOtroDashboard,
  extra,
}: ImportResultPanelProps) {
  const { importacion, validacionFilas, datasetId, error, pasoFallido } = resultado
  const huboExito = importacion !== null && importacion.estado !== 'RECHAZADA'
  const { mensaje: mensajeFallo, detalleTecnico } = mensajeAmableFallo(pasoFallido, error)

  const [advertencias, setAdvertencias] = useState<ErrorImportacionGenericaResponseDto[] | null>(null)
  const [cargandoAdv, setCargandoAdv] = useState(false)
  const [errorAdv, setErrorAdv] = useState<string | null>(null)
  const [mostrarTrazabilidad, setMostrarTrazabilidad] = useState(false)

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
        <Card title="Datos importados correctamente" className={styles.cardExito}>
          <ul className={styles.resumen}>
            <li>Archivo importado: {importacion.nombreArchivo}</li>
            <li>Filas importadas: {importacion.filasImportadas}</li>
            {extra && <li>Filas excluidas: {extra.filasExcluidas}</li>}
            {importacion.filasConError > 0 && <li>Filas con error: {importacion.filasConError}</li>}
            {tieneAdvertencias && <li>Advertencias: {importacion.totalAdvertencias}</li>}
            <li>Dataset: creado</li>
          </ul>
          <p>{extra ? extra.resumen : mensajeAmable(importacion.estado)}</p>

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
                  <ImportErrorsTable errores={advertencias} mensajeVacio="No hay advertencias." />
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

      {huboExito && datasetId !== null && (
        <div className={styles.ctaPrincipal}>
          <DashboardInicialCta datasetId={datasetId} />
        </div>
      )}

      <div className={styles.ctas}>
        {huboExito && datasetId !== null && (
          <>
            <Link className="btn btnSecondary" to={`/datasets/${datasetId}`}>
              Ver dataset
            </Link>
            {onCrearOtroDashboard && (
              <button type="button" className="btn btnSecondary" onClick={onCrearOtroDashboard}>
                Crear otro dashboard
              </button>
            )}
            <Link className="btn btnSecondary" to="/datasets">
              Ir a datasets
            </Link>
            <button
              type="button"
              className="btn btnSecondary"
              onClick={() => setMostrarTrazabilidad((v) => !v)}
            >
              {mostrarTrazabilidad ? 'Ocultar trazabilidad' : 'Ver trazabilidad de la importación'}
            </button>
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

      {huboExito && mostrarTrazabilidad && (
        <div style={{ marginTop: 'var(--spacing-md)' }}>
          {extra ? (
            <TrazabilidadImportacionPanel importacionTrabajoId={extra.importacionTrabajoId} />
          ) : (
            <TrazabilidadImportacionPanel importacionGenericaId={importacion.importacionId} />
          )}
        </div>
      )}
    </div>
  )
}
