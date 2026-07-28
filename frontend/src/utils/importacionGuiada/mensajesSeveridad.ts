// Textos consistentes para severidad de errores de la copia de trabajo,
// compartidos entre GrupoProblemaRow y FilaTrabajoErrorRow. Deliberadamente no
// reutiliza problemaLegible() de sugerenciasErrores.ts: ese helper es del
// asistente de columnas (antes de tener datos reales) y usa frases como
// "Faltan valores obligatorios" que no deben aparecer en una advertencia no
// bloqueante (ver Fase 6.8C.4.2).

export function etiquetaBadgeSeveridad(bloqueante: boolean): string {
  return bloqueante ? 'ERROR BLOQUEANTE' : 'ADVERTENCIA OPCIONAL'
}

/**
 * Texto corto para un problema, según severidad y tipo de error. No debe
 * usarse "Faltan valores obligatorios" para una advertencia: ese campo es
 * opcional y no bloquea nada.
 */
export function mensajeSeveridad(tipoError: string, bloqueante: boolean): string {
  if (tipoError === 'VALOR_OBLIGATORIO_VACIO') {
    return bloqueante ? 'Campo obligatorio vacío.' : 'Dato opcional no informado.'
  }
  if (bloqueante) return 'Este dato impide importar.'
  return 'Dato opcional no informado. Puedes revisarlo o continuar.'
}
