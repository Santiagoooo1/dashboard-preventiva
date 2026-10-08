import type { BloqueInformeResponseDto, PaginaInformeResponseDto } from '../../api/types'

/**
 * Paginación física del editor (Fase 6.9S.0).
 *
 * <p>Reparte los bloques de cada página lógica entre las hojas A4 que hagan
 * falta, a partir de <b>medidas reales del DOM</b>. No hay constantes de altura
 * inventadas: el alto útil y el de cada bloque los mide un componente oculto con
 * el mismo ancho, padding y rejilla que la hoja de verdad.
 *
 * <p>Es puro cálculo de maquetado: no toca la base de datos. Las
 * {@code PaginaInforme} persistidas siguen siendo lo que el usuario creó —sus
 * secciones y sus saltos manuales—; las hojas físicas se derivan de ellas. Si
 * fuese al contrario, cambiar el zoom o una fuente escribiría páginas en
 * PostgreSQL.
 */

/** Tolerancia al agrupar bloques en filas del grid, en píxeles. */
const TOLERANCIA_FILA = 4

/**
 * Partes en que se reparte una comparación interanual (Fase 6.9S.0.1).
 *
 * <p>Son las mismas unidades que ya usa la impresión. El bloque compuesto no es
 * atómico: resumen + matriz + gráfica no caben juntos en un A4 en cuanto hay
 * tres años, y tratarlo como indivisible hacía que el editor lo marcara como
 * imposible mientras el PDF lo repartía sin problema en tres hojas.
 */
export const UNIDADES_COMPARACION = ['RESUMEN', 'MATRIZ', 'GRAFICA'] as const
export type UnidadComparacion = (typeof UNIDADES_COMPARACION)[number]

/**
 * Clave de una unidad de maquetado. Estable y única: el bloque a secas, o el
 * bloque y su parte.
 */
export function claveUnidad(
  bloqueId: number,
  unidad?: UnidadComparacion,
  indiceSerie?: number,
): string {
  if (!unidad) return String(bloqueId)
  const base = `${bloqueId}:${unidad.toLowerCase()}`
  return indiceSerie === undefined ? base : `${base}:${indiceSerie}`
}

/** Medidas de un bloque tomadas del DOM. */
export interface MedidaBloque {
  alto: number
  /** Posición vertical dentro de la rejilla; sirve para saber qué bloques comparten fila. */
  top: number
  /** Solo en tablas: permite repartir filas entre hojas sin inventar alturas. */
  tabla?: {
    altoCabecera: number
    altoFila: number
    filas: number
    /** Alto de la fila TOTAL, que solo va en el último fragmento. */
    altoTotal: number
  }
}

export interface Medidas {
  /** Alto útil real del cuerpo de una hoja, medido sobre la hoja oculta. */
  altoUtil: number
  /** Separación vertical entre filas de la rejilla. */
  hueco: number
  /**
   * Medidas indexadas por {@link claveUnidad}. Antes la clave era el id del
   * bloque; dejó de servir cuando una comparación pasó a medirse en tres
   * partes, cada una con su propia altura.
   */
  porUnidad: Map<string, MedidaBloque>
}

/**
 * Un bloque, o el trozo de un bloque, colocado en una hoja.
 *
 * <p>Es lo que consume {@code HojaInforme}: la hoja no sabe de páginas lógicas
 * ni físicas, solo pinta la lista que le den. Por eso el editor, la vista previa
 * y la ruta de impresión pueden usar la misma hoja.
 */
