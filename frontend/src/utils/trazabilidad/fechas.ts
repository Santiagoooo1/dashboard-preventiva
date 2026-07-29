export function formatearFechaHora(fechaIso: string | null): string {
  if (!fechaIso) return '—'
  const fecha = new Date(fechaIso)
  if (Number.isNaN(fecha.getTime())) return fechaIso
  return fecha.toLocaleString('es-ES', { dateStyle: 'medium', timeStyle: 'short' })
}
