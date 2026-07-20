// Primitivas mínimas para los gráficos SVG propios: escala lineal y ticks
// "redondos". No hay dependencias externas a propósito.

function numeroRedondo(rango: number, redondear: boolean): number {
  const exponente = Math.floor(Math.log10(rango))
  const fraccion = rango / 10 ** exponente
  let fraccionRedonda: number
  if (redondear) {
    if (fraccion < 1.5) fraccionRedonda = 1
    else if (fraccion < 3) fraccionRedonda = 2
    else if (fraccion < 7) fraccionRedonda = 5
    else fraccionRedonda = 10
  } else {
    if (fraccion <= 1) fraccionRedonda = 1
    else if (fraccion <= 2) fraccionRedonda = 2
    else if (fraccion <= 5) fraccionRedonda = 5
    else fraccionRedonda = 10
  }
  return fraccionRedonda * 10 ** exponente
}

export interface EscalaY {
  min: number
  max: number
  ticks: number[]
  /** Convierte un valor del dominio en coordenada Y del SVG. */
  y: (valor: number) => number
}

/**
 * Escala vertical con ticks redondos. El dominio siempre incluye el 0 para que
 * las barras se comparen contra una línea base honesta.
 */
export function crearEscalaY(valores: number[], alturaUtil: number, margenSuperior: number): EscalaY {
  const finitos = valores.filter((v) => Number.isFinite(v))
  const maxDato = finitos.length > 0 ? Math.max(...finitos, 0) : 0
  const minDato = finitos.length > 0 ? Math.min(...finitos, 0) : 0

  if (maxDato === minDato) {
    const max = maxDato === 0 ? 1 : maxDato
    return {
      min: 0,
      max,
      ticks: [0, max],
      y: (valor) => margenSuperior + alturaUtil - (valor / max) * alturaUtil,
    }
  }

  const rango = numeroRedondo(maxDato - minDato, false)
  const paso = numeroRedondo(rango / 4, true)
  const min = Math.floor(minDato / paso) * paso
  const max = Math.ceil(maxDato / paso) * paso

  const ticks: number[] = []
  for (let t = min; t <= max + paso / 2; t += paso) {
    ticks.push(Number(t.toFixed(10)))
  }

  return {
    min,
    max,
    ticks,
    y: (valor) => margenSuperior + alturaUtil - ((valor - min) / (max - min)) * alturaUtil,
  }
}

/** Reparte n bandas en el ancho útil y devuelve centro y grosor de cada una. */
export function crearBandas(n: number, anchoUtil: number, margenIzquierdo: number, ratioBarra = 0.62) {
  const paso = n > 0 ? anchoUtil / n : anchoUtil
  const grosor = Math.max(2, paso * ratioBarra)
  return {
    paso,
    grosor,
    centro: (i: number) => margenIzquierdo + paso * i + paso / 2,
  }
}

/** Acorta etiquetas largas para los ejes, conservando el texto completo en el tooltip. */
export function acortar(texto: string, maximo: number): string {
  return texto.length > maximo ? `${texto.slice(0, maximo - 1)}…` : texto
}
