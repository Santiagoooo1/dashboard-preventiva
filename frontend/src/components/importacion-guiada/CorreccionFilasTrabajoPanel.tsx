import { useEffect, useState } from 'react'
import {
  actualizarExclusionFilaTrabajo,
  corregirCeldaImportacionTrabajo,
  deshacerCorreccionCeldaImportacionTrabajo,
  descartarImportacionTrabajo,
  excluirSimilaresImportacionTrabajo,
  importarDesdeTrabajo,
  listarFilasImportacionTrabajo,
  obtenerImportacionTrabajo,
  revalidarImportacionTrabajo,
} from '../../api/importacionesTrabajoApi'
import { listarMapeosPlantilla } from '../../api/plantillasImportacionApi'
import type {
  FilaImportacionTrabajoResponseDto,
  ImportacionTrabajoResponseDto,
  ImportarDesdeTrabajoResponseDto,
  RevalidarImportacionTrabajoResponseDto,
} from '../../api/types'
import { Card } from '../Card'
import { ErrorBanner } from '../ErrorBanner'
import { StateContainer } from '../StateContainer'
import { FilaTrabajoErrorRow } from './FilaTrabajoErrorRow'
import { ResumenImportacionTrabajo } from './ResumenImportacionTrabajo'
import styles from './CorreccionFilasTrabajo.module.css'

const TAMANIO_PAGINA = 20

interface CorreccionFilasTrabajoPanelProps {
  importacionTrabajoId: number
  onImportado: (resultado: ImportarDesdeTrabajoResponseDto) => void
  /** "Volver y subir otro archivo": descarta la copia de trabajo y vuelve al inicio del asistente. */
  onVolver: () => void
}