export interface FragmentoBloque {
  clave: string
  bloque: BloqueInformeResponseDto
  /** true cuando el bloque es más alto que una hoja entera y no se puede partir. */
  noCabe: boolean
  /** Para una comparación, qué parte de ella es este fragmento. */
  unidad?: UnidadComparacion
  /**
   * En una matriz de distribución, el año concreto que pinta este fragmento.
   *
   * <p>La matriz de una DISTRIBUCION es una tabla por año, y al imprimir
   * Chromium ya la reparte entre esas tablas: se comprobó con 3 años × 12 meses
   * × 5 categorías y salió 2024+2025 en una hoja y 2026 en la siguiente, sin
   * partir ninguna tabla ni perder filas. El editor usa el mismo criterio —el
   * año—, que es semántico y no inventa ninguna partición de categorías.
   */
  indiceSerie?: number
  /**
   * false en las partes que no deben repetir los controles de edición.
   *
   * <p>Una comparación aparece en tres fragmentos, pero sigue siendo UN bloque
   * en la base de datos: enseñar ↑↓ ¼ ½ ✕ tres veces sugeriría que son tres
   * cosas distintas y que se pueden mover por separado.
   */
  conControles?: boolean
}

/** Bloques de una página lógica, sin partir nada: lo que usan impresión y vista previa. */
export function elementosDe(bloques: BloqueInformeResponseDto[]): FragmentoBloque[] {
  return bloques.map((bloque) => ({ clave: String(bloque.id), bloque, noCabe: false }))
}

/**
 * Una comparación ya calculada y comparable se reparte en sus tres partes; todo
 * lo demás queda como una sola unidad.
 *
 * <p>Es lo que miden el medidor y lo que coloca el paginador, así que ambos ven
 * exactamente la misma lista y no pueden discrepar.
 */
export function expandirUnidades(bloques: BloqueInformeResponseDto[]): FragmentoBloque[] {
  return bloques.flatMap<FragmentoBloque>((bloque) => {
    if (bloque.tipoBloque !== 'COMPARACION_INTERANUAL' || !bloque.comparacion?.comparable) {
      return [{ clave: claveUnidad(bloque.id), bloque, noCabe: false, conControles: true }]
    }
    const comparacion = bloque.comparacion
    // La matriz de distribución se reparte por años; la de tasa/recuento es una
    // sola tabla de como mucho doce filas y nunca necesita partirse.
    const porAnios =
      comparacion.tipoComparacion === 'DISTRIBUCION' && comparacion.series.length > 1

    return UNIDADES_COMPARACION.flatMap<FragmentoBloque>((unidad, i) => {
      if (unidad === 'MATRIZ' && porAnios) {
        return comparacion.series.map<FragmentoBloque>((_, indiceSerie) => ({
          clave: claveUnidad(bloque.id, unidad, indiceSerie),
          bloque,
          noCabe: false,
          unidad,
          indiceSerie,
          conControles: false,
        }))
      }
      return [{
        clave: claveUnidad(bloque.id, unidad),
        bloque,
        noCabe: false,
        unidad,
        // Los controles van solo en la primera parte: el bloque es uno.
        conControles: i === 0,
      }]
    })
  })
}

/**
 * Agrupa fragmentos consecutivos que deben pintarse dentro del mismo bloque de
 * la rejilla: los años de una misma matriz.
 *
 * <p>Si cada año fuese un bloque suelto, la rejilla metería su hueco entre
 * ellos y el conjunto ocuparía más que al imprimir. Agrupándolos, lo que se
 * pinta coincide con lo que se midió.
 */
export function agruparConsecutivos(fragmentos: FragmentoBloque[]): FragmentoBloque[][] {
  const grupos: FragmentoBloque[][] = []
  for (const f of fragmentos) {
    const previo = grupos[grupos.length - 1]
    const anterior = previo?.[previo.length - 1]
    const mismaMatriz =
      anterior !== undefined &&
      anterior.unidad === 'MATRIZ' &&
      f.unidad === 'MATRIZ' &&
      anterior.bloque.id === f.bloque.id
    if (mismaMatriz) previo.push(f)
    else grupos.push([f])
  }
  return grupos
}

export interface HojaFisica {
  /** Número de hoja física, 1-based y continuo en todo el documento. */
  numero: number
  paginaLogicaId: number
  orientacion: string
  /** true si es continuación automática de la misma página lógica. */
  continuacion: boolean
  fragmentos: FragmentoBloque[]
}

/** Tipos que pueden repartirse entre hojas partiendo su contenido. */
function esDivisible(bloque: BloqueInformeResponseDto): boolean {
  return bloque.tipoBloque === 'TABLA' && contarFilas(bloque) > 0
}

