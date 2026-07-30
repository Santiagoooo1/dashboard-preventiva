// Traduce los estados técnicos que devuelve el backend (ImportacionTrabajo,
// ImportacionGenerica) a texto en lenguaje claro para pantalla. No cubre
// estados que no se muestran nunca en crudo al usuario.
const TEXTOS_ESTADO: Record<string, string> = {
  EN_EDICION: 'En edición',
  LISTA_PARA_IMPORTAR: 'Lista para importar',
  IMPORTADA: 'Importada',
  IMPORTADA_CON_ERRORES: 'Importada con advertencias',
  DESCARTADA: 'Descartada',
  RECHAZADA: 'Rechazada',
}

export function traducirEstado(estado: string): string {
  return TEXTOS_ESTADO[estado] ?? estado
}
