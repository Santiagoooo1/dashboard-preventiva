import type { BloqueInformeResponseDto, ComparacionInteranualRequestDto } from '../../api/types'
import { DashboardWidgetRenderer } from '../dashboard/DashboardWidgetRenderer'
import { ETIQUETA_COMPARACION } from '../comparacion/conceptosComparables'
import { claveUnidad, type UnidadComparacion } from './paginacion'
import { MatrizCategoriasInteranual, MatrizInteranual } from '../comparacion/MatrizInteranual'
import { ResumenAnual } from '../comparacion/ResumenAnual'
import { GraficaInteranual } from '../comparacion/GraficaInteranual'
import styles from './Informe.module.css'

interface BloqueInformeRendererProps {
  bloque: BloqueInformeResponseDto
  /** En el editor algunos elementos invisibles se representan; al imprimir, no. */
  enEdicion?: boolean
  /**
   * Para una comparación, pinta solo esa parte (Fase 6.9S.0.1).
   *
   * <p>Sin la prop se pinta completa, que es lo que necesitan la vista previa y
   * la ruta de impresión. El editor la pasa porque reparte la comparación entre
   * hojas por sus unidades semánticas.
   */
  unidad?: UnidadComparacion
  /** Año concreto de una matriz de distribución repartida por años. */
  indiceSerie?: number
}

/**
 * Pinta un bloque del informe (Fase 6.9Q).
 *
 * <p>Reutiliza los componentes reales del dashboard y de la comparación, no
 * capturas ni copias: lo que se ve en el informe es el mismo gráfico vectorial
 * que en su pantalla de origen. Eso es lo que permitirá que el PDF de 6.9R
 * salga con calidad y texto seleccionable en vez de una imagen pixelada.
 *
 * <p>El texto se pinta como texto, nunca como HTML: los informes los escriben
 * personas y no hay razón para aceptar marcado arbitrario.
 */
