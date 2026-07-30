import type { TipoEventoImportacionTrabajo } from '../../api/types'

// Textos en lenguaje clínico-administrativo para cada tipo de evento técnico
// de trazabilidad (Fase 6.8D.2). No mostrar el nombre técnico del enum en pantalla.
const TEXTOS_EVENTO: Record<TipoEventoImportacionTrabajo, string> = {
  COPIA_CREADA: 'Se creó una copia interna del archivo.',
  CELDA_CORREGIDA: 'Se corrigió una celda.',
  CORRECCION_CELDA_DESHECHA: 'Se deshizo una corrección de celda.',
  CORRECCIONES_FILA_DESHECHAS: 'Se deshicieron las correcciones de una fila.',
  TODAS_CORRECCIONES_DESHECHAS: 'Se deshicieron todas las correcciones.',
  FILA_EXCLUIDA: 'Se excluyó una fila de la importación.',
  FILA_INCLUIDA: 'Se volvió a incluir una fila.',
  FILAS_SIMILARES_EXCLUIDAS: 'Se excluyeron filas con el mismo problema.',
  TODAS_EXCLUSIONES_DESHECHAS: 'Se deshicieron todas las exclusiones.',
  COPIA_RESTAURADA_ORIGINAL: 'Se restauró la copia al estado original.',
  COLUMNA_RELLENADA: 'Se rellenó una columna en bloque.',
  COLUMNA_NORMALIZADA: 'Se normalizó una columna.',
  REVALIDACION_EJECUTADA: 'Se revalidó la copia interna.',
  IMPORTACION_REALIZADA: 'Se importaron los datos corregidos.',
  DATASET_ACTIVADO: 'El dataset pasó a estar activo.',
  BORRADOR_DESCARTADO: 'Se descartó el borrador.',
  COPIA_DESCARTADA: 'Se descartó la copia interna.',
}

export function textoEvento(tipoEvento: string): string {
  return TEXTOS_EVENTO[tipoEvento as TipoEventoImportacionTrabajo] ?? tipoEvento
}

export type CategoriaEvento =
  | 'creacion'
  | 'correccion'
  | 'exclusion'
  | 'restauracion'
  | 'importacion'
  | 'activacion'
  | 'otro'

const CATEGORIA_POR_EVENTO: Record<TipoEventoImportacionTrabajo, CategoriaEvento> = {
  COPIA_CREADA: 'creacion',
  CELDA_CORREGIDA: 'correccion',
  CORRECCION_CELDA_DESHECHA: 'correccion',
  CORRECCIONES_FILA_DESHECHAS: 'correccion',
  TODAS_CORRECCIONES_DESHECHAS: 'correccion',
  COLUMNA_RELLENADA: 'correccion',
  COLUMNA_NORMALIZADA: 'correccion',
  FILA_EXCLUIDA: 'exclusion',
  FILA_INCLUIDA: 'exclusion',
  FILAS_SIMILARES_EXCLUIDAS: 'exclusion',
  TODAS_EXCLUSIONES_DESHECHAS: 'exclusion',
  COPIA_RESTAURADA_ORIGINAL: 'restauracion',
  IMPORTACION_REALIZADA: 'importacion',
  DATASET_ACTIVADO: 'activacion',
  REVALIDACION_EJECUTADA: 'otro',
  BORRADOR_DESCARTADO: 'otro',
  COPIA_DESCARTADA: 'otro',
}

export function categoriaEvento(tipoEvento: string): CategoriaEvento {
  return CATEGORIA_POR_EVENTO[tipoEvento as TipoEventoImportacionTrabajo] ?? 'otro'
}
