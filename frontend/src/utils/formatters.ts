export function formatNumber(value: number | null | undefined, decimals = 2): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return value.toLocaleString('es-ES', { maximumFractionDigits: decimals })
}

export function clampAncho(ancho: number): number {
  return Math.min(Math.max(ancho, 1), 12)
}