function contarFilas(bloque: BloqueInformeResponseDto): number {
  return bloque.widget?.serieTemporal?.puntos?.length ?? 0
}

/**
 * Agrupa las unidades en las filas que el navegador ha formado de verdad.
 *
 * <p>Se agrupa por la posición vertical medida, no por el ancho declarado:
 * calcular «12 columnas / ancho» a mano volvería a equivocarse en cuanto el grid
 * decida otra cosa. Si `[KPI][KPI][BARRAS]` comparten fila, la fila ocupa el
 * alto del más alto y viaja entera: paginar cada columna por separado partiría
 * la fila por la mitad.
 */
function agruparEnFilas(unidades: FragmentoBloque[], medidas: Medidas): FragmentoBloque[][] {
  const filas: FragmentoBloque[][] = []
  let topActual: number | null = null

  for (const unidad of unidades) {
    const medida = medidas.porUnidad.get(unidad.clave)
    if (!medida) {
      filas.push([unidad])
      topActual = null
      continue
    }
    if (topActual !== null && Math.abs(medida.top - topActual) <= TOLERANCIA_FILA) {
      filas[filas.length - 1].push(unidad)
    } else {
      filas.push([unidad])
      topActual = medida.top
    }
  }
  return filas
}

function altoDeFila(fila: FragmentoBloque[], medidas: Medidas): number {
  return Math.max(...fila.map((u) => medidas.porUnidad.get(u.clave)?.alto ?? 0), 0)
}

/**
 * Copia un bloque de tabla quedándose con un rango de filas.
 *
 * <p>Recorta los datos ya calculados; no recalcula nada. Así el fragmento lo
 * pinta el mismo `DashboardWidgetRenderer` de siempre, con su cabecera repetida,
 * y las cifras son las que vinieron del backend. La fila TOTAL solo se conserva
 * en el último trozo: repetirla en cada hoja diría tres veces un total que es
 * uno.
 */
export function recortarTabla(
  bloque: BloqueInformeResponseDto,
  desde: number,
  hasta: number,
  esUltimo: boolean,
): BloqueInformeResponseDto {
  const serie = bloque.widget?.serieTemporal
  if (!bloque.widget || !serie?.puntos) return bloque

  return {
    ...bloque,
    widget: {
      ...bloque.widget,
      serieTemporal: {
        ...serie,
        puntos: serie.puntos.slice(desde, hasta),
        total: esUltimo ? serie.total : null,
      },
    },
  }
}

/**
 * Una fila suelta, o el grupo de años de una matriz de distribución.
 *
 * <p>El grupo existe para reproducir lo que hace la impresión: ver
 * {@link agruparMatrices}.
 */
interface Agrupacion {
  filas: FragmentoBloque[][]
  /** true cuando las filas son los años de una misma matriz. */
  grupoMatriz: boolean
}

/**
 * Junta los años consecutivos de una misma matriz en un solo grupo.
 *
 * <p>Reproduce la semántica que la impresión ya aplica de hecho: la matriz
 * lleva `break-inside: avoid`, así que Chromium intenta no empezarla al final de
 * una hoja y, cuando no cabe entera, la corta entre las tablas de cada año.
 * Colocando año a año el editor metía la matriz de 2024 junto al resumen solo
 * porque ese año cabía, y el PDF la empezaba en la hoja siguiente.
 *
 * <p>Es una regla deliberadamente estrecha: solo para los años de una matriz de
 * distribución. No se toca cómo se colocan tablas temporales, KPIs, gráficas ni
 * nada más, y no se pretende implementar las reglas de fragmentación de CSS.
 */
