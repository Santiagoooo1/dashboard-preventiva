import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import type {
  BloqueInformeRequestDto,
  BloqueInformeResponseDto,
  ComparacionInteranualRequestDto,
  InformeClinicoResponseDto,
  PaginaInformeResponseDto,
} from '../api/types'
import {
  actualizarBloque,
  actualizarInforme,
  anadirBloque,
  anadirPagina,
  duplicarPagina,
  eliminarBloque,
  eliminarPagina,
  moverBloque,
  obtenerInforme,
  obtenerInformeConResultados,
} from '../api/informesApi'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { FormField } from '../components/FormField'
import { BibliotecaElementos } from '../components/informes/BibliotecaElementos'
import { descargarPdfDeInforme } from '../components/informes/descargaPdf'
import { FormularioComparacion } from '../components/informes/FormularioComparacion'
import { HojaInforme } from '../components/informes/HojaInforme'
import { MedidorHojas } from '../components/informes/MedidorHojas'
import { paginar, type Medidas } from '../components/informes/paginacion'
import { usePersistenciaInforme } from '../components/informes/usePersistenciaInforme'
import styles from '../components/informes/Informe.module.css'

/** Espera antes de mandar el título. Suficiente para no hacer un PUT por tecla. */
const RETARDO_TITULO = 600

/**
 * Editor de informes (Fase 6.9Q; WYSIWYG en 6.9Q.3; paginación en 6.9S.0).
 *
 * <p>El lienzo enseña las <b>hojas físicas</b> apiladas, como un procesador de
 * textos. Antes mostraba una página lógica a la vez y avisaba de que «no cabe en
 * un A4» sin enseñar dónde iba a parar lo que sobraba: para saber cómo quedaría
 * el documento había que generar el PDF. Ahora el reparto se ve.
 *
 * <p>Las cifras salen de {@code GET /informes/:id/resultados}, el mismo agregado
 * que usa la vista previa: una llamada para todo el documento y ningún cálculo
 * en el navegador.
 *
 * <p>Todo se guarda solo. El título con un pequeño retardo, el resto en el acto,
 * pero pasando todos por la misma cola, así que el documento tiene un único
 * estado: guardando, guardado o error.
 */
