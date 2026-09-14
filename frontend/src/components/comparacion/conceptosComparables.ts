import type { CampoMetricaMetadataDto, TipoComparacionInteranual } from '../../api/types'

/**
 * Reglas compartidas entre la pantalla de comparación y el editor de informes
 * (extraídas en la Fase 6.9Q.1).
 *
 * <p>Estaban dentro de `ComparacionInteranualPage`. Copiarlas al editor habría
 * creado dos listas de opciones que se separan a la primera corrección: un
 * hospital vería «Casos (recuento)» en una pantalla y no en la otra.
 */

/** Qué comparaciones admite cada tipo de dato. El backend rechaza el resto. */
export function comparacionesPara(tipoDato: string): TipoComparacionInteranual[] {
  if (tipoDato === 'BOOLEANO') return ['TASA', 'RECUENTO', 'DISTRIBUCION']
  if (tipoDato === 'TEXTO') return ['DISTRIBUCION']
  if (tipoDato === 'ENTERO' || tipoDato === 'DECIMAL') return ['RESUMEN_NUMERICO']
  return []
}

export const ETIQUETA_COMPARACION: Record<TipoComparacionInteranual, string> = {
  TASA: 'Tasa (% de casos sobre los documentados)',
  RECUENTO: 'Casos (recuento)',
  DISTRIBUCION: 'Distribución por categorías',
  RESUMEN_NUMERICO: 'Resumen numérico (N, media, mínimo, máximo)',
}

/**
 * Conceptos presentes en TODOS los datasets seleccionados, con el mismo tipo.
 *
 * <p>Se intersecta por código canónico, no por etiqueta: «LOCALIZACIÓN DE LA
 * INFECCIÓN», «SITIO DE LA INFECCIÓN» y «LOCALIZACIÓN ILQ» son el mismo
 * concepto y deben aparecer como una sola opción. Ofrecer uno que falte en algún
 * año llevaría a una comparación que el backend va a rechazar, y el usuario no
 * entendería por qué se la ofrecimos.
 */
export function conceptosComunes(
  seleccionados: number[],
  camposPorDataset: Map<number, CampoMetricaMetadataDto[]>,
): CampoMetricaMetadataDto[] {
  if (seleccionados.length === 0) return []
  const listas = seleccionados.map((id) => camposPorDataset.get(id))
  if (listas.some((l) => l === undefined)) return []

  const [primera, ...resto] = listas as CampoMetricaMetadataDto[][]
  return primera.filter((campo) =>
    resto.every((otros) =>
      otros.some((c) => c.codigo === campo.codigo && c.tipoDato === campo.tipoDato),
    ),
  )
}
