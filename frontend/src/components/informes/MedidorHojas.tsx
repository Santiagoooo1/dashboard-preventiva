import { useCallback, useEffect, useRef } from 'react'
import type { InformeClinicoResponseDto, PaginaInformeResponseDto } from '../../api/types'
import { HojaInforme } from './HojaInforme'
import { elementosDe, type MedidaBloque, type Medidas } from './paginacion'
import styles from './Informe.module.css'

/** Tope de mediciones emitidas por cada cambio de contenido. Ver `emisiones`. */
const MAX_EMISIONES = 12

interface MedidorHojasProps {
  informe: InformeClinicoResponseDto
  paginas: PaginaInformeResponseDto[]
  onMedidas: (medidas: Medidas) => void
}

/**
 * Mide los bloques sobre una hoja A4 real pero invisible (Fase 6.9S.0).
 *
 * <p>La alternativa era estimar alturas con constantes, y eso se rompe con el
 * primer título que ocupa dos líneas o la primera gráfica con más categorías.
 * Aquí se renderiza el contenido completo —sin partir— en una hoja con
 * exactamente el mismo ancho, padding y rejilla que la visible, y se leen las
 * alturas del navegador.
 *
 * <p>Está fuera de la vista pero <b>no</b> con `display: none`: un elemento sin
 * caja no tiene dimensiones y todo mediría cero. Se saca de pantalla con
 * `position: absolute` y se oculta a lectores y al ratón.
 *
 * <p>Las hojas visibles nunca se miden: si lo hicieran, repaginar cambiaría lo
 * medido y el cálculo entraría en bucle. El medidor siempre ve el documento sin
 * paginar, así que su resultado es estable.
 *
 * <p>Mide el documento <b>sin expandir</b>, es decir con la misma estructura que
 * la ruta de impresión: una comparación es un solo bloque con sus partes
 * dentro, y cada parte lleva su `data-unidad`. Midiéndolo expandido —tres
 * bloques sueltos de la rejilla— cada año cargaba con un hueco de rejilla y un
 * margen que la impresión no tiene, y el editor concluía que solo cabía un año
 * por hoja donde el PDF metía dos.
 */
