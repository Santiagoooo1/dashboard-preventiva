import { useCallback, useEffect, useState } from 'react'
import {
  PASOS_DASHBOARD_INICIAL,
  buscarDashboardInicialExistente,
  crearDashboardInicial,
  datasetTieneRegistros,
} from './orquestadorDashboardInicial'
import type { PasoProgresoDashboardInicial, ResultadoDashboardInicial } from './orquestadorDashboardInicial'

export type EstadoDashboardInicial = 'comprobando' | 'sin-datos' | 'idle' | 'creando' | 'creado' | 'error'

export interface UseDashboardInicialResult {
  estado: EstadoDashboardInicial
  /** Id del panel ya existente, si `estado` es 'idle'/'creado' y ya había uno. */
  panelExistenteId: number | null
  pasos: PasoProgresoDashboardInicial[]
  resultado: ResultadoDashboardInicial | null
  error: string | null
  /** Devuelve el resultado directamente (no solo vía estado) para que el llamante decida si navega. */
  crear: () => Promise<ResultadoDashboardInicial>
}

/**
 * Estado y acciones compartidas entre todos los puntos de la app donde se
 * puede crear o abrir el dashboard inicial (resultado de importación, detalle
 * del dataset, y los listados vacíos de métricas/paneles). Centraliza la
 * comprobación de idempotencia (¿ya existe un panel "dashboard_inicial"?)
 * para que ningún punto de entrada pueda crear uno duplicado.
 *
 * `comprobarRegistros`: cuando es true, además comprueba si el dataset tiene
 * algún registro importado (usando el mismo conteo que la métrica "Total de
 * registros") y expone el estado 'sin-datos' si no lo hay. Se puede
 * desactivar en contextos donde ya se sabe con certeza que la importación
 * acaba de tener éxito (ver DashboardInicialCta), para no arriesgar un falso
 * "sin datos" por un fallo transitorio de esa comprobación extra.
 */
export function useDashboardInicial(
  datasetId: number,
  comprobarRegistros: boolean,
): UseDashboardInicialResult {
  const [estado, setEstado] = useState<EstadoDashboardInicial>('comprobando')
  const [panelExistenteId, setPanelExistenteId] = useState<number | null>(null)
  const [pasos, setPasos] = useState<PasoProgresoDashboardInicial[]>(PASOS_DASHBOARD_INICIAL)
  const [resultado, setResultado] = useState<ResultadoDashboardInicial | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelado = false
    setEstado('comprobando')

    Promise.all([
      buscarDashboardInicialExistente(datasetId).catch(() => null),
      comprobarRegistros ? datasetTieneRegistros(datasetId) : Promise.resolve(true),
    ]).then(([panel, tieneRegistros]) => {
      if (cancelado) return
      setPanelExistenteId(panel?.id ?? null)
      if (panel === null && !tieneRegistros) {
        setEstado('sin-datos')
        return
      }
      setEstado('idle')
    })

    return () => {
      cancelado = true
    }
  }, [datasetId, comprobarRegistros])

  const crear = useCallback(async () => {
    setEstado('creando')
    setError(null)
    setPasos(PASOS_DASHBOARD_INICIAL.map((p) => ({ ...p })))

    const res = await crearDashboardInicial(datasetId, (clave, estadoPaso) => {
      setPasos((actual) => actual.map((p) => (p.clave === clave ? { ...p, estado: estadoPaso } : p)))
    })
    setResultado(res)

    if (res.errorPanel) {
      setError('No se pudo crear el dashboard inicial automáticamente. Puedes continuar en modo avanzado.')
      setEstado('error')
      return res
    }
    setPanelExistenteId(res.panelId)
    setEstado('creado')
    return res
  }, [datasetId])

  return { estado, panelExistenteId, pasos, resultado, error, crear }
}