export function BloqueInformeRenderer({
  bloque,
  enEdicion,
  unidad,
  indiceSerie,
}: BloqueInformeRendererProps) {
  // Una referencia rota no tumba el documento: se dice qué pasa y se sigue.
  if (!bloque.disponible) {
    return (
      <div className={styles.bloqueNoDisponible}>
        <p>{bloque.motivoNoDisponible ?? 'Este elemento ya no está disponible.'}</p>
      </div>
    )
  }

  switch (bloque.tipoBloque) {
    case 'TITULO':
      return <h2 className={styles.titulo}>{bloque.contenidoTexto}</h2>

    case 'SUBTITULO':
      return <h3 className={styles.subtitulo}>{bloque.contenidoTexto}</h3>

    case 'TEXTO':
      // `white-space: pre-wrap` conserva los saltos de línea sin necesidad de
      // interpretar marcado.
      return <p className={styles.texto}>{bloque.contenidoTexto}</p>

    case 'SEPARADOR':
      return <hr className={styles.separador} />

    case 'SALTO_PAGINA':
      // Al imprimir es una ruptura y nada más. En el editor se dibuja: un
      // hueco enorme sin explicación parece un fallo de maquetado.
      return enEdicion ? (
        <div className={styles.saltoPagina}>
          <span>Salto de página</span>
        </div>
      ) : (
        <div className={styles.saltoPaginaImpreso} aria-hidden="true" />
      )

    case 'COMPARACION_INTERANUAL': {
      const comparacion = bloque.comparacion
      // Mientras llega el cálculo se describe la configuración en vez de dejar
      // un hueco mudo: con varias comparaciones en la misma hoja no se sabría
      // cuál es cuál.
      if (!comparacion) return <ComparacionPendiente config={bloque.configuracionComparacion} />
      if (!comparacion.comparable) {
        return (
          <div className={styles.bloqueNoDisponible}>
            <p>{comparacion.motivoNoComparable}</p>
          </div>
        )
      }
      // Bloque compuesto: sus tres partes son las unidades de paginación.
      // Marcarlo entero como indivisible produciría páginas imposibles —resumen
      // + matriz de 36 meses + gráfica no caben juntos en un A4—, así que el
      // navegador puede separarlas, pero nunca partir una por dentro.
      const anchaDeMas = demasiadasColumnas(comparacion)

      // En el render COMPLETO cada parte lleva su clave de unidad: así el
      // medidor toma las alturas dentro de esta misma estructura —la que se
      // imprime— en vez de sobre bloques separados de la rejilla, que añadían
      // un hueco y un margen por año. Cuando se pide una parte concreta la
      // etiqueta la pone la hoja, y repetirla aquí la contaría dos veces.
      const etiqueta = (u: UnidadComparacion, i?: number) =>
        unidad === undefined ? claveUnidad(bloque.id, u, i) : undefined

      const resumen = (
        <div className={styles.unidadImpresion} data-unidad={etiqueta('RESUMEN')}>
          <ResumenAnual comparacion={comparacion} />
        </div>
      )
      // Si se pide un año concreto se recorta la lista de series. Son los
      // mismos datos ya calculados, solo filtrados: la tabla la sigue pintando
      // el componente de siempre.
      const comparacionMatriz =
        indiceSerie === undefined
          ? comparacion
          : { ...comparacion, series: comparacion.series.slice(indiceSerie, indiceSerie + 1) }

      const aviso = anchaDeMas && (
        <p className={styles.avisoAnchura}>
          Esta tabla tiene {comparacion.categorias.length} categorías y se imprime con letra
          reducida. Si resulta ilegible, usa una comparación con menos categorías.
        </p>
      )

      // En el render completo la matriz se despliega año a año, cada uno con su
      // clave: es la misma descomposición que usa el editor para repartirla, y
      // deja al medidor leer la altura real de cada año tal como se imprime.
      const matriz = (
        <div className={`${styles.unidadImpresion} ${anchaDeMas ? styles.tablaComprimida : ''}`}>
          {/* El aviso de anchura acompaña al primer año, no a los tres. */}
          {(indiceSerie === undefined || indiceSerie === 0) && aviso}
          {comparacion.tipoComparacion !== 'DISTRIBUCION' ? (
            <div data-unidad={etiqueta('MATRIZ')}>
              <MatrizInteranual comparacion={comparacion} />
            </div>
          ) : indiceSerie !== undefined ? (
            <MatrizCategoriasInteranual comparacion={comparacionMatriz} />
          ) : (
            comparacion.series.map((serie, i) => (
              <div key={`${serie.datasetId}-${serie.anio}`} data-unidad={etiqueta('MATRIZ', i)}>
                <MatrizCategoriasInteranual
                  comparacion={{ ...comparacion, series: comparacion.series.slice(i, i + 1) }}
                />
              </div>
            ))
          )}
        </div>
      )
      const grafica = (
        <div className={styles.unidadImpresion} data-unidad={etiqueta('GRAFICA')}>
          <GraficaInteranual comparacion={comparacion} />
        </div>
      )

      // El título acompaña a la primera parte: repetirlo en las tres haría
      // creer que son tres comparaciones distintas.
      const encabezado = (!unidad || unidad === 'RESUMEN') && (
        <h4 className={styles.tituloBloque}>{comparacion.etiquetaConcepto}</h4>
      )

      return (
        <div className={styles.comparacionCompuesta}>
          {encabezado}
          {(!unidad || unidad === 'RESUMEN') && resumen}
          {(!unidad || unidad === 'MATRIZ') && matriz}
          {(!unidad || unidad === 'GRAFICA') && grafica}
        </div>
      )
    }

    default: {
      // KPI, GRAFICA y TABLA comparten renderizador con el dashboard.
      const widget = bloque.widget
      if (!widget) return <PendienteDeCalculo />
      return (
        <div className={styles.bloqueWidget}>
          <h4 className={styles.tituloBloque}>{bloque.tituloPersonalizado ?? widget.titulo}</h4>
          <DashboardWidgetRenderer widget={widget} />
        </div>
      )
    }
  }
}

/**
 * Hueco mientras llega el resultado.
 *
 * <p>Desde la 6.9Q.3 el editor también pinta cifras reales, así que esto ya no
 * es un estado permanente: solo se ve el instante entre añadir un bloque y
 * recibir su cálculo. Ocupa sitio a propósito, para que la hoja no dé un salto
 * cuando el dato entra.
 */
/**
 * Umbral de columnas a partir del cual una matriz no cabe en A4 vertical con
 * tipografía normal.
 *
 * <p>Doce columnas a ~15 mm son 180 mm, justo el ancho útil. Por encima se
 * reduce la letra hasta un suelo legible y se avisa. No se recortan columnas ni
 * se ocultan categorías: un informe clínico al que le faltan categorías en
 * silencio es peor que uno con letra pequeña.
 */
const MAX_COLUMNAS_A4 = 12

function demasiadasColumnas(comparacion: { categorias: string[] }): boolean {
  return comparacion.categorias.length > MAX_COLUMNAS_A4
}

function PendienteDeCalculo() {
  return <div className={styles.esqueleto} aria-label="Calculando…" />
}

function ComparacionPendiente({ config }: { config?: ComparacionInteranualRequestDto | null }) {
  if (!config) return <PendienteDeCalculo />
  const datasets = config.datasetIds.length
  return (
    <p className={styles.pendiente}>
      Comparación entre años de «{config.codigoCanonico}» ({ETIQUETA_COMPARACION[config.tipoComparacion]},
      por {config.granularidad.toLowerCase()}, {datasets} {datasets === 1 ? 'dataset' : 'datasets'}).
      Calculando…
    </p>
  )
}