export function MedidorHojas({ informe, paginas, onMedidas }: MedidorHojasProps) {
  const contenedor = useRef<HTMLDivElement>(null)
  const cuerpos = useRef<Map<number, HTMLDivElement>>(new Map())
  const programado = useRef(0)
  // Última medida emitida, para no provocar renders con valores idénticos.
  const ultima = useRef('')
  /**
   * Presupuesto de emisiones por cada cambio de contenido.
   *
   * <p>La firma ya evita emitir medidas idénticas, pero no protege del caso en
   * que dos estados se alternen: medir → repaginar → medir y volver al primero.
   * Eso deja el hilo principal girando y la página no responde. Con un tope, en
   * el peor caso la paginación se queda en la última medida buena en lugar de
   * colgar el editor. Se comprobó que hace falta: sin él, un documento con
   * varios párrafos largos bloqueaba la pestaña.
   */
  const emisiones = useRef(0)

  const medir = useCallback(() => {
    const porUnidad = new Map<string, MedidaBloque>()
    let altoUtil = 0
    let hueco = 0

    for (const pagina of paginas) {
      const cuerpo = cuerpos.current.get(pagina.id)
      if (!cuerpo) continue

      // El cuerpo de la hoja ES el área útil: va entre la cabecera y el pie,
      // dentro de una hoja de 297 mm. No hay que calcular nada.
      altoUtil = Math.max(altoUtil, cuerpo.clientHeight)
      if (hueco === 0) {
        hueco = parseFloat(getComputedStyle(cuerpo).rowGap || '0') || 0
      }

      const origen = cuerpo.getBoundingClientRect().top
      // Se mide por unidad: una comparación aporta tres medidas —resumen,
      // matriz y gráfica—, cada una con su altura real.
      for (const nodo of cuerpo.querySelectorAll<HTMLElement>('[data-unidad]')) {
        const clave = nodo.dataset.unidad
        if (!clave) continue
        const caja = nodo.getBoundingClientRect()
        porUnidad.set(clave, {
          alto: caja.height,
          top: caja.top - origen,
          tabla: medirTabla(nodo),
        })
      }
    }

    if (altoUtil <= 0) return

    const medidas: Medidas = { altoUtil, hueco, porUnidad }
    // Firma de lo medido: si nada ha cambiado no se vuelve a paginar. Es la
    // salvaguarda contra medir → renderizar → medir sin fin.
    const firma = JSON.stringify([
      Math.round(altoUtil),
      Math.round(hueco),
      [...porUnidad].map(([k, m]) => [k, Math.round(m.alto), Math.round(m.top), m.tabla?.filas ?? 0]),
    ])
    if (firma === ultima.current) return
    if (emisiones.current >= MAX_EMISIONES) return
    emisiones.current += 1
    ultima.current = firma
    onMedidas(medidas)
  }, [paginas, onMedidas])

  /** Agrupa las mediciones en un solo frame: evita medir en cada píxel. */
  const medirPronto = useCallback(() => {
    cancelAnimationFrame(programado.current)
    programado.current = requestAnimationFrame(() => requestAnimationFrame(medir))
  }, [medir])

  // Contenido nuevo: se renueva el presupuesto de mediciones.
  useEffect(() => {
    emisiones.current = 0
    ultima.current = ''
  }, [paginas])

  useEffect(() => {
    medirPronto()
    const nodo = contenedor.current
    if (!nodo) return

    // Se observan solo los bloques, no el contenedor: su alto depende de lo
    // medido, así que observarlo realimenta el propio cálculo. Las gráficas SVG
    // se dibujan después del primer render y los datos llegan de la API, así que
    // una sola medición daría alturas de un documento a medio pintar.
    const observador = new ResizeObserver(medirPronto)
    nodo.querySelectorAll('[data-unidad]').forEach((b) => observador.observe(b))

    return () => {
      observador.disconnect()
      cancelAnimationFrame(programado.current)
    }
  }, [medirPronto, paginas])

  return (
    <div className={styles.medidor} ref={contenedor} aria-hidden="true" data-medidor="1">
      {paginas.map((pagina, i) => (
        <HojaInforme
          key={pagina.id}
          informe={informe}
          elementos={elementosDe(pagina.bloques)}
          orientacion={pagina.orientacion}
          numero={i + 1}
          total={paginas.length}
          refCuerpo={(nodo) => {
            if (nodo) cuerpos.current.set(pagina.id, nodo)
            else cuerpos.current.delete(pagina.id)
          }}
        />
      ))}
    </div>
  )
}

/**
 * Alturas de una tabla: cabecera, fila y fila TOTAL.
 *
 * <p>Se leen del DOM en vez de deducirlas de la tipografía. Con ellas se calcula
 * cuántas filas caben en lo que queda de hoja sin inventar ninguna cifra; si la
 * tabla no tiene la forma esperada se devuelve `undefined` y la tabla se trata
 * como indivisible, que es preferible a partirla por un número supuesto.
 */
function medirTabla(nodo: HTMLElement): MedidaBloque['tabla'] {
  const tablas = nodo.querySelectorAll('table')
  if (tablas.length === 0) return undefined

  const principal = tablas[0]
  const cabecera = principal.querySelector('thead')
  const filas = principal.querySelectorAll('tbody tr')
  if (!cabecera || filas.length === 0) return undefined

  // La fila TOTAL vive en una <table> aparte, detrás de la principal.
  const tablaTotal = tablas.length > 1 ? tablas[tablas.length - 1] : null

  return {
    altoCabecera: cabecera.getBoundingClientRect().height,
    // Promedio de las filas reales: unas ocupan dos líneas si la etiqueta es larga.
    altoFila:
      [...filas].reduce((suma, f) => suma + f.getBoundingClientRect().height, 0) / filas.length,
    filas: filas.length,
    altoTotal: tablaTotal ? tablaTotal.getBoundingClientRect().height : 0,
  }
}
