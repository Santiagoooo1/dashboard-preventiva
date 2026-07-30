import { useEffect, useMemo, useState } from 'react'
import {
  actualizarExclusionFilaTrabajo,
  corregirCeldaImportacionTrabajo,
  deshacerCorreccionCeldaImportacionTrabajo,
  deshacerTodasLasCorrecciones,
  deshacerTodasLasCorreccionesDeFila,
  deshacerTodasLasExclusiones,
  descartarImportacionTrabajo,
  excluirSimilaresImportacionTrabajo,
  importarDesdeTrabajo,
  listarErroresImportacionTrabajo,
  listarFilasImportacionTrabajo,
  normalizarColumnaImportacionTrabajo,
  obtenerImportacionTrabajo,
  rellenarColumnaImportacionTrabajo,
  restaurarOriginalImportacionTrabajo,
  revalidarImportacionTrabajo,
} from '../../api/importacionesTrabajoApi'
import type { EstrategiaNormalizacion } from '../../api/importacionesTrabajoApi'
import { listarMapeosPlantilla } from '../../api/plantillasImportacionApi'
import type {
  ErrorImportacionTrabajoDto,
  FilaImportacionTrabajoResponseDto,
  ImportacionTrabajoResponseDto,
  ImportarDesdeTrabajoResponseDto,
  RevalidarImportacionTrabajoResponseDto,
} from '../../api/types'
import {
  agruparErroresPorColumna,
  numerosFilaConError,
  separarErroresGlobales,
} from '../../utils/importacionGuiada/sugerenciasErrores'
import { esAdvertenciaClinicaEsperable } from '../../utils/importacionGuiada/advertenciasClinicas'
import { Card } from '../Card'
import { ErrorBanner } from '../ErrorBanner'
import { StateContainer } from '../StateContainer'
import { FilaTrabajoErrorRow } from './FilaTrabajoErrorRow'
import { GrupoProblemaRow } from './GrupoProblemaRow'
import { ProblemaGlobalImportacion } from './ProblemaGlobalImportacion'
import { ResumenImportacionTrabajo } from './ResumenImportacionTrabajo'
import { TrazabilidadImportacionPanel } from '../trazabilidad/TrazabilidadImportacionPanel'
import styles from './CorreccionFilasTrabajo.module.css'

// Tamaño de página generoso: minimiza que "ver filas afectadas" de un grupo
// quede repartido entre varias páginas (el backend no permite filtrar filas
// por columna/tipoError, solo paginar y pedir "solo con errores").
const TAMANIO_PAGINA = 100

interface CorreccionFilasTrabajoPanelProps {
  importacionTrabajoId: number
  onImportado: (resultado: ImportarDesdeTrabajoResponseDto) => void
  /** "Volver y subir otro archivo": descarta la copia de trabajo y vuelve al inicio del asistente. */
  onVolver: () => void
  /**
   * "Volver a columnas": descarta la copia de trabajo y vuelve a revisar los
   * mapeos. Ausente cuando no hay columnas reconstruidas que mostrar (p. ej.
   * al reanudar un borrador sin el archivo original): en ese caso se oculta
   * el botón y, si se indica `avisoVolverAColumnasNoDisponible`, se muestra
   * ese mensaje en su lugar (ver Fase 6.8E.2.1, punto 3).
   */
  onVolverAColumnas?: () => void
  /** Mensaje mostrado en vez del botón "Volver a columnas" cuando no está disponible. */
  avisoVolverAColumnasNoDisponible?: string
  /** "Cancelar creación": descarta la copia de trabajo y el dataset BORRADOR asociado. */
  onCancelarCreacion: () => void
}

const CONFIRMACION_PERDER_CORRECCIONES =
  'Se perderán las correcciones hechas en esta copia interna. El archivo original no se modificará.'

function claveGrupo(severidad: string, nombreColumna: string): string {
  return `${severidad}|${nombreColumna}`
}

