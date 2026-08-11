import type { PrioridadDashboardCampo } from '../../api/types'

/**
 * Cómo se presenta al usuario clínico la prioridad de un campo (Fase 6.9J.1).
 *
 * El enum es técnico; aquí vive la única traducción, para que el mismo concepto
 * no acabe llamándose de dos maneras en dos pantallas.
 */

export const ORDEN_PRIORIDAD: PrioridadDashboardCampo[] = [
  'FUNDAMENTAL',
  'IMPORTANTE',
  'NORMAL',
  'EXCLUIR',
]

export const ETIQUETA_PRIORIDAD: Record<PrioridadDashboardCampo, string> = {
  FUNDAMENTAL: 'Fundamental',
  IMPORTANTE: 'Importante',
  NORMAL: 'Normal',
  EXCLUIR: 'Excluir',
}

export const AYUDA_PRIORIDAD: Record<PrioridadDashboardCampo, string> = {
  FUNDAMENTAL: 'Campo especialmente relevante para indicadores, filtros o gráficos.',
  IMPORTANTE: 'Campo útil que conviene considerar en el análisis.',
  NORMAL: 'Campo disponible para análisis sin prioridad especial.',
  EXCLUIR: 'No se propondrá automáticamente en dashboards.',
}
