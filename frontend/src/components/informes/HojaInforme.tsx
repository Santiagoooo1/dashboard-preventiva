import { useEffect, useRef, useState, type ReactNode } from 'react'
import type {
  BloqueInformeResponseDto,
  InformeClinicoResponseDto,
  PaginaInformeResponseDto,
  TipoBloqueInforme,
} from '../../api/types'
import { BloqueInformeRenderer } from './BloqueInformeRenderer'
import styles from './Informe.module.css'

interface HojaInformeProps {
  informe: InformeClinicoResponseDto
  pagina: PaginaInformeResponseDto
  numero: number
  total: number
  /** Controles de edición sobre cada bloque; ausentes en vista previa e impresión. */
  controlesBloque?: (bloqueId: number, indice: number, total: number) => ReactNode
  /**
   * Mide la hoja y avisa si la página lógica no cabe en un A4. Se activa en
   * pantalla (editor y vista previa); al imprimir sobra, porque ahí el
   * navegador ya reparte el contenido de verdad.
   */
  avisarDesbordamiento?: boolean
  /** Además de pintar el aviso, lo comunica hacia arriba. */
  onDesbordamiento?: (desborda: boolean) => void
}

/**
 * Tipos de bloque que NO pueden partirse entre dos hojas físicas (6.9R.1).
 *
 * <p>Una gráfica cortada por la mitad no es media gráfica: son dos imágenes
 * ilegibles. Si no cabe en lo que queda de hoja, pasa entera a la siguiente.
 * Las tablas son la excepción deliberada —ver {@link TIPOS_DIVISIBLES}—.
 */
const TIPOS_ATOMICOS: TipoBloqueInforme[] = [
  'KPI',
  'GRAFICA',
  'TITULO',
  'SUBTITULO',
  'SEPARADOR',
]

/**
 * Bloques que sí pueden continuar en la hoja siguiente.
 *
 * <p>Una tabla de 36 meses no cabe en un A4 y no hay tipografía honesta que la
 * haga caber: se continúa, repitiendo la cabecera. La comparación interanual es
 * un bloque compuesto y se fragmenta por sus tres unidades semánticas (resumen,
 * matriz, gráfica), nunca por dentro de una de ellas.
 */
const TIPOS_DIVISIBLES: TipoBloqueInforme[] = ['TABLA', 'COMPARACION_INTERANUAL', 'TEXTO']

/** Alto imprimible aproximado de un A4 vertical, en píxeles CSS (297mm − márgenes). */
const ALTO_UTIL_A4_PX = ((297 - 14 * 2) / 25.4) * 96

/**
 * Una hoja del informe (Fase 6.9Q; paginación en 6.9R.1).
 *
 * <p>Mide 210 × 297 mm de verdad y coloca los bloques sobre una rejilla de 12
 * columnas. Guardar el ancho en columnas y no en píxeles es lo que hace que el
 * documento aguante un cambio de fuente, de navegador o el salto a PDF: una
 * posición absoluta se descuadra en cuanto un título ocupa una línea más.
 *
 * <p>Es una <b>página lógica</b>: la que el usuario creó. Al imprimir puede
 * convertirse en varias <b>páginas físicas</b> si lleva una tabla larga. Por eso
 * en pantalla la hoja tiene alto fijo de A4 y al imprimir ese alto se suelta:
 * forzarlo allí recortaría filas en silencio, que es justo lo que no puede
 * pasar en un documento clínico.
 */