// Estrategia de normalización segura según el tipo de dato de la columna; si
// no se conoce el tipo, no se ofrece normalización (mejor no adivinar).
function estrategiaParaTipoDato(tipoDato: string | undefined): EstrategiaNormalizacion | null {
  switch (tipoDato) {
    case 'FECHA':
      return 'FECHA'
    case 'BOOLEANO':
      return 'BOOLEANO'
    case 'ENTERO':
    case 'DECIMAL':
      return 'NUMERO'
    case 'TEXTO':
      return 'TEXTO_TRIM'
    default:
      return null
  }
}

export function CorreccionFilasTrabajoPanel({
  importacionTrabajoId,
  onImportado,
  onVolver,
  onVolverAColumnas,
  avisoVolverAColumnasNoDisponible,
  onCancelarCreacion,
}: CorreccionFilasTrabajoPanelProps) {
  const [trabajo, setTrabajo] = useState<ImportacionTrabajoResponseDto | null>(null)
  const [tiposPorColumna, setTiposPorColumna] = useState<Map<string, string>>(new Map())
  const [todosLosErrores, setTodosLosErrores] = useState<ErrorImportacionTrabajoDto[]>([])
  const [filas, setFilas] = useState<FilaImportacionTrabajoResponseDto[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  const [cargando, setCargando] = useState(true)
  const [accionEnCurso, setAccionEnCurso] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Aviso breve tras una acción en bloque (p. ej. "Se han excluido 69 filas
  // por HC vacío"). Se limpia en cuanto empieza otra acción.
  const [mensajeAccion, setMensajeAccion] = useState<string | null>(null)

  // Grupos con "ver filas afectadas" desplegado, y grupos de advertencia
  // ocultados solo en esta pantalla (no modifican datos, ver punto 3 del spec).
  const [gruposExpandidos, setGruposExpandidos] = useState<Set<string>>(new Set())
  const [advertenciasOcultas, setAdvertenciasOcultas] = useState<Set<string>>(new Set())
  // Las advertencias (importantes y campos opcionales) empiezan colapsadas
  // siempre, para no llenar la pantalla de editores cuando hay cientos.
  const [advertenciasVisibles, setAdvertenciasVisibles] = useState(false)
  const [mostrarTrazabilidad, setMostrarTrazabilidad] = useState(false)

  useEffect(() => {
    let cancelado = false

    async function cargarInicial() {
      setCargando(true)
      setError(null)
      try {
        const trabajoInicial = await obtenerImportacionTrabajo(importacionTrabajoId)
        if (cancelado) return
        setTrabajo(trabajoInicial)

        const [todosErrores, pagina, mapeos] = await Promise.all([
          listarErroresImportacionTrabajo(importacionTrabajoId),
          listarFilasImportacionTrabajo(importacionTrabajoId, { page: 0, size: TAMANIO_PAGINA, soloConErrores: true }),
          listarMapeosPlantilla(trabajoInicial.plantillaId),
        ])
        if (cancelado) return
        setTodosLosErrores(todosErrores)
        setFilas(pagina.content)
        setPage(pagina.page)
        setTotalPages(pagina.totalPages)
        setTiposPorColumna(new Map(mapeos.map((m) => [m.nombreColumnaOrigen, m.tipoDato])))
      } catch (err) {
        if (!cancelado) {
          setError(err instanceof Error ? err.message : 'No se pudo cargar la copia interna.')
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
    mensajeExito?: string,
  ) => {
    setAccionEnCurso(mensaje)
    setError(null)
    setMensajeAccion(null)
    try {
      const resultado = await accion()
      setTrabajo(resultado.importacionTrabajo)
      const [todosErrores] = await Promise.all([
        listarErroresImportacionTrabajo(importacionTrabajoId),
        recargarFilas(page),
      ])
      setTodosLosErrores(todosErrores)
      if (mensajeExito) setMensajeAccion(mensajeExito)
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

  const handleExcluirSimilares = (tipoError: string, nombreColumna: string, totalFilas?: number) =>
    ejecutarAccion(
      'Excluyendo filas similares…',
      () => excluirSimilaresImportacionTrabajo(importacionTrabajoId, tipoError, nombreColumna),
      totalFilas !== undefined
        ? `Se han excluido ${totalFilas} fila(s) por ${nombreColumna} vacío o inválido.`
        : `Se han excluido filas por ${nombreColumna} vacío o inválido.`,
    )

  const handleDeshacerCorreccionesFila = (numeroFila: number) =>
    ejecutarAccion('Guardando corrección…', () =>
      deshacerTodasLasCorreccionesDeFila(importacionTrabajoId, numeroFila),
    )

  const handleDeshacerTodasCorrecciones = () => {
    if (
      !window.confirm(
        'Se eliminarán las correcciones hechas en esta copia interna. El archivo original no se modificará.',
      )
    ) {
      return
    }
    return ejecutarAccion('Deshaciendo correcciones…', () => deshacerTodasLasCorrecciones(importacionTrabajoId))
  }

  const handleDeshacerTodasExclusiones = () => {
    if (
      !window.confirm(
        'Se incluirán de nuevo todas las filas excluidas de esta copia interna. El archivo original no se modificará.',
      )
    ) {
      return
    }
    return ejecutarAccion('Deshaciendo exclusiones…', () => deshacerTodasLasExclusiones(importacionTrabajoId))
  }

  const handleRestaurarOriginal = () => {
    if (
      !window.confirm(
        'Vas a deshacer todas las correcciones y exclusiones de esta copia interna, volviendo al archivo tal como se leyó. El archivo original no se modifica.',
      )
    ) {
      return
    }
    return ejecutarAccion('Restaurando copia al estado original…', () =>
      restaurarOriginalImportacionTrabajo(importacionTrabajoId),
    )
  }

  const handleRellenarColumna = (nombreColumna: string, tipoError: string, valor: string) =>
    ejecutarAccion('Guardando corrección…', () =>
      rellenarColumnaImportacionTrabajo(importacionTrabajoId, {
        nombreColumna,
        tipoError,
        valor,
        soloFilasConEsteProblema: true,
      }),
    )

  const handleNormalizarColumna = (nombreColumna: string, estrategia: EstrategiaNormalizacion) =>
    ejecutarAccion('Guardando corrección…', () =>
      normalizarColumnaImportacionTrabajo(importacionTrabajoId, nombreColumna, estrategia),
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

  // Las tres formas de salir de esta pantalla descartan la copia de trabajo
  // actual (no tiene sentido conservar correcciones de un archivo que se va a
  // dejar de corregir); solo cambia a dónde se navega después.
  const descartarCopiaYSalir = async (salir: () => void) => {
    if (!window.confirm(CONFIRMACION_PERDER_CORRECCIONES)) return
    try {
      await descartarImportacionTrabajo(importacionTrabajoId)
    } catch {
      // Si no se pudo descartar, igual dejamos que el usuario continúe: no es bloqueante.
    }
    salir()
  }

  const handleVolver = () => descartarCopiaYSalir(onVolver)
  const handleVolverAColumnas = onVolverAColumnas
    ? () => descartarCopiaYSalir(onVolverAColumnas)
    : undefined
  const handleCancelarCreacion = () => descartarCopiaYSalir(onCancelarCreacion)

  const toggleGrupoExpandido = (clave: string) => {
    setGruposExpandidos((actual) => {
      const siguiente = new Set(actual)
      if (siguiente.has(clave)) siguiente.delete(clave)
      else siguiente.add(clave)
      return siguiente
    })
  }

  const ocultarAdvertencia = (nombreColumna: string) => {
    setAdvertenciasOcultas((actual) => new Set(actual).add(claveGrupo('ADVERTENCIA', nombreColumna)))
  }

  const bloqueado = accionEnCurso !== null

  // Errores sin columna asociada (p. ej. "el archivo no tiene ninguna fila
  // clínica reconocible"): no son un problema de una columna ni de una fila
  // real, así que se separan antes de agrupar por columna (si no, el
  // agrupador los mete bajo una columna ficticia "Columna desconocida" con
  // una "fila afectada" que en realidad no existe).
  const { globales: erroresGlobales, porColumna: erroresPorColumnaTodos } = useMemo(
    () => separarErroresGlobales(todosLosErrores),
    [todosLosErrores],
  )
  const erroresGlobalesBloqueantes = useMemo(
    () => erroresGlobales.filter((e) => e.severidad === 'ERROR'),
    [erroresGlobales],
  )
  const erroresGlobalesAdvertencia = useMemo(
    () => erroresGlobales.filter((e) => e.severidad !== 'ERROR'),
    [erroresGlobales],
  )

  // Filas activas (no excluidas) agrupadas por problema: la fuente de verdad
  // para los recuentos es listarErroresImportacionTrabajo (sin paginar, cubre
  // toda la copia de trabajo), no la página de filas cargada.
  const gruposBloqueantes = useMemo(
    () => agruparErroresPorColumna(erroresPorColumnaTodos.filter((e) => e.severidad === 'ERROR')),
    [erroresPorColumnaTodos],
  )

  // Las advertencias se separan en "campos opcionales esperables" (datos de
  // seguimiento que pueden faltar legítimamente: no hubo reingreso, no hubo
  // cultivo...) y "advertencias importantes" (el resto: cualquier otra que no
  // encaje en ese patrón). Así el grueso del ruido clínico esperado no se
  // mezcla con lo que sí conviene revisar.
  const advertenciasTodas = useMemo(
    () => erroresPorColumnaTodos.filter((e) => e.severidad === 'ADVERTENCIA'),
    [erroresPorColumnaTodos],
  )
  const gruposAdvertenciasEsperablesTodos = useMemo(
    () => agruparErroresPorColumna(advertenciasTodas.filter(esAdvertenciaClinicaEsperable)),
    [advertenciasTodas],
  )
  const gruposAdvertenciasImportantesTodos = useMemo(
    () => agruparErroresPorColumna(advertenciasTodas.filter((e) => !esAdvertenciaClinicaEsperable(e))),
    [advertenciasTodas],
  )
  const gruposAdvertenciasEsperables = gruposAdvertenciasEsperablesTodos.filter(
    (g) => !advertenciasOcultas.has(claveGrupo('ADVERTENCIA', g.nombreColumna)),
  )
  const gruposAdvertenciasImportantes = gruposAdvertenciasImportantesTodos.filter(
    (g) => !advertenciasOcultas.has(claveGrupo('ADVERTENCIA', g.nombreColumna)),
  )
  const totalGruposAdvertencias =
    gruposAdvertenciasEsperablesTodos.length +
    gruposAdvertenciasImportantesTodos.length +
    erroresGlobalesAdvertencia.length

  const filasExcluidas = filas.filter((f) => f.excluida)

  // Filas cargadas en la página actual que tienen el problema, más los
  // números de fila que lo tienen pero no están en esta página (para no
  // dejar el desplegable vacío sin explicación, ver Fase 6.8E.2).
  const filasYFaltantesDelGrupo = (severidad: string, nombreColumna: string) => {
    const cargadas = filas.filter(
      (f) => !f.excluida && f.errores.some((e) => e.severidad === severidad && e.nombreColumna === nombreColumna),
    )
    const numerosCargados = new Set(cargadas.map((f) => f.numeroFilaOriginal))
    const faltantes = numerosFilaConError(todosLosErrores, severidad, nombreColumna).filter(
      (n) => !numerosCargados.has(n),
    )
    return { cargadas, faltantes }
  }

  if (trabajo?.estado === 'DESCARTADA') {
    return (
      <Card title="Copia interna descartada">
        <p>Esta copia interna fue descartada.</p>
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
      <Card title="Copia interna ya importada">
        <p>Esta copia interna ya fue importada.</p>
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
        {trabajo && <ResumenImportacionTrabajo trabajo={trabajo} bloqueado={bloqueado} onImportar={handleImportar} />}
        {accionEnCurso && (
          <p className={styles.estadoAccion} role="status">
            {accionEnCurso}
          </p>
        )}
        {!accionEnCurso && mensajeAccion && (
          <p className={styles.avisoAccionExitosa} role="status">
            {mensajeAccion}
          </p>
        )}

        <div className={styles.accionesGlobales}>
          <button
            type="button"
            className="btn btnSecondary"
            disabled={bloqueado}
            onClick={handleDeshacerTodasCorrecciones}
          >
            Deshacer todas las correcciones
          </button>
          <button
            type="button"
            className="btn btnSecondary"
            disabled={bloqueado}
            onClick={handleDeshacerTodasExclusiones}
          >
            Deshacer todas las exclusiones
          </button>
          <button type="button" className="btn btnDanger" disabled={bloqueado} onClick={handleRestaurarOriginal}>
            Restaurar copia al estado original
          </button>
          <button
            type="button"
            className="btn btnSecondary"
            onClick={() => setMostrarTrazabilidad((v) => !v)}
          >
            {mostrarTrazabilidad ? 'Ocultar historial de cambios' : 'Ver historial de cambios'}
          </button>
        </div>

        {mostrarTrazabilidad && (
          <div className={styles.seccion}>
            <TrazabilidadImportacionPanel importacionTrabajoId={importacionTrabajoId} titulo="Historial de cambios" />
          </div>
        )}

        {erroresGlobalesBloqueantes.length > 0 && (
          <section className={styles.seccion}>
            <h4>Problemas del archivo</h4>
            {erroresGlobalesBloqueantes.map((error, i) => (
              <ProblemaGlobalImportacion
                key={i}
                error={error}
                bloqueante
                disabled={bloqueado}
                onVolverAColumnas={handleVolverAColumnas}
                onVolver={handleVolver}
              />
            ))}
          </section>
        )}

        {gruposBloqueantes.length > 0 && (
          <section className={styles.seccion}>
            <h4>Problemas que impiden importar</h4>
            {gruposBloqueantes.map((grupo) => {
              const clave = claveGrupo('ERROR', grupo.nombreColumna)
              const expandido = gruposExpandidos.has(clave)
              const estrategia = estrategiaParaTipoDato(tiposPorColumna.get(grupo.nombreColumna))
              const { cargadas, faltantes } = filasYFaltantesDelGrupo('ERROR', grupo.nombreColumna)
              return (
                <div key={clave}>
                  <GrupoProblemaRow
                    grupo={grupo}
                    bloqueante
                    expandido={expandido}
                    disabled={bloqueado}
                    onToggleExpandir={() => toggleGrupoExpandido(clave)}
                    onExcluirSimilares={handleExcluirSimilares}
                    onVolverAColumnas={handleVolverAColumnas}
                    onRellenarColumna={
                      grupo.tipoErrorPredominante === 'VALOR_OBLIGATORIO_VACIO'
                        ? (valor) => handleRellenarColumna(grupo.nombreColumna, grupo.tipoErrorPredominante, valor)
                        : undefined
                    }
                    onNormalizarColumna={
                      estrategia ? () => handleNormalizarColumna(grupo.nombreColumna, estrategia) : undefined
                    }
                    estrategiaNormalizacion={estrategia}
                  />
                  {expandido && faltantes.length > 0 && (
                    <p className={styles.notaAdvertencia}>
                      {faltantes.map((n) => `Fila Excel ${n}`).join(', ')} afectada
                      {faltantes.length === 1 ? '' : 's'}, pero no está{faltantes.length === 1 ? '' : 'n'} cargada
                      {faltantes.length === 1 ? '' : 's'} en esta página. Usa la paginación para verla
                      {faltantes.length === 1 ? '' : 'las'}.
                    </p>
                  )}
                  {expandido &&
                    cargadas.map((fila) => (
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
                        onDeshacerCorreccionesFila={handleDeshacerCorreccionesFila}
                      />
                    ))}
                </div>
              )
            })}
          </section>
        )}

        {totalGruposAdvertencias > 0 && (
          <div className={styles.cajaAdvertenciasCompacta}>
            <p>
              {trabajo?.totalAdvertencias ?? 0} advertencia{(trabajo?.totalAdvertencias ?? 0) === 1 ? '' : 's'}{' '}
              opcional{(trabajo?.totalAdvertencias ?? 0) === 1 ? '' : 'es'}. No bloquean la importación.
            </p>
            <div className={styles.acciones}>
              <button
                type="button"
                className="btn btnSecondary"
                onClick={() => setAdvertenciasVisibles((v) => !v)}
              >
                {advertenciasVisibles ? 'Ocultar advertencias opcionales' : 'Revisar advertencias'}
              </button>
            </div>
          </div>
        )}

        {advertenciasVisibles && (
          <>
            {erroresGlobalesAdvertencia.length > 0 && (
              <section className={styles.seccion}>
                <h4>Problemas del archivo</h4>
                {erroresGlobalesAdvertencia.map((error, i) => (
                  <ProblemaGlobalImportacion
                    key={i}
                    error={error}
                    bloqueante={false}
                    disabled={bloqueado}
                    onVolverAColumnas={handleVolverAColumnas}
                    onVolver={handleVolver}
                  />
                ))}
              </section>
            )}

            {gruposAdvertenciasImportantesTodos.length > 0 && (
              <section className={styles.seccion}>
                <h4>Advertencias importantes</h4>
                {gruposAdvertenciasImportantes.length === 0 ? (
                  <p className={styles.notaAdvertencia}>
                    Ocultaste todos los tipos de advertencia en esta pantalla.{' '}
                    <button
                      type="button"
                      className="btn btnSecondary"
                      onClick={() => setAdvertenciasOcultas(new Set())}
                    >
                      Mostrar de nuevo
                    </button>
                  </p>
                ) : (
                  gruposAdvertenciasImportantes.map((grupo) => {
                    const clave = claveGrupo('ADVERTENCIA', grupo.nombreColumna)
                    const expandido = gruposExpandidos.has(clave)
                    const estrategia = estrategiaParaTipoDato(tiposPorColumna.get(grupo.nombreColumna))
                    const { cargadas, faltantes } = filasYFaltantesDelGrupo('ADVERTENCIA', grupo.nombreColumna)
                    return (
                      <div key={clave}>
                        <GrupoProblemaRow
                          grupo={grupo}
                          bloqueante={false}
                          expandido={expandido}
                          disabled={bloqueado}
                          onToggleExpandir={() => toggleGrupoExpandido(clave)}
                          onExcluirSimilares={handleExcluirSimilares}
                          onOcultar={() => ocultarAdvertencia(grupo.nombreColumna)}
                          onVolverAColumnas={handleVolverAColumnas}
                          onRellenarColumna={
                            grupo.tipoErrorPredominante === 'VALOR_OBLIGATORIO_VACIO'
                              ? (valor) =>
                                  handleRellenarColumna(grupo.nombreColumna, grupo.tipoErrorPredominante, valor)
                              : undefined
                          }
                          onNormalizarColumna={
                            estrategia ? () => handleNormalizarColumna(grupo.nombreColumna, estrategia) : undefined
                          }
                          estrategiaNormalizacion={estrategia}
                        />
                        {expandido && faltantes.length > 0 && (
                          <p className={styles.notaAdvertencia}>
                            {faltantes.map((n) => `Fila Excel ${n}`).join(', ')} afectada
                            {faltantes.length === 1 ? '' : 's'}, pero no está{faltantes.length === 1 ? '' : 'n'} cargada
                            {faltantes.length === 1 ? '' : 's'} en esta página. Usa la paginación para verla
                            {faltantes.length === 1 ? '' : 'las'}.
                          </p>
                        )}
                        {expandido &&
                          cargadas.map((fila) => (
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
                              onDeshacerCorreccionesFila={handleDeshacerCorreccionesFila}
                            />
                          ))}
                      </div>
                    )
                  })
                )}
              </section>
            )}

            {gruposAdvertenciasEsperablesTodos.length > 0 && (
              <section className={styles.seccion}>
                <h4>Campos opcionales no informados</h4>
                <p className={styles.notaAdvertencia}>
                  Estos campos pueden estar vacíos si no aplican al paciente.
                </p>
                {gruposAdvertenciasEsperables.map((grupo) => {
                  const clave = claveGrupo('ADVERTENCIA', grupo.nombreColumna)
                  const expandido = gruposExpandidos.has(clave)
                  const estrategia = estrategiaParaTipoDato(tiposPorColumna.get(grupo.nombreColumna))
                  const { cargadas, faltantes } = filasYFaltantesDelGrupo('ADVERTENCIA', grupo.nombreColumna)
                  return (
                    <div key={clave}>
                      <GrupoProblemaRow
                        grupo={grupo}
                        bloqueante={false}
                        expandido={expandido}
                        disabled={bloqueado}
                        onToggleExpandir={() => toggleGrupoExpandido(clave)}
                        onExcluirSimilares={handleExcluirSimilares}
                        onOcultar={() => ocultarAdvertencia(grupo.nombreColumna)}
                        onVolverAColumnas={handleVolverAColumnas}
                        onRellenarColumna={
                          grupo.tipoErrorPredominante === 'VALOR_OBLIGATORIO_VACIO'
                            ? (valor) =>
                                handleRellenarColumna(grupo.nombreColumna, grupo.tipoErrorPredominante, valor)
                            : undefined
                        }
                        onNormalizarColumna={
                          estrategia ? () => handleNormalizarColumna(grupo.nombreColumna, estrategia) : undefined
                        }
                        estrategiaNormalizacion={estrategia}
                        advertenciaClinicaEsperable
                      />
                      {expandido && faltantes.length > 0 && (
                        <p className={styles.notaAdvertencia}>
                          {faltantes.map((n) => `Fila Excel ${n}`).join(', ')} afectada
                          {faltantes.length === 1 ? '' : 's'}, pero no está{faltantes.length === 1 ? '' : 'n'} cargada
                          {faltantes.length === 1 ? '' : 's'} en esta página. Usa la paginación para verla
                          {faltantes.length === 1 ? '' : 'las'}.
                        </p>
                      )}
                      {expandido &&
                        cargadas.map((fila) => (
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
                            onDeshacerCorreccionesFila={handleDeshacerCorreccionesFila}
                          />
                        ))}
                    </div>
                  )
                })}
              </section>
            )}
          </>
        )}

        {(trabajo?.totalFilasExcluidas ?? 0) > 0 && (
          <details className={styles.seccionColapsable}>
            <summary>Ver filas excluidas ({trabajo?.totalFilasExcluidas ?? 0})</summary>
            {filasExcluidas.length === 0 ? (
              <p className={styles.notaAdvertencia}>
                Hay filas excluidas en otra página. Usa la paginación para verlas.
              </p>
            ) : (
              filasExcluidas.map((fila) => (
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
                  onDeshacerCorreccionesFila={handleDeshacerCorreccionesFila}
                />
              ))
            )}
          </details>
        )}

        {erroresGlobalesBloqueantes.length === 0 &&
          gruposBloqueantes.length === 0 &&
          totalGruposAdvertencias === 0 &&
          filas.length === 0 && <p className="stateEmpty">No hay errores que corregir.</p>}

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
        </div>
        <div className={styles.acciones}>
          {handleVolverAColumnas ? (
            <button type="button" className="btn btnSecondary" disabled={bloqueado} onClick={handleVolverAColumnas}>
              Volver a columnas
            </button>
          ) : (
            avisoVolverAColumnasNoDisponible && (
              <p className={styles.notaAdvertencia}>{avisoVolverAColumnasNoDisponible}</p>
            )
          )}
          <button type="button" className="btn btnSecondary" disabled={bloqueado} onClick={handleVolver}>
            Volver y subir otro archivo
          </button>
          <button type="button" className="btn btnDanger" disabled={bloqueado} onClick={handleCancelarCreacion}>
            Cancelar creación
          </button>
        </div>
      </StateContainer>
    </Card>
  )
}