function agruparMatrices(filas: FragmentoBloque[][]): Agrupacion[] {
  const esAnioDeMatriz = (fila: FragmentoBloque[]) =>
    fila.length === 1 && fila[0].unidad === 'MATRIZ' && fila[0].indiceSerie !== undefined

  const grupos: Agrupacion[] = []
  for (const fila of filas) {
    const anterior = grupos[grupos.length - 1]
    const mismaMatriz =
      anterior?.grupoMatriz &&
      esAnioDeMatriz(fila) &&
      anterior.filas[0][0].bloque.id === fila[0].bloque.id

    if (mismaMatriz) {
      anterior.filas.push(fila)
    } else {
      grupos.push({ filas: [fila], grupoMatriz: esAnioDeMatriz(fila) })
    }
  }
  // Un solo año no es un grupo: se comporta como cualquier otra fila.
  return grupos.map((g) => (g.filas.length > 1 ? g : { ...g, grupoMatriz: false }))
}

/**
 * Calcula las hojas físicas de todo el documento.
 *
 * <p>Sin medidas todavía —primer render— devuelve una hoja por página lógica:
 * es la mejor aproximación disponible y evita un parpadeo con el documento
 * vacío.
 */
export function paginar(
  paginas: PaginaInformeResponseDto[],
  medidas: Medidas | null,
): HojaFisica[] {
  const hojas: HojaFisica[] = []
  let numero = 0

  for (const pagina of paginas) {
    const nuevaHoja = (continuacion: boolean): HojaFisica => {
      numero += 1
      const hoja: HojaFisica = {
        numero,
        paginaLogicaId: pagina.id,
        orientacion: pagina.orientacion,
        continuacion,
        fragmentos: [],
      }
      hojas.push(hoja)
      return hoja
    }

    if (!medidas || medidas.altoUtil <= 0) {
      const hoja = nuevaHoja(false)
      hoja.fragmentos = expandirUnidades(pagina.bloques)
      continue
    }

    let hoja = nuevaHoja(false)
    let restante = medidas.altoUtil

    const cerrarHoja = () => {
      hoja = nuevaHoja(true)
      restante = medidas.altoUtil
    }

    const colocarFila = (fila: FragmentoBloque[], alto: number) => {
      fila.forEach((u) => hoja.fragmentos.push(u))
      restante -= alto
    }

    const filas = agruparEnFilas(expandirUnidades(pagina.bloques), medidas)

    for (const grupo of agruparMatrices(filas)) {
      // La matriz completa se trata como una pieza: o cabe en lo que queda, o
      // empieza en hoja nueva. Solo si no cabe ni en una hoja vacía se reparte
      // por años. Es lo que hace la impresión, que aquí es la referencia.
      if (grupo.grupoMatriz) {
        const hueco = hoja.fragmentos.length > 0 ? medidas.hueco : 0
        const altoGrupo =
          grupo.filas.reduce((suma, f) => suma + altoDeFila(f, medidas), 0) +
          medidas.hueco * (grupo.filas.length - 1)

        if (altoGrupo + hueco <= restante) {
          grupo.filas.forEach((f, i) => colocarFila(f, altoDeFila(f, medidas) + (i === 0 ? hueco : medidas.hueco)))
          continue
        }

        if (hoja.fragmentos.length > 0) {
          cerrarHoja()
        }

        if (altoGrupo <= medidas.altoUtil) {
          grupo.filas.forEach((f, i) => colocarFila(f, altoDeFila(f, medidas) + (i === 0 ? 0 : medidas.hueco)))
          continue
        }

        // Ni en una hoja vacía: se reparten los años, cada uno entero. Una
        // tabla anual nunca se corta por dentro.
        for (const fila of grupo.filas) {
          const altoAnio = altoDeFila(fila, medidas)
          if (altoAnio + (hoja.fragmentos.length > 0 ? medidas.hueco : 0) > restante
              && hoja.fragmentos.length > 0) {
            cerrarHoja()
          }
          const sep = hoja.fragmentos.length > 0 ? medidas.hueco : 0
          const imposible = altoAnio > medidas.altoUtil
          fila.forEach((u) => hoja.fragmentos.push({ ...u, noCabe: imposible }))
          restante = imposible ? 0 : restante - altoAnio - sep
        }
        continue
      }

      const fila = grupo.filas[0]

      // Un salto manual corta aquí y lo que siga empieza en hoja nueva.
      if (fila.length === 1 && fila[0].bloque.tipoBloque === 'SALTO_PAGINA') {
        hoja.fragmentos.push(fila[0])
        cerrarHoja()
        continue
      }

      const alto = altoDeFila(fila, medidas) + (hoja.fragmentos.length > 0 ? medidas.hueco : 0)

      if (alto <= restante) {
        fila.forEach((u) => hoja.fragmentos.push(u))
        restante -= alto
        continue
      }

      // Una tabla sola en su fila se reparte por filas de datos.
      if (fila.length === 1 && esDivisible(fila[0].bloque)) {
        restante = repartirTabla(fila[0].bloque, medidas, hoja, restante, cerrarHoja, () => hoja)
        continue
      }

      // No cabe en lo que queda: pasa entera a la siguiente hoja.
      if (hoja.fragmentos.length > 0) {
        cerrarHoja()
      }

      const altoSolo = altoDeFila(fila, medidas)
      // Si tampoco cabe en una hoja vacía, no hay paginación posible: se coloca
      // y se señala. Es el único caso en que el desbordamiento es un error, y
      // afecta solo a esta unidad: una matriz gigantesca no convierte en
      // imposible el resumen ni la gráfica de su misma comparación.
      const imposible = altoSolo > medidas.altoUtil
      fila.forEach((u) => hoja.fragmentos.push({ ...u, noCabe: imposible }))
      restante = imposible ? 0 : medidas.altoUtil - altoSolo
    }
  }

  return hojas
}

