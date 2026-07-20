export const AVISO_FORMATO_CODIGO =
  'Se recomienda usar letras, números y guiones bajos, sin espacios ni acentos.'

// Aviso suave, no bloqueante: el backend no exige este formato todavía,
// pero los códigos limpios evitan problemas futuros de integración.
export function esCodigoRecomendado(codigo: string): boolean {
  return /^[A-Za-z0-9_]*$/.test(codigo)
}

// Devuelve el aviso solo cuando hay algo escrito y no cumple el formato.
export function avisoCodigo(codigo: string): string | undefined {
  return codigo !== '' && !esCodigoRecomendado(codigo) ? AVISO_FORMATO_CODIGO : undefined
}
