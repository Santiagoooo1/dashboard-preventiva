import { useState } from 'react'
import type { ResumenTrazabilidadImportacionTrabajoDto } from '../../api/types'
import { formatearFechaHora } from '../../utils/trazabilidad/fechas'
import styles from './Trazabilidad.module.css'

interface ResumenTrazabilidadCardProps {
  resumen: ResumenTrazabilidadImportacionTrabajoDto
}

export function ResumenTrazabilidadCard({ resumen }: ResumenTrazabilidadCardProps) {
  const [copiado, setCopiado] = useState(false)

  const copiarHash = async () => {
    if (!resumen.hashArchivoOriginal) return
    try {
      await navigator.clipboard.writeText(resumen.hashArchivoOriginal)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2000)
    } catch {
      // Si el navegador no permite copiar, el hash sigue seleccionable a mano.
    }
  }

  return (
    <ul className={styles.resumenLista}>
      <li>Archivo original: {resumen.nombreArchivoOriginal ?? '—'}</li>
      <li className={styles.hashFila}>
        Hash del archivo original:{' '}
        {resumen.hashArchivoOriginal ? (
          <>
            <span className={styles.hashValor}>{resumen.hashArchivoOriginal}</span>
            <button type="button" className={`btn btnSecondary ${styles.botonCopiar}`} onClick={copiarHash}>
              Copiar
            </button>
            {copiado && <span className={styles.avisoCopiado}>Copiado</span>}
          </>
        ) : (
          '—'
        )}
      </li>
      <li>Dataset: {resumen.datasetId}</li>
      <li>Plantilla: {resumen.plantillaId}</li>
      <li>Copia de trabajo: {resumen.importacionTrabajoId}</li>
      <li>Importación final: {resumen.importacionGenericaId ?? 'aún no importada'}</li>
      <li>Estado: {resumen.estado}</li>
      <li>Filas leídas: {resumen.totalFilasLeidas}</li>
      <li>Filas excluidas: {resumen.totalFilasExcluidas}</li>
      <li>Errores: {resumen.totalErrores}</li>
      <li>Advertencias: {resumen.totalAdvertencias}</li>
      <li>Importable: {resumen.importable ? 'Sí' : 'No'}</li>
      <li>Eventos registrados: {resumen.totalEventos}</li>
      <li>Correcciones manuales: {resumen.totalCorreccionesManuales}</li>
      <li>Correcciones en bloque: {resumen.totalCorreccionesEnBloque}</li>
      <li>Normalizaciones: {resumen.totalNormalizaciones}</li>
      <li>Exclusiones: {resumen.totalExclusiones}</li>
      <li>Restauraciones: {resumen.totalRestauraciones}</li>
      <li>Creada: {formatearFechaHora(resumen.fechaCreacion)}</li>
      <li>Última revalidación: {formatearFechaHora(resumen.fechaUltimaRevalidacion)}</li>
    </ul>
  )
}