/**
 * Reparte una tabla entre hojas usando el alto medido de su cabecera y de una
 * fila.
 *
 * @return el alto que queda libre en la hoja donde terminó la tabla.
 */
function repartirTabla(
  bloque: BloqueInformeResponseDto,
  medidas: Medidas,
  hojaInicial: HojaFisica,
  restanteInicial: number,
  cerrarHoja: () => void,
  hojaActual: () => HojaFisica,
): number {
  const tabla = medidas.porUnidad.get(claveUnidad(bloque.id))?.tabla
  const totalFilas = contarFilas(bloque)
  if (!tabla || tabla.altoFila <= 0) {
    // Sin medidas de fila no se puede repartir con criterio: se coloca entera y
    // que se vea el problema, en vez de partirla por un número inventado.
    hojaInicial.fragmentos.push({ clave: claveUnidad(bloque.id), bloque, noCabe: true, conControles: true })
    return 0
  }

  let colocadas = 0
  let restante = restanteInicial
  let fragmento = 0

  while (colocadas < totalFilas) {
    const hoja = hojaActual()
    const hueco = hoja.fragmentos.length > 0 ? medidas.hueco : 0
    const disponible = restante - hueco - tabla.altoCabecera
    let caben = Math.floor(disponible / tabla.altoFila)

    if (caben < 1) {
      // Ni una fila entra en lo que queda: se pasa a la hoja siguiente.
      cerrarHoja()
      restante = medidas.altoUtil
      continue
    }

    caben = Math.min(caben, totalFilas - colocadas)
    const esUltimo = colocadas + caben >= totalFilas
    // La fila TOTAL necesita su propio espacio en el fragmento final.
    if (esUltimo && disponible - caben * tabla.altoFila < tabla.altoTotal && caben > 1) {
      caben -= 1
    }

    const ultimoDeVerdad = colocadas + caben >= totalFilas
    hoja.fragmentos.push({
      clave: `${bloque.id}-f${fragmento}`,
      bloque: recortarTabla(bloque, colocadas, colocadas + caben, ultimoDeVerdad),
      noCabe: false,
      // Los controles van en el primer trozo: la tabla sigue siendo un bloque.
      conControles: fragmento === 0,
    })

    colocadas += caben
    restante -= hueco + tabla.altoCabecera + caben * tabla.altoFila + (ultimoDeVerdad ? tabla.altoTotal : 0)
    fragmento += 1

    if (!ultimoDeVerdad) {
      cerrarHoja()
      restante = medidas.altoUtil
    }
  }

  return Math.max(0, restante)
}