export function HojaInforme({
  informe,
  pagina,
  numero,
  total,
  controlesBloque,
  avisarDesbordamiento,
  onDesbordamiento,
}: HojaInformeProps) {
  const horizontal = pagina.orientacion === 'HORIZONTAL'
  const fecha = informe.generadoEn ?? informe.actualizadoEn
  const cuerpoRef = useRef<HTMLDivElement>(null)
  const [desborda, setDesborda] = useState(false)

  const midiendo = Boolean(avisarDesbordamiento)

  useEffect(() => {
    const nodo = cuerpoRef.current
    if (!midiendo || !nodo) return

    const medir = () => {
      const excede = nodo.scrollHeight > ALTO_UTIL_A4_PX
      setDesborda(excede)
      onDesbordamiento?.(excede)
    }

    medir()
    // Las gráficas se dibujan después del primer render y las cifras llegan de
    // la API: medir una sola vez daría un aviso desfasado.
    const observador = new ResizeObserver(medir)
    observador.observe(nodo)
    return () => observador.disconnect()
  }, [midiendo, onDesbordamiento, pagina])

  return (
    <article
      className={[
        styles.hoja,
        horizontal ? styles.hojaHorizontal : '',
        desborda ? styles.hojaDesbordada : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <header className={styles.cabeceraHoja}>
        <span>{informe.tituloVisible}</span>
        {/* La numeración lógica no se imprime: con una tabla larga, la hoja 1
            de 2 lógicas puede ser la física 1 de 3. Ver 6.9R.2. */}
        <span className={styles.numeracionLogica}>
          Página {numero} de {total}
        </span>
      </header>

      <div className={styles.cuerpoHoja} ref={cuerpoRef}>
        {pagina.bloques.length === 0 ? (
          <p className={styles.hojaVacia}>
            Empieza añadiendo indicadores, gráficas, tablas o texto.
          </p>
        ) : (
          pagina.bloques.map((bloque, indice) => (
            <div
              key={bloque.id}
              className={`${styles.bloque} ${claseFragmentacion(bloque)}`}
              // El ancho viaja como número de columnas, no como medida física.
              style={{ ['--span' as string]: bloque.ancho }}
            >
              {controlesBloque && (
                <div className={styles.controlesBloque}>
                  {controlesBloque(bloque.id, indice, pagina.bloques.length)}
                </div>
              )}
              <div className={controlesBloque ? styles.marcoEdicion : undefined}>
                <BloqueInformeRenderer bloque={bloque} enEdicion={Boolean(controlesBloque)} />
              </div>
            </div>
          ))
        )}

        {/* Marca dónde termina el A4: mientras se edita se ve cuánto queda. */}
        {midiendo && <div className={styles.limitePagina} aria-hidden="true" />}
      </div>

      {midiendo && desborda && (
        <p className={styles.avisoDesbordamiento} role="status">
          Esta página supera el espacio imprimible A4. Al imprimir continuará en otra hoja.
        </p>
      )}

      {/* Trazabilidad: cuándo se consultó y de qué datasets sale. Sin ids. */}
      <footer className={styles.pieHoja}>
        <span>Consultado: {formatearFecha(fecha)}</span>
        {informe.datasetsUtilizados && informe.datasetsUtilizados.length > 0 && (
          <span className={styles.pieDatasets}>Datos de: {resumirDatasets(informe.datasetsUtilizados)}</span>
        )}
      </footer>
    </article>
  )
}

function claseFragmentacion(bloque: BloqueInformeResponseDto): string {
  if (TIPOS_DIVISIBLES.includes(bloque.tipoBloque)) return styles.bloqueDivisible
  if (TIPOS_ATOMICOS.includes(bloque.tipoBloque)) return styles.bloqueAtomico
  return ''
}

/**
 * Pie legible con muchos datasets.
 *
 * <p>Un hospital puede comparar seis años y seis nombres largos ocupan tres
 * líneas del pie. Se enseñan los primeros y se cuenta el resto en vez de
 * recortar la línea con puntos suspensivos, que escondería cuáles faltan.
 */
function resumirDatasets(nombres: string[]): string {
  const MAXIMO = 3
  if (nombres.length <= MAXIMO) return nombres.join(' · ')
  const resto = nombres.length - MAXIMO
  return `${nombres.slice(0, MAXIMO).join(' · ')} y ${resto} más`
}

function formatearFecha(iso: string | null): string {
  if (!iso) return '—'
  const fecha = new Date(iso)
  if (Number.isNaN(fecha.getTime())) return '—'
  const dd = String(fecha.getDate()).padStart(2, '0')
  const mm = String(fecha.getMonth() + 1).padStart(2, '0')
  const hh = String(fecha.getHours()).padStart(2, '0')
  const mi = String(fecha.getMinutes()).padStart(2, '0')
  return `${dd}/${mm}/${fecha.getFullYear()} ${hh}:${mi}`
}
