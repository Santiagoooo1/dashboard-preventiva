import type { ErrorImportacionTrabajoDto } from '../../api/types'
import { normalizarNombreColumna } from './identificadoresFilaTrabajo'

// Tipos de error que representan "no hay dato" (a diferencia de
// FORMATO_*_INVALIDO, que desde la Fase 6.8C.4.1 siempre es ERROR y por tanto
// nunca llega aquí, porque esta función solo tiene sentido para advertencias).
const TIPOS_VALOR_VACIO = new Set(['VALOR_OBLIGATORIO_VACIO', 'PACIENTE_SIGUE_INGRESADO', 'VALOR_AUSENTE_CLINICO'])

// Columnas de seguimiento clínico que legítimamente pueden quedar vacías si
// no aplica al paciente (no hubo reingreso, no hubo cultivo, no hubo
// infección...). Los patrones toleran variantes de plantilla en plantilla
// (MICROOR. / MICROORGANISMO, RESULTADO CULTIVO_1, RESISTENCIA 1...).
const PATRONES_ADVERTENCIA_ESPERABLE: RegExp[] = [
  /FECHA.*REINGRESO/,
  /REINGRESO.*ILQ/,
  /FECHA.*INFECCION.*LOCALIZACION/,
  /FECHA.*FIN.*VIGILANCIA/,
  /CULTIVO.*ILQ/,
  /TIPO.*MUESTRA/,
  /OTRA.*MUESTRA/,
  /RESULTADO.*CULTIVO/,
  /MICROOR/,
  /RESISTENCIA/,
  /LOCALIZACION.*INFECCION/,
  /MOTIVOS.*INADECUACION/,
  /TECNICA.*ELIMINACION.*VELLO/,
  /EDAD.*MES/,
  /COMENTARIOS/,
]

/**
 * true si el error es una advertencia (no bloqueante) sobre un dato de
 * seguimiento clínico que puede faltar legítimamente (p. ej. no hubo
 * reingreso, no hubo cultivo). Sirve para separar "campos opcionales no
 * informados" del resto de advertencias en la pantalla de corrección.
 */
export function esAdvertenciaClinicaEsperable(error: ErrorImportacionTrabajoDto): boolean {
  if (error.severidad !== 'ADVERTENCIA') return false
  if (!TIPOS_VALOR_VACIO.has(error.tipoError)) return false
  if (!error.nombreColumna) return false

  const normalizado = normalizarNombreColumna(error.nombreColumna)
  return PATRONES_ADVERTENCIA_ESPERABLE.some((patron) => patron.test(normalizado))
}