export function InformeEditorPage() {
  const { informeId } = useParams()
  const navegar = useNavigate()
  const [informe, setInforme] = useState<InformeClinicoResponseDto | null>(null)
  const [titulo, setTitulo] = useState('')
  const [comparacionAbierta, setComparacionAbierta] = useState(false)
  const [generandoPdf, setGenerandoPdf] = useState(false)
  const [errorCarga, setErrorCarga] = useState<string | null>(null)
  const [errorPdf, setErrorPdf] = useState<string | null>(null)

  // Resultados por id de bloque: sobreviven a una recarga de solo estructura.
  const [resultados, setResultados] = useState<Map<number, BloqueInformeResponseDto>>(new Map())
  const [calculando, setCalculando] = useState(false)
  const [medidas, setMedidas] = useState<Medidas | null>(null)
  /** Hoja física sobre la que se está trabajando; decide dónde se añade. */
  const [hojaActiva, setHojaActiva] = useState(1)

  const persistencia = usePersistenciaInforme()
  const { mutar, esperarInactividad } = persistencia

  // Lo último que el usuario ha escrito. La mutación encolada lee de aquí, no
  // del valor que hubiera cuando se programó: así tres pulsaciones seguidas
  // mandan el texto final, no un estado intermedio.
  const tituloRef = useRef('')
  tituloRef.current = titulo
  const tituloServidor = useRef('')
  const temporizadorTitulo = useRef<number>(0)

  const recargar = useCallback(
    async (conResultados: boolean) => {
      if (!informeId) return
      if (conResultados) setCalculando(true)
      try {
        const datos = conResultados
          ? await obtenerInformeConResultados(informeId)
          : await obtenerInforme(informeId)
        setInforme(datos)
        tituloServidor.current = datos.tituloVisible
        // No se pisa lo que el usuario está escribiendo: añadir un bloque
        // recarga la estructura, y eso borraba el título a medio teclear.
        if (tituloRef.current === '' || tituloRef.current === datos.tituloVisible) {
          setTitulo(datos.tituloVisible)
        }
        if (conResultados) {
          const mapa = new Map<number, BloqueInformeResponseDto>()
          datos.paginas?.forEach((p) => p.bloques.forEach((b) => mapa.set(b.id, b)))
          setResultados(mapa)
        }
      } catch (e) {
        setErrorCarga(e instanceof Error ? e.message : 'No se pudo cargar el informe.')
      } finally {
        if (conResultados) setCalculando(false)
      }
    },
    [informeId],
  )

  useEffect(() => {
    recargar(true)
  }, [recargar])

  // ------------------------------------------------------------------
  // Autoguardado
  // ------------------------------------------------------------------

  /** Encola una mutación estructural. Recarga después para reflejar el servidor. */
  const mutarEstructura = useCallback(
    (accion: () => Promise<unknown>, conResultados = true) =>
      mutar(accion, () => recargar(conResultados)),
    [mutar, recargar],
  )

  const guardarTitulo = useCallback(() => {
    if (!informeId) return
    window.clearTimeout(temporizadorTitulo.current)
    if (tituloRef.current.trim() === tituloServidor.current.trim()) return
    mutar(
      () => {
        // Se lee el texto en el momento de ejecutar, no al encolar: si el
        // usuario ha seguido escribiendo, se manda lo último.
        const texto = tituloRef.current
        tituloServidor.current = texto
        return actualizarInforme(informeId, { nombre: texto, titulo: texto })
      },
      () => recargar(false),
    )
  }, [informeId, mutar, recargar])

  const escribirTitulo = (texto: string) => {
    setTitulo(texto)
    window.clearTimeout(temporizadorTitulo.current)
    temporizadorTitulo.current = window.setTimeout(guardarTitulo, RETARDO_TITULO)
  }

  useEffect(() => () => window.clearTimeout(temporizadorTitulo.current), [])

  // ------------------------------------------------------------------
  // Paginación física
  // ------------------------------------------------------------------

  const paginas: PaginaInformeResponseDto[] = useMemo(() => {
    const crudas = informe?.paginas ?? []
    // Cada bloque se pinta con su resultado ya calculado encima, así que el
    // medidor y el lienzo ven exactamente lo mismo que la vista previa.
    return crudas.map((p) => ({
      ...p,
      bloques: p.bloques.map((b) => {
        const calculado = resultados.get(b.id)
        return calculado
          ? {
              ...b,
              widget: calculado.widget,
              comparacion: calculado.comparacion,
              disponible: calculado.disponible,
              motivoNoDisponible: calculado.motivoNoDisponible,
              datasetNombre: calculado.datasetNombre,
            }
          : b
      }),
    }))
  }, [informe, resultados])

  const hojas = useMemo(() => paginar(paginas, medidas), [paginas, medidas])

  // La hoja activa puede desaparecer al borrar contenido.
  useEffect(() => {
    setHojaActiva((actual) => Math.min(Math.max(1, actual), Math.max(1, hojas.length)))
  }, [hojas.length])

  /** Página lógica sobre la que actúan «añadir» y las acciones de sección. */
  const paginaLogicaActiva = useMemo(() => {
    const hoja = hojas.find((h) => h.numero === hojaActiva) ?? hojas[hojas.length - 1]
    return paginas.find((p) => p.id === hoja?.paginaLogicaId) ?? paginas[paginas.length - 1]
  }, [hojas, hojaActiva, paginas])

  const bloquesNoCaben = useMemo(
    () => hojas.flatMap((h) => h.fragmentos).filter((f) => f.noCabe).length,
    [hojas],
  )

  // ------------------------------------------------------------------
  // Acciones
  // ------------------------------------------------------------------

  const anadir = (bloque: BloqueInformeRequestDto) => {
    if (!informeId || !paginaLogicaActiva) return
    mutarEstructura(() => anadirBloque(informeId, paginaLogicaActiva.id, bloque))
  }

  // En secuencia, no en paralelo: el orden de los bloques lo asigna el backend
  // al insertar, y lanzar varias peticiones a la vez dejaría el resultado a
  // merced de cuál conteste antes.
  const anadirVarios = (bloques: BloqueInformeRequestDto[]) => {
    if (!informeId || !paginaLogicaActiva) return
    mutarEstructura(async () => {
      for (const bloque of bloques) {
        await anadirBloque(informeId, paginaLogicaActiva.id, bloque)
      }
    })
  }

  const anadirComparacion = (config: ComparacionInteranualRequestDto) => {
    setComparacionAbierta(false)
    // Ancho completo: una matriz de años por categorías no se lee en media hoja.
    anadir({ tipoBloque: 'COMPARACION_INTERANUAL', configuracionComparacion: config, ancho: 12 })
  }

  const bloqueDe = (bloqueId: number) =>
    paginas
      .flatMap((p) => p.bloques.map((b) => ({ b, paginaId: p.id })))
      .find((x) => x.b.id === bloqueId)

  const cambiarAncho = (bloqueId: number, ancho: number) => {
    const encontrado = bloqueDe(bloqueId)
    if (!informeId || !encontrado) return
    const { b, paginaId } = encontrado
    // El ancho no altera ninguna cifra, pero sí la paginación: se recarga la
    // estructura y se reaprovechan los resultados ya recibidos.
    mutarEstructura(
      () =>
        actualizarBloque(informeId, paginaId, bloqueId, {
          tipoBloque: b.tipoBloque,
          ancho,
          metricaId: b.metricaId,
          tipoVisualizacion: b.tipoVisualizacion,
          tipoResultadoWidget: b.tipoResultadoWidget,
          configuracionWidget: b.configuracionWidget,
          configuracionComparacion: b.configuracionComparacion,
          contenidoTexto: b.contenidoTexto,
          tituloPersonalizado: b.tituloPersonalizado,
        }),
      false,
    )
  }

  const mover = (bloqueId: number, destino: number) => {
    const encontrado = bloqueDe(bloqueId)
    if (!informeId || !encontrado) return
    mutarEstructura(() => moverBloque(informeId, encontrado.paginaId, bloqueId, destino), false)
  }

  const borrarBloque = (bloqueId: number) => {
    const encontrado = bloqueDe(bloqueId)
    if (!informeId || !encontrado) return
    mutarEstructura(() => eliminarBloque(informeId, encontrado.paginaId, bloqueId))
  }

  const editarTexto = (bloqueId: number) => {
    const encontrado = bloqueDe(bloqueId)
    if (!informeId || !encontrado) return
    const nuevo = window.prompt('Texto del bloque:', encontrado.b.contenidoTexto ?? '')
    if (nuevo === null || nuevo.trim() === '') return
    mutarEstructura(() =>
      actualizarBloque(informeId, encontrado.paginaId, bloqueId, {
        tipoBloque: encontrado.b.tipoBloque,
        ancho: encontrado.b.ancho,
        contenidoTexto: nuevo,
      }),
    )
  }

  // ------------------------------------------------------------------
  // Vista previa y PDF
  // ------------------------------------------------------------------

  /**
   * Deja el documento a salvo antes de leerlo desde el servidor.
   *
   * <p>No hay esperas fijas: se vacía el retardo del título, se encola su
   * guardado y se espera a que la cola quede libre. Si algo quedó en error no se
   * continúa: abrir la vista previa o generar un PDF de una versión incierta es
   * peor que no abrirla.
   */
  const asegurarPersistido = async (): Promise<boolean> => {
    guardarTitulo()
    await esperarInactividad()
    return persistencia.estado !== 'error'
  }

  const irAVistaPrevia = async () => {
    if (!informeId || generandoPdf) return
    if (await asegurarPersistido()) {
      navegar(`/informes/${informeId}/vista-previa`)
    }
  }

  const descargarPdf = async () => {
    if (!informeId || generandoPdf) return
    setErrorPdf(null)
    if (!(await asegurarPersistido())) return

    setGenerandoPdf(true)
    try {
      await descargarPdfDeInforme(informeId)
    } catch (e) {
      setErrorPdf(e instanceof Error && e.message ? e.message : 'No se pudo generar el PDF.')
    } finally {
      setGenerandoPdf(false)
    }
  }

  // ------------------------------------------------------------------
  // Render
  // ------------------------------------------------------------------

  if (!informe) {
    return (
      <Card title="Informe">
        <ErrorBanner mensaje={errorCarga} />
        {!errorCarga && <p className="stateLoading">Cargando…</p>}
      </Card>
    )
  }

  const bloqueando = persistencia.estado === 'guardando' || generandoPdf

  return (
    <div>
      <Card title="Informe">
        <ErrorBanner mensaje={errorCarga} />
        <ErrorBanner mensaje={errorPdf} />
        <FormField
          label="Título del informe"
          help="Identifica el informe en la lista y encabeza cada hoja. Se guarda solo."
        >
          <input
            value={titulo}
            onChange={(e) => escribirTitulo(e.target.value)}
            onBlur={guardarTitulo}
          />
        </FormField>

        <div className={styles.acciones}>
          <EstadoGuardado persistencia={persistencia} />

          <button
            type="button"
            className="btn btnSecondary"
            disabled={bloqueando}
            onClick={irAVistaPrevia}
          >
            Vista previa
          </button>
          <button
            type="button"
            className="btn btnSecondary"
            disabled={bloqueando}
            onClick={descargarPdf}
          >
            {generandoPdf ? 'Generando PDF…' : 'Descargar PDF'}
          </button>
          <Link className="btn btnSecondary" to="/informes">
            Volver
          </Link>
        </div>
      </Card>

      <div className={styles.editor}>
        <BibliotecaElementos
          onAnadir={anadir}
          onAnadirVarios={anadirVarios}
          onAbrirComparacion={() => setComparacionAbierta(true)}
        />

        {/* `data-lienzo` distingue las hojas visibles de las del medidor, que
            están fuera de pantalla pero siguen en el layout. */}
        <div className={styles.lienzo} data-lienzo="1">
          {comparacionAbierta && (
            <FormularioComparacion
              onAceptar={anadirComparacion}
              onCancelar={() => setComparacionAbierta(false)}
            />
          )}

          <div className={styles.cabeceraPagina}>
            <span className={styles.calculando}>
              {calculando && 'Calculando…'}
              {!calculando && bloquesNoCaben > 0 && (
                <span className={styles.avisoInline}>
                  ⚠ {bloquesNoCaben} bloque{bloquesNoCaben > 1 ? 's' : ''} no cabe
                  {bloquesNoCaben > 1 ? 'n' : ''} en un A4
                </span>
              )}
            </span>
            <span className={styles.acciones}>
              {/* Añadir página crea un salto manual, una sección nueva. Ya no
                  hace falta pulsarlo para arreglar un desbordamiento: eso se
                  pagina solo. */}
              <button
                type="button"
                className="btn btnSecondary"
                disabled={bloqueando}
                onClick={() => informeId && mutarEstructura(() => anadirPagina(informeId), false)}
              >
                + Añadir sección
              </button>
              <button
                type="button"
                className="btn btnSecondary"
                disabled={bloqueando || !paginaLogicaActiva}
                onClick={() =>
                  informeId &&
                  paginaLogicaActiva &&
                  mutarEstructura(() => duplicarPagina(informeId, paginaLogicaActiva.id))
                }
              >
                Duplicar sección
              </button>
              <button
                type="button"
                className="btn btnDanger"
                disabled={bloqueando || paginas.length <= 1 || !paginaLogicaActiva}
                onClick={() =>
                  informeId &&
                  paginaLogicaActiva &&
                  mutarEstructura(() => eliminarPagina(informeId, paginaLogicaActiva.id))
                }
              >
                Eliminar sección
              </button>
            </span>
          </div>

          {/* Hojas físicas apiladas: se ve el documento completo y dónde cae
              cada cosa, en vez de navegar página a página a ciegas. */}
          {hojas.map((hoja) => (
            <HojaInforme
              key={`${hoja.paginaLogicaId}-${hoja.numero}`}
              informe={informe}
              elementos={hoja.fragmentos}
              orientacion={hoja.orientacion}
              numero={hoja.numero}
              total={hojas.length}
              activa={hoja.numero === hojaActiva}
              onActivar={() => setHojaActiva(hoja.numero)}
              controlesBloque={(bloqueId) => (
                <ControlesBloque
                  bloqueId={bloqueId}
                  posicion={posicionEnPagina(paginas, hoja.paginaLogicaId, bloqueId)}
                  totalEnPagina={paginas.find((p) => p.id === hoja.paginaLogicaId)?.bloques.length ?? 0}
                  esTexto={esTexto(bloqueDe(bloqueId)?.b.tipoBloque)}
                  deshabilitado={bloqueando}
                  onMover={mover}
                  onAncho={cambiarAncho}
                  onTexto={editarTexto}
                  onBorrar={borrarBloque}
                />
              )}
            />
          ))}
        </div>
      </div>

      {/* Mide sobre una hoja A4 real pero fuera de la vista. Nunca se miden las
          hojas visibles: repaginar cambiaría lo medido y el cálculo no pararía. */}
      <MedidorHojas informe={informe} paginas={paginas} onMedidas={setMedidas} />
    </div>
  )
}

