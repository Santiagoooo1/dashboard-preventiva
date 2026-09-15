import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router'
import type { InformeClinicoResponseDto } from '../api/types'
import { obtenerInformeConResultados } from '../api/informesApi'
import { HojaInforme } from '../components/informes/HojaInforme'
import styles from '../components/informes/Informe.module.css'

/**
 * El documento y nada más (Fase 6.9R.1).
 *
 * <p>Vive FUERA de `MainLayout`: sin navegación, sin botones, sin tarjetas de
 * la aplicación. Se intentó primero ocultar el cromo con reglas `@media print`
 * desde la vista previa, y esa vía obliga a ir persiguiendo cada componente
 * nuevo que alguien añada a la interfaz; en cuanto se escapa uno, aparece
 * impreso en el informe de un hospital. Aquí no hay nada que ocultar porque no
 * hay nada más en la página.
 *
 * <p>Una sola llamada a `/informes/:id/resultados`, la misma que la vista
 * previa, y el mismo `HojaInforme`. Esta ruta es la que la 6.9R.2 abrirá en
 * Chromium headless para generar el PDF, así que lo que se vea aquí es lo que
 * saldrá impreso.
 */
export function InformeImpresionPage() {
  const { informeId } = useParams()
  const [parametros] = useSearchParams()
  const [informe, setInforme] = useState<InformeClinicoResponseDto | null>(null)
  const [error, setError] = useState<string | null>(null)

  // `?auto=1` lanza el diálogo de impresión al terminar de pintar. Sin el
  // parámetro la ruta es solo una vista limpia, útil para revisar el documento
  // sin que salte un diálogo encima.
  const automatico = parametros.get('auto') === '1'

  useEffect(() => {
    if (!informeId) return
    obtenerInformeConResultados(informeId)
      .then(setInforme)
      .catch((e) => setError(e instanceof Error ? e.message : 'No se pudo cargar el informe.'))
  }, [informeId])

  useEffect(() => {
    if (!informe || !automatico) return
    // Dos marcos: el primero pinta el DOM, el segundo deja al navegador aplicar
    // el layout antes de medir las páginas. Imprimir en el mismo tick produce
    // documentos con la última hoja a medio componer.
    const id = requestAnimationFrame(() => requestAnimationFrame(() => window.print()))
    return () => cancelAnimationFrame(id)
  }, [informe, automatico])

  if (error) {
    return <p className={styles.avisoImpresion}>{error}</p>
  }

  if (!informe) {
    // Marcador que la 6.9R.2 puede esperar antes de capturar: mientras exista,
    // el documento no está listo.
    return (
      <p className={styles.avisoImpresion} data-informe-estado="calculando">
        Calculando el informe…
      </p>
    )
  }

  const paginas = informe.paginas ?? []

  return (
    <div className={styles.documento} data-informe-estado="listo">
      {paginas.map((pagina, i) => (
        <HojaInforme
          key={pagina.id}
          informe={informe}
          pagina={pagina}
          numero={i + 1}
          total={paginas.length}
        />
      ))}
    </div>
  )
}
