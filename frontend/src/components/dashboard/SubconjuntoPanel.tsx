import { useCallback, useEffect, useId, useRef, useState } from 'react'
import type {
  FiltroMetricaDto,
  SubconjuntoPaginaDto,
  SubconjuntoPerfilDto,
  SubconjuntoRequestDto,
  SubconjuntoResumenDto,
} from '../../api/types'
import { pacientesSubconjunto, perfilSubconjunto, registrosSubconjunto } from '../../api/subconjuntoApi'
import { firmaFiltros } from './seleccionGrafica'
import { SubconjuntoTabla } from './SubconjuntoTabla'
import { SubconjuntoPerfil } from './SubconjuntoPerfil'
import styles from './SubconjuntoPanel.module.css'

type Pestana = 'PACIENTES' | 'REGISTROS' | 'PERFIL'

interface SubconjuntoPanelProps {
  panelId: string
  filtros: FiltroMetricaDto[]
  resumen: SubconjuntoResumenDto | null
  /** Descripción legible de la selección y del contexto global, ya resuelta. */
  descripcionSeleccion: string
  descripcionContexto: string | null
  onCerrar: () => void
}

const TAMANOS = [20, 50]

export function SubconjuntoPanel({
  panelId,
  filtros,
  resumen,
  descripcionSeleccion,
  descripcionContexto,
  onCerrar,
}: SubconjuntoPanelProps) {
  const idBase = useId()
  const tienePacientes = resumen?.tienePacientes ?? false

  const [pestana, setPestana] = useState<Pestana>(tienePacientes ? 'PACIENTES' : 'REGISTROS')
  const [pagina, setPagina] = useState(0)
  const [tamano, setTamano] = useState(TAMANOS[0])
  const [busqueda, setBusqueda] = useState('')
  const [busquedaAplicada, setBusquedaAplicada] = useState('')
  const [orden, setOrden] = useState<{ campo: string; direccion: 'ASC' | 'DESC' } | null>(null)
  const [ocultas, setOcultas] = useState<Set<string>>(new Set())
  const [columnasAbiertas, setColumnasAbiertas] = useState(false)

  const [datosPagina, setDatosPagina] = useState<SubconjuntoPaginaDto | null>(null)
  const [perfil, setPerfil] = useState<SubconjuntoPerfilDto | null>(null)
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reintento, setReintento] = useState(0)

  const cabeceraRef = useRef<HTMLHeadingElement>(null)
  const peticionRef = useRef(0)

  // Firma del subconjunto: si cambia, todo lo cargado deja de ser válido.
  const firma = firmaFiltros(filtros)

  useEffect(() => {
    cabeceraRef.current?.focus()
  }, [])

  // Si cambia el subconjunto, se descarta lo anterior en vez de mostrarlo un
  // instante junto a los conteos nuevos.
  useEffect(() => {
    setDatosPagina(null)
    setPerfil(null)
    setPagina(0)
    setBusqueda('')
    setBusquedaAplicada('')
    setOrden(null)
  }, [firma])

  // Debounce de la búsqueda: no se lanza una petición por tecla.
  useEffect(() => {
    const id = setTimeout(() => {
      setBusquedaAplicada(busqueda.trim())
      setPagina(0)
    }, 350)
    return () => clearTimeout(id)
  }, [busqueda])

  const construirRequest = useCallback(
    (): SubconjuntoRequestDto => ({
      filtros,
      campoIndividuo: resumen?.campoIndividuo ?? null,
      pagina,
      tamano,
      ordenCampo: orden?.campo ?? null,
      ordenDireccion: orden?.direccion ?? null,
      busquedaIndividuo: busquedaAplicada || null,
    }),
    [filtros, resumen, pagina, tamano, orden, busquedaAplicada],
  )

  // Carga diferida: solo se pide la pestaña que está visible.
  useEffect(() => {
    const controller = new AbortController()
    const id = ++peticionRef.current
    setCargando(true)
    setError(null)

    const request = construirRequest()
    const promesa =
      pestana === 'PERFIL'
        ? perfilSubconjunto(panelId, request, controller.signal).then((p) => {
            if (id === peticionRef.current) setPerfil(p)
          })
        : (pestana === 'PACIENTES' ? pacientesSubconjunto : registrosSubconjunto)(
            panelId,
            request,
            controller.signal,
          ).then((p) => {
            if (id === peticionRef.current) setDatosPagina(p)
          })

    promesa
      .catch(() => {
        if (id === peticionRef.current && !controller.signal.aborted) {
          // Sin endpoint, DTO ni traza: el detalle falla, el dashboard no.
          setError('No se pudo cargar el detalle del subconjunto.')
        }
      })
      .finally(() => {
        if (id === peticionRef.current) setCargando(false)
      })

    return () => controller.abort()
  }, [panelId, pestana, construirRequest, reintento])

  const pestanas: { valor: Pestana; etiqueta: string }[] = [
    ...(tienePacientes ? [{ valor: 'PACIENTES' as const, etiqueta: 'Pacientes' }] : []),
    { valor: 'REGISTROS', etiqueta: 'Registros' },
    { valor: 'PERFIL', etiqueta: 'Perfil del grupo' },
  ]

  const columnasVisibles = (datosPagina?.columnas ?? []).filter(
    (c) => c.identificador || !ocultas.has(c.codigo),
  )

  const alternarColumna = (codigo: string) =>
    setOcultas((previas) => {
      const nuevas = new Set(previas)
      if (nuevas.has(codigo)) nuevas.delete(codigo)
      else nuevas.add(codigo)
      return nuevas
    })

  return (
    <section className={styles.panel} aria-label="Detalle del subconjunto seleccionado">
      <header className={styles.cabecera}>
        <div className={styles.cabeceraTexto}>
          <h2 className={styles.titulo} tabIndex={-1} ref={cabeceraRef}>
            Subconjunto seleccionado
          </h2>
          <p className={styles.condicion}>{descripcionSeleccion}</p>
          {descripcionContexto && <p className={styles.contexto}>Contexto global: {descripcionContexto}</p>}
        </div>
        <button type="button" className="btn btnSecondary" onClick={onCerrar}>
          Cerrar
        </button>
      </header>

      {resumen && (
        <div className={styles.resumen}>
          {resumen.tienePacientes && (
            <div className={styles.tarjetaResumen}>
              <span className={styles.resumenValor}>{resumen.totalPacientesUnicos}</span>
              <span className={styles.resumenEtiqueta}>pacientes únicos</span>
            </div>
          )}
          <div className={styles.tarjetaResumen}>
            <span className={styles.resumenValor}>{resumen.totalRegistros}</span>
            <span className={styles.resumenEtiqueta}>registros clínicos</span>
          </div>
          {!resumen.tienePacientes && (
            <p className={styles.avisoSinIndividuo}>
              Este conjunto de datos no tiene un identificador individual disponible.
            </p>
          )}
        </div>
      )}

      <div className={styles.tabs} role="tablist" aria-label="Vistas del subconjunto">
        {pestanas.map((p) => (
          <button
            key={p.valor}
            id={`${idBase}-tab-${p.valor}`}
            type="button"
            role="tab"
            aria-selected={pestana === p.valor}
            aria-controls={`${idBase}-panel-${p.valor}`}
            className={`${styles.tab} ${pestana === p.valor ? styles.tabActiva : ''}`}
            onClick={() => {
              setPestana(p.valor)
              setPagina(0)
            }}
          >
            {p.etiqueta}
          </button>
        ))}
      </div>

      <div
        id={`${idBase}-panel-${pestana}`}
        role="tabpanel"
        aria-labelledby={`${idBase}-tab-${pestana}`}
        className={styles.contenido}
      >
        {error ? (
          <div className={styles.error} role="alert">
            <p className={styles.errorTexto}>{error}</p>
            <div className={styles.erroresAcciones}>
              <button type="button" className="btn btnSecondary" onClick={() => setReintento((r) => r + 1)}>
                Reintentar
              </button>
              <button type="button" className="btn btnSecondary" onClick={onCerrar}>
                Cerrar detalle
              </button>
            </div>
          </div>
        ) : pestana === 'PERFIL' ? (
          <SubconjuntoPerfil perfil={perfil} cargando={cargando} />
        ) : (
          <>
            <div className={styles.controles}>
              {tienePacientes && (
                <label className={styles.busqueda}>
                  <span className={styles.busquedaEtiqueta}>Buscar</span>
                  <input
                    type="search"
                    placeholder="Buscar paciente o HC…"
                    value={busqueda}
                    onChange={(e) => setBusqueda(e.target.value)}
                  />
                </label>
              )}

              <div className={styles.controlesDerecha}>
                <label className={styles.tamano}>
                  <span className={styles.busquedaEtiqueta}>Filas</span>
                  <select
                    value={tamano}
                    onChange={(e) => {
                      setTamano(Number(e.target.value))
                      setPagina(0)
                    }}
                  >
                    {TAMANOS.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </label>

                <details
                  className={styles.columnas}
                  open={columnasAbiertas}
                  onToggle={(e) => setColumnasAbiertas((e.currentTarget as HTMLDetailsElement).open)}
                >
                  <summary className={styles.columnasResumen}>Columnas</summary>
                  <div className={styles.columnasLista}>
                    {(datosPagina?.columnas ?? []).map((c) => (
                      <label key={c.codigo} className={styles.columnaOpcion}>
                        <input
                          type="checkbox"
                          checked={c.identificador || !ocultas.has(c.codigo)}
                          // El identificador no puede ocultarse: sin él las filas
                          // dejarían de ser distinguibles.
                          disabled={c.identificador}
                          onChange={() => alternarColumna(c.codigo)}
                        />
                        {c.etiqueta}
                      </label>
                    ))}
                    <button type="button" className={styles.restaurar} onClick={() => setOcultas(new Set())}>
                      Restaurar columnas
                    </button>
                  </div>
                </details>
              </div>
            </div>

            <SubconjuntoTabla
              pagina={datosPagina}
              columnas={columnasVisibles}
              cargando={cargando}
              mostrarNumeroRegistros={pestana === 'PACIENTES'}
              orden={orden}
              onOrdenar={(campo) =>
                setOrden((actual) =>
                  actual?.campo === campo
                    ? { campo, direccion: actual.direccion === 'ASC' ? 'DESC' : 'ASC' }
                    : { campo, direccion: 'ASC' },
                )
              }
            />

            {datosPagina && datosPagina.totalPaginas > 1 && (
              <nav className={styles.paginacion} aria-label="Paginación">
                <button
                  type="button"
                  className="btn btnSecondary"
                  disabled={pagina === 0 || cargando}
                  onClick={() => setPagina((p) => Math.max(0, p - 1))}
                >
                  Anterior
                </button>
                <span className={styles.paginaTexto}>
                  Página {datosPagina.pagina + 1} de {datosPagina.totalPaginas} · {datosPagina.totalElementos}{' '}
                  {pestana === 'PACIENTES' ? 'pacientes' : 'registros'}
                </span>
                <button
                  type="button"
                  className="btn btnSecondary"
                  disabled={pagina + 1 >= datosPagina.totalPaginas || cargando}
                  onClick={() => setPagina((p) => p + 1)}
                >
                  Siguiente
                </button>
              </nav>
            )}
          </>
        )}
      </div>
    </section>
  )
}