export function CorreccionFilasTrabajoPanel({
  importacionTrabajoId,
  onImportado,
  onVolver,
}: CorreccionFilasTrabajoPanelProps) {
  const [trabajo, setTrabajo] = useState<ImportacionTrabajoResponseDto | null>(null)
  const [tiposPorColumna, setTiposPorColumna] = useState<Map<string, string>>(new Map())
  const [filas, setFilas] = useState<FilaImportacionTrabajoResponseDto[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  const [cargando, setCargando] = useState(true)
  const [accionEnCurso, setAccionEnCurso] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelado = false

    async function cargarInicial() {
      setCargando(true)
      setError(null)
      try {
        const trabajoInicial = await obtenerImportacionTrabajo(importacionTrabajoId)
        if (cancelado) return
        setTrabajo(trabajoInicial)

        const [pagina, mapeos] = await Promise.all([
          listarFilasImportacionTrabajo(importacionTrabajoId, { page: 0, size: TAMANIO_PAGINA, soloConErrores: true }),
          listarMapeosPlantilla(trabajoInicial.plantillaId),
        ])
        if (cancelado) return
        setFilas(pagina.content)
        setPage(pagina.page)
        setTotalPages(pagina.totalPages)
        setTiposPorColumna(new Map(mapeos.map((m) => [m.nombreColumnaOrigen, m.tipoDato])))
      } catch (err) {
        if (!cancelado) {
          setError(err instanceof Error ? err.message : 'No se pudo cargar la copia de trabajo.')
        }
      } finally {
        if (!cancelado) setCargando(false)
      }
    }

    cargarInicial()
    return () => {
      cancelado = true
    }
  }, [importacionTrabajoId])

  const recargarFilas = async (paginaObjetivo: number) => {
    const pagina = await listarFilasImportacionTrabajo(importacionTrabajoId, {
      page: paginaObjetivo,
      size: TAMANIO_PAGINA,
      soloConErrores: true,
    })
    // Si la página quedó vacía porque las filas que quedaban se corrigieron,
    // retrocede a la última página con contenido en vez de dejar la vista en blanco.
    if (pagina.content.length === 0 && paginaObjetivo > 0 && pagina.totalPages > 0) {
      return recargarFilas(pagina.totalPages - 1)
    }
    setFilas(pagina.content)
    setPage(pagina.page)
    setTotalPages(pagina.totalPages)
  }

  const ejecutarAccion = async (
    mensaje: string,
    accion: () => Promise<RevalidarImportacionTrabajoResponseDto>,
  ) => {
    setAccionEnCurso(mensaje)
    setError(null)
    try {
      const resultado = await accion()
      setTrabajo(resultado.importacionTrabajo)
      await recargarFilas(page)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo completar la acción.')
    } finally {
      setAccionEnCurso(null)
    }
  }

  const cambiarPagina = async (nuevaPagina: number) => {
    setAccionEnCurso('Cargando filas con errores…')
    setError(null)
    try {
      await recargarFilas(nuevaPagina)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudieron cargar las filas.')
    } finally {
      setAccionEnCurso(null)
    }
  }

  const handleGuardarCelda = (numeroFila: number, columna: string, valor: string) =>
    ejecutarAccion('Guardando corrección…', () =>
      corregirCeldaImportacionTrabajo(importacionTrabajoId, numeroFila, columna, valor),
    )

  const handleDeshacerCelda = (numeroFila: number, columna: string) =>
    ejecutarAccion('Guardando corrección…', () =>
      deshacerCorreccionCeldaImportacionTrabajo(importacionTrabajoId, numeroFila, columna),
    )

  const handleExcluirFila = (numeroFila: number) =>
    ejecutarAccion('Excluyendo fila…', () => actualizarExclusionFilaTrabajo(importacionTrabajoId, numeroFila, true))

  const handleIncluirFila = (numeroFila: number) =>
    ejecutarAccion('Excluyendo fila…', () => actualizarExclusionFilaTrabajo(importacionTrabajoId, numeroFila, false))

  const handleExcluirSimilares = (tipoError: string, nombreColumna: string) =>
    ejecutarAccion('Excluyendo filas similares…', () =>
      excluirSimilaresImportacionTrabajo(importacionTrabajoId, tipoError, nombreColumna),
    )

  const handleRevalidar = () =>
    ejecutarAccion('Revalidando…', () => revalidarImportacionTrabajo(importacionTrabajoId))

  const handleImportar = async () => {
    setAccionEnCurso('Importando datos corregidos…')
    setError(null)
    try {
      const resultado = await importarDesdeTrabajo(importacionTrabajoId)
      onImportado(resultado)
    } catch {
      setError('No se pudo importar la copia corregida. Revisa si quedan errores pendientes.')
    } finally {
      setAccionEnCurso(null)
    }
  }

  const handleVolver = async () => {
    try {
      await descartarImportacionTrabajo(importacionTrabajoId)
    } catch {
      // Si no se pudo descartar, igual dejamos que el usuario continúe: no es bloqueante.
    }
    onVolver()
  }

  const bloqueado = accionEnCurso !== null

  if (trabajo?.estado === 'DESCARTADA') {
    return (
      <Card title="Copia de trabajo descartada">
        <p>Esta copia de trabajo fue descartada.</p>
        <div className={styles.acciones}>
          <button type="button" className="btn btnSecondary" onClick={onVolver}>
            Volver
          </button>
        </div>
      </Card>
    )
  }

  if (trabajo?.estado === 'IMPORTADA') {
    return (
      <Card title="Copia de trabajo ya importada">
        <p>Esta copia de trabajo ya fue importada.</p>
        <div className={styles.acciones}>
          <button type="button" className="btn btnSecondary" onClick={onVolver}>
            Volver
          </button>
        </div>
      </Card>
    )
  }

  return (
    <Card title="Corregir errores en la app">
      <ErrorBanner mensaje={error} />
      <StateContainer loading={cargando} error={null}>
        {trabajo && <ResumenImportacionTrabajo trabajo={trabajo} />}
        {accionEnCurso && (
          <p className={styles.estadoAccion} role="status">
            {accionEnCurso}
          </p>
        )}

        {filas.length === 0 ? (
          <p className="stateEmpty">No quedan filas con errores pendientes.</p>
        ) : (
          <>
            {filas.map((fila) => (
              <FilaTrabajoErrorRow
                key={fila.id}
                fila={fila}
                tiposPorColumna={tiposPorColumna}
                disabled={bloqueado}
                onGuardarCelda={handleGuardarCelda}
                onDeshacerCelda={handleDeshacerCelda}
                onExcluirFila={handleExcluirFila}
                onIncluirFila={handleIncluirFila}
                onExcluirSimilares={handleExcluirSimilares}
              />
            ))}

            {totalPages > 1 && (
              <div className={styles.paginacion}>
                <button
                  type="button"
                  className="btn btnSecondary"
                  disabled={page === 0 || bloqueado}
                  onClick={() => cambiarPagina(page - 1)}
                >
                  Anterior
                </button>
                <span>
                  Página {page + 1} de {totalPages}
                </span>
                <button
                  type="button"
                  className="btn btnSecondary"
                  disabled={page + 1 >= totalPages || bloqueado}
                  onClick={() => cambiarPagina(page + 1)}
                >
                  Siguiente
                </button>
              </div>
            )}
          </>
        )}

        <div className={styles.acciones}>
          <button type="button" className="btn btnSecondary" disabled={bloqueado} onClick={handleRevalidar}>
            Revalidar correcciones
          </button>
          <button
            type="button"
            className="btn btnPrimary"
            disabled={bloqueado || !trabajo?.importable || trabajo?.estado !== 'LISTA_PARA_IMPORTAR'}
            onClick={handleImportar}
          >
            Importar datos corregidos
          </button>
          <button type="button" className="btn btnSecondary" disabled={bloqueado} onClick={handleVolver}>
            Volver y subir otro archivo
          </button>
        </div>
      </StateContainer>
    </Card>
  )
}
