import type { ReactNode } from 'react'
import type { BloqueInformeResponseDto, InformeClinicoResponseDto, TipoBloqueInforme } from '../../api/types'
import { BloqueInformeRenderer } from './BloqueInformeRenderer'
import { agruparConsecutivos, type FragmentoBloque } from './paginacion'
import { formatearFecha, resumirDatasets } from './textoInforme'
import styles from './Informe.module.css'

interface HojaInformeProps {
  informe: InformeClinicoResponseDto
  /** Contenido de ESTA hoja. Lo decide quien la usa: la página lógica completa
   *  al imprimir, o el reparto físico calculado en el editor. */
  elementos: FragmentoBloque[]
  orientacion?: string | null
  numero: number
  total: number
  /** Controles de edición sobre cada bloque; ausentes en vista previa e impresión. */
  controlesBloque?: (bloqueId: number, indice: number, total: number) => ReactNode
  /** Se avisa del nodo del cuerpo para poder medirlo desde fuera. */
  refCuerpo?: (nodo: HTMLDivElement | null) => void
  /** Marca la hoja donde se está trabajando. */
  activa?: boolean
  onActivar?: () => void
}

/**
 * Tipos de bloque que NO pueden partirse entre dos hojas físicas (6.9R.1).
 *
 * <p>Una gráfica cortada por la mitad no es media gráfica: son dos imágenes
 * ilegibles. Si no cabe en lo que queda de hoja, pasa entera a la siguiente.
 * Las tablas son la excepción deliberada —ver {@link TIPOS_DIVISIBLES}—.
 */
const TIPOS_ATOMICOS: TipoBloqueInforme[] = ['KPI', 'GRAFICA', 'TITULO', 'SUBTITULO', 'SEPARADOR']

/**
 * Bloques que sí pueden continuar en la hoja siguiente.
 *
 * <p>Una tabla de 36 meses no cabe en un A4 y no hay tipografía honesta que la
 * haga caber: se continúa, repitiendo la cabecera. La comparación interanual es
 * un bloque compuesto y se fragmenta por sus tres unidades semánticas (resumen,
 * matriz, gráfica), nunca por dentro de una de ellas.
 */
const TIPOS_DIVISIBLES: TipoBloqueInforme[] = ['TABLA', 'COMPARACION_INTERANUAL', 'TEXTO']

/**
 * Una hoja A4 del informe (Fase 6.9Q; paginación en 6.9R.1 y 6.9S.0).
 *
 * <p>Mide 210 × 297 mm de verdad y coloca los bloques sobre una rejilla de 12
 * columnas. Guardar el ancho en columnas y no en píxeles es lo que hace que el
 * documento aguante un cambio de fuente, de navegador o el salto a PDF: una
 * posición absoluta se descuadra en cuanto un título ocupa una línea más.
 *
 * <p>La hoja no sabe nada de páginas lógicas ni físicas: recibe una lista de
 * elementos y los pinta. Eso es lo que permite que el editor, la vista previa y
 * la ruta de impresión usen <b>esta misma hoja</b> con el mismo ancho, padding,
 * tipografía y renderizadores, en vez de una aproximación por cada sitio.
 */
export function HojaInforme({
  informe,
  elementos,
  orientacion,
  numero,
  total,
  controlesBloque,
  refCuerpo,
  activa,
  onActivar,
}: HojaInformeProps) {
  const horizontal = orientacion === 'HORIZONTAL'
  const fecha = informe.generadoEn ?? informe.actualizadoEn

  return (
    <article
      className={[styles.hoja, horizontal ? styles.hojaHorizontal : '', activa ? styles.hojaActiva : '']
        .filter(Boolean)
        .join(' ')}
      onFocusCapture={onActivar}
      onMouseDown={onActivar}
    >
      <header className={styles.cabeceraHoja}>
        <span>{informe.tituloVisible}</span>
        {/* La numeración lógica no se imprime: la física la ponen las margin
            boxes de @page, que son las únicas que saben cuántas hojas salen. */}
        <span className={styles.numeracionLogica}>
          Página {numero} de {total}
        </span>
      </header>

      <div className={styles.cuerpoHoja} ref={refCuerpo}>
        {elementos.length === 0 ? (
          <p className={styles.hojaVacia}>Empieza añadiendo indicadores, gráficas, tablas o texto.</p>
        ) : (
          /* Los años consecutivos de una misma matriz se pintan en UN bloque de
             la rejilla, no en uno por año: así ocupan el mismo alto que al
             imprimir, que es lo que el medidor ha medido. */
          agruparConsecutivos(elementos).map((grupo, indice) => (
            <div
              key={grupo[0].clave}
              className={[
                styles.bloque,
                claseFragmentacion(grupo[0].bloque),
                grupo.some((e) => e.noCabe) ? styles.bloqueNoCabe : '',
              ]
                .filter(Boolean)
                .join(' ')}
              data-bloque-id={grupo[0].bloque.id}
              // El ancho viaja como número de columnas, no como medida física.
              style={{ ['--span' as string]: grupo[0].bloque.ancho }}
            >
              {/* `conControles` es false en las partes que no deben repetirlos:
                  una comparación son varios fragmentos pero UN bloque. */}
              {controlesBloque && grupo[0].conControles !== false && (
                <div className={styles.controlesBloque}>
                  {controlesBloque(grupo[0].bloque.id, indice, elementos.length)}
                </div>
              )}

              {grupo.some((e) => e.noCabe) && (
                <p className={styles.avisoBloqueNoCabe} role="status">
                  ⚠ Este bloque no cabe en una hoja A4. Redúcelo de ancho o divídelo en varios.
                </p>
              )}

              <div className={controlesBloque ? styles.marcoEdicion : undefined}>
                {grupo.map((elemento) => (
                  <div key={elemento.clave} data-unidad={elemento.clave}>
                    <BloqueInformeRenderer
                      bloque={elemento.bloque}
                      enEdicion={Boolean(controlesBloque)}
                      unidad={elemento.unidad}
                      indiceSerie={elemento.indiceSerie}
                    />
                  </div>
                ))}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Trazabilidad: cuándo se consultó y de qué datasets sale. Sin ids. */}
      <footer className={styles.pieHoja}>
        <span>Consultado: {formatearFecha(fecha)}</span>
        {informe.datasetsUtilizados && informe.datasetsUtilizados.length > 0 && (
          <span className={styles.pieDatasets}>
            Datos de: {resumirDatasets(informe.datasetsUtilizados)}
          </span>
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