/** Posición del bloque dentro de su página lógica, no dentro de la hoja física. */
function posicionEnPagina(
  paginas: PaginaInformeResponseDto[],
  paginaId: number,
  bloqueId: number,
): number {
  const pagina = paginas.find((p) => p.id === paginaId)
  return pagina ? pagina.bloques.findIndex((b) => b.id === bloqueId) : -1
}

interface ControlesBloqueProps {
  bloqueId: number
  posicion: number
  totalEnPagina: number
  esTexto: boolean
  deshabilitado: boolean
  onMover: (bloqueId: number, destino: number) => void
  onAncho: (bloqueId: number, ancho: number) => void
  onTexto: (bloqueId: number) => void
  onBorrar: (bloqueId: number) => void
}

/**
 * Controles de un bloque.
 *
 * <p>Mueven y redimensionan dentro de la <b>página lógica</b>: las hojas físicas
 * son un reparto derivado, así que subir un bloque significa subirlo en su
 * sección, no dentro de la hoja donde ha caído. Si se ordenara por la hoja
 * física, mover el primer bloque de una continuación haría cosas
 * imprevisibles.
 */
function ControlesBloque({
  bloqueId,
  posicion,
  totalEnPagina,
  esTexto,
  deshabilitado,
  onMover,
  onAncho,
  onTexto,
  onBorrar,
}: ControlesBloqueProps) {
  return (
    <>
      <button
        type="button"
        disabled={deshabilitado || posicion <= 0}
        title="Subir"
        onClick={() => onMover(bloqueId, posicion - 1)}
      >
        ↑
      </button>
      <button
        type="button"
        disabled={deshabilitado || posicion < 0 || posicion >= totalEnPagina - 1}
        title="Bajar"
        onClick={() => onMover(bloqueId, posicion + 1)}
      >
        ↓
      </button>
      <button
        type="button"
        disabled={deshabilitado}
        title="Un cuarto de ancho"
        onClick={() => onAncho(bloqueId, 3)}
      >
        ¼
      </button>
      <button
        type="button"
        disabled={deshabilitado}
        title="Media página"
        onClick={() => onAncho(bloqueId, 6)}
      >
        ½
      </button>
      <button
        type="button"
        disabled={deshabilitado}
        title="Ancho completo"
        onClick={() => onAncho(bloqueId, 12)}
      >
        1/1
      </button>
      {esTexto && (
        <button
          type="button"
          disabled={deshabilitado}
          title="Editar texto"
          onClick={() => onTexto(bloqueId)}
        >
          Editar
        </button>
      )}
      <button
        type="button"
        disabled={deshabilitado}
        title="Eliminar bloque"
        onClick={() => onBorrar(bloqueId)}
      >
        ✕
      </button>
    </>
  )
}

/**
 * Estado del documento: uno solo para el título y para la estructura.
 *
 * <p>Nunca dice «✓ Guardado» con una mutación en vuelo, que era justo la
 * incoherencia anterior: se movía un widget y el indicador seguía tranquilo.
 */
function EstadoGuardado({
  persistencia,
}: {
  persistencia: ReturnType<typeof usePersistenciaInforme>
}) {
  if (persistencia.estado === 'guardando') {
    return <span className={styles.estadoGuardado}>Guardando…</span>
  }
  if (persistencia.estado === 'error') {
    return (
      <span
        className={styles.estadoError}
        data-persistencia="error"
        data-reintentable={String(persistencia.puedeReintentar)}
      >
        ⚠ No se pudo guardar
        {persistencia.puedeReintentar && (
          <button type="button" className="btn btnSecondary" onClick={persistencia.reintentar}>
            Reintentar
          </button>
        )}
      </span>
    )
  }
  return <span className={styles.estadoGuardado}>✓ Guardado</span>
}

function esTexto(tipo?: string): boolean {
  return tipo === 'TITULO' || tipo === 'SUBTITULO' || tipo === 'TEXTO'
}
